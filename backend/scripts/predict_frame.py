import json
import hashlib
from pathlib import Path

import cv2
import numpy as np


def find_first_model(repo_root: Path):
    """
    Looks for:
      repo_root/backend/models/**.prototxt
      repo_root/backend/models/**.caffemodel
    Returns (prototxt_path, caffemodel_path)
    """
    base = repo_root / "backend" / "models"
    prototxts = list(base.rglob("*.prototxt"))
    caffes = list(base.rglob("*.caffemodel"))

    print("Searching models in:", base)
    print("Found prototxts:", len(prototxts))
    print("Found caffemodels:", len(caffes))

    if not prototxts or not caffes:
        raise FileNotFoundError(
            f"Could not find .prototxt/.caffemodel under {base}. "
            f"Check your folder location."
        )

    # Pick the first pair found (fine for now). Later you can hardcode a specific folder.
    return prototxts[0], caffes[0]


def main():
    # If this file is backend/scripts/predict_frame.py:
    # parents[0] = scripts, parents[1] = backend, parents[2] = repo root
    repo_root = Path(__file__).resolve().parents[2]
    backend_root = Path(__file__).resolve().parents[1]

    # ---- load model ----
    prototxt, model = find_first_model(repo_root)
    print("Using prototxt:", prototxt)
    print("Using caffemodel:", model)

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

    np.set_printoptions(precision=12, suppress=False)

    results = []
    for s in spots:
        sid = s["id"]
        x, y, w, h = s["rect"]

        patch = img[y : y + h, x : x + w]
        if patch.size == 0:
            print("EMPTY PATCH (out of bounds?)", sid, s["rect"])
            continue

        # Debug patch saved + hash so you can confirm it changes
        patch_debug_path = backend_root / "data" / "patch_debug.jpg"
        cv2.imwrite(str(patch_debug_path), patch)
        md5 = hashlib.md5(patch.tobytes()).hexdigest()
        print(f"{sid} patch shape={patch.shape} mean={float(patch.mean()):.4f} md5={md5}")

        blob = cv2.dnn.blobFromImage(
            patch,
            scalefactor=SCALE,
            size=(INPUT_W, INPUT_H),
            mean=MEAN,
            swapRB=SWAP_RB,
            crop=False,
        )

        net.setInput(blob)
        out = net.forward().flatten()  # default output is fine

        if out.size != 2:
            raise RuntimeError(f"Expected 2 outputs from softmax, got shape {out.shape} for {sid}: {out}")

        p_empty = float(out[0])
        p_occ = float(out[1])
        occupied = p_occ > 0.5

        print(f"{sid} out={out}  occupied={occupied}  conf={p_occ:.6f}")

        results.append(
            {
                "id": sid,
                "occupied": occupied,
                "confidence": p_occ,
                "p_empty": p_empty,
            }
        )

    # Optional: write results to a file for the frontend/backend to consume
    out_path = backend_root / "data" / "predictions.json"
    with open(out_path, "w") as f:
        json.dump({"spots": results}, f, indent=2)
    print("Wrote:", out_path)


if __name__ == "__main__":
    main()
