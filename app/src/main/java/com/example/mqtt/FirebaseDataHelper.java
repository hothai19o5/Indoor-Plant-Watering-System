package com.example.mqtt;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseDataHelper {
    private static final String TAG = "FirebaseDataHelper";
    private final DatabaseReference databaseReference;  // Lấy dữ liệu từ node sensor_data, dữ liệu gửi liên tục
    private final DatabaseReference databaseReference2; // Lấy dữ liệu từ node sensor_data_30min, 30p gửi 1 lần
    private final Context context;

    public interface DataCallback<T> {
        void onCallback(T data);
    }
    
    public FirebaseDataHelper(Context context) {
        this.context = context;
        // Khởi tạo Firebase Database
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://projectii-fabc6-default-rtdb.asia-southeast1.firebasedatabase.app");
        databaseReference = database.getReference("sensor_data");
        databaseReference2 = database.getReference("sensor_data_30min");
    }
    
    public void saveSensorData(SensorData data) {
        // Tạo key tự động và lấy reference
        DatabaseReference newDataRef = databaseReference.push();
        
        // Tạo map dữ liệu
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("timestamp", data.getTimestamp());
        dataMap.put("temperature", data.getTemperature());
        dataMap.put("humidity", data.getHumidity());
        dataMap.put("soilMoisture", data.getSoilMoisture());
        dataMap.put("batteryLevel", data.getBatteryLevel());
        dataMap.put("waterTankLevel", data.getTankWaterLevel());
        
        // Lưu dữ liệu
        newDataRef.setValue(dataMap)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Data saved successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Error saving data", e));
    }

    public void getLatestData(DataCallback<SensorData> callback) {
        Query query = databaseReference.orderByKey().limitToLast(1); // Sắp xếp theo key, lấy bản ghi cuối

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if (!dataSnapshot.exists() || dataSnapshot.getChildrenCount() == 0) {
                    Log.w(TAG, "No data available");
                    callback.onCallback(null); // Trả về null nếu không có dữ liệu
                    return;
                }

                // Không cần vòng lặp, vì chỉ có 1 phần tử
                DataSnapshot snapshot = dataSnapshot.getChildren().iterator().next(); // Lấy snapshot trực tiếp

                try {
                    // Sử dụng getValue(Class) để tự động chuyển đổi
                    Float temperature = snapshot.child("temperature").getValue(Float.class);
                    Float humidity = snapshot.child("humidity").getValue(Float.class);
                    Float soilMoisture = snapshot.child("soilMoisture").getValue(Float.class);
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                    Float batteryLevel = snapshot.child("batteryLevel").getValue(Float.class);
                    Float tankWaterLevel = snapshot.child("tankWaterLevel").getValue(Float.class);

                    // Kiểm tra null trước khi sử dụng
                    temperature = (temperature == null) ? 0f : temperature;
                    humidity = (humidity == null) ? 0f : humidity;
                    soilMoisture = (soilMoisture == null) ? 0f : soilMoisture;
                    timestamp = (timestamp == null) ? 0L : timestamp;
                    batteryLevel = (batteryLevel == null) ? 0f : batteryLevel;
                    tankWaterLevel = (tankWaterLevel == null) ? 0f : tankWaterLevel;

                    // Tạo object SensorData
                    SensorData latestData = new SensorData();
                    latestData.setTemperature(temperature);
                    latestData.setHumidity(humidity);
                    latestData.setSoilMoisture(soilMoisture);
                    latestData.setTimestamp(timestamp);
                    latestData.setTankWaterLevel(tankWaterLevel);
                    latestData.setBatteryLevel(batteryLevel);

                    callback.onCallback(latestData); // Trả về dữ liệu

                } catch (Exception e) {
                    Log.e(TAG, "Error processing data", e);
                    callback.onCallback(null); // Trả về null nếu có lỗi
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to read data", error.toException());
                callback.onCallback(null); // Trả về null nếu có lỗi
            }
        });
    }

    public void getDataFromInHistory(DataCallback<List<SensorDataRecord>> callback) {
        // Lấy firebase interval từ SharedPreferences
        String PREFS_NAME = "AppSettings";
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String KEY_FIREBASE_INTERVAL = "firebaseInterval";
        long firebaseIntervalMillis = sharedPreferences.getLong(KEY_FIREBASE_INTERVAL, 300000); // Mặc định 5 phút nếu không tìm thấy
        
        long now = System.currentTimeMillis() / 1000;
        long intervalInSeconds = firebaseIntervalMillis / 1000;
        long startTime = now - intervalInSeconds;
        // Trường hợp query dưới 1 giờ thì lấy toàn bộ dữ liệu trong 1 giờ
        if(firebaseIntervalMillis <= 3600000) {
            Query query = databaseReference.orderByChild("timestamp").startAt(startTime);

            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    List<SensorDataRecord> dataList = new ArrayList<>();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        SensorDataRecord record = new SensorDataRecord();

                        // Lấy giá trị từ snapshot, có kiểm tra null
                        Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                        Float temperature = snapshot.child("temperature").getValue(Float.class);
                        Float humidity = snapshot.child("humidity").getValue(Float.class);
                        Float soilMoisture = snapshot.child("soilMoisture").getValue(Float.class);

                        if (timestamp != null) {
                            record.setTimestamp(timestamp * 1000);
                        }
                        if (temperature != null) {
                            record.setTemperature(temperature);
                        }
                        if (humidity != null) {
                            record.setHumidity(humidity);
                        }
                        if (soilMoisture != null) {
                            record.setSoilMoisture(soilMoisture);
                        }

                        dataList.add(record);
                    }

                    callback.onCallback(dataList);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to read data", error.toException());
                    callback.onCallback(null); // Tốt hơn là trả về null thay vì danh sách rỗng
                }
            });
        } else {    // Các TH thống kê dữ liêu trong thời gian dài thì lấy dữ liệu mỗi 30 phút / lần
            Query query = databaseReference2.orderByChild("timestamp").startAt(startTime);

            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    List<SensorDataRecord> dataList = new ArrayList<>();

                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        SensorDataRecord record = new SensorDataRecord();

                        // Lấy giá trị từ snapshot, có kiểm tra null
                        Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                        Float temperature = snapshot.child("temperature").getValue(Float.class);
                        Float humidity = snapshot.child("humidity").getValue(Float.class);
                        Float soilMoisture = snapshot.child("soilMoisture").getValue(Float.class);

                        if (timestamp != null) {
                            record.setTimestamp(timestamp * 1000);
                        }
                        if (temperature != null) {
                            record.setTemperature(temperature);
                        }
                        if (humidity != null) {
                            record.setHumidity(humidity);
                        }
                        if (soilMoisture != null) {
                            record.setSoilMoisture(soilMoisture);
                        }

                        dataList.add(record);
                    }

                    callback.onCallback(dataList);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to read data", error.toException());
                    callback.onCallback(null); // Tốt hơn là trả về null thay vì danh sách rỗng
                }
            });
        }
    }
}
