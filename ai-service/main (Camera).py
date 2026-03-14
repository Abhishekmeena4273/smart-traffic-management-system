import cv2
from ultralytics import YOLO
import requests
import time

# 1. Load the YOLO Model (It will download automatically the first time)
print("Loading AI Model...")
model = YOLO('yolov8n.pt')  # 'n' means nano (fastest for laptops)

# 2. Define what we consider as 'vehicles'
# COCO dataset classes: 2=car, 3=motorcycle, 5=bus, 7=truck
VEHICLE_CLASSES = [2, 3, 5, 7] 

# 3. Open Webcam (0 is usually the default camera)
cap = cv2.VideoCapture(0)

# Backend URL
BACKEND_URL = "http://localhost:8080/api/traffic/update"

print("Starting Traffic Detection... Press 'q' to quit")

while True:
    ret, frame = cap.read()
    if not ret:
        break

    # 4. Run AI Detection
    results = model(frame, verbose=False)
    
    # 5. Count Vehicles
    vehicle_count = 0
    for result in results:
        boxes = result.boxes
        for box in boxes:
            cls = int(box.cls[0])
            if cls in VEHICLE_CLASSES:
                vehicle_count += 1
    
    # 6. Send Data to Spring Boot
    try:
        data = {
            "laneId": "LANE_NORTH",
            "vehicleCount": vehicle_count,
            "signalState": "UNKNOWN" # Backend will decide this
        }
        # Send every 2 seconds to avoid flooding
        requests.post(BACKEND_URL, json=data, timeout=1)
        print(f"Sent: {vehicle_count} vehicles detected")
    except Exception as e:
        print(f"Could not connect to Backend: {e}")

    # 7. Show the Video with Boxes
    # Plot results on the frame
    annotated_frame = results[0].plot()
    cv2.imshow("Smart Traffic AI", annotated_frame)

    # Press 'q' to exit
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()