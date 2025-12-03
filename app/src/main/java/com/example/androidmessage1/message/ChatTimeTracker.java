package com.example.androidmessage1.message;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ChatTimeTracker {
    private static final String TAG = "ChatTimeTracker";
    private static final String PREFS_NAME = "chat_time_tracker";
    private static final String KEY_ENTER_TIME = "enter_time_";
    private static final String KEY_CHAT_ID = "current_chat_id";
    private static final String KEY_CHAT_NAME = "current_chat_name";
    private static final String KEY_IS_GROUP = "current_is_group";

    private static ChatTimeTracker instance;
    private SharedPreferences prefs;
    private String currentUserId;

    private ChatTimeTracker(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
    }

    public static synchronized ChatTimeTracker getInstance(Context context) {
        if (instance == null) {
            instance = new ChatTimeTracker(context);
        }
        return instance;
    }

    // Вызывается при входе в чат
    public void trackChatEnter(String chatId, String chatName, boolean isGroup) {
        if (currentUserId == null) return;

        // Сохраняем информацию о текущем чате
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_ENTER_TIME + chatId, System.currentTimeMillis());
        editor.putString(KEY_CHAT_ID, chatId);
        editor.putString(KEY_CHAT_NAME, chatName);
        editor.putBoolean(KEY_IS_GROUP, isGroup);
        editor.apply();

        Log.d(TAG, "Started tracking chat: " + chatName + " (" + chatId + ")");
    }

    // Вызывается при выходе из чата
    public void trackChatExit() {
        if (currentUserId == null) return;

        String chatId = prefs.getString(KEY_CHAT_ID, null);
        String chatName = prefs.getString(KEY_CHAT_NAME, null);
        boolean isGroup = prefs.getBoolean(KEY_IS_GROUP, false);
        long enterTime = prefs.getLong(KEY_ENTER_TIME + chatId, 0);

        if (chatId != null && enterTime > 0) {
            long timeSpent = System.currentTimeMillis() - enterTime;

            if (timeSpent > 1000) { // Минимум 1 секунда
                updateChatStatistics(chatId, chatName, timeSpent, isGroup);
                Log.d(TAG, "Tracked " + timeSpent + "ms in chat: " + chatName);
            }

            // Очищаем данные о текущем чате
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_CHAT_ID);
            editor.remove(KEY_CHAT_NAME);
            editor.remove(KEY_IS_GROUP);
            editor.apply();
        }
    }

    // Обновляет статистику в Firebase
    private void updateChatStatistics(String chatId, String chatName, long timeSpent, boolean isGroup) {
        if (currentUserId == null || chatId == null) return;

        DatabaseReference statsRef = FirebaseDatabase.getInstance()
                .getReference("UserStatistics")
                .child(currentUserId)
                .child("chatStatistics")
                .child(chatId);

        statsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                ChatStatistics stats;
                if (task.getResult().exists()) {
                    // Обновляем существующую статистику
                    stats = task.getResult().getValue(ChatStatistics.class);
                    if (stats != null) {
                        stats.addTimeSpent(timeSpent);
                        stats.setLastActivity(new Date());
                    } else {
                        stats = new ChatStatistics(chatId, chatName, isGroup);
                        stats.addTimeSpent(timeSpent);
                    }
                } else {
                    // Создаем новую статистику
                    stats = new ChatStatistics(chatId, chatName, isGroup);
                    stats.addTimeSpent(timeSpent);
                }

                // Сохраняем в Firebase
                statsRef.setValue(stats);
                Log.d(TAG, "Statistics updated for chat: " + chatName);
            }
        });
    }

    // Отслеживание отправки сообщения
    public void trackMessageSent(String chatId, String chatName, boolean isGroup) {
        if (currentUserId == null || chatId == null) return;

        DatabaseReference statsRef = FirebaseDatabase.getInstance()
                .getReference("UserStatistics")
                .child(currentUserId)
                .child("chatStatistics")
                .child(chatId);

        statsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                ChatStatistics stats;
                if (task.getResult().exists()) {
                    stats = task.getResult().getValue(ChatStatistics.class);
                    if (stats != null) {
                        stats.incrementMessageCount();
                        stats.setLastActivity(new Date());
                    } else {
                        stats = new ChatStatistics(chatId, chatName, isGroup);
                        stats.incrementMessageCount();
                    }
                } else {
                    stats = new ChatStatistics(chatId, chatName, isGroup);
                    stats.incrementMessageCount();
                }

                statsRef.setValue(stats);
                Log.d(TAG, "Message count incremented for chat: " + chatName);
            }
        });
    }

    // Получение топ 4 чатов по времени
    public static void getTopChats(String userId, StatisticsCallback callback) {
        DatabaseReference statsRef = FirebaseDatabase.getInstance()
                .getReference("UserStatistics")
                .child(userId)
                .child("chatStatistics");

        statsRef.orderByChild("totalTimeSpent").limitToLast(4).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Map<String, ChatStatistics> chatMap = new HashMap<>();

                if (task.getResult().exists()) {
                    for (var snapshot : task.getResult().getChildren()) {
                        ChatStatistics stats = snapshot.getValue(ChatStatistics.class);
                        if (stats != null) {
                            chatMap.put(snapshot.getKey(), stats);
                        }
                    }
                }

                callback.onStatisticsLoaded(chatMap);
            } else {
                callback.onError(task.getException());
            }
        });
    }

    public interface StatisticsCallback {
        void onStatisticsLoaded(Map<String, ChatStatistics> chatStatistics);
        void onError(Exception e);
    }
}