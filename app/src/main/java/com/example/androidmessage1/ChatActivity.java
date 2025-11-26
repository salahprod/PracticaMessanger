package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.databinding.ActivityChatBinding;
import com.example.androidmessage1.message.Message;
import com.example.androidmessage1.message.MessageAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private String chatId;
    private String otherUserId;
    private MessageAdapter messageAdapter;
    private List<Message> messages = new ArrayList<>();
    private ValueEventListener messagesListener;
    private ValueEventListener userStatusListener;
    private String currentUserId;
    private Handler statusUpdateHandler;
    private Runnable statusUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("otherUserId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (chatId == null) {
            Toast.makeText(this, "Chat ID is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Обновляем онлайн статус текущего пользователя
        updateUserOnlineStatus();

        initializeViews();

        if (otherUserId == null) {
            getOtherUserIdFromChat();
        } else {
            loadOtherUserData();
            loadMessages();
            setupKeyboardBehavior();
            markAllMessagesAsRead();
            startUserStatusTracking();
        }
    }

    // Метод для обновления онлайн статуса
    private void updateUserOnlineStatus() {
        if (currentUserId != null) {
            // Устанавливаем текущего пользователя онлайн
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .setValue(true);

            // Устанавливаем время последней активности
            long currentTime = System.currentTimeMillis();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

            String currentTimeStr = timeFormat.format(new Date(currentTime));
            String currentDateStr = dateFormat.format(new Date(currentTime));

            HashMap<String, Object> updateData = new HashMap<>();
            updateData.put("lastOnline", currentTime);
            updateData.put("lastOnlineTime", currentTimeStr);
            updateData.put("lastOnlineDate", currentDateStr);

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(updateData);

            // Устанавливаем слушатель для автоматического установки офлайн статуса при выходе
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .setValue(false);
        }
    }

    private void initializeViews() {
        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(messages);
        binding.messagesRv.setAdapter(messageAdapter);

        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });

        binding.sendMessageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
        binding.exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitToMainActivity();
            }
        });
        binding.sendVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ChatActivity.this, "Video feature coming soon", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startUserStatusTracking() {
        if (otherUserId == null) return;

        // Удаляем предыдущий слушатель, если он есть
        if (userStatusListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(otherUserId)
                    .removeEventListener(userStatusListener);
        }

        userStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
                    Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
                    String lastOnlineTime = snapshot.child("lastOnlineTime").getValue(String.class);
                    String lastOnlineDate = snapshot.child("lastOnlineDate").getValue(String.class);

                    updateUserStatusDisplay(isOnline, lastOnline, lastOnlineTime, lastOnlineDate);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatActivity", "Failed to load user status", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Users")
                .child(otherUserId)
                .addValueEventListener(userStatusListener);

        // Запускаем периодическое обновление статуса
        startPeriodicStatusUpdate();
    }

    private void startPeriodicStatusUpdate() {
        statusUpdateHandler = new Handler();
        statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Принудительно обновляем статус каждую минуту для актуальности
                if (otherUserId != null) {
                    FirebaseDatabase.getInstance().getReference("Users")
                            .child(otherUserId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
                                        Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
                                        String lastOnlineTime = snapshot.child("lastOnlineTime").getValue(String.class);
                                        String lastOnlineDate = snapshot.child("lastOnlineDate").getValue(String.class);

                                        updateUserStatusDisplay(isOnline, lastOnline, lastOnlineTime, lastOnlineDate);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e("ChatActivity", "Failed to update user status", error.toException());
                                }
                            });
                }
                statusUpdateHandler.postDelayed(this, 60000); // Обновляем каждую минуту
            }
        };
        statusUpdateHandler.post(statusUpdateRunnable);
    }

    private void updateUserStatusDisplay(Boolean isOnline, Long lastOnline, String lastOnlineTime, String lastOnlineDate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isOnline != null && isOnline) {
                    binding.userStatus.setText("online");
                    binding.userStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else {
                    if (lastOnline != null) {
                        String statusText = formatLastSeen(lastOnline, lastOnlineTime, lastOnlineDate);
                        binding.userStatus.setText(statusText);
                    } else {
                        binding.userStatus.setText("offline");
                    }
                    binding.userStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
            }
        });
    }

    private String formatLastSeen(long lastOnlineTimestamp, String lastOnlineTime, String lastOnlineDate) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastOnlineTimestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);

        // Получаем текущую дату и дату последней активности
        java.util.Calendar currentCal = java.util.Calendar.getInstance();
        java.util.Calendar lastOnlineCal = java.util.Calendar.getInstance();
        lastOnlineCal.setTimeInMillis(lastOnlineTimestamp);

        int currentDay = currentCal.get(java.util.Calendar.DAY_OF_YEAR);
        int currentYear = currentCal.get(java.util.Calendar.YEAR);
        int lastOnlineDay = lastOnlineCal.get(java.util.Calendar.DAY_OF_YEAR);
        int lastOnlineYear = lastOnlineCal.get(java.util.Calendar.YEAR);

        // Проверяем, был ли пользователь онлайн вчера
        boolean isYesterday = (currentDay - lastOnlineDay == 1 && currentYear == lastOnlineYear) ||
                (currentDay == 1 && lastOnlineDay >= 365 && currentYear - lastOnlineYear == 1);

        // Проверяем, был ли пользователь онлайн позавчера или раньше
        boolean isMoreThanTwoDays = (currentDay - lastOnlineDay > 1 && currentYear == lastOnlineYear) ||
                (currentYear - lastOnlineYear > 0);

        if (minutes < 1) {
            return "was online just now";
        } else if (minutes < 60) {
            return "was online " + minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return "was online " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (isYesterday) {
            return "was online yesterday at " + (lastOnlineTime != null ? lastOnlineTime : "unknown time");
        } else if (isMoreThanTwoDays) {
            // Для активности старше 2 дней показываем полную дату и время
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy 'at' HH:mm", Locale.getDefault());
            return "was online " + dateFormat.format(new Date(lastOnlineTimestamp));
        } else {
            return "was online " + (lastOnlineTime != null ? lastOnlineTime : "unknown time") + " " + (lastOnlineDate != null ? lastOnlineDate : "");
        }
    }

    // ВАЖНО: Метод для отметки всех сообщений как прочитанных
    private void markAllMessagesAsRead() {
        if (chatId == null || otherUserId == null) {
            Log.e("ChatActivity", "chatId or otherUserId is null");
            return;
        }

        final String currentChatId = this.chatId;
        final String currentOtherUserId = this.otherUserId;

        Log.d("ChatActivity", "Marking all messages as read in chat: " + currentChatId);

        FirebaseDatabase.getInstance().getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        HashMap<String, Object> updates = new HashMap<>();
                        final int[] markedAsRead = {0};

                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            // Отмечаем как прочитанные сообщения от другого пользователя, которые еще не прочитаны
                            if (messageId != null && ownerId != null &&
                                    ownerId.equals(currentOtherUserId) &&
                                    (isRead == null || !isRead)) {

                                updates.put("Chats/" + currentChatId + "/messages/" + messageId + "/isRead", true);
                                markedAsRead[0]++;
                                Log.d("ChatActivity", "Marking message as read: " + messageId);
                            }
                        }

                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            Log.d("ChatActivity", "Successfully marked " + markedAsRead[0] + " messages as read");
                                        } else {
                                            Log.e("ChatActivity", "Failed to mark messages as read", task.getException());
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("ChatActivity", "Failed to mark messages as read", error.toException());
                    }
                });
    }

    private void setupKeyboardBehavior() {
        binding.messageEt.postDelayed(new Runnable() {
            @Override
            public void run() {
                binding.messageEt.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(binding.messageEt, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 200);

        final ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int previousHeight = 0;

            @Override
            public void onGlobalLayout() {
                int heightDiff = binding.getRoot().getRootView().getHeight() - binding.getRoot().getHeight();
                if (Math.abs(heightDiff - previousHeight) > 100) {
                    previousHeight = heightDiff;

                    if (heightDiff > 400) {
                        binding.messagesRv.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                scrollToBottom();
                            }
                        }, 100);
                    }
                }
            }
        };

        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

        binding.messageEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    binding.messagesRv.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scrollToBottom();
                        }
                    }, 200);
                }
            }
        });
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    // ВАЖНО: Исправленный метод отправки сообщения
    private void sendMessage() {
        String messageText = binding.messageEt.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        String date = dateFormat.format(new Date());

        binding.messageEt.setText("");

        final String currentChatId = this.chatId;

        String messageKey = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .push()
                .getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        // ВАЖНО: При отправке сообщения устанавливаем isRead = true только для отправителя
        // Для получателя сообщение будет непрочитанным (isRead = false)
        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("id", messageKey);
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false); // ВАЖНО: по умолчанию сообщение непрочитанное

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .child(messageKey)
                .setValue(messageInfo)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateLastMessageInChat(messageText, System.currentTimeMillis());
                        Log.d("ChatActivity", "Message sent with isRead = false (will be marked as read by receiver)");
                    } else {
                        Toast.makeText(ChatActivity.this, "Send error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateLastMessageInChat(String lastMessage, long timestamp) {
        final String currentChatId = this.chatId;

        HashMap<String, Object> updateData = new HashMap<>();
        updateData.put("LastMessage", lastMessage);
        updateData.put("LastMessageTime", timestamp);
        updateData.put("lastMessageTimestamp", timestamp);

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .updateChildren(updateData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("ChatActivity", "Last message updated: " + lastMessage);
                    } else {
                        Log.e("ChatActivity", "Failed to update last message", task.getException());
                    }
                });
    }

    private void loadOtherUserData() {
        if (otherUserId == null) return;

        final String currentOtherUserId = this.otherUserId;

        FirebaseDatabase.getInstance().getReference("Users").child(currentOtherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String username = snapshot.child("login").getValue(String.class);
                            if (username != null) {
                                binding.chatUserName.setText(username);
                            } else {
                                String email = snapshot.child("email").getValue(String.class);
                                if (email != null && email.contains("@")) {
                                    binding.chatUserName.setText(email.substring(0, email.indexOf("@")));
                                } else {
                                    binding.chatUserName.setText("Unknown User");
                                }
                            }

                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage != null && !profileImage.isEmpty()) {
                                Glide.with(ChatActivity.this)
                                        .load(profileImage)
                                        .placeholder(R.drawable.artem)
                                        .error(R.drawable.artem)
                                        .into(binding.chatUserAvatar);
                            }

                            // Загружаем начальный статус
                            Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
                            Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
                            String lastOnlineTime = snapshot.child("lastOnlineTime").getValue(String.class);
                            String lastOnlineDate = snapshot.child("lastOnlineDate").getValue(String.class);

                            updateUserStatusDisplay(isOnline, lastOnline, lastOnlineTime, lastOnlineDate);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void getOtherUserIdFromChat() {
        final String currentChatId = this.chatId;
        final String currentCurrentUserId = this.currentUserId;

        FirebaseDatabase.getInstance().getReference("Chats").child(currentChatId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String userId1 = snapshot.child("user1").getValue(String.class);
                            String userId2 = snapshot.child("user2").getValue(String.class);

                            if (userId1 != null && userId2 != null) {
                                String newOtherUserId = userId1.equals(currentCurrentUserId) ? userId2 : userId1;
                                otherUserId = newOtherUserId;

                                loadOtherUserData();
                                loadMessages();
                                setupKeyboardBehavior();
                                markAllMessagesAsRead();
                                startUserStatusTracking();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load chat data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadMessages() {
        if (chatId == null || otherUserId == null) return;

        final String currentChatId = this.chatId;
        final String currentOtherUserId = this.otherUserId;

        if (messagesListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(currentChatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }

        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messages.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String messageId = messageSnapshot.getKey();
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        String text = messageSnapshot.child("text").getValue(String.class);
                        String date = messageSnapshot.child("date").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        if (messageId != null && ownerId != null && text != null && date != null) {
                            Message message = new Message(messageId, ownerId, text, date);
                            messages.add(message);

                            // Автоматически помечаем входящие сообщения как прочитанные при загрузке
                            if (ownerId.equals(currentOtherUserId) && (isRead == null || !isRead)) {
                                markSingleMessageAsRead(messageId);
                            }
                        }
                    }
                }

                messageAdapter.notifyDataSetChanged();
                scrollToBottom();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .addValueEventListener(messagesListener);
    }

    private void markSingleMessageAsRead(String messageId) {
        final String currentChatId = this.chatId;

        FirebaseDatabase.getInstance().getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .child(messageId)
                .child("isRead")
                .setValue(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("ChatActivity", "Message marked as read: " + messageId);
                    } else {
                        Log.e("ChatActivity", "Failed to mark message as read: " + messageId);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }
        // Обновляем онлайн статус при возвращении в приложение
        updateUserOnlineStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }
    }

    private void exitToMainActivity() {
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && binding.messageEt.hasFocus()) {
            imm.hideSoftInputFromWindow(binding.messageEt.getWindowToken(), 0);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Очищаем onDisconnect при выходе
        if (currentUserId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .cancel();
        }

        // Очищаем слушатели
        if (messagesListener != null && chatId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }

        if (userStatusListener != null && otherUserId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(otherUserId)
                    .removeEventListener(userStatusListener);
        }

        if (statusUpdateHandler != null && statusUpdateRunnable != null) {
            statusUpdateHandler.removeCallbacks(statusUpdateRunnable);
        }
    }
}