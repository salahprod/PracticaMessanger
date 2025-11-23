package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
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
        setContentView(binding.getRoot());

        // Получаем данные из Intent
        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("otherUserId");

        if (chatId == null) {
            Toast.makeText(this, "Chat ID is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        System.out.println("✅ ChatActivity started - Chat ID: " + chatId);

        initializeViews();
        loadOtherUserData();
        loadMessages(chatId);
        setupKeyboardBehavior();
    }

    private void initializeViews() {
        // Настройка RecyclerView
        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(messages);
        binding.messagesRv.setAdapter(messageAdapter);

        // Автопрокрутка к новым сообщениям
        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });

        // Обработчики кликов
        binding.sendMessageBtn.setOnClickListener(v -> sendMessage());
        binding.exitBtn.setOnClickListener(v -> exitToMainActivity());
        binding.sendVideoBtn.setOnClickListener(v ->
                Toast.makeText(this, "Video feature coming soon", Toast.LENGTH_SHORT).show());
    }

    private void setupKeyboardBehavior() {
        // Автоматически показываем клавиатуру при открытии чата
        binding.messageEt.postDelayed(() -> {
            binding.messageEt.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.messageEt, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);

        // Слушатель изменений клавиатуры
        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int previousHeight = 0;

            @Override
            public void onGlobalLayout() {
                int heightDiff = binding.getRoot().getRootView().getHeight() - binding.getRoot().getHeight();
                if (Math.abs(heightDiff - previousHeight) > 100) { // Клавиатура изменила состояние
                    previousHeight = heightDiff;

                    if (heightDiff > 400) { // Клавиатура открыта
                        binding.messagesRv.postDelayed(() -> scrollToBottom(), 100);
                    }
                }
            }
        });

        // Прокрутка при фокусе на поле ввода
        binding.messageEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.messagesRv.postDelayed(() -> scrollToBottom(), 200);
            }
        });
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    private void sendMessage() {
        String messageText = binding.messageEt.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        String date = dateFormat.format(new Date());

        // Очищаем поле ввода сразу
        binding.messageEt.setText("");

        String currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
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

        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId)
                .child("messages")
                .child(messageKey)
                .setValue(messageInfo)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateLastMessage(messageText, date);
                        System.out.println("✅ Message sent: " + messageText);
                    } else {
                        Toast.makeText(this, "Send error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateLastMessage(String lastMessage, String date) {
        HashMap<String, Object> updateData = new HashMap<>();
        updateData.put("lastMessage", lastMessage);
        updateData.put("lastMessageTime", date);
        updateData.put("lastMessageTimestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId)
                .updateChildren(updateData);
    }

    private void loadOtherUserData() {
        if (otherUserId == null) {
            getOtherUserIdFromChat();
            return;
        }

        FirebaseDatabase.getInstance().getReference("Users").child(otherUserId)
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
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void getOtherUserIdFromChat() {
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
                                loadOtherUserData();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load chat data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadMessages(String chatId) {
        if (messagesListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(chatId)
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

                        if (messageId != null && ownerId != null && text != null && date != null) {
                            messages.add(new Message(messageId, ownerId, text, date));
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
                .child(chatId)
                .child("messages")
                .addValueEventListener(messagesListener);
    }

    private void exitToMainActivity() {
        // Скрываем клавиатуру перед выходом
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
        if (messagesListener != null && chatId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }
    }
}