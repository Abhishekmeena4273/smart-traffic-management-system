# 🚦 Smart Traffic Management System

> AI-Powered Adaptive Traffic Signal Control Using Computer Vision

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)
![Python](https://img.shields.io/badge/Python-3.10-blue)
![React](https://img.shields.io/badge/React-18-cyan)
![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-green)

## 📋 Project Overview

This system uses **YOLOv8 computer vision** to detect vehicles in real-time and **dynamically adjusts traffic signals** based on traffic density. Built with a microservices architecture using Spring Boot (Java), Python (AI), React (Frontend), and MongoDB (Database).

## ✨ Features

- 🎥 **Real-time Vehicle Detection** using YOLOv8 AI
- 🚦 **Adaptive Signal Control** (RED → YELLOW → GREEN transitions)
- 📊 **Live Dashboard** with embedded video feed
- 📈 **Traffic Analytics** with real-time charts
- 🗑️ **Video Upload Manager** - Upload & switch traffic videos via web UI
- 🎮 **GPU Acceleration** support (CUDA)
- 🔒 **User Authentication** ready (Spring Security)

steps to run this 

cd backend

mvn spring-boot:run


cd ai-service

python -m venv venv

venv\scripts\activate

pip install -r requirements.txt

python main.py


pip install torch torchvision torchaudio (will only use cpu)

for gpu 
run nvidia-smi
check the cuda version
find the cuXXX on  https://download.pytorch.org/whl/  

pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cuXXX

for me 
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu130

cd frontend

npm install

npm start

add videos - go to ai-service/uploads
go to http://localhost:5000/
select play and reload the 

http://localhost:3000/dashboard


