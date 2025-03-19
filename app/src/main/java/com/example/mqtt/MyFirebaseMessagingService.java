package com.example.mqtt;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMsgService";
    private final Gson gson = new Gson();
    
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        
        // Kiểm tra nếu message chứa data
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Message data: " + remoteMessage.getData());
            
            try {
                String jsonData = new Gson().toJson(remoteMessage.getData());
                SensorData sensorData = gson.fromJson(jsonData, SensorData.class);
                
                // Lưu dữ liệu vào Firebase
                FirebaseDataHelper dataHelper = new FirebaseDataHelper();
                dataHelper.saveSensorData(sensorData);
                
                // Thông báo cho MainActivity cập nhật UI
                if (MainActivity.instance != null) {
                    MainActivity.instance.updateFromFCM(sensorData);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing FCM data", e);
            }
        }
    }
    
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "New FCM token: " + token);
        // Gửi token lên server của bạn để xử lý
    }
}