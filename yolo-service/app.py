from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
from ultralytics import YOLO
import cv2
import numpy as np
import os
import torch
from typing import List

app = FastAPI(title="YOLO Vehicle Detection Service")

VEHICLE_CLASSES = {2: "car", 5: "bus", 7: "truck", 3: "motorcycle", 1: "bicycle"}

model = None
model_path = os.path.join(os.path.dirname(__file__), "weights", "yolov8n.pt")


def get_model():
    global model
    if model is None:
        try:
            model = YOLO(model_path)
        except Exception:
            model = YOLO("yolov8n.pt")
        if torch.cuda.is_available():
            model.half()
            torch.backends.cudnn.benchmark = True
    return model


class DetectionResult(BaseModel):
    type: str
    confidence: float
    x: float
    y: float
    width: float
    height: float


class DetectResponse(BaseModel):
    vehicleCount: int
    density: float
    vehicles: List[DetectionResult]


def process_frame(frame):
    if frame is None:
        return 0, 0.0, []

    results = get_model()(frame, verbose=False, imgsz=416, conf=0.25)
    frame_area = frame.shape[0] * frame.shape[1]

    vehicles = []
    total_bbox_area = 0

    for result in results:
        for box in result.boxes:
            cls_id = int(box.cls[0])
            if cls_id in VEHICLE_CLASSES:
                conf = float(box.conf[0])
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                w = x2 - x1
                h = y2 - y1
                total_bbox_area += w * h

                vehicles.append(DetectionResult(
                    type=VEHICLE_CLASSES[cls_id],
                    confidence=round(conf, 3),
                    x=round(x1 / frame.shape[1], 4),
                    y=round(y1 / frame.shape[0], 4),
                    width=round(w / frame.shape[1], 4),
                    height=round(h / frame.shape[0], 4)
                ))

    density = min(1.0, total_bbox_area / frame_area) if frame_area > 0 else 0.0
    return len(vehicles), round(density, 4), vehicles


def decode_image(data: bytes):
    nparr = np.frombuffer(data, np.uint8)
    return cv2.imdecode(nparr, cv2.IMREAD_COLOR)


@app.post("/detect/batch")
async def detect_batch(files: List[UploadFile] = File(...)):
    results = {}

    frames = []
    valid_indices = []

    for i, file in enumerate(files):
        data = await file.read()
        frame = decode_image(data)
        if frame is not None:
            frames.append(frame)
            valid_indices.append(i)
        else:
            results[i] = {"vehicleCount": 0, "density": 0.0, "vehicles": []}

    if not frames:
        return results

    batch_results = get_model()(frames, verbose=False, imgsz=416, conf=0.25)

    for idx, result_idx in enumerate(valid_indices):
        frame = frames[idx]
        frame_area = frame.shape[0] * frame.shape[1]
        vehicles = []
        total_bbox_area = 0

        for box in batch_results[idx].boxes:
            cls_id = int(box.cls[0])
            if cls_id in VEHICLE_CLASSES:
                conf = float(box.conf[0])
                x1, y1, x2, y2 = box.xyxy[0].tolist()
                w = x2 - x1
                h = y2 - y1
                total_bbox_area += w * h

                vehicles.append({
                    "type": VEHICLE_CLASSES[cls_id],
                    "confidence": round(conf, 3),
                    "x": round(x1 / frame.shape[1], 4),
                    "y": round(y1 / frame.shape[0], 4),
                    "width": round(w / frame.shape[1], 4),
                    "height": round(h / frame.shape[0], 4)
                })

        density = min(1.0, total_bbox_area / frame_area) if frame_area > 0 else 0.0
        results[result_idx] = {
            "vehicleCount": len(vehicles),
            "density": round(density, 4),
            "vehicles": vehicles
        }

    return results


@app.get("/health")
async def health():
    return {"status": "ok", "model_loaded": model is not None}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5001)
