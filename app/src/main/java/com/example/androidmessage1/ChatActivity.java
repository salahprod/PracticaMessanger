package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import java.util.Objects;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private String chatId;
    private String otherUserId;
    private MessageAdapter messageAdapter;
    private List<Message> messages = new ArrayList<>();
    private ValueEventListener messagesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Получаем данные из Intent
        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("otherUserId");

        // Загружаем данные собеседника
        loadOtherUserData();

        // Инициализация RecyclerView
        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(messages);
        binding.messagesRv.setAdapter(messageAdapter);

        // Загружаем сообщения
        loadMessages(chatId);

        // Обработчик отправки сообщения
        binding.sendMessageBtn.setOnClickListener(v -> {
            String messageText = binding.messageEt.getText().toString().trim();
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Message field cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
            String date = simpleDateFormat.format(new Date());

            binding.messageEt.setText("");
            sendMessage(chatId, messageText, date);
        });

        // ОБРАБОТЧИК КНОПКИ ВЫХОДА
        binding.exitBtn.setOnClickListener(v -> {
            exitToMainActivity();
        });
    }

    // МЕТОД ВЫХОДА В MAIN ACTIVITY
    private void exitToMainActivity() {
        // Закрываем текущую активность и возвращаемся в MainActivity
        Intent intent = new Intent(ChatActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();

        // Можно добавить анимацию перехода
        //overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // Загрузка данных собеседника (аватарка и никнейм)
    private void loadOtherUserData() {
        if (otherUserId == null) {
            // Если otherUserId не передан, получаем его из данных чата
            getOtherUserIdFromChat();
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users").child(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Получаем никнейм
                            String username = snapshot.child("login").getValue(String.class);
                            if (username != null) {
                                binding.chatUserName.setText(username);
                            } else {
                                // Если нет логина, используем email
                                String email = snapshot.child("email").getValue(String.class);
                                if (email != null && email.contains("@")) {
                                    binding.chatUserName.setText(email.substring(0, email.indexOf("@")));
                                } else {
                                    binding.chatUserName.setText("Unknown User");
                                }
                            }

                            // Получаем аватарку
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage != null && !profileImage.isEmpty()) {
                                Glide.with(ChatActivity.this)
                                        .load(profileImage)
                                        .placeholder(R.drawable.artem)
                                        .error(R.drawable.artem)
                                        .into(binding.chatUserAvatar);
                            } else {
                                // Устанавливаем дефолтную аватарку
                                binding.chatUserAvatar.setImageResource(R.drawable.artem);
                            }
                        } else {
                            binding.chatUserName.setText("Unknown User");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                        binding.chatUserName.setText("Unknown User");
                    }
                });
    }

    // Получение ID собеседника из данных чата
    private void getOtherUserIdFromChat() {
        if (chatId == null) {
            Toast.makeText(this, "Chat ID is null", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseDatabase.getInstance().getReference("Chats").child(chatId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                            String userId1 = snapshot.child("user1").getValue(String.class);
                            String userId2 = snapshot.child("user2").getValue(String.class);

                            if (userId1 != null && userId2 != null) {
                                otherUserId = userId1.equals(currentUserId) ? userId2 : userId1;
                                loadOtherUserData(); // Загружаем данные после получения ID
                            } else {
                                binding.chatUserName.setText("Unknown User");
                            }
                        } else {
                            binding.chatUserName.setText("Unknown User");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load chat data", Toast.LENGTH_SHORT).show();
                        binding.chatUserName.setText("Unknown User");
                    }
                });
    }

    // Отправка сообщения
    private void sendMessage(String chatId, String message, String date) {
        if (chatId == null) {
            Toast.makeText(this, "Chat ID is null", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        // Создаем уникальный ключ для сообщения
        String messageKey = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId)
                .child("messages")
                .push()
                .getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        // Данные сообщения
        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", message);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());

        // Сохраняем сообщение в Firebase
        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId)
                .child("messages")
                .child(messageKey)
                .setValue(messageInfo)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Обновляем последнее сообщение в чате
                        updateLastMessage(chatId, message, date);
                        System.out.println("✅ Сообщение отправлено: " + message);
                    } else {
                        Toast.makeText(this, "Ошибка отправки: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Обновление последнего сообщения в чате
    private void updateLastMessage(String chatId, String lastMessage, String date) {
        HashMap<String, Object> updateData = new HashMap<>();
        updateData.put("lastMessage", lastMessage);
        updateData.put("lastMessageTime", date);
        updateData.put("lastMessageTimestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId)
                .updateChildren(updateData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        System.out.println("✅ Последнее сообщение обновлено");
                    } else {
                        System.out.println("❌ Ошибка обновления последнего сообщения");
                    }
                });
    }

    // Загрузка сообщений в реальном времени
    private void loadMessages(String chatId) {
        if (chatId == null) {
            Toast.makeText(this, "Chat ID is null", Toast.LENGTH_SHORT).show();
            return;
        }

        // Удаляем старый слушатель если есть
        if (messagesListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }

        // Создаем новый слушатель для реального времени
        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messages.clear();

                if (!snapshot.exists()) {
                    System.out.println("❌ Нет сообщений в чате: " + chatId);
                    messageAdapter.notifyDataSetChanged();
                    return;
                }

                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    String messageId = messageSnapshot.getKey();
                    String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                    String text = messageSnapshot.child("text").getValue(String.class);
                    String date = messageSnapshot.child("date").getValue(String.class);

                    if (messageId != null && ownerId != null && text != null && date != null) {
                        messages.add(new Message(messageId, ownerId, text, date));
                    }
                }

                // Обновляем адаптер
                messageAdapter.notifyDataSetChanged();

                // Прокручиваем к последнему сообщению
                if (messages.size() > 0) {
                    binding.messagesRv.scrollToPosition(messages.size() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Ошибка загрузки сообщений: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        // Подключаем слушатель
        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId)
                .child("messages")
                .addValueEventListener(messagesListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Удаляем слушатель при закрытии активности
        if (messagesListener != null && chatId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }
    }
}