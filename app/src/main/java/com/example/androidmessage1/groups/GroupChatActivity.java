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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class GroupChatActivity extends AppCompatActivity {

    private ActivityGroupChatBinding binding;
    private String groupId;
    private String groupName;
    private GroupMessageAdapter messageAdapter;
    private List<GroupMessage> messages = new ArrayList<>();
    private ValueEventListener messagesListener;
    private ValueEventListener groupInfoListener;
    private ValueEventListener usersListener;
    private ValueEventListener userRoleListener;
    private String currentUserId;
    private List<String> groupMembers = new ArrayList<>();
    private DatabaseReference groupRef;
    private boolean isAdmin = false;
    private boolean isOwner = false;

    private int totalMembersCount = 0;
    private int onlineMembersCount = 0;

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

        groupRef = FirebaseDatabase.getInstance().getReference("Groups").child(groupId);

        Log.d("GroupChatActivity", "Opening group: " + groupId);
        Log.d("GroupChatActivity", "Current user: " + currentUserId);

        initializeViews();
        loadGroupInfo();
        loadMessages();
        setupCurrentUserOnlineStatus();
        markAllMessagesAsRead();
        checkUserRole();

        binding.sendMessageBtn.setOnClickListener(v -> sendMessage());
        binding.exitBtn.setOnClickListener(v -> finish());
        binding.sendVideoBtn.setOnClickListener(v -> {
            Toast.makeText(GroupChatActivity.this, "Video feature coming soon", Toast.LENGTH_SHORT).show();
        });

        binding.groupImage.setOnClickListener(v -> openGroupSettings());
        binding.groupName.setOnClickListener(v -> openGroupSettings());
    }

    private void checkUserRole() {
        userRoleListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String role = snapshot.getValue(String.class);
                    if (role != null) {
                        if ("owner".equals(role)) {
                            isOwner = true;
                            isAdmin = true;
                        } else if ("admin".equals(role)) {
                            isAdmin = true;
                            isOwner = false;
                        } else {
                            isAdmin = false;
                            isOwner = false;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load user role", error.toException());
            }
        };

        groupRef.child("members").child(currentUserId).addValueEventListener(userRoleListener);
    }

    private void openGroupSettings() {
        Intent intent = new Intent(this, GroupSettingsActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("isAdmin", isAdmin);
        intent.putExtra("isOwner", isOwner);
        startActivity(intent);
    }

    private void initializeViews() {
        binding.groupName.setText(groupName != null ? groupName : "Group");

        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new GroupMessageAdapter(messages, groupId);
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

                    loadGroupMembers(snapshot);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load group info", error.toException());
            }
        };

        groupRef.addValueEventListener(groupInfoListener);
    }

    private void loadGroupMembers(@NonNull DataSnapshot groupSnapshot) {
        List<String> newGroupMembers = new ArrayList<>();
        DataSnapshot membersSnapshot = groupSnapshot.child("members");

        Log.d("GroupChatActivity", "=== DEBUG MEMBERS STRUCTURE ===");

        if (membersSnapshot.exists()) {
            // Логируем всю структуру members для отладки
            for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                String memberKey = memberSnapshot.getKey();
                Object memberValue = memberSnapshot.getValue();

                Log.d("GroupChatActivity", "Member - Key: " + memberKey + ", Value: " + memberValue + ", Value type: " + (memberValue != null ? memberValue.getClass().getSimpleName() : "null"));

                // СПОСОБ 1: Если значение - строка и похоже на ID пользователя
                if (memberValue instanceof String) {
                    String memberId = (String) memberValue;
                    if (isValidUserId(memberId)) {
                        newGroupMembers.add(memberId);
                        Log.d("GroupChatActivity", "✅ Added member from value: " + memberId);
                        continue;
                    }
                }

                // СПОСОБ 2: Если ключ похож на ID пользователя
                if (isValidUserId(memberKey)) {
                    newGroupMembers.add(memberKey);
                    Log.d("GroupChatActivity", "✅ Added member from key: " + memberKey);
                    continue;
                }

                Log.d("GroupChatActivity", "❌ Skipped member - Key: " + memberKey + ", Value: " + memberValue);
            }

            // СПОСОБ 3: Пробуем найти пользователей в других полях
            tryAlternativeMemberSources(groupSnapshot, newGroupMembers);

            groupMembers = newGroupMembers;
            totalMembersCount = groupMembers.size();
            updateMembersDisplay();

            Log.d("GroupChatActivity", "=== FINAL MEMBERS LIST ===");
            for (String member : groupMembers) {
                Log.d("GroupChatActivity", "Member: " + member);
            }
            Log.d("GroupChatActivity", "Total valid members: " + totalMembersCount);

            if (totalMembersCount > 0) {
                loadOnlineStatus();
            } else {
                onlineMembersCount = 0;
                updateMembersDisplay();
                Log.w("GroupChatActivity", "No valid member IDs found!");
            }
        } else {
            Log.w("GroupChatActivity", "No members found in group");
            groupMembers.clear();
            totalMembersCount = 0;
            updateMembersDisplay();
        }
    }

    private boolean isValidUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        // Проверяем что это не цифра (игнорируем "0", "1", "2", "3" и т.д.)
        if (userId.matches("\\d+")) {
            return false;
        }

        // Проверяем что ID имеет нормальную длину (обычно Firebase ID длинные)
        if (userId.length() < 10) {
            return false;
        }

        return true;
    }

    private void tryAlternativeMemberSources(@NonNull DataSnapshot groupSnapshot, List<String> membersList) {
        // Пробуем поле membersList
        DataSnapshot membersListSnapshot = groupSnapshot.child("membersList");
        if (membersListSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found membersList field");
            for (DataSnapshot memberSnapshot : membersListSnapshot.getChildren()) {
                String memberId = memberSnapshot.getValue(String.class);
                if (isValidUserId(memberId) && !membersList.contains(memberId)) {
                    membersList.add(memberId);
                    Log.d("GroupChatActivity", "✅ Added member from membersList: " + memberId);
                }
            }
        }

        // Пробуем поле participants
        DataSnapshot participantsSnapshot = groupSnapshot.child("participants");
        if (participantsSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found participants field");
            for (DataSnapshot participantSnapshot : participantsSnapshot.getChildren()) {
                String participantId = participantSnapshot.getValue(String.class);
                if (isValidUserId(participantId) && !membersList.contains(participantId)) {
                    membersList.add(participantId);
                    Log.d("GroupChatActivity", "✅ Added participant: " + participantId);
                }
            }
        }

        // Пробуем поле users
        DataSnapshot usersSnapshot = groupSnapshot.child("users");
        if (usersSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found users field");
            for (DataSnapshot userSnapshot : usersSnapshot.getChildren()) {
                String userId = userSnapshot.getValue(String.class);
                if (isValidUserId(userId) && !membersList.contains(userId)) {
                    membersList.add(userId);
                    Log.d("GroupChatActivity", "✅ Added user: " + userId);
                }
            }
        }

        // Пробуем поле memberIds
        DataSnapshot memberIdsSnapshot = groupSnapshot.child("memberIds");
        if (memberIdsSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found memberIds field");
            for (DataSnapshot memberIdSnapshot : memberIdsSnapshot.getChildren()) {
                String memberId = memberIdSnapshot.getValue(String.class);
                if (isValidUserId(memberId) && !membersList.contains(memberId)) {
                    membersList.add(memberId);
                    Log.d("GroupChatActivity", "✅ Added member from memberIds: " + memberId);
                }
            }
        }
    }

    private void loadOnlineStatus() {
        if (usersListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .removeEventListener(usersListener);
        }

        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                onlineMembersCount = 0;

                Log.d("GroupChatActivity", "=== CHECKING ONLINE STATUS FOR " + totalMembersCount + " MEMBERS ===");

                for (String memberId : groupMembers) {
                    DataSnapshot userSnapshot = usersSnapshot.child(memberId);

                    if (userSnapshot.exists()) {
                        Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);
                        Long lastOnline = userSnapshot.child("lastOnline").getValue(Long.class);

                        Log.d("GroupChatActivity", "User " + memberId + " - isOnline: " + isOnline + ", lastOnline: " + lastOnline);

                        // ТОЛЬКО ПРОВЕРКА isOnline = true (реальный онлайн статус)
                        boolean isUserOnline = isOnline != null && isOnline;

                        if (isUserOnline) {
                            onlineMembersCount++;
                            Log.d("GroupChatActivity", "✅ ONLINE: " + memberId);
                        } else {
                            Log.d("GroupChatActivity", "❌ OFFLINE: " + memberId);
                        }
                    } else {
                        Log.d("GroupChatActivity", "🚫 USER NOT FOUND IN DATABASE: " + memberId);
                    }
                }

                Log.d("GroupChatActivity", "RESULT: " + onlineMembersCount + " online out of " + totalMembersCount);
                updateMembersDisplay();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load users data", error.toException());
                onlineMembersCount = 0;
                updateMembersDisplay();
            }
        };

        FirebaseDatabase.getInstance().getReference("Users")
                .addValueEventListener(usersListener);
    }

    private void updateMembersDisplay() {
        runOnUiThread(() -> {
            String membersText = totalMembersCount + " member" + (totalMembersCount != 1 ? "s" : "");
            binding.membersCount.setText(membersText);

            if (onlineMembersCount > 0) {
                String onlineText = onlineMembersCount + " online";
                binding.onlineCount.setText(onlineText);
                binding.onlineCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                binding.onlineCount.setText("offline");
                binding.onlineCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        });
    }

    private void setupCurrentUserOnlineStatus() {
        if (currentUserId != null) {
            Log.d("GroupChatActivity", "Setting current user online: " + currentUserId);

            // Устанавливаем статус онлайн для текущего пользователя
            HashMap<String, Object> onlineUpdates = new HashMap<>();
            onlineUpdates.put("isOnline", true);
            onlineUpdates.put("lastOnline", System.currentTimeMillis());

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(onlineUpdates)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("GroupChatActivity", "✅ Current user set to online: " + currentUserId);
                        } else {
                            Log.e("GroupChatActivity", "❌ Failed to set current user online", task.getException());
                        }
                    });

            // НЕ устанавливаем обработчики отключения - пользователь остается онлайн
            // пока находится в любом из активностей приложения
        }
    }

    private void loadMessages() {
        if (messagesListener != null) {
            groupRef.child("messages").removeEventListener(messagesListener);
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

                            if (!ownerId.equals(currentUserId) && (isRead == null || !isRead)) {
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
                Log.e("GroupChatActivity", "Failed to load messages", error.toException());
                Toast.makeText(GroupChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        groupRef.child("messages").addValueEventListener(messagesListener);
    }

    private void sendMessage() {
        String messageText = binding.messageEt.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String date = dateFormat.format(new Date());

        binding.messageEt.setText("");

        final String finalMessageText = messageText;
        final String finalDate = date;

        String messageKey = groupRef.child("messages").push().getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalMessageKey = messageKey;

        loadUserDataWithCustomSettings(currentUserId, new UserDataCallback() {
            @Override
            public void onUserDataLoaded(String userName, String userAvatar) {
                HashMap<String, Object> messageInfo = new HashMap<>();
                messageInfo.put("text", finalMessageText);
                messageInfo.put("ownerId", currentUserId);
                messageInfo.put("senderName", userName);
                messageInfo.put("senderAvatar", userAvatar);
                messageInfo.put("date", finalDate);
                messageInfo.put("timestamp", System.currentTimeMillis());
                messageInfo.put("isRead", false);

                HashMap<String, Object> updates = new HashMap<>();
                updates.put("Groups/" + groupId + "/messages/" + finalMessageKey, messageInfo);
                updates.put("Groups/" + groupId + "/lastMessage", finalMessageText);
                updates.put("Groups/" + groupId + "/lastMessageSender", userName);
                updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

                FirebaseDatabase.getInstance().getReference()
                        .updateChildren(updates);
            }

            @Override
            public void onError(String error) {
                Log.e("GroupChatActivity", "Failed to load user data: " + error);
                sendBasicMessage(finalMessageText, finalDate, finalMessageKey);
            }
        });
    }

    private interface UserDataCallback {
        void onUserDataLoaded(String userName, String userAvatar);
        void onError(String error);
    }

    private void loadUserDataWithCustomSettings(String userId, UserDataCallback callback) {
        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot customSnapshot) {
                        final String[] customName = {null};
                        final String[] customAvatar = {null};

                        if (customSnapshot.exists()) {
                            customName[0] = customSnapshot.child("customName").getValue(String.class);
                            customAvatar[0] = customSnapshot.child("customImage").getValue(String.class);
                        }

                        FirebaseDatabase.getInstance().getReference("Users")
                                .child(userId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                        String userName = "User";
                                        String userAvatar = "";

                                        if (userSnapshot.exists()) {
                                            String originalName = userSnapshot.child("login").getValue(String.class);
                                            if (originalName == null || originalName.trim().isEmpty()) {
                                                String email = userSnapshot.child("email").getValue(String.class);
                                                if (email != null && email.contains("@")) {
                                                    originalName = email.substring(0, email.indexOf("@"));
                                                } else {
                                                    originalName = "User";
                                                }
                                            }

                                            String originalAvatar = userSnapshot.child("profileImage").getValue(String.class);
                                            if (originalAvatar == null) originalAvatar = "";

                                            if (customName[0] != null && !customName[0].isEmpty()) {
                                                userName = customName[0];
                                            } else {
                                                userName = originalName;
                                            }

                                            if (customAvatar[0] != null && !customAvatar[0].isEmpty()) {
                                                userAvatar = customAvatar[0];
                                            } else {
                                                userAvatar = originalAvatar;
                                            }
                                        }

                                        callback.onUserDataLoaded(userName, userAvatar);
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        callback.onError("Failed to load user data: " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Failed to load custom settings: " + error.getMessage());
                    }
                });
    }

    private void sendBasicMessage(String messageText, String date, String messageKey) {
        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("senderName", "User");
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Groups/" + groupId + "/messages/" + messageKey, messageInfo);
        updates.put("Groups/" + groupId + "/lastMessage", messageText);
        updates.put("Groups/" + groupId + "/lastMessageSender", "User");
        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates);
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    private void markAllMessagesAsRead() {
        if (groupId == null) return;

        groupRef.child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        HashMap<String, Object> updates = new HashMap<>();

                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            if (messageId != null && ownerId != null &&
                                    !ownerId.equals(currentUserId) &&
                                    (isRead == null || !isRead)) {

                                updates.put("Groups/" + groupId + "/messages/" + messageId + "/isRead", true);
                            }
                        }

                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupChatActivity", "Failed to mark messages as read", error.toException());
                    }
                });
    }

    private void markSingleMessageAsRead(String messageId) {
        groupRef.child("messages").child(messageId).child("isRead").setValue(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("GroupChatActivity", "=== ON RESUME ===");
        setupCurrentUserOnlineStatus();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("GroupChatActivity", "=== ON PAUSE ===");
        if (groupId != null) {
            markAllMessagesAsRead();
        }
        // НЕ устанавливаем офлайн статус при паузе - пользователь остается онлайн
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("GroupChatActivity", "=== ON DESTROY ===");

        // НЕ устанавливаем офлайн статус при уничтожении активности
        // Пользователь остается онлайн, если переходит в другую активность приложения

        if (messagesListener != null && groupId != null) {
            groupRef.child("messages").removeEventListener(messagesListener);
        }

        if (groupInfoListener != null && groupId != null) {
            groupRef.removeEventListener(groupInfoListener);
        }

        if (usersListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .removeEventListener(usersListener);
        }

        if (userRoleListener != null && groupId != null) {
            groupRef.child("members").child(currentUserId).removeEventListener(userRoleListener);
        }
    }
}