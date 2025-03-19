package com.example.mqtt;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class MqttApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Khởi tạo Firebase
        FirebaseApp.initializeApp(this);
        Log.d("MqttApplication", "Firebase initialized");
        
//        // Bật tính năng lưu trữ offline
//        try {
//            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
//            Log.d("MqttApplication", "Firebase persistence enabled");
//        } catch (Exception e) {
//            Log.e("MqttApplication", "Firebase initialization error", e);
//        }
    }
}