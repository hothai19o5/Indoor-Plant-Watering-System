package com.example.mqtt;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Add these imports for BottomNavigation
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.mqtt.fragments.HumidityFragment;
import com.example.mqtt.fragments.PumpFragment;
import com.example.mqtt.fragments.SettingFragment;
import com.example.mqtt.fragments.SoilMoisFragment;
import com.example.mqtt.fragments.TemperatureFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView temperatureTextView;
    private TextView humidityTextView;
    private TextView soilMoistureTextView;

    private final Gson gson = new Gson();
    private FirebaseDataHelper firebaseDataHelper;
    private DatabaseReference commandsRef;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable dataUpdateRunnable;
    private static final int UPDATE_INTERVAL = 3000; // 3 giây

    @SuppressLint("StaticFieldLeak")
    public static MainActivity instance;  // Không cần thiết phải có instance

    private BottomNavigationView bottomNavigationView;

    // Biến để theo dõi fragment hiện tại
    private Fragment currentFragment;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this; // Không nên lưu instance kiểu này

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Initialize Bottom Navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_pump) {
                replaceFragment(new PumpFragment());
                return true;
            } else if (itemId == R.id.nav_temperature) {
                replaceFragment(new TemperatureFragment());
                return true;
            } else if (itemId == R.id.nav_humidity) {
                replaceFragment(new HumidityFragment());
                return true;
            } else if (itemId == R.id.nav_soilmoisture) {
                replaceFragment(new SoilMoisFragment());
                return true;
            } else if (itemId == R.id.nav_setting) {
                replaceFragment(new SettingFragment());
                return true;
            }
            
            return false;
        });
        
        // Set default fragment
        replaceFragment(new PumpFragment());

        Log.d("MainActivity", "Initializing Firebase connection");

        firebaseDataHelper = new FirebaseDataHelper();
        commandsRef = FirebaseDatabase.getInstance().getReference("commands");

        temperatureTextView = findViewById(R.id.temperatureTextView);
        humidityTextView = findViewById(R.id.humidityTextView);
        soilMoistureTextView = findViewById(R.id.soilMoistureTextView);

        // Không gọi loadLatestDataFromFirebase() ở đây nữa

        // Tạo Runnable để cập nhật dữ liệu định kỳ
        dataUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                loadLatestDataFromFirebase(); // Gọi hàm lấy dữ liệu
                handler.postDelayed(this, UPDATE_INTERVAL); // Lên lịch chạy lại sau UPDATE_INTERVAL mili giây
            }
        };

        // Bắt đầu cập nhật dữ liệu
        startDataUpdates();


        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // findViewById(R.id.turnOnPumpButton).setOnClickListener(v -> sendCommand("TURN_ON_PUMP"));
        // findViewById(R.id.turnOffPumpButton).setOnClickListener(v -> sendCommand("TURN_OFF_PUMP"));
        // findViewById(R.id.resetButton).setOnClickListener(v -> sendCommand("RESET"));
        // ...các dòng khác
//        findViewById(R.id.temperatureStatsButton).setOnClickListener(v -> {
//            Intent intent = new Intent(MainActivity.this, StatsActivity.class);
//            intent.putExtra(StatsActivity.EXTRA_STAT_TYPE, StatsActivity.TYPE_TEMPERATURE);
//            startActivity(intent);
//        });
//        findViewById(R.id.humidityStatsButton).setOnClickListener(v -> {
//            Intent intent = new Intent(MainActivity.this, StatsActivity.class);
//            intent.putExtra(StatsActivity.EXTRA_STAT_TYPE, StatsActivity.TYPE_HUMIDITY);
//            startActivity(intent);
//        });
//        findViewById(R.id.soilMoistureStatsButton).setOnClickListener(v -> {
//            Intent intent = new Intent(MainActivity.this, StatsActivity.class);
//            intent.putExtra(StatsActivity.EXTRA_STAT_TYPE, StatsActivity.TYPE_SOIL_MOISTURE);
//            startActivity(intent);
//        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public interface DataUpdateListener {
        void onDataUpdate(SensorData sensorData);
    }

    private void loadLatestDataFromFirebase() {
        Log.d("MainActivity", "Loading latest data from Firebase");
        firebaseDataHelper.getLatestData(sensorData -> { // Sử dụng lambda cho gọn
            Log.d("MainActivity", "Data callback received: " + (sensorData != null ? "data exists" : "no data"));
            // Không cập nhật UI trực tiếp ở đây nữa
            // Thay vào đó, thông báo cho fragment hiện tại
            if (currentFragment instanceof DataUpdateListener) {
                runOnUiThread(() -> {
                    ((DataUpdateListener)currentFragment).onDataUpdate(sensorData);
                });
            }
        });
    }


    public void sendCommand(String command) {
        Map<String, Object> commandMap = new HashMap<>();
        commandMap.put("type", command);

        commandsRef.push().setValue(commandMap)
                .addOnSuccessListener(aVoid -> Toast.makeText(MainActivity.this, "Đã gửi lệnh: " + command, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Lỗi khi gửi lệnh", Toast.LENGTH_SHORT).show();
                    Log.e("MainActivity", "Lỗi gửi lệnh", e);
                });
    }

    // Add method for fragments to call
    public void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    public void updateFromFCM(SensorData sensorData) { // Cái này bạn chưa dùng, nhưng cứ để đây
        if (sensorData != null) {
            updateUI(sensorData);
        }
    }
    private void startDataUpdates() {
        handler.postDelayed(dataUpdateRunnable, UPDATE_INTERVAL); // Bắt đầu lần cập nhật đầu tiên
    }

    private void stopDataUpdates() {
        handler.removeCallbacks(dataUpdateRunnable); // Dừng cập nhật
    }

    @Override
    protected void onResume() {
        super.onResume();
        startDataUpdates(); // Bắt đầu/tiếp tục cập nhật khi Activity ở trạng thái resumed
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopDataUpdates(); // Dừng cập nhật khi Activity không còn được hiển thị
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        stopDataUpdates(); // Dừng Handler khi activity destroy.
    }


    @SuppressLint("SetTextI18n")
    private void updateUI(SensorData sensorData) {
        if (sensorData != null) {
            Log.d("MainActivity", "Updating UI with: " + sensorData.getTemperature() + "°C, " +
                    sensorData.getHumidity() + "%, " + sensorData.getSoilMoisture() + "%");
            temperatureTextView.setText(sensorData.getTemperature() + "°C");
            humidityTextView.setText((int)sensorData.getHumidity() + "%");
            soilMoistureTextView.setText((int)sensorData.getSoilMoisture() + "%");
        } else {
            Log.w("MainActivity", "SensorData is null in updateUI");
            // Hiển thị giá trị mặc định hoặc thông báo lỗi
            temperatureTextView.setText("--°C");
            humidityTextView.setText("--%");
            soilMoistureTextView.setText("--%");
        }
    }

    private void replaceFragment(Fragment fragment) {
        currentFragment = fragment;
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }
}