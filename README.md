# 🪴INDOOR PLANT WATERING SYSTEM

![ESP8266](https://img.shields.io/badge/ESP8266-RealTime-blue)
![Java](https://img.shields.io/badge/Java-Android-yellowgreen)
![Cpp](https://img.shields.io/badge/Cpp-Hardware-violet)
![Firebase](https://img.shields.io/badge/Firebase-STT-red)

## 🧠 Overview
This project is a smart **Indoor Plant Watering System** that monitors and controls soil moisture, temperature, and humidity to ensure your plants are always well cared for. The system includes a **hardware module** (based on ESP8266/ESP32) and an **Android mobile app** for real-time monitoring and manual or automatic control.

## 📟 Features
- 🌱 Real-time monitoring of:
    - Soil moisture
    - Air temperature and humidity
    - Batterry level ( Updating... )
    - Power source status battery or adapter ( Updating... )
    - Water tank level ( Updating... )
- 📱 Android app with:
    - Sensor data display
    - Manual water triggering
    - Automatic watering mode
    - Firebase real-time database integration
    - Data statistics
    - Add hardware device ( Updating... )
- 🔌 Hardware:
    - ESP8266/ESP32 + DHT11 + soil moisture sensor V1.2
    - INA219 for monitoring battery level
    - Ultrasonic Distance Sensor HC-SR04
    - Supports both 12V power adapter and 12V battery pack
    - Sends sensor data every 5 seconds to Firebase
- ☁️ Cloud Function:
    - Send notification from ESP to App

## 🚀 Technologies Used
- **Embedded**: ESP8266 / ESP32, DHT11, Capacitive soil moisture sensor V1.2, INA219, YX850, HC-SR04, LM2596
- **Android**: Java, Firebase Realtime Database
- **Cloud**: Firebase Functions (JavaScript)

## ⚙️ Setup Instructions

### Hardware
1. Wire the sensors and components as shown in the diagram below.
2. Flash ESP8266/ESP32 with firmware (in `/firmware` or `/esp32_code` folder).
3. Configure Wi-Fi credentials and Firebase credentials.

### Android App
1. Open project in Android Studio.
2. Ensure Firebase is correctly linked (google-services.json present).
3. Build and run on physical device or emulator.
4. Use the app to monitor plant health and control watering.

### Firebase
1. Create Firebase Realtime Database and enable read/write access.
2. Set up Cloud Function to summarize data (found in `/cloud_functions` folder).
3. Update ESP firmware and Android app with database URL and API keys.

## 🖼️ Images

### Hardware module wiring diagram
<p align="center">
  <img src="https://github.com/user-attachments/assets/e3222fae-8ff2-4b44-9eb6-628ee79bf657" alt="" width="90%">
</p>

### Android App
<p align="center">
  <img src="https://github.com/user-attachments/assets/89eb3115-d9ee-4dc7-a790-9b9ca165fac4" alt="" width="15%">
  <img src="https://github.com/user-attachments/assets/589abbd2-8fb0-4771-9d4d-71b01cf1fc3b" alt="" width="15%">
  <img src="https://github.com/user-attachments/assets/780ef7d3-ece8-4cef-9f74-7057cbdca9a1" alt="" width="15%">
  <img src="https://github.com/user-attachments/assets/098b7cf9-32fb-4fb7-8989-5de7894e81fa" alt="" width="15%">
  <img src="https://github.com/user-attachments/assets/19f0b916-e32a-4e65-a820-40f8e970b52e" alt="" width="15%">
  <img src="https://github.com/user-attachments/assets/944dbc05-c679-4dae-8753-7f5476bad9ad" alt="" width="15%">
</p>

