package com.example.mqtt.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mqtt.MainActivity;
import com.example.mqtt.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SettingFragment extends Fragment {

    View view;
    private DatabaseReference databaseReferenceCommand;
    private DatabaseReference databaseReferenceSensor;
    private DatabaseReference databaseReferenceConfig;
    private long pumpDurationMillis = 5000; // Giá trị mặc định (5 giây)
    private long firebaseIntervalMillis = 300000; // Giá trị mặc định (5 phút)
    private int autoPump = 0;
    private int heightWaterTank = 100;
    
    // Added to track changes in settings
    private long savedPumpDurationMillis = 5000;
    private long savedFirebaseIntervalMillis = 300000;
    private int savedAutoPump = 0;
    private int savedheightWaterTank = 100;

    private boolean settingsChanged = false;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppSettings";
    private static final String KEY_PUMP_DURATION = "pumpDuration";
    private static final String KEY_FIREBASE_INTERVAL = "firebaseInterval";
    private static final String KEY_AUTO_PUMP = "autoPump";
    private static final String KEY_HEIGHT_WATER_PUMP = "heightWaterTank";
    private Button saveSettingsButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = inflater.inflate(R.layout.fragment_setting_page, container, false);

        // Initialize SharedPreferences
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Load saved settings
        pumpDurationMillis = sharedPreferences.getLong(KEY_PUMP_DURATION, 5000);
        firebaseIntervalMillis = sharedPreferences.getLong(KEY_FIREBASE_INTERVAL, 300000);
        autoPump = sharedPreferences.getInt(KEY_AUTO_PUMP, 0);
        heightWaterTank = sharedPreferences.getInt(KEY_HEIGHT_WATER_PUMP, 100);
        
        // Initialize saved values
        savedPumpDurationMillis = pumpDurationMillis;
        savedFirebaseIntervalMillis = firebaseIntervalMillis;
        savedAutoPump = autoPump;
        savedheightWaterTank = heightWaterTank;

        databaseReferenceCommand = FirebaseDatabase.getInstance().getReference("commands");
        databaseReferenceSensor = FirebaseDatabase.getInstance().getReference("sensor_data");
        databaseReferenceConfig = FirebaseDatabase.getInstance().getReference("config");

        Button resetEspButton = view.findViewById(R.id.resetButton);
        Button logoutButton = view.findViewById(R.id.logoutButton);
        Button clearCommandButton = view.findViewById(R.id.clearCommandButton);
        Button clearSensorButton = view.findViewById(R.id.clearSensorButton);
        Spinner pumpDurationSpinner = view.findViewById(R.id.pump_duration_spinner);
        Spinner firebaseIntervalSpinner = view.findViewById(R.id.firebase_interval_spinner);
        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch autoPumpSwitch = view.findViewById(R.id.auto_pump_switch);
        EditText heightWaterTankEditText = view.findViewById(R.id.height_water_pump);
        saveSettingsButton = view.findViewById(R.id.saveSettingsButton);
        
        // Set up adapters and initial values for spinners
        setupSpinners(pumpDurationSpinner, firebaseIntervalSpinner);

        // Set up switch
        setupSwitch(autoPumpSwitch);

        // Set up edit text
        setupEditText(heightWaterTankEditText);
        
        // Initially disable save button until changes are made
        saveSettingsButton.setEnabled(false);

        // Thiết lập Spinner và lưu giá trị tạm thời
        pumpDurationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedDuration = parent.getItemAtPosition(position).toString();
                pumpDurationMillis = convertToMillis(selectedDuration);
                checkForChanges();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Không làm gì
            }
        });

        firebaseIntervalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedInterval = parent.getItemAtPosition(position).toString();
                firebaseIntervalMillis = convertToMillis(selectedInterval);
                checkForChanges();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Không làm gì
            }
        });

        autoPumpSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoPump = isChecked ? 1 : 0;
            checkForChanges();
        });

        //Sử dụng OnEditorActionListener (Khi nhấn Done/Enter)
        heightWaterTankEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                String inputText = heightWaterTankEditText.getText().toString().trim();

                if (!inputText.isEmpty()) {
                    try {
                        int newHeight = Integer.parseInt(inputText);
                        if (newHeight >= 0 && newHeight <= 1000) {
                            heightWaterTank = newHeight;
                            checkForChanges();

                            // Ẩn bàn phím và clear focus
                            heightWaterTankEditText.clearFocus();
                            InputMethodManager imm = (InputMethodManager) requireActivity()
                                    .getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(heightWaterTankEditText.getWindowToken(), 0);
                        } else {
                            heightWaterTankEditText.setText(String.valueOf(heightWaterTank));
                        }
                    } catch (NumberFormatException e) {
                        heightWaterTankEditText.setText(String.valueOf(heightWaterTank));
                    }
                } else {
                    heightWaterTankEditText.setText(String.valueOf(heightWaterTank));
                }
                return true;
            }
            return false;
        });

        resetEspButton.setOnClickListener(v -> {
            showConfirmationDialog("Reset Hardware", "Are you sure you want to reset the hardware?", () -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).sendCommand("RESET");
                }
            });
        });

        logoutButton.setOnClickListener(v -> {
            showConfirmationDialog("Log Out", "Are you sure you want to log out?", () -> {
                FirebaseAuth.getInstance().signOut();
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToLogin();
                }
            });
        });

        clearCommandButton.setOnClickListener(v -> {
            showConfirmationDialog("Clear Command", "Are you sure you want to clear the command?", () -> {
                databaseReferenceCommand.removeValue()
                        .addOnSuccessListener(aVoid -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Success")
                                    .setMessage("Command data cleared successfully.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        })
                        .addOnFailureListener(e -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Error")
                                    .setMessage("Failed to clear command data: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
            });
        });

        clearSensorButton.setOnClickListener(v -> {
            showConfirmationDialog("Clear Sensor", "Are you sure you want to clear the sensor data?", () -> {
                databaseReferenceSensor.removeValue()
                        .addOnSuccessListener(aVoid -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Success")
                                    .setMessage("Sensor data cleared successfully.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        })
                        .addOnFailureListener(e -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Error")
                                    .setMessage("Failed to clear sensor data: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                        });
            });
        });

        saveSettingsButton.setOnClickListener(v -> {
            showConfirmationDialog("Save Settings", "Are you sure you want to save the settings?", () -> {
                // Save to SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putLong(KEY_PUMP_DURATION, pumpDurationMillis);
                editor.putLong(KEY_FIREBASE_INTERVAL, firebaseIntervalMillis);
                editor.putInt(KEY_AUTO_PUMP, autoPump);
                editor.putInt(KEY_HEIGHT_WATER_PUMP, heightWaterTank);
                editor.apply();
                
                // Update saved values
                savedPumpDurationMillis = pumpDurationMillis;
                savedFirebaseIntervalMillis = firebaseIntervalMillis;
                savedAutoPump = autoPump;
                savedheightWaterTank = heightWaterTank;
                
                // Create config data map
                Map<String, Object> configData = new HashMap<>();
                configData.put("pumpDuration", pumpDurationMillis);
                configData.put("autoPump", autoPump);
                configData.put("heightWaterTank", heightWaterTank);
                
                // Clear existing data and set new data
                databaseReferenceConfig.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // After clearing, set the new data
                        databaseReferenceConfig.setValue(configData)
                            .addOnSuccessListener(aVoid -> {
                                new AlertDialog.Builder(requireContext())
                                    .setTitle("Success")
                                    .setMessage("Settings saved successfully.")
                                    .setPositiveButton("OK", null)
                                    .show();
                                
                                // Disable save button after successful save
                                settingsChanged = false;
                                saveSettingsButton.setEnabled(false);
                            })
                            .addOnFailureListener(e -> {
                                new AlertDialog.Builder(requireContext())
                                    .setTitle("Error")
                                    .setMessage("Failed to save settings: " + e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show();
                            });
                    } else {
                        new AlertDialog.Builder(requireContext())
                            .setTitle("Error")
                            .setMessage("Failed to clear previous config: " + task.getException().getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    }
                });
            });
        });

        return view;
    }

    private void setupSwitch(@SuppressLint("UseSwitchCompatOrMaterialCode") Switch autoPumpSwitch) {
        autoPumpSwitch.setChecked(autoPump == 1);
    }

    private void setupEditText(EditText heightWaterTankEditText) {
        heightWaterTankEditText.setText(String.valueOf(heightWaterTank));
    }
    
    private void setupSpinners(Spinner pumpDurationSpinner, Spinner firebaseIntervalSpinner) {
        // Set up pump duration spinner
        ArrayAdapter<CharSequence> pumpAdapter = ArrayAdapter.createFromResource(requireContext(), 
                R.array.pump_durations, android.R.layout.simple_spinner_item);
        pumpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pumpDurationSpinner.setAdapter(pumpAdapter);
        
        // Set up firebase interval spinner
        ArrayAdapter<CharSequence> firebaseAdapter = ArrayAdapter.createFromResource(requireContext(), 
                R.array.firebase_intervals, android.R.layout.simple_spinner_item);
        firebaseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        firebaseIntervalSpinner.setAdapter(firebaseAdapter);
        
        // Set selections based on saved values
        setSpinnerSelectionFromMillis(pumpDurationSpinner, pumpDurationMillis, pumpAdapter);
        setSpinnerSelectionFromMillis(firebaseIntervalSpinner, firebaseIntervalMillis, firebaseAdapter);
    }
    
    private void setSpinnerSelectionFromMillis(Spinner spinner, long millis, ArrayAdapter<CharSequence> adapter) {
        for (int i = 0; i < adapter.getCount(); i++) {
            String item = Objects.requireNonNull(adapter.getItem(i)).toString();
            if (convertToMillis(item) == millis) {
                spinner.setSelection(i);
                break;
            }
        }
    }
    
    private void checkForChanges() {
        // Check if any settings have changed from saved values
        boolean hasChanges = pumpDurationMillis != savedPumpDurationMillis || 
                        firebaseIntervalMillis != savedFirebaseIntervalMillis ||
                        autoPump != savedAutoPump ||
                        heightWaterTank != savedheightWaterTank;
        
        if (hasChanges != settingsChanged) {
            settingsChanged = hasChanges;
            saveSettingsButton.setEnabled(hasChanges);
        }
    }

    // Chuyển đổi chuỗi thời gian thành milliseconds
    private long convertToMillis(String timeString) {
        String[] parts = timeString.split(" ");
        int value = Integer.parseInt(parts[0]);
        String unit = parts[1].toLowerCase();
        switch (unit) {
            case "s":
                return value * 1000L;
            case "min":
                return value * 60 * 1000L;
            case "hour":
                return value * 60 * 60 * 1000L;
            case "day":
                return value * 24 * 60 * 60 * 1000L;
            default:
                return value * 1000L; // Mặc định là giây nếu không xác định
        }
    }

    // Hàm hiển thị dialog xác nhận
    private void showConfirmationDialog(String title, String message, final Runnable onConfirm) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> {
                    onConfirm.run(); // Gọi hành động khi người dùng nhấn "Yes"
                })
                .setNegativeButton("No", (dialog, which) -> {
                    dialog.dismiss(); // Đóng dialog khi nhấn "No"
                })
                .setCancelable(false) // Không cho phép đóng dialog bằng nút back
                .show();
    }

    // Class để định dạng dữ liệu gửi lên Firebase
    public static class SettingsConfig {
        private final long pumpDuration;
        private final long firebaseInterval;

        public SettingsConfig(long pumpDuration, long firebaseInterval) {
            this.pumpDuration = pumpDuration;
            this.firebaseInterval = firebaseInterval;
        }

        public long getPumpDuration() {
            return pumpDuration;
        }

        public long getFirebaseInterval() {
            return firebaseInterval;
        }
    }
}