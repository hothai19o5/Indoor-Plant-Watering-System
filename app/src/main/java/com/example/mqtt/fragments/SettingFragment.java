package com.example.mqtt.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mqtt.MainActivity;
import com.example.mqtt.R;
import com.google.firebase.auth.FirebaseAuth;

public class SettingFragment extends Fragment {
    
    View view;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = inflater.inflate(R.layout.fragment_setting_page, container, false);

        Button resetEspButton = view.findViewById(R.id.resetButton);
        Button logoutButton = view.findViewById(R.id.logoutButton);

        resetEspButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).sendCommand("RESET");
            }
        });

        logoutButton.setOnClickListener(v -> {
            // Logout from Firebase
            FirebaseAuth.getInstance().signOut();

            // Return to login screen
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToLogin();
            }
        });
        
        return view;
    }

}
