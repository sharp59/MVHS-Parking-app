import json
from pathlib import Path

import cv2
import numpy as np


def find_first_model(repo_root: Path):
    """
    Looks for:
      repo_root/backend/models/method4/**.prototxt
      repo_root/backend/models/method4/**.caffemodel
    Returns (prototxt_path, caffemodel_path)
    """
    base = repo_root / "backend" / "models" / "method4"
    prototxts = list(base.rglob("*.prototxt"))
    caffes = list(base.rglob("*.caffemodel"))

    if not prototxts or not caffes:
        raise FileNotFoundError(
            f"Could not find .prototxt/.caffemodel under {base}. Check your folder location."
        )

    # Pick the first pair found (fine for now).
    return prototxts[0], caffes[0]


def main():
    repo_root = Path(__file__).resolve().parents[2]   # .../MVHS-Parking-app
    backend_root = Path(__file__).resolve().parents[1]  # .../MVHS-Parking-app/backend

    # ---- load model ----
    prototxt, model = find_first_model(repo_root)
    net = cv2.dnn.readNetFromCaffe(str(prototxt), str(model))

    # ---- load data ----
    img_path = backend_root / "data" / "frame.jpg"
    spots_path = backend_root / "data" / "spots.json"

    img = cv2.imread(str(img_path))
    if img is None:
        raise FileNotFoundError(f"Could not read image: {img_path}")

    with open(spots_path, "r") as f:
        spots = json.load(f)["spots"]

    # ---- inference settings (the ones that worked for you) ----
    INPUT_W, INPUT_H = 224, 224
    SCALE = 1 / 255.0
    MEAN = (0, 0, 0)
    SWAP_RB = False

    np.set_printoptions(precision=8, suppress=False)

    results = []
    correct = 0
    total = 0

    for s in spots:
        sid = s["id"]
        x, y, w, h = s["rect"]

        # "real value" from spots.json (defaults to False if missing)
        real_occupied = bool(s.get("occupied", False))

        patch = img[y : y + h, x : x + w]
        if patch.size == 0:
            print(f"{sid}: [?, ?], ?, real={real_occupied}, correct?=False  (EMPTY PATCH out of bounds)")
            continue

        blob = cv2.dnn.blobFromImage(
            patch,
            scalefactor=SCALE,
            size=(INPUT_W, INPUT_H),
            mean=MEAN,
            swapRB=SWAP_RB,
            crop=False,
        )

        net.setInput(blob)
        out = net.forward().flatten()

        if out.size != 2:
            raise RuntimeError(f"Expected 2 outputs, got shape {out.shape} for {sid}: {out}")

        p_unocc = float(out[0])  # unoccupied/empty confidence
        p_occ = float(out[1])    # occupied confidence

        pred_occupied = p_occ > 0.5
        is_correct = (pred_occupied == real_occupied)

        total += 1
        if is_correct:
            correct += 1

        pred_text = "occupied" if pred_occupied else "unoccupied"
        real_text = "occupied" if real_occupied else "unoccupied"

        print(
            f"{sid}: [{p_unocc:.6f}, {p_occ:.6f}], "
            f"pred={pred_text}, real={real_text}, correct?={is_correct}"
        )

        results.append(
            {
                "id": sid,
                "predicted_occupied": pred_occupied,
                "real_occupied": real_occupied,
                "confidence_occupied": p_occ,
                "confidence_unoccupied": p_unocc,
                "correct": is_correct,
            }
        )

    # Summary line
    print(f"\n{correct}/{total}")

    # Optional: write results for later use
    out_path = backend_root / "data" / "predictions.json"
    with open(out_path, "w") as f:
        json.dump({"spots": results, "correct": correct, "total": total}, f, indent=2)
    print("Wrote:", out_path)


if __name__ == "__main__":
    main()
