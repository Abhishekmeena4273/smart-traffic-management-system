@echo off
title Smart Traffic Management - Launcher
color 0A

echo ============================================
echo   Smart Traffic Management for Urban
echo   Congestion - Launcher
echo ============================================
echo.

echo [1/3] Checking Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found. Install JDK 17+ and add to PATH.
    pause
    exit /b 1
)
echo       Java OK

echo.
echo [2/3] Starting YOLO Detection Service (port 5001)...
if exist "yolo-service\app.py" (
    where python >nul 2>&1
    if %errorlevel% equ 0 (
        start "YOLO Service" cmd /k "cd yolo-service && pip install -q -r requirements.txt 2>nul && uvicorn app:app --port 5001"
        echo       YOLO service starting...
    ) else (
        echo       Python not found - skipping YOLO (simulation mode)
    )
) else (
    echo       yolo-service not found - simulation mode
)

echo.
echo [3/3] Starting Spring Boot Application (port 8080)...
echo       Please wait, first run downloads dependencies...
echo.

start "Spring Boot App" cmd /k "mvn spring-boot:run"

echo.
echo ============================================
echo   Application starting...
echo.
echo   Dashboard: http://localhost:8080
echo   Login:     admin / admin123
echo   H2 Console: http://localhost:8080/h2-console
echo   YOLO API:  http://localhost:5001/docs
echo ============================================
echo.
echo Press any key to close this window (services keep running)...
pause >nul
