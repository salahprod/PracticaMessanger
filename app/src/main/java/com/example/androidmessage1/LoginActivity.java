package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.androidmessage1.databinding.ActivityLoginBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private static final String TAG = "LoginActivity";

    public void openRegisterActivity(View view) {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EdgeToEdge.enable(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Кнопка входа
        binding.buttonLogin1.setOnClickListener(v -> {
            String email = binding.emailText1.getText().toString().trim();
            String password = binding.passwordText1.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getApplicationContext(), "Fields cannot be empty", Toast.LENGTH_SHORT).show();
            } else {
                // Блокируем кнопку и показываем сообщение о процессе
                binding.buttonLogin1.setEnabled(false);
                Toast.makeText(getApplicationContext(), "Logging in...", Toast.LENGTH_SHORT).show();

                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {

                            // Включаем кнопку обратно
                            binding.buttonLogin1.setEnabled(true);

                            if (task.isSuccessful()) {
                                Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                                // Устанавливаем онлайн статус и переходим в MainActivity
                                setUserOnlineAndNavigate();
                            } else {
                                String errorMessage = "Login failed";
                                if (task.getException() != null) {
                                    errorMessage = task.getException().getMessage();
                                }
                                Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        // Кликабельное "Условия использования"
        binding.politicalButton1.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, PoliticalActivity.class);
            startActivity(intent);
        });
    }

    private void setUserOnlineAndNavigate() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (currentUserId != null) {
            long currentTime = System.currentTimeMillis();

            // Форматируем время и дату
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            String currentTimeString = timeFormat.format(new Date());
            String currentDateString = dateFormat.format(new Date());

            Map<String, Object> updates = new HashMap<>();
            updates.put("isOnline", true);
            updates.put("lastOnline", currentTime);
            updates.put("lastOnlineTime", currentTimeString);
            updates.put("lastOnlineDate", currentDateString);

            Log.d(TAG, "Setting user online: " + currentUserId + " at " + currentTimeString + " " + currentDateString);

            // Обновляем поля в базе данных
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(updates)
                    .addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            Log.d(TAG, "Successfully set user online and updated lastOnline time");

                            // Устанавливаем onDisconnect для автоматического offline при разрыве соединения
                            setupOnDisconnect(currentUserId, currentTime, currentTimeString, currentDateString);

                        } else {
                            Log.e(TAG, "Failed to update online status", updateTask.getException());
                            // Если не удалось обновить онлайн статус, все равно переходим
                            Toast.makeText(LoginActivity.this, "Login successful, but online status update failed", Toast.LENGTH_SHORT).show();
                            navigateToMainActivity();
                        }
                    });
        } else {
            // Если userId null, все равно переходим
            navigateToMainActivity();
        }
    }

    private void setupOnDisconnect(String userId, long currentTime, String currentTimeString, String currentDateString) {
        // Устанавливаем onDisconnect для isOnline
        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("isOnline")
                .onDisconnect()
                .setValue(false)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "onDisconnect set for isOnline");
                    } else {
                        Log.e(TAG, "Failed to set onDisconnect for isOnline", task.getException());
                    }
                });

        // Устанавливаем onDisconnect для lastOnline
        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("lastOnline")
                .onDisconnect()
                .setValue(currentTime)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "onDisconnect set for lastOnline");
                    } else {
                        Log.e(TAG, "Failed to set onDisconnect for lastOnline", task.getException());
                    }
                });

        // Устанавливаем onDisconnect для lastOnlineTime
        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("lastOnlineTime")
                .onDisconnect()
                .setValue(currentTimeString)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "onDisconnect set for lastOnlineTime");
                    } else {
                        Log.e(TAG, "Failed to set onDisconnect for lastOnlineTime", task.getException());
                    }
                });

        // Устанавливаем onDisconnect для lastOnlineDate
        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("lastOnlineDate")
                .onDisconnect()
                .setValue(currentDateString)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "onDisconnect set for lastOnlineDate");
                    } else {
                        Log.e(TAG, "Failed to set onDisconnect for lastOnlineDate", task.getException());
                    }

                    // Переходим в MainActivity после настройки всех onDisconnect
                    navigateToMainActivity();
                });
    }

    private void navigateToMainActivity() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}