@echo off
title Smart Traffic Management - Launcher
color 0A

echo ============================================
echo   Smart Traffic Management for Urban
echo   Congestion - Launcher
echo ============================================
echo.

echo [1/2] Checking Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Install JDK 17+ and add to PATH.
    pause
    exit /b 1
)
echo       Java OK

echo.
echo [2/2] Building application...
call mvn clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo [ERROR] Build failed. Check the errors above.
    pause
    exit /b 1
)
echo       Build OK

echo.
echo [3/3] Starting application (port 8080)...
echo       YOLO detection runs in-process (ONNX Runtime INT8)
echo       Database: H2 embedded (./data/trafficdb)
echo.

start "Smart Traffic Management" cmd /k "title Smart Traffic App && java -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar target\smart-traffic-management-1.0.0.jar"

echo.
echo ============================================
echo   Application starting...
echo.
echo   Dashboard:  http://localhost:8080
echo   Login:      admin / admin123
echo   H2 Console: http://localhost:8080/h2-console
echo   Health:     http://localhost:8080/api/health
echo ============================================
echo.
echo Press any key to close this window (app keeps running)...
pause >nul
