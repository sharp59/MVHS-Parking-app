import json
import cv2

img = cv2.imread("data/frame.jpg")

with open("data/spots.json") as f:
    spots = json.load(f)["spots"]

for s in spots:
    x, y, w, h = s["rect"]
    cv2.rectangle(img, (x, y), (x+w, y+h), (0, 255, 0), 2)
    cv2.putText(img, s["id"], (x, y-5),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0,255,0), 1)

cv2.imwrite("data/debug_boxes.jpg", img)
print("Wrote data/debug_boxes.jpg")