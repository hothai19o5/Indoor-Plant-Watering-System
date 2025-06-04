package com.example.mqtt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameEditText;
    private EditText passwordEditText;
    private ImageView visiablePassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Khởi tạo Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        Button loginButton = findViewById(R.id.login);
        Button forgotPasswordButton = findViewById(R.id.forgot_password);
        visiablePassword = findViewById(R.id.togglePasswordVisibility);

        loginButton.setOnClickListener(v -> {
            String email = usernameEditText.getText().toString();
            String password = passwordEditText.getText().toString();

            Log.d("LoginActivity", "Email: " + email + ", Password: " + password);
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginWithFirebase(email, password);
        });

        forgotPasswordButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        visiablePassword.setOnClickListener(v -> {
            if (passwordEditText.getInputType() == 1) {
                passwordEditText.setInputType(129);
                visiablePassword.setImageResource(R.drawable.baseline_visibility_off_24);
            } else if (passwordEditText.getInputType() == 129) {
                passwordEditText.setInputType(1);
                visiablePassword.setImageResource(R.drawable.baseline_visibility_24);
            }
        });
    }
    
    private void loginWithFirebase(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    // Đăng nhập thành công
                    mAuth.getCurrentUser();
                    Log.d("LoginActivity", "Login successful!");
                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                    startMainActivity();
                } else {
                    // Đăng nhập thất bại
                    Log.w("LoginActivity", "Login failed", task.getException());
                    Toast.makeText(LoginActivity.this, "Authentication failed: " + Objects.requireNonNull(task.getException()).getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void startMainActivity() {
        Log.d("LoginActivity", "Starting MainActivity");
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        // Kiểm tra người dùng đã đăng nhập chưa
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startMainActivity();
        }
    }
}
