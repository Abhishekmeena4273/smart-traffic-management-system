# Deployment Guide - Render (Free)

This guide covers deploying the Smart Traffic Management System to Render for free.

---

## Architecture

| Component | Service | Free Limit |
|-----------|---------|------------|
| **Spring Boot (Backend)** | Render Web Service | 750 hours/month |
| **PostgreSQL (Database)** | Render PostgreSQL | 5MB storage |
| **Python YOLO (Detection)** | Render Web Service | 750 hours/month |

---

## Step 1: Push Code to GitHub

Ensure your code is pushed to a GitHub repository:
```bash
git add .
git commit -m "Ready for deployment"
git push origin main
```

---

## Step 2: Deploy PostgreSQL (Render)

1. Go to [render.com](https://render.com) → Sign up with GitHub
2. Dashboard → **New** → **PostgreSQL**
3. Configure:
   - **Name**: `traffic-db`
   - **Plan**: Free
   - **Database Name**: `trafficdb`
   - **User**: `postgres`
4. Click **Create Database**
5. **Copy the Internal URL** (you'll need it for DB_URL)

---

## Step 3: Deploy Spring Boot (Backend)

1. Dashboard → **New** → **Web Service**
2. Connect your GitHub repository
3. Configure:
   - **Name**: `smart-traffic-backend`
   - **Build Command**: `mvn package -DskipTests`
   - **Start Command**: `java -jar target/smart-traffic-management-0.0.1-SNAPSHOT.jar`
4. Add Environment Variables:
   ```
   DB_URL=jdbc:postgresql://[internal-host]:5432/trafficdb
   DB_USER=postgres
   DB_PASSWORD=[from PostgreSQL settings]
   ```
5. Click **Deploy Web Service**

Wait for deployment to complete (may take 2-5 minutes).

---

## Step 4: Deploy Python YOLO Service

1. Dashboard → **New** → **Web Service**
2. Connect your GitHub repository (same repo)
3. Configure:
   - **Name**: `traffic-yolo`
   - **Root Directory**: `yolo-service`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `python app.py`
4. Add Environment Variables:
   ```
   PORT=5001
   ```
5. Click **Deploy Web Service**

Wait for deployment to complete.

---

## Step 5: Update Backend to Point to YOLO

1. In Render dashboard, go to your **smart-traffic-backend** service
2. Add environment variable:
   ```
   yolo.service.url=https://traffic-yolo.onrender.com
   ```
3. Redeploy the backend

---

## Step 6: Access Your Deployed Application

Once deployed:
- **Backend**: `https://smart-traffic-backend.onrender.com`
- **YOLO Service**: `https://traffic-yolo.onrender.com`

---

## Important Notes

### Sleep Mode
- Render's free tier **sleeps after 15 minutes** of inactivity
- First request after sleep takes ~10-30 seconds to wake up
- **For presentation**: Keep the page active or refresh before demo

### Demo Tips
1. Open the application 1-2 minutes before presentation
2. Keep the browser tab active during presentation
3. Have a backup plan (run locally if needed)

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Build fails | Check that `system.properties` exists with Java 17 |
| Database connection error | Verify DB_URL format and credentials |
| YOLO not responding | Check YOLO service logs in Render dashboard |
| Application slow | Normal - free tier sleeps after 15 min |

---

## Local Development (Backup)

If deployed version has issues, run locally:
```bash
# Terminal 1: Start PostgreSQL (Docker)
docker run -d --name traffic-postgres -e POSTGRES_DB=trafficdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15-alpine

# Terminal 2: Start YOLO
cd yolo-service
pip install -r requirements.txt
python app.py

# Terminal 3: Start Spring Boot
mvn spring-boot:run
```

---

## Default Credentials

- **Username**: `admin`
- **Password**: `admin123`

---

## Questions?

If you encounter issues, check:
1. Render service logs (available in dashboard)
2. Environment variables are correctly set
3. Database is running and accessible
