package com.example.androidmessage1.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class NetworkTimeService extends Service {
    private static final String TAG = "NetworkTimeService";
    private Handler handler;
    private Runnable timeUpdateRunnable;
    private boolean isRunning = false;
    private String currentUserId;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        handler = new Handler();
        currentUserId = getCurrentUserId();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Обновляем currentUserId при каждом запуске
        currentUserId = getCurrentUserId();

        if (!isRunning) {
            startTimeTracking();
            isRunning = true;
        }

        return START_STICKY;
    }

    private String getCurrentUserId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    private void startTimeTracking() {
        timeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                updateOnlineTime();
                handler.postDelayed(this, 10000); // Обновляем каждые 10 секунд
            }
        };

        handler.post(timeUpdateRunnable);
        Log.d(TAG, "Time tracking started for user: " + currentUserId);
    }

    private void updateOnlineTime() {
        // Обновляем ID пользователя на случай смены аккаунта
        String userId = getCurrentUserId();

        if (userId != null && !userId.equals(currentUserId)) {
            // Пользователь сменился - устанавливаем предыдущего оффлайн
            setUserOffline(currentUserId);
            currentUserId = userId;
            Log.d(TAG, "User changed to: " + currentUserId);
        }

        if (currentUserId != null) {
            try {
                // Получаем текущее точное время
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                String currentTime = timeFormat.format(new Date());
                String currentDate = dateFormat.format(new Date());
                long timestamp = System.currentTimeMillis();

                // Обновляем время в базе данных одним запросом
                HashMap<String, Object> updates = new HashMap<>();
                updates.put("lastOnline", timestamp);
                updates.put("lastOnlineTime", currentTime);
                updates.put("lastOnlineDate", currentDate);
                updates.put("isOnline", true);

                FirebaseDatabase.getInstance().getReference("Users")
                        .child(currentUserId)
                        .updateChildren(updates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "Online time updated for user: " + currentUserId);
                            } else {
                                Log.e(TAG, "Failed to update online time", task.getException());
                            }
                        });

            } catch (Exception e) {
                Log.e(TAG, "Error updating online time", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        // Устанавливаем статус offline при уничтожении сервиса
        setUserOffline(currentUserId);

        if (handler != null && timeUpdateRunnable != null) {
            handler.removeCallbacks(timeUpdateRunnable);
        }

        isRunning = false;
    }

    private void setUserOffline(String userId) {
        if (userId != null) {
            try {
                HashMap<String, Object> updates = new HashMap<>();
                updates.put("isOnline", false);

                FirebaseDatabase.getInstance().getReference("Users")
                        .child(userId)
                        .updateChildren(updates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d(TAG, "User set to offline: " + userId);
                            } else {
                                Log.e(TAG, "Failed to set user offline: " + userId, task.getException());
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error setting user offline: " + userId, e);
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed");
        setUserOffline(currentUserId);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}