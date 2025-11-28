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
    private ValueEventListener userRoleListener;
    private String currentUserId;
    private List<String> groupMembers = new ArrayList<>();
    private Set<String> onlineUsers = new HashSet<>();
    private DatabaseReference groupRef;
    private boolean isAdmin = false;
    private boolean isOwner = false;

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

        Log.d("GroupChatActivity", "Opening group: " + groupId + ", name: " + groupName);
        Log.d("GroupChatActivity", "Current user ID: " + currentUserId);

        initializeViews();
        loadGroupInfo();
        loadMessages();
        setupOnlineStatus();
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
                            Log.d("GroupChatActivity", "User is OWNER of the group");
                        } else if ("admin".equals(role)) {
                            isAdmin = true;
                            isOwner = false;
                            Log.d("GroupChatActivity", "User is ADMIN of the group");
                        } else {
                            isAdmin = false;
                            isOwner = false;
                            Log.d("GroupChatActivity", "User is REGULAR MEMBER of the group");
                        }
                    } else {
                        // Если роль null, считаем обычным участником
                        isAdmin = false;
                        isOwner = false;
                        Log.d("GroupChatActivity", "User role is null, considering as regular member");
                    }
                } else {
                    // Если пользователя нет в members, считаем обычным участником
                    isAdmin = false;
                    isOwner = false;
                    Log.d("GroupChatActivity", "User not found in members, considering as regular member");
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

                    // ТОЧНЫЙ подсчет участников группы
                    groupMembers.clear();
                    DataSnapshot membersSnapshot = snapshot.child("members");
                    if (membersSnapshot.exists()) {
                        for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                            String memberId = memberSnapshot.getKey();
                            if (memberId != null) {
                                groupMembers.add(memberId);
                                Log.d("GroupChatActivity", "Found member: " + memberId);
                            }
                        }
                        updateMembersCount();
                        loadOnlineMembers();
                        Log.d("GroupChatActivity", "Total members: " + groupMembers.size());
                    } else {
                        Log.w("GroupChatActivity", "No members found in group");
                        updateMembersCount();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load group info", error.toException());
            }
        };

        groupRef.addValueEventListener(groupInfoListener);
    }

    private void updateMembersCount() {
        int totalMembers = groupMembers.size();
        String membersText = totalMembers + " member" + (totalMembers != 1 ? "s" : "");
        binding.membersCount.setText(membersText);
        Log.d("GroupChatActivity", "Updated members count: " + membersText);
    }

    private void setupOnlineStatus() {
        if (currentUserId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .setValue(true);

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("lastOnline")
                    .setValue(System.currentTimeMillis());

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .setValue(false);

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("lastOnline")
                    .onDisconnect()
                    .setValue(System.currentTimeMillis());
        }
    }

    private void loadOnlineMembers() {
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

        FirebaseDatabase.getInstance().getReference("Users")
                .addValueEventListener(onlineUsersListener);
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

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
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

                        final String finalCurrentUserName = currentUserName;
                        final String finalCurrentUserAvatar = currentUserAvatar;

                        HashMap<String, Object> messageInfo = new HashMap<>();
                        messageInfo.put("text", finalMessageText);
                        messageInfo.put("ownerId", currentUserId);
                        messageInfo.put("senderName", finalCurrentUserName);
                        messageInfo.put("senderAvatar", finalCurrentUserAvatar);
                        messageInfo.put("date", finalDate);
                        messageInfo.put("timestamp", System.currentTimeMillis());
                        messageInfo.put("isRead", false);

                        HashMap<String, Object> updates = new HashMap<>();
                        updates.put("Groups/" + groupId + "/messages/" + finalMessageKey, messageInfo);
                        updates.put("Groups/" + groupId + "/lastMessage", finalMessageText);
                        updates.put("Groups/" + groupId + "/lastMessageSender", finalCurrentUserName);
                        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

                        FirebaseDatabase.getInstance().getReference()
                                .updateChildren(updates);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupChatActivity", "Failed to load user info", error.toException());
                        sendBasicMessage(finalMessageText, finalDate, finalMessageKey);
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
                        final int[] markedAsRead = {0};

                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            if (messageId != null && ownerId != null &&
                                    !ownerId.equals(currentUserId) &&
                                    (isRead == null || !isRead)) {

                                updates.put("Groups/" + groupId + "/messages/" + messageId + "/isRead", true);
                                markedAsRead[0]++;
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
        setupOnlineStatus();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (messagesListener != null && groupId != null) {
            groupRef.child("messages").removeEventListener(messagesListener);
        }

        if (groupInfoListener != null && groupId != null) {
            groupRef.removeEventListener(groupInfoListener);
        }

        if (onlineUsersListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .removeEventListener(onlineUsersListener);
        }

        if (userRoleListener != null && groupId != null) {
            groupRef.child("members").child(currentUserId).removeEventListener(userRoleListener);
        }

        if (currentUserId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .cancel();
        }
    }
}