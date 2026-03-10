import cv2

net = cv2.dnn.readNetFromCaffe(
    "models/cnrpark/method4/deploy.prototxt",
    "models/cnrpark/method4/model.caffemodel"
)

print("Model loaded successfully")