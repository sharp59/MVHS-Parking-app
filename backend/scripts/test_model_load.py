import cv2

net = cv2.dnn.readNetFromCaffe(
    "models/cnrpark/method1/deploy.prototxt",
    "models/cnrpark/method1/model.caffemodel"
)

print("Model loaded successfully")