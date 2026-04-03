# Local Testing Guide - Full Demo

This guide covers how to run the complete traffic management system locally with:
- Spring Boot (Java) - Backend API & Signal Logic
- Python YOLO Service - Vehicle Detection
- PostgreSQL - Database (via Docker)

---

## Prerequisites

Make sure you have these installed:
- **Java 17** - `java -version`
- **Maven** - `mvn -version`  
- **Python 3.8+** - `python --version`
- **Docker Desktop** - For PostgreSQL

---

## Step 1: Start PostgreSQL (Database)

### Option A: Docker (Recommended)
```bash
# Start PostgreSQL container
docker run -d \
  --name traffic-postgres \
  -e POSTGRES_DB=trafficdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine

# Verify it's running
docker ps
```

### Option B: Using docker-compose
```bash
docker-compose up -d postgres
```

---

## Step 2: Start YOLO Service (Python)

The YOLO service handles vehicle detection.

```bash
# Navigate to yolo-service
cd yolo-service

# Create virtual environment (optional but recommended)
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Start YOLO service (runs on port 5001)
python app.py
```

You should see output like:
```
YOLO Service starting on port 5001...
Loading YOLOv8 model...
Model loaded successfully!
```

---

## Step 3: Start Spring Boot (Java)

Open a new terminal:

```bash
# Navigate to project root
cd smart-traffic-management

# Build and run
mvn spring-boot:run
```

The application will start on **http://localhost:8080**

---

## Step 4: Verify Everything Works

### Check the services:

| Service | URL | What to verify |
|---------|-----|----------------|
| **Spring Boot** | http://localhost:8080 | Dashboard loads |
| **YOLO Service** | http://localhost:5001 | "YOLO service running" message |
| **PostgreSQL** | Port 5432 | Connected automatically |

### Test the Flow:

1. Open browser → http://localhost:8080
2. Click **"Start Processing"**
3. System should:
   - Show "Waiting for traffic data..."
   - Run vehicle detection via YOLO
   - Calculate densities
   - Transition to GREEN with head start logic

---

## Quick Commands Summary

```bash
# Terminal 1: PostgreSQL (Docker)
docker run -d --name traffic-postgres -e POSTGRES_DB=trafficdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15-alpine

# Terminal 2: YOLO Service
cd yolo-service
pip install -r requirements.txt
python app.py

# Terminal 3: Spring Boot
cd smart-traffic-management
mvn spring-boot:run
```

---

## Troubleshooting

### Issue: "Connection refused" to YOLO service
**Solution:** Make sure YOLO is running on port 5001. Check firewall settings.

### Issue: PostgreSQL connection error
**Solution:** 
- Check Docker is running: `docker ps`
- Verify credentials in application.properties match

### Issue: Videos not loading
**Solution:** Add sample videos to `src/main/resources/static/videos/` with names:
- lane1.mp4, lane2.mp4, lane3.mp4, lane4.mp4

### Issue: "No such file or directory" for YOLO
**Solution:** Ensure you're in the yolo-service directory and dependencies are installed.

---

## Shutting Down

```bash
# Stop Spring Boot: Ctrl+C in terminal

# Stop YOLO: Ctrl+C in terminal

# Stop PostgreSQL:
docker stop traffic-postgres
docker rm traffic-postgres

# Or stop all:
docker-compose down
```

---

## For Presentation Tips

1. **Start everything 5 minutes before** - Gives time for YOLO model to load
2. **Use pre-recorded videos** - Upload sample traffic videos for consistent demo
3. **Keep browser tab open** - Don't refresh during presentation
4. **Have a backup** - Test the demo twice before presentation

---

## Alternative: Test Without YOLO (Simplified)

If YOLO is too complex to run locally, you can test signal logic only:

```bash
# Just run Spring Boot - signals will work with mock/zero detection
mvn spring-boot:run
```

The signal logic (head start, turns, phases) will still work - just without real vehicle detection.

---

## Questions?

If you run into issues, check:
1. Docker Desktop is running
2. All ports are free (8080, 5001, 5432)
3. Java 17 is set as default
