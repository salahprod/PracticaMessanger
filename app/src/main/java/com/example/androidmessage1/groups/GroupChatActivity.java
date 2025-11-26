package com.example.androidmessage1.groups;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.ActivityGroupChatBinding;
import com.example.androidmessage1.groups.messages.GroupMessage;
import com.example.androidmessage1.groups.messages.GroupMessageAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupChatActivity extends AppCompatActivity {

    private ActivityGroupChatBinding binding;
    private String groupId;
    private String groupName;
    private GroupMessageAdapter messageAdapter;
    private List<GroupMessage> messages = new ArrayList<>();
    private ValueEventListener messagesListener;
    private ValueEventListener groupInfoListener;
    private ValueEventListener onlineUsersListener;
    private String currentUserId;
    private List<String> groupMembers = new ArrayList<>();
    private Set<String> onlineUsers = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (groupId == null) {
            Toast.makeText(this, "Group ID is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d("GroupChatActivity", "Opening group: " + groupId + ", name: " + groupName);

        initializeViews();
        loadGroupInfo();
        loadMessages();
        setupOnlineStatus();
        markAllMessagesAsRead();

        binding.sendMessageBtn.setOnClickListener(v -> sendMessage());
        binding.exitBtn.setOnClickListener(v -> finish());
        binding.sendVideoBtn.setOnClickListener(v -> {
            Toast.makeText(GroupChatActivity.this, "Video feature coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void initializeViews() {
        binding.groupName.setText(groupName != null ? groupName : "Group");

        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new GroupMessageAdapter(messages);
        binding.messagesRv.setAdapter(messageAdapter);

        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });
    }

    private void loadGroupInfo() {
        groupInfoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("groupName").getValue(String.class);
                    String image = snapshot.child("groupImage").getValue(String.class);

                    if (name != null) {
                        binding.groupName.setText(name);
                        groupName = name;
                    }

                    if (image != null && !image.isEmpty()) {
                        Glide.with(GroupChatActivity.this)
                                .load(image)
                                .placeholder(R.drawable.artem)
                                .error(R.drawable.artem)
                                .into(binding.groupImage);
                    } else {
                        binding.groupImage.setImageResource(R.drawable.artem);
                    }

                    // Загружаем список участников группы
                    groupMembers.clear();
                    DataSnapshot membersSnapshot = snapshot.child("members");
                    if (membersSnapshot.exists()) {
                        for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                            String memberId = memberSnapshot.getValue(String.class);
                            if (memberId != null && !groupMembers.contains(memberId)) {
                                groupMembers.add(memberId);
                            }
                        }
                        updateMembersCount();
                        loadOnlineMembers();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load group info", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .addValueEventListener(groupInfoListener);
    }

    private void setupOnlineStatus() {
        // Устанавливаем текущего пользователя онлайн
        if (currentUserId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .setValue(true);

            // Устанавливаем слушатель для удаления онлайн статуса при выходе
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .setValue(false);
        }
    }

    private void loadOnlineMembers() {
        // Очищаем предыдущий слушатель
        if (onlineUsersListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .removeEventListener(onlineUsersListener);
        }

        onlineUsersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                onlineUsers.clear();
                int onlineCount = 0;

                for (String memberId : groupMembers) {
                    DataSnapshot userSnapshot = snapshot.child(memberId);
                    if (userSnapshot.exists()) {
                        Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);
                        if (isOnline != null && isOnline) {
                            onlineUsers.add(memberId);
                            onlineCount++;
                        }
                    }
                }

                updateOnlineCount(onlineCount);
                Log.d("GroupChatActivity", "Online users: " + onlineCount + " out of " + groupMembers.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load online users", error.toException());
                binding.onlineCount.setText("offline");
                binding.onlineCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        };

        // Слушаем изменения статуса всех пользователей
        FirebaseDatabase.getInstance().getReference("Users")
                .addValueEventListener(onlineUsersListener);
    }

    private void updateMembersCount() {
        int totalMembers = groupMembers.size();
        binding.membersCount.setText(totalMembers + " member" + (totalMembers > 1 ? "s" : ""));
    }

    private void updateOnlineCount(int onlineCount) {
        if (onlineCount > 0) {
            binding.onlineCount.setText(onlineCount + " online");
            binding.onlineCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            binding.onlineCount.setText("offline");
            binding.onlineCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void loadMessages() {
        if (messagesListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Groups")
                    .child(groupId)
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
                        String senderName = messageSnapshot.child("senderName").getValue(String.class);
                        String senderAvatar = messageSnapshot.child("senderAvatar").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        if (messageId != null && ownerId != null && text != null) {
                            GroupMessage message = new GroupMessage(messageId, ownerId, text, date);

                            if (senderName != null && !senderName.isEmpty()) {
                                message.setSenderName(senderName);
                            }

                            if (senderAvatar != null && !senderAvatar.isEmpty()) {
                                message.setSenderAvatar(senderAvatar);
                            }

                            messages.add(message);

                            // Автоматически помечаем входящие сообщения как прочитанные при загрузке
                            if (!ownerId.equals(currentUserId) && (isRead == null || !isRead)) {
                                markSingleMessageAsRead(messageId);
                            }
                        }
                    }
                }

                messageAdapter.notifyDataSetChanged();
                scrollToBottom();

                Log.d("GroupChatActivity", "Loaded " + messages.size() + " messages");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load messages", error.toException());
                Toast.makeText(GroupChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance()
                .getReference("Groups")
                .child(groupId)
                .child("messages")
                .addValueEventListener(messagesListener);
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

        String messageKey = FirebaseDatabase.getInstance()
                .getReference("Groups")
                .child(groupId)
                .child("messages")
                .push()
                .getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        // Получаем информацию о текущем пользователе
        FirebaseDatabase.getInstance().getReference("Users")
                .child(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String currentUserName = "User";
                        String currentUserAvatar = "";

                        if (snapshot.exists()) {
                            String login = snapshot.child("login").getValue(String.class);
                            if (login != null && !login.isEmpty()) {
                                currentUserName = login;
                            } else {
                                String email = snapshot.child("email").getValue(String.class);
                                if (email != null && email.contains("@")) {
                                    currentUserName = email.substring(0, email.indexOf("@"));
                                }
                            }

                            String avatar = snapshot.child("profileImage").getValue(String.class);
                            if (avatar != null && !avatar.isEmpty()) {
                                currentUserAvatar = avatar;
                            }
                        }

                        // ВАЖНО: При отправке сообщения устанавливаем isRead = false
                        // Каждый пользователь сам отметит сообщение как прочитанное когда откроет чат
                        HashMap<String, Object> messageInfo = new HashMap<>();
                        messageInfo.put("text", messageText);
                        messageInfo.put("ownerId", currentUserId);
                        messageInfo.put("senderName", currentUserName);
                        messageInfo.put("senderAvatar", currentUserAvatar);
                        messageInfo.put("date", date);
                        messageInfo.put("timestamp", System.currentTimeMillis());
                        messageInfo.put("isRead", false); // ВАЖНО: сообщение непрочитанное для всех

                        HashMap<String, Object> updates = new HashMap<>();
                        updates.put("Groups/" + groupId + "/messages/" + messageKey, messageInfo);
                        updates.put("Groups/" + groupId + "/lastMessage", messageText);
                        updates.put("Groups/" + groupId + "/lastMessageSender", currentUserName);
                        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

                        FirebaseDatabase.getInstance().getReference()
                                .updateChildren(updates)
                                .addOnCompleteListener(task -> {
                                    if (!task.isSuccessful()) {
                                        Log.e("GroupChatActivity", "Send error: " + task.getException());
                                        Toast.makeText(GroupChatActivity.this, "Send error", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Log.d("GroupChatActivity", "Group message sent with isRead = false");
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupChatActivity", "Failed to load user info", error.toException());
                        sendBasicMessage(messageText, date, messageKey);
                    }
                });
    }

    private void sendBasicMessage(String messageText, String date, String messageKey) {
        // ВАЖНО: При отправке сообщения устанавливаем isRead = false
        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("senderName", "User");
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false); // ВАЖНО: сообщение непрочитанное для всех

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Groups/" + groupId + "/messages/" + messageKey, messageInfo);
        updates.put("Groups/" + groupId + "/lastMessage", messageText);
        updates.put("Groups/" + groupId + "/lastMessageSender", "User");
        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("GroupChatActivity", "Basic group message sent with isRead = false");
                    }
                });
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    // ВАЖНО: Метод для отметки всех сообщений как прочитанных
    private void markAllMessagesAsRead() {
        if (groupId == null) {
            Log.e("GroupChatActivity", "groupId is null");
            return;
        }

        Log.d("GroupChatActivity", "Marking all group messages as read in group: " + groupId);

        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
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

                            // Отмечаем как прочитанные сообщения от других пользователей, которые еще не прочитаны
                            if (messageId != null && ownerId != null &&
                                    !ownerId.equals(currentUserId) &&
                                    (isRead == null || !isRead)) {

                                updates.put("Groups/" + groupId + "/messages/" + messageId + "/isRead", true);
                                markedAsRead[0]++;
                                Log.d("GroupChatActivity", "Marking group message as read: " + messageId);
                            }
                        }

                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            Log.d("GroupChatActivity", "Successfully marked " + markedAsRead[0] + " group messages as read");
                                        } else {
                                            Log.e("GroupChatActivity", "Failed to mark group messages as read", task.getException());
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupChatActivity", "Failed to mark group messages as read", error.toException());
                    }
                });
    }

    private void markSingleMessageAsRead(String messageId) {
        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("messages")
                .child(messageId)
                .child("isRead")
                .setValue(true)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("GroupChatActivity", "Group message marked as read: " + messageId);
                    } else {
                        Log.e("GroupChatActivity", "Failed to mark group message as read: " + messageId);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
        // Обновляем онлайн статус при возвращении в приложение
        setupOnlineStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
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
        if (messagesListener != null && groupId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Groups")
                    .child(groupId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }

        if (groupInfoListener != null && groupId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Groups")
                    .child(groupId)
                    .removeEventListener(groupInfoListener);
        }

        if (onlineUsersListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .removeEventListener(onlineUsersListener);
        }
    }
}