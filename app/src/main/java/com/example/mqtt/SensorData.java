package com.example.mqtt;

import androidx.annotation.NonNull;

import java.util.Date;

public class SensorData {
    private long timestamp;
    private float temperature;
    private float humidity;
    private float soilMoisture;

    public SensorData() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Date getDate() {
        return new Date(timestamp);
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
    
    @NonNull
    @Override
    public String toString() {
        return "SensorData{" +
                "timestamp=" + new Date(timestamp) +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                ", soilMoisture=" + soilMoisture +
                '}';
    }
}
