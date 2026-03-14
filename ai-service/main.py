# ============================================================================
# SMART TRAFFIC AI SERVICE - FIXED VERSION
# ============================================================================
from flask import Flask, Response, request, jsonify, render_template_string
import cv2
from ultralytics import YOLO
import requests
import os
import torch
import threading
from werkzeug.utils import secure_filename
import glob
import time

# ============================================================================
# FLASK SETUP
# ============================================================================
app = Flask(__name__)
app.config['UPLOAD_FOLDER'] = 'uploads'
app.config['MAX_CONTENT_LENGTH'] = 1024 * 1024 * 1024  # 1GB max (increased from 500MB)
ALLOWED_EXTENSIONS = {'mp4', 'avi', 'mov', 'mkv', 'webm'}

# Create uploads folder
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

# Global variables
current_frame = None
vehicle_count_global = 0
cap = None
current_video = None
is_running = False
video_thread = None

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def get_available_videos():
    """Get list of all available video files"""
    videos = []
    if os.path.exists(app.config['UPLOAD_FOLDER']):
        for f in os.listdir(app.config['UPLOAD_FOLDER']):
            if f.lower().endswith(tuple(ALLOWED_EXTENSIONS)):
                videos.append(f)
    for f in os.listdir('.'):
        if f.lower().endswith(tuple(ALLOWED_EXTENSIONS)) and f != 'main.py':
            if f not in videos:
                videos.append(f)
    return videos

# ============================================================================
# AI MODEL SETUP
# ============================================================================
print("🤖 Loading YOLO AI Model...")
model = YOLO('yolov8n.pt')

if torch.cuda.is_available():
    print(f"🎮 GPU Detected: {torch.cuda.get_device_name(0)}")
else:
    print("⚠️  Running on CPU")

VEHICLE_CLASSES = [2, 3, 5, 7]
BACKEND_URL = "http://localhost:8080/api/traffic/update"

# ============================================================================
# VIDEO PROCESSING FUNCTION (No yield - runs in background)
# ============================================================================
def process_video_loop(video_path):
    """Background thread to process video frames"""
    global current_frame, vehicle_count_global, cap, is_running
    
    cap = cv2.VideoCapture(video_path)
    is_running = True
    frame_count = 0
    
    print(f"🎬 Processing video: {video_path}")
    
    while is_running:
        if not cap.isOpened():
            print("⚠️  Video not open, reopening...")
            cap = cv2.VideoCapture(video_path)
            time.sleep(1)
            continue
        
        ret, frame = cap.read()
        
        if not ret:
            print("🔄 Video ended, looping...")
            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
            time.sleep(0.1)
            continue
        
        frame_count += 1
        if frame_count % 2 != 0:
            continue
        
        # AI Detection
        results = model(frame, verbose=False)
        
        # Count vehicles
        vehicle_count = 0
        for result in results:
            boxes = result.boxes
            for box in boxes:
                cls = int(box.cls[0])
                if cls in VEHICLE_CLASSES:
                    vehicle_count += 1
        
        vehicle_count_global = vehicle_count
        
        # Send to backend
        try:
            data = {
                "laneId": "LANE_NORTH",
                "vehicleCount": vehicle_count,
                "signalState": "UNKNOWN"
            }
            response = requests.post(BACKEND_URL, json=data, timeout=1)
            print(f"📊 Sent: {vehicle_count} vehicles | Status: {response.status_code}")
        except Exception as e:
            print(f"❌ Backend error: {e}")
        
        # Draw results
        annotated_frame = results[0].plot()
        cv2.putText(annotated_frame, f"Vehicles: {vehicle_count}", (10, 30), 
                    cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
        
        status_color = (0, 255, 0) if vehicle_count > 2 else (0, 0, 255)
        status_text = "HIGH TRAFFIC" if vehicle_count > 2 else "LOW TRAFFIC"
        cv2.putText(annotated_frame, status_text, (10, 70), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, status_color, 2)
        
        current_frame = annotated_frame
        
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break
    
    is_running = False
    if cap:
        cap.release()
    cv2.destroyAllWindows()

# ============================================================================
# FRAME GENERATOR FOR STREAMING (Has yield - for Flask response)
# ============================================================================
def generate_frames():
    """Generate frames from current_frame for MJPEG stream"""
    while True:
        if current_frame is not None:
            ret, jpeg = cv2.imencode('.jpg', current_frame)
            if ret:
                yield (b'--frame\r\n'
                       b'Content-Type: image/jpeg\r\n\r\n' + jpeg.tobytes() + b'\r\n')
        else:
            import numpy as np
            placeholder = np.zeros((480, 640, 3), dtype=np.uint8)
            cv2.putText(placeholder, "Loading...", (220, 240),
                       cv2.FONT_HERSHEY_SIMPLEX, 1, (255, 255, 255), 2)
            ret, jpeg = cv2.imencode('.jpg', placeholder)
            if ret:
                yield (b'--frame\r\n'
                       b'Content-Type: image/jpeg\r\n\r\n' + jpeg.tobytes() + b'\r\n')
        time.sleep(0.1)

# ============================================================================
# START VIDEO PROCESSING
# ============================================================================
def start_video_processing(video_path):
    """Start video processing in background thread"""
    global video_thread, is_running, current_video
    
    # Stop existing thread
    is_running = False
    if video_thread and video_thread.is_alive():
        video_thread.join(timeout=2)
    
    # Start new thread
    current_video = os.path.basename(video_path)
    video_thread = threading.Thread(target=process_video_loop, args=(video_path,), daemon=True)
    video_thread.start()
    print(f"✅ Started processing: {current_video}")

# ============================================================================
# FLASK ROUTES
# ============================================================================

@app.route('/')
def index():
    """Video upload and management interface"""
    videos = get_available_videos()
    return render_template_string('''
    <!DOCTYPE html>
    <html>
    <head>
        <title>🚦 Smart Traffic - Video Manager</title>
        <style>
            body {
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
                color: white;
                margin: 0;
                padding: 40px;
                min-height: 100vh;
            }
            .container {
                max-width: 800px;
                margin: 0 auto;
                background: rgba(255,255,255,0.1);
                padding: 40px;
                border-radius: 16px;
                backdrop-filter: blur(10px);
            }
            h1 { text-align: center; margin-bottom: 30px; }
            .upload-section {
                background: rgba(255,255,255,0.05);
                padding: 30px;
                border-radius: 12px;
                margin-bottom: 30px;
                border: 2px dashed rgba(76, 175, 80, 0.5);
            }
            .video-list { margin-top: 20px; }
            .video-item {
                background: rgba(255,255,255,0.05);
                padding: 15px;
                margin: 10px 0;
                border-radius: 8px;
                display: flex;
                justify-content: space-between;
                align-items: center;
            }
            .video-item.active {
                border: 2px solid #4CAF50;
                background: rgba(76, 175, 80, 0.2);
            }
            button {
                background: #4CAF50;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 8px;
                cursor: pointer;
                font-size: 14px;
                margin: 5px;
            }
            button:hover { background: #45a049; }
            button.secondary { background: #2196F3; }
            button.secondary:hover { background: #1976D2; }
            input[type="file"] { margin: 10px 0; }
            .status {
                text-align: center;
                padding: 10px;
                margin: 10px 0;
                border-radius: 8px;
                background: rgba(76, 175, 80, 0.2);
            }
            .error { background: rgba(244, 67, 54, 0.2); }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>🚦 Smart Traffic Video Manager</h1>
            
            <div class="upload-section">
                <h3>📤 Upload New Video</h3>
                <p style="color:#aaa;font-size:13px;">Max size: 1GB | Supported: MP4, AVI, MOV, MKV, WEBM</p>
                <form method="post" action="/upload" enctype="multipart/form-data">
                    <input type="file" name="video" accept="video/*" required>
                    <br>
                    <button type="submit">Upload Video</button>
                </form>
            </div>
            
            <div class="video-list">
                <h3>📹 Available Videos</h3>
                {% if videos %}
                    {% for video in videos %}
                    <div class="video-item {% if video == current_video %}active{% endif %}">
                        <span>🎬 {{ video }}</span>
                        <div>
                            {% if video == current_video %}
                                <button disabled>▶️ Currently Playing</button>
                            {% else %}
                                <form method="post" action="/select_video" style="display:inline;">
                                    <input type="hidden" name="video" value="{{ video }}">
                                    <button type="submit">▶️ Play</button>
                                </form>
                            {% endif %}
                        </div>
                    </div>
                    {% endfor %}
                {% else %}
                    <p style="text-align:center;color:#888;">No videos found. Upload one above!</p>
                {% endif %}
            </div>
            
            <div style="text-align:center;margin-top:30px;">
                <a href="/video_feed" target="_blank">
                    <button class="secondary">📺 View Live Stream</button>
                </a>
                <a href="http://localhost:3000" target="_blank">
                    <button class="secondary">🖥️ Open Dashboard</button>
                </a>
                <a href="/health">
                    <button class="secondary">❤️ System Health</button>
                </a>
            </div>
            
            {% if current_video %}
            <div class="status">
                ✅ Currently Playing: <strong>{{ current_video }}</strong>
            </div>
            {% endif %}
        </div>
    </body>
    </html>
    ''', videos=videos, current_video=current_video)

@app.route('/upload', methods=['POST'])
def upload_video():
    """Handle video upload"""
    try:
        if 'video' not in request.files:
            return jsonify({'error': 'No file part'}), 400
        
        file = request.files['video']
        if file.filename == '':
            return jsonify({'error': 'No selected file'}), 400
        
        if file and allowed_file(file.filename):
            filename = secure_filename(file.filename)
            filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
            file.save(filepath)
            print(f"✅ Video uploaded: {filename} ({os.path.getsize(filepath) / 1024 / 1024:.2f} MB)")
            return jsonify({'success': True, 'filename': filename, 'message': 'Upload successful! Refresh to see it in the list.'})
        
        return jsonify({'error': 'Invalid file type. Use MP4, AVI, MOV, MKV, or WEBM'}), 400
    except Exception as e:
        print(f"❌ Upload error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/select_video', methods=['POST'])
def select_video():
    """Switch to a different video"""
    try:
        video_name = request.form.get('video')
        if not video_name:
            return jsonify({'error': 'No video specified'}), 400
        
        # Find video path
        video_path = None
        upload_path = os.path.join(app.config['UPLOAD_FOLDER'], video_name)
        if os.path.exists(upload_path):
            video_path = upload_path
        elif os.path.exists(video_name):
            video_path = video_name
        else:
            return jsonify({'error': 'Video not found'}), 404
        
        # Start processing
        start_video_processing(video_path)
        
        return jsonify({'success': True, 'video': video_name})
    except Exception as e:
        print(f"❌ Select video error: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/video_feed')
def video_feed():
    """MJPEG video stream"""
    return Response(generate_frames(), 
                    mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/vehicle_count')
def get_vehicle_count():
    """Get current vehicle count"""
    return str(vehicle_count_global)

@app.route('/health')
def health_check():
    """Health check endpoint"""
    return jsonify({
        "status": "ok",
        "gpu": torch.cuda.is_available(),
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU",
        "current_video": current_video,
        "processing": is_running,
        "vehicle_count": vehicle_count_global
    })

# ============================================================================
# MAIN
# ============================================================================
if __name__ == '__main__':
    print("\n" + "="*60)
    print("🚦 SMART TRAFFIC AI SERVICE STARTED")
    print("="*60)
    print(f"📹 Video Manager: http://localhost:5000")
    print(f"📺 Live Stream:    http://localhost:5000/video_feed")
    print(f"📊 Vehicle Count:  http://localhost:5000/vehicle_count")
    print(f"❤️  Health Check:   http://localhost:5000/health")
    print(f"🔗 Backend:        {BACKEND_URL}")
    print("="*60)
    print("💡 Upload videos at http://localhost:5000")
    print("💡 Press Ctrl+C to exit\n")
    
    # Auto-load traffic.mp4 if exists
    if os.path.exists('traffic.mp4'):
        print("🎬 Auto-loading traffic.mp4...")
        start_video_processing('traffic.mp4')
    elif os.path.exists(os.path.join('uploads', 'traffic.mp4')):
        print("🎬 Auto-loading uploads/traffic.mp4...")
        start_video_processing(os.path.join('uploads', 'traffic.mp4'))
    
    app.run(host='0.0.0.0', port=5000, threaded=True)