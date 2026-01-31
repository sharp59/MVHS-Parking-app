import json
import cv2
import numpy as np
from pathlib import Path
import hashlib
import numpy as np

#empty: [270, 550, 50, 50]
#occupied: [230, 900, 180, 100]

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

def run_variant(name, scalefactor, mean, swapRB):
    blob = cv2.dnn.blobFromImage(
        patch, scalefactor=scalefactor, size=(224,224),
        mean=mean, swapRB=swapRB, crop=False
    )
    net.setInput(blob)
    out = net.forward("score").flatten()
    print(name, out)


for s in spots:
    x, y, w, h = s["rect"]
    patch = img[y:y+h, x:x+w]

    cv2.imwrite("data/patch_debug.jpg", patch)

    h = hashlib.md5(patch.tobytes()).hexdigest()
    print("patch shape:", patch.shape, "md5:", h, "mean:", float(patch.mean()))
    

    cv2.imwrite("data/patch_debug.jpg", patch)
    print("patch shape:", patch.shape)
    print("patch mean pixel:", patch.mean())

    blob = cv2.dnn.blobFromImage(
        patch,
        scalefactor=1/255.0,
        size=(224, 224),
        mean=(0, 0, 0),
        swapRB=False,
        crop=False
    )
    out = net.forward("score").flatten()


    net.setInput(blob)
    out = net.forward("score").flatten()

    np.set_printoptions(precision=12, suppress=False)
    print(s["id"], out)