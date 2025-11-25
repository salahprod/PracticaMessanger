package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
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

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

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
            Map<String, Object> updates = new HashMap<>();
            updates.put("isOnline", true);

            // Обновляем поле isOnline в базе данных
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(updates)
                    .addOnCompleteListener(updateTask -> {
                        if (updateTask.isSuccessful()) {
                            // Устанавливаем onDisconnect для автоматического offline при разрыве соединения
                            FirebaseDatabase.getInstance().getReference("Users")
                                    .child(currentUserId)
                                    .child("isOnline")
                                    .onDisconnect()
                                    .setValue(false);

                            // Переходим в MainActivity
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        } else {
                            // Если не удалось обновить онлайн статус, все равно переходим
                            Toast.makeText(LoginActivity.this, "Login successful, but online status update failed", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            overridePendingTransition(0, 0);
                            finish();
                        }
                    });
        } else {
            // Если userId null, все равно переходим
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            overridePendingTransition(0, 0);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}