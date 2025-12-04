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

import com.example.androidmessage1.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    public void goToLoginActivity(View view) {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private ActivityRegisterBinding binding;
    private static final String TAG = "RegisterActivity";

    // Паттерны для проверки почтовых доменов
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    // Список разрешенных почтовых доменов
    private static final String[] ALLOWED_DOMAINS = {
            "gmail.com",
            "mail.ru",
            "yandex.ru",
            "yandex.com",
            "yandex.ua",
            "yandex.kz",
            "yandex.by",
            "inbox.ru",
            "list.ru",
            "bk.ru",
            "ya.ru",
            "rambler.ru"
    };

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
                String email = binding.emailText2.getText().toString().trim().toLowerCase();
                String password = binding.passwordText2.getText().toString().trim();

                if (login.isEmpty() || password.isEmpty() || email.isEmpty()) {
                    Toast.makeText(getApplicationContext(), "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (password.length() < 6) {
                    Toast.makeText(getApplicationContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Проверка формата email
                if (!isValidEmail(email)) {
                    Toast.makeText(getApplicationContext(),
                            "Please enter a valid email (Gmail, Yandex, or Mail.ru)",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Проверка разрешенных доменов
                if (!isAllowedEmailDomain(email)) {
                    Toast.makeText(getApplicationContext(),
                            "Please use Gmail, Yandex, or Mail.ru email address",
                            Toast.LENGTH_LONG).show();
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

                                // Получаем текущее время для статуса
                                long currentTime = System.currentTimeMillis();
                                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                                String currentTimeString = timeFormat.format(new Date());
                                String currentDateString = dateFormat.format(new Date());

                                Log.d(TAG, "Registering user: " + userId + " with online status");

                                // Создаем данные пользователя для базы с онлайн статусом
                                Map<String, Object> userInfo = new HashMap<>();
                                userInfo.put("login", login);
                                userInfo.put("email", email);
                                userInfo.put("profileImage", "");
                                userInfo.put("isOnline", true);
                                userInfo.put("lastOnline", currentTime);
                                userInfo.put("lastOnlineTime", currentTimeString);
                                userInfo.put("lastOnlineDate", currentDateString);

                                // Записываем в базу данных
                                FirebaseDatabase.getInstance()
                                        .getReference()
                                        .child("Users")
                                        .child(userId)
                                        .setValue(userInfo)
                                        .addOnCompleteListener(dbTask -> {
                                            if (dbTask.isSuccessful()) {
                                                Log.d(TAG, "User data saved successfully, setting up onDisconnect");

                                                // Настраиваем onDisconnect для всех полей статуса
                                                setupOnDisconnectForNewUser(userId, currentTime, currentTimeString, currentDateString);

                                            } else {
                                                binding.button2.setEnabled(true);
                                                Log.e(TAG, "Error saving user data", dbTask.getException());
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

    // Метод для проверки общего формата email
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    // Метод для проверки разрешенных доменов
    private boolean isAllowedEmailDomain(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        // Извлекаем домен из email (часть после @)
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }

        String domain = parts[1].toLowerCase().trim();

        // Проверяем домен в списке разрешенных
        for (String allowedDomain : ALLOWED_DOMAINS) {
            if (domain.equals(allowedDomain)) {
                return true;
            }
        }

        return false;
    }

    private void setupOnDisconnectForNewUser(String userId, long currentTime, String currentTimeString, String currentDateString) {
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
                        // Все onDisconnect установлены, можно переходить
                        completeRegistration();
                    } else {
                        Log.e(TAG, "Failed to set onDisconnect for lastOnlineDate", task.getException());
                        // Все равно переходим, даже если onDisconnect не установился
                        completeRegistration();
                    }
                });
    }

    private void completeRegistration() {
        Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(RegisterActivity.this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}