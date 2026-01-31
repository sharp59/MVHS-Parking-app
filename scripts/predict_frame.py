import json
import cv2
import numpy as np
from pathlib import Path

# ---- load model ----
root = Path(__file__).resolve().parents[1]
prototxt = list((root / "models/cnrpark").rglob("*.prototxt"))[0]
model = list((root / "models/cnrpark").rglob("*.caffemodel"))[0]

net = cv2.dnn.readNetFromCaffe(str(prototxt), str(model))

# TODO: we may adjust these after inspecting deploy.prototxt
INPUT_W, INPUT_H = 224, 224
MEAN = (104, 117, 123)

# ---- load data ----
img = cv2.imread("data/frame.jpg")
with open("data/spots.json") as f:
    spots = json.load(f)["spots"]

for s in spots:
    x, y, w, h = s["rect"]
    patch = img[y:y+h, x:x+w]

    cv2.imwrite("data/patch_debug.jpg", patch)
    print("patch shape:", patch.shape)
    print("patch mean pixel:", patch.mean())

    blob = cv2.dnn.blobFromImage(
        patch,
        scalefactor=1.0,
        size=(INPUT_W, INPUT_H),
        mean=MEAN,
        swapRB=False,
        crop=False
    )

    net.setInput(blob)
    out = net.forward().flatten()

    print(s["id"], out)