import cv2
from ultralytics import YOLO
import requests
import time
import os

# 1. Load the YOLO Model
# 1. Load the YOLO Model
print("Loading AI Model...")
model = YOLO('yolov8n.pt')

# Use GPU if available
import torch
if torch.cuda.is_available():
    print(f"🎮 Using GPU: {torch.cuda.get_device_name(0)}")
    # YOLOv8 automatically uses GPU if available
else:
    print("⚠️  Using CPU (GPU not available)")

# 2. Define vehicle classes
VEHICLE_CLASSES = [2, 3, 5, 7]  # car, motorcycle, bus, truck

# 3. Check if video file exists, otherwise use webcam
video_file = 'traffic.mp4'
image_file = 'traffic.jpg'

if os.path.exists(video_file):
    print(f" Using video file: {video_file}")
    cap = cv2.VideoCapture(video_file)
    # Enable looping
    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
elif os.path.exists(image_file):
    print(f"🖼️ Using image file: {image_file}")
    cap = cv2.VideoCapture(image_file)
else:
    print("📹 No video/image found, using webcam")
    cap = cv2.VideoCapture(0)

# Backend URL
BACKEND_URL = "http://localhost:8080/api/traffic/update"

print("Starting Traffic Detection... Press 'q' to quit")

frame_count = 0
while True:
    ret, frame = cap.read()
    
    # If video ends, loop back to start
    if not ret:
        if os.path.exists(video_file):
            print("🔄 Video ended, looping...")
            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
            continue
        else:
            break

    frame_count += 1
    
    # Process every 3rd frame for speed
    if frame_count % 3 != 0:
        continue

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
            "signalState": "UNKNOWN"
        }
        requests.post(BACKEND_URL, json=data, timeout=1)
        print(f"📊 Sent: {vehicle_count} vehicles detected")
    except Exception as e:
        print(f"❌ Could not connect to Backend: {e}")

    # 7. Show the Video with Boxes
    annotated_frame = results[0].plot()
    
    # Add text overlay
    cv2.putText(annotated_frame, f"Vehicles: {vehicle_count}", (10, 30), 
                cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
    
    cv2.imshow("Smart Traffic AI", annotated_frame)

    # Press 'q' to exit
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv2.destroyAllWindows()