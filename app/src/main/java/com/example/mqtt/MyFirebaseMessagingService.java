package com.example.mqtt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MyFirebaseMsgService";
    private final Gson gson = new Gson();
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Nếu có phần notification
        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            showNotification(title, body);
        }
        
        // Kiểm tra nếu message chứa data
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Message data: " + remoteMessage.getData());
            
            try {
                String jsonData = new Gson().toJson(remoteMessage.getData());
                SensorData sensorData = gson.fromJson(jsonData, SensorData.class);
                
                // Lưu dữ liệu vào Firebase
                FirebaseDataHelper dataHelper = new FirebaseDataHelper(this);
                dataHelper.saveSensorData(sensorData);

            } catch (Exception e) {
                Log.e(TAG, "Error processing FCM data", e);
            }
        }
    }
    
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // Gửi token lên server của bạn để xử lý
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("fcmToken");
        ref.setValue(token);
    }

    private void showNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "fcm_default_channel";

        NotificationChannel channel = new NotificationChannel(channelId, "FCM Notifications", NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.plant) // icon tùy chỉnh
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(0, builder.build());
    }
}

