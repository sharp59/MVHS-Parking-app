import json
from pathlib import Path

import cv2
import numpy as np

# ==========================================================
# BACKGROUND SUBTRACTION (OPTIONAL FUTURE FEATURE)
# ----------------------------------------------------------
# When you capture an empty parking lot image, enable this:
#
# background = cv2.imread("background.jpg")
#
# Then later replace the crop line with:
#
# crop = diff[y:y+h, x:x+w].copy()
#
# ==========================================================


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

    return prototxts[0], caffes[0]


def main():

    repo_root = Path(__file__).resolve().parents[2]
    backend_root = Path(__file__).resolve().parents[1]

    # -----------------------------
    # LOAD MODEL
    # -----------------------------

    prototxt, model = find_first_model(repo_root)

    net = cv2.dnn.readNetFromCaffe(str(prototxt), str(model))

    # -----------------------------
    # LOAD DATA
    # -----------------------------

    img_path = backend_root / "data" / "frame.jpg"
    spots_path = backend_root / "data" / "spots.json"

    img = cv2.imread(str(img_path))

    if img is None:
        raise FileNotFoundError(f"Could not read image: {img_path}")

    # ==========================================================
    # BACKGROUND SUBTRACTION (ENABLE LATER)
    #
    # diff = cv2.absdiff(img, background)
    #
    # ==========================================================

    with open(spots_path, "r") as f:
        spots = json.load(f)["spots"]

    # -----------------------------
    # INFERENCE SETTINGS
    # -----------------------------

    INPUT_W, INPUT_H = 224, 224
    SCALE = 1 / 255.0
    MEAN = (0, 0, 0)
    SWAP_RB = False

    np.set_printoptions(precision=8, suppress=False)

    results = []
    correct = 0
    total = 0

    # -----------------------------
    # PROCESS EACH PARKING SPOT
    # -----------------------------

    for s in spots:

        sid = s["id"]

        pts = np.array(s["polygon"], np.int32)

        x, y, w, h = cv2.boundingRect(pts)

        # ------------------------------------------------------
        # NORMAL CROPPING (CURRENT BEHAVIOR)
        # ------------------------------------------------------

        crop = img[y:y+h, x:x+w].copy()

        # ------------------------------------------------------
        # BACKGROUND SUBTRACTION VERSION (ENABLE LATER)
        #
        # crop = diff[y:y+h, x:x+w].copy()
        #
        # ------------------------------------------------------

        mask = np.zeros((h, w), dtype=np.uint8)

        pts_shifted = pts - [x, y]

        cv2.fillPoly(mask, [pts_shifted], 255)

        patch = cv2.bitwise_and(crop, crop, mask=mask)

        if patch.size == 0:
            print(f"{sid}: EMPTY PATCH (out of bounds)")
            continue

        # Resize to CNN input
        patch = cv2.resize(patch, (INPUT_W, INPUT_H))

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
            raise RuntimeError(f"Expected 2 outputs, got {out.shape} for {sid}: {out}")

        p_unocc = float(out[0])
        p_occ = float(out[1])

        pred_occupied = p_occ > 0.5

        real_occupied = bool(s.get("occupied", False))

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

    # -----------------------------
    # SUMMARY
    # -----------------------------

    print(f"\nAccuracy: {correct}/{total}")

    # -----------------------------
    # SAVE RESULTS
    # -----------------------------

    out_path = backend_root / "data" / "predictions.json"

    with open(out_path, "w") as f:
        json.dump(
            {
                "spots": results,
                "correct": correct,
                "total": total,
            },
            f,
            indent=2,
        )

    print("Wrote:", out_path)


if __name__ == "__main__":
    main()