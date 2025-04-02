package com.example.mqtt.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mqtt.MainActivity;
import com.example.mqtt.R;
import com.example.mqtt.SensorData;

public class PumpFragment extends Fragment implements MainActivity.DataUpdateListener {
    
    View view;
    private TextView temperatureTextView;
    private TextView humidityTextView;
    private TextView soilMoistureTextView;
    private ImageView wifiConnect;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = inflater.inflate(R.layout.fragment_pump_page, container, false);

        temperatureTextView = view.findViewById(R.id.temperatureTextView);
        humidityTextView = view.findViewById(R.id.humidityTextView);
        soilMoistureTextView = view.findViewById(R.id.soilMoistureTextView);
        wifiConnect = view.findViewById(R.id.wifiIcon);

        Button turnOnPumpButton = view.findViewById(R.id.turnOnPumpButton);
        Button turnOffPumpButton = view.findViewById(R.id.turnOffPumpButton);

        turnOnPumpButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).sendCommand("TURN_ON_PUMP");
            }
        });

        turnOffPumpButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).sendCommand("TURN_OFF_PUMP");
            }
        });
        
        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onDataUpdate(SensorData sensorData) {
        if (sensorData != null) {
            Log.d("PumpFragment", "Updating UI with: " + sensorData.getTemperature() + "°C, " +
                    sensorData.getHumidity() + "%, " + sensorData.getSoilMoisture() + "%");

            temperatureTextView.setText(sensorData.getTemperature() + "°C");
            humidityTextView.setText((int)sensorData.getHumidity() + "%");
            soilMoistureTextView.setText((int)sensorData.getSoilMoisture() + "%");

            if(System.currentTimeMillis()/1000 - sensorData.getTimestamp() < 5) {
                wifiConnect.setImageResource(R.drawable.baseline_wifi_24);
            } else {
                wifiConnect.setImageResource(R.drawable.baseline_wifi_off_24);
                temperatureTextView.setText("--°C");
                humidityTextView.setText("--%");
                soilMoistureTextView.setText("--%");
            }
        } else {
            Log.w("PumpFragment", "SensorData is null in onDataUpdate");
            temperatureTextView.setText("--°C");
            humidityTextView.setText("--%");
            soilMoistureTextView.setText("--%");
        }
    }
   
}
