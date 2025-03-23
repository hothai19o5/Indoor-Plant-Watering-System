package com.example.mqtt;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirebaseDataHelper {
    private static final String TAG = "FirebaseDataHelper";
    private final DatabaseReference databaseReference;
    
    public interface DataCallback<T> {
        void onCallback(T data);
    }
    
    public FirebaseDataHelper() {
        FirebaseDatabase database = FirebaseDatabase.getInstance("https://projectii-fabc6-default-rtdb.asia-southeast1.firebasedatabase.app");
        databaseReference = database.getReference("sensor_data");
        Log.d(TAG, "FirebaseDataHelper initialized with reference: " + databaseReference.toString());
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
        
        // Lưu dữ liệu
        newDataRef.setValue(dataMap)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Data saved successfully"))
            .addOnFailureListener(e -> Log.e(TAG, "Error saving data", e));
    }

    public void getLatestData(DataCallback<SensorData> callback) {
        Log.d(TAG, "Getting latest data...");
        Query query = databaseReference.orderByKey().limitToLast(1); // Sắp xếp theo key, lấy bản ghi cuối
        Log.d(TAG, "Query: " + query.toString());

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Data snapshot received: " + dataSnapshot.exists() + ", childCount: " + dataSnapshot.getChildrenCount());

                if (!dataSnapshot.exists() || dataSnapshot.getChildrenCount() == 0) {
                    Log.w(TAG, "No data available");
                    callback.onCallback(null); // Trả về null nếu không có dữ liệu
                    return;
                }

                // Không cần vòng lặp, vì chỉ có 1 phần tử
                DataSnapshot snapshot = dataSnapshot.getChildren().iterator().next(); // Lấy snapshot trực tiếp

                Log.d(TAG, "Processing snapshot: " + snapshot.getKey());

                try {
                    // Sử dụng getValue(Class) để tự động chuyển đổi
                    Float temperature = snapshot.child("temperature").getValue(Float.class);
                    Float humidity = snapshot.child("humidity").getValue(Float.class);
                    Float soilMoisture = snapshot.child("soilMoisture").getValue(Float.class);
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);

                    // Kiểm tra null trước khi sử dụng
                    temperature = (temperature == null) ? 0f : temperature;
                    humidity = (humidity == null) ? 0f : humidity;
                    soilMoisture = (soilMoisture == null) ? 0f : soilMoisture;
                    timestamp = (timestamp == null) ? 0L : timestamp;



                    // Tạo object SensorData
                    SensorData latestData = new SensorData();
                    latestData.setTemperature(temperature);
                    latestData.setHumidity(humidity);
                    latestData.setSoilMoisture(soilMoisture);
                    latestData.setTimestamp(timestamp);


                    Log.d(TAG, "Created SensorData: " + latestData.getTemperature() + "°C, " + latestData.getHumidity() + "%, " + latestData.getSoilMoisture() + "%" + "timestamp: " + latestData.getTimestamp());
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

    public void getDataFromLast1Hour(DataCallback<List<SensorDataRecord>> callback) {
        // Tính thời điểm 1 ngày trước
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, 0);
        long oneHourAgo = calendar.getTimeInMillis()/1000 - 3600;
        Log.d("FirebaseDataHelper", "oneHourAgo: " + oneHourAgo);

        Query query = databaseReference.orderByChild("timestamp").startAt(oneHourAgo);
        Log.d("FirebaseDataHelper", "Query: " + query.toString());

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Data snapshot received: " + dataSnapshot.exists() + ", childCount: " + dataSnapshot.getChildrenCount());
                List<SensorDataRecord> dataList = new ArrayList<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    SensorDataRecord record = new SensorDataRecord();

                    // Lấy giá trị từ snapshot, có kiểm tra null
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);
                    Float temperature = snapshot.child("temperature").getValue(Float.class);
                    Float humidity = snapshot.child("humidity").getValue(Float.class);
                    Float soilMoisture = snapshot.child("soilMoisture").getValue(Float.class);

                    if (timestamp != null) {
                        record.setTimestamp(timestamp*1000);
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