package com.example.mqtt;

import androidx.annotation.NonNull;

public class SensorData {
    private long timestamp;
    private float temperature;
    private float humidity;
    private float soilMoisture;
    private float batteryLevel;
    private float tankWaterLevel;

    public SensorData() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getHumidity() {
        return humidity;
    }

    public void setHumidity(float humidity) {
        this.humidity = humidity;
    }

    public float getSoilMoisture() {
        return soilMoisture;
    }

    public void setSoilMoisture(float soilMoisture) {
        this.soilMoisture = soilMoisture;
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(float batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public float getTankWaterLevel() {
        return tankWaterLevel;
    }

    public void setTankWaterLevel(float tankWaterLevel) {
        this.tankWaterLevel = tankWaterLevel;
    }

    @NonNull
    @Override
    public String toString() {
        return "SensorData{" +
                "timestamp=" + timestamp +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", soilMoisture=" + soilMoisture +
                ", batteryLevel=" + batteryLevel +
                ", tankWaterLevel=" + tankWaterLevel +
                '}';
    }
}
