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

import com.example.androidmessage1.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    public void goToLoginActivity(View view) {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Кнопка регистрации
        binding.button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String login = binding.loginText2.getText().toString().trim();
                String email = binding.emailText2.getText().toString().trim();
                String password = binding.passwordText2.getText().toString().trim();

                if (login.isEmpty() || password.isEmpty() || email.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (password.length() < 6) {
                    Toast.makeText(getApplicationContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Блокируем кнопку чтобы избежать повторных нажатий
                binding.button2.setEnabled(false);
                Toast.makeText(getApplicationContext(), "Registering...", Toast.LENGTH_SHORT).show();

                // Создаем пользователя в Firebase Auth
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Регистрация успешна - получаем UID созданного пользователя
                                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                                // Создаем данные пользователя для базы с онлайн статусом
                                Map<String, Object> userInfo = new HashMap<>();
                                userInfo.put("login", login);
                                userInfo.put("email", email);
                                userInfo.put("profileImage", "");
                                userInfo.put("isOnline", true);

                                // Записываем в базу данных
                                FirebaseDatabase.getInstance()
                                        .getReference()
                                        .child("Users")
                                        .child(userId)
                                        .setValue(userInfo)
                                        .addOnCompleteListener(dbTask -> {
                                            if (dbTask.isSuccessful()) {
                                                // Устанавливаем onDisconnect для автоматического offline
                                                FirebaseDatabase.getInstance().getReference("Users")
                                                        .child(userId)
                                                        .child("isOnline")
                                                        .onDisconnect()
                                                        .setValue(false);

                                                Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                                finish();
                                            } else {
                                                binding.button2.setEnabled(true);
                                                Toast.makeText(RegisterActivity.this, "Error saving user data: " +
                                                        dbTask.getException().getMessage(), Toast.LENGTH_LONG).show();
                                            }
                                        });

                            } else {
                                // Ошибка регистрации
                                binding.button2.setEnabled(true);
                                String errorMessage = "Registration failed";
                                if (task.getException() != null) {
                                    errorMessage = task.getException().getMessage();
                                }
                                Toast.makeText(RegisterActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        // Кликабельный текст "Условия использования"
        binding.politicalButton2.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, PoliticalActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}