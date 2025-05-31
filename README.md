# INDOOR PLANT WATERING SYSTEM

![ESP8266](https://img.shields.io/badge/ESP8266-RealTime-blue)
![Java](https://img.shields.io/badge/Java-Android-yellowgreen)
![Cpp](https://img.shields.io/badge/Cpp-Hardware-violet)
![Firebase](https://img.shields.io/badge/Firebase-STT-red)

## Overview
This project is a smart **Indoor Plant Watering System** that monitors and controls soil moisture, temperature, and humidity to ensure your plants are always well cared for. The system includes a **hardware module** (based on ESP8266/ESP32) and an **Android mobile app** for real-time monitoring and manual or automatic control.

## Features
- Real-time monitoring of:
    - Soil moisture
    - Air temperature and humidity
    - Batterry level
    - Power source status battery or adapter
    - Water tank level
- Android app with:
    - Sensor data display
    - Manual water triggering
    - Automatic watering mode
    - Firebase real-time database integration
    - Data statistics
    - Change Pump Duration
    - Change Height Water Tank
- Hardware:
    - ESP8266/ESP32 + DHT11 + soil moisture sensor V1.2
    - INA219 for monitoring battery level
    - Ultrasonic Distance Sensor HC-SR04
    - Supports both 12V power adapter and 12V battery pack
    - Sends sensor data every 5 seconds to Firebase
    - Send Notification to App if Auto Pump or Error Sensor or Low Battery
- Cloud Function:
    - Send notification from ESP to App

## Technologies Used
- **Embedded**: ESP8266 / ESP32, DHT11, Capacitive soil moisture sensor V1.2, INA219, YX850, HC-SR04, LM2596
- **Android**: Java, Firebase Realtime Database
- **Cloud**: Firebase Functions (JavaScript)

## Setup Instructions

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

## Hardware

https://github.com/user-attachments/assets/b19b17c1-4aba-45e8-9089-8fadac99c43f

## Android App

https://github.com/user-attachments/assets/75760552-f5a5-4cc4-a8c6-85efd7374c9e

## Hardware diagram

<p align="center">
  <img src="https://github.com/user-attachments/assets/e3222fae-8ff2-4b44-9eb6-628ee79bf657" alt="Hardware diagram" width="90%">
</p>



