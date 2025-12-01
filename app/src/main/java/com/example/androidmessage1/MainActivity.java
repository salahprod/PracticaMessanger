package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.androidmessage1.bottomnav.chats.FragmentChat;
import com.example.androidmessage1.bottomnav.new_chat.NewChatFragment;
import com.example.androidmessage1.bottomnav.profile.ProfileFragment;
import com.example.androidmessage1.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivity";

    // Храним текущий выбранный фрагмент
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Устанавливаем пользователя онлайн при запуске приложения
        setUserOnline();

        // Начальный фрагмент - чаты
        currentFragment = new FragmentChat();
        getSupportFragmentManager().beginTransaction()
                .replace(binding.fragmentContainer.getId(), currentFragment)
                .commit();

        binding.bottomNav.setSelectedItemId(R.id.chats);

        // Создаем экземпляры фрагментов
        FragmentChat fragmentChat = new FragmentChat();
        NewChatFragment newChatFragment = new NewChatFragment();
        ProfileFragment profileFragment = new ProfileFragment();

        Map<Integer, Fragment> fragmentMap = new HashMap<>();
        fragmentMap.put(R.id.chats, fragmentChat);
        fragmentMap.put(R.id.new_chat, newChatFragment);
        fragmentMap.put(R.id.profile, profileFragment);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = fragmentMap.get(item.getItemId());

            if (selectedFragment != null && selectedFragment != currentFragment) {
                currentFragment = selectedFragment;

                // Используем addToBackStack для сохранения состояния фрагментов
                getSupportFragmentManager().beginTransaction()
                        .replace(binding.fragmentContainer.getId(), selectedFragment)
                        .addToBackStack(null)
                        .commit();

                return true;
            }

            return false;
        });

        // Обработка повторного нажатия на уже выбранный элемент
        binding.bottomNav.setOnItemReselectedListener(item -> {
            // Если повторно нажимаем на текущий элемент, можно обновить фрагмент
            if (item.getItemId() == R.id.chats && currentFragment instanceof FragmentChat) {
                // Перезагружаем чаты для обновления данных
                FragmentChat refreshedFragment = new FragmentChat();
                currentFragment = refreshedFragment;
                getSupportFragmentManager().beginTransaction()
                        .replace(binding.fragmentContainer.getId(), refreshedFragment)
                        .commit();
            } else if (item.getItemId() == R.id.profile && currentFragment instanceof ProfileFragment) {
                // Перезагружаем профиль для обновления данных
                ProfileFragment refreshedFragment = new ProfileFragment();
                currentFragment = refreshedFragment;
                getSupportFragmentManager().beginTransaction()
                        .replace(binding.fragmentContainer.getId(), refreshedFragment)
                        .commit();
            }
        });
    }

    private void setUserOnline() {
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

            Log.d(TAG, "Setting user online: " + currentUserId);

            // Обновляем поля в базе данных
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(updates)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Successfully set user online");

                            // Устанавливаем onDisconnect для автоматического offline при разрыве соединения
                            setupOnDisconnect(currentUserId, currentTime, currentTimeString, currentDateString);
                        } else {
                            Log.e(TAG, "Failed to set user online", task.getException());
                        }
                    });
        }
    }

    private void setupOnDisconnect(String userId, long currentTime, String currentTimeString, String currentDateString) {
        // Устанавливаем onDisconnect для всех полей
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
                });
    }

    // Метод для выхода, который можно вызвать из фрагментов
    public void logoutFromApp() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (currentUserId != null) {
            // Сначала отменяем все onDisconnect операции
            cancelAllOnDisconnect(currentUserId);

            // Затем устанавливаем статус офлайн
            setUserOffline(currentUserId);

            // Выходим из Firebase Auth
            FirebaseAuth.getInstance().signOut();

            Log.d(TAG, "User logged out successfully from MainActivity: " + currentUserId);

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        }

        // Переходим на экран логина
        navigateToLogin();
    }

    // Метод для отмены всех onDisconnect операций
    private void cancelAllOnDisconnect(String userId) {
        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("isOnline")
                .onDisconnect()
                .cancel();

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("lastOnline")
                .onDisconnect()
                .cancel();

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("lastOnlineTime")
                .onDisconnect()
                .cancel();

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("lastOnlineDate")
                .onDisconnect()
                .cancel();

        Log.d(TAG, "All onDisconnect operations cancelled for user: " + userId);
    }

    // Метод для установки статуса офлайн
    private void setUserOffline(String userId) {
        long currentTime = System.currentTimeMillis();

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        String currentTimeString = timeFormat.format(new Date());
        String currentDateString = dateFormat.format(new Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline", false);
        updates.put("lastOnline", currentTime);
        updates.put("lastOnlineTime", currentTimeString);
        updates.put("lastOnlineDate", currentDateString);

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User set offline successfully: " + userId);
                    } else {
                        Log.e(TAG, "Failed to set user offline", task.getException());
                    }
                });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Если есть фрагменты в back stack, вернуться к предыдущему
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();

            // Обновляем текущий фрагмент
            Fragment fragment = getSupportFragmentManager().findFragmentById(binding.fragmentContainer.getId());
            if (fragment != null) {
                currentFragment = fragment;

                // Обновляем выбранный элемент в bottom navigation
                if (fragment instanceof FragmentChat) {
                    binding.bottomNav.setSelectedItemId(R.id.chats);
                } else if (fragment instanceof NewChatFragment) {
                    binding.bottomNav.setSelectedItemId(R.id.new_chat);
                } else if (fragment instanceof ProfileFragment) {
                    binding.bottomNav.setSelectedItemId(R.id.profile);
                }
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем статус онлайн при возвращении в приложение
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            setUserOnline();
        }

        // Обновляем текущий фрагмент при возвращении в приложение
        if (currentFragment instanceof ProfileFragment) {
            // Перезагружаем профиль для обновления данных
            ProfileFragment refreshedFragment = new ProfileFragment();
            currentFragment = refreshedFragment;
            getSupportFragmentManager().beginTransaction()
                    .replace(binding.fragmentContainer.getId(), refreshedFragment)
                    .commit();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Не устанавливаем offline здесь, onDisconnect позаботится об этом
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // При выходе из приложения устанавливаем офлайн статус
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Отменяем onDisconnect
            cancelAllOnDisconnect(currentUserId);

            // Устанавливаем статус offline
            setUserOffline(currentUserId);
        }
    }
}