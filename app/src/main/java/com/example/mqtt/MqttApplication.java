package com.example.mqtt;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public class MqttApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Khởi tạo Firebase
        FirebaseApp.initializeApp(this);
        Log.d("MqttApplication", "Firebase initialized");

    }
}
