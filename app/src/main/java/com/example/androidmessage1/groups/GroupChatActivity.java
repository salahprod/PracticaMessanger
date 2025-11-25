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

public class GroupChatActivity extends AppCompatActivity {

    private ActivityGroupChatBinding binding;
    private String groupId;
    private String groupName;
    private MessageAdapter messageAdapter;
    private List<Message> messages = new ArrayList<>();
    private ValueEventListener messagesListener;
    private ValueEventListener groupInfoListener;
    private String currentUserId;
    private List<String> groupMembers = new ArrayList<>();

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

        initializeViews();
        loadGroupInfo();
        loadMessages();
        loadOnlineMembers();
    }

    private void initializeViews() {
        binding.groupName.setText(groupName != null ? groupName : "Group");

        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(messages);
        binding.messagesRv.setAdapter(messageAdapter);

        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });

        binding.sendMessageBtn.setOnClickListener(v -> sendMessage());
        binding.exitBtn.setOnClickListener(v -> finish());
    }

    private void loadGroupInfo() {
        groupInfoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Load group basic info
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
                    }

                    // Load members and update count
                    groupMembers.clear();
                    DataSnapshot membersSnapshot = snapshot.child("members");
                    if (membersSnapshot.exists()) {
                        for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                            String memberId = memberSnapshot.getValue(String.class);
                            if (memberId != null) {
                                groupMembers.add(memberId);
                            }
                        }
                    }

                    // Update members count
                    updateMembersCount();
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

    private void updateMembersCount() {
        int totalMembers = groupMembers.size();
        binding.membersCount.setText(totalMembers + " member" + (totalMembers > 1 ? "s" : ""));
    }

    private void loadOnlineMembers() {
        // This is a simplified version - you'll need to implement online status tracking
        // For now, we'll just show the total members count
        FirebaseDatabase.getInstance().getReference("Users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int onlineCount = 0;

                        // Count members who have online status (you need to implement this)
                        for (String memberId : groupMembers) {
                            DataSnapshot userSnapshot = snapshot.child(memberId);
                            if (userSnapshot.exists()) {
                                // Check if user is online (you need to implement online status tracking)
                                Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);
                                if (isOnline != null && isOnline) {
                                    onlineCount++;
                                }
                            }
                        }

                        if (onlineCount > 0) {
                            binding.onlineCount.setText(onlineCount + " online");
                            binding.onlineCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        } else {
                            binding.onlineCount.setText("offline");
                            binding.onlineCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.onlineCount.setText("offline");
                        binding.onlineCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    }
                });
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

                        if (messageId != null && ownerId != null && text != null) {
                            if (date == null) {
                                // If date is null, create one from timestamp
                                Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);
                                if (timestamp != null) {
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                                    date = dateFormat.format(new Date(timestamp));
                                } else {
                                    date = "Unknown time";
                                }
                            }

                            Message message = new Message(messageId, ownerId, text, date);
                            messages.add(message);
                        }
                    }
                }

                messageAdapter.notifyDataSetChanged();
                scrollToBottom();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(GroupChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance()
                .getReference("Groups")
                .child(groupId)
                .child("messages")
                .addValueEventListener(messagesListener);
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

        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Groups/" + groupId + "/messages/" + messageKey, messageInfo);
        updates.put("Groups/" + groupId + "/lastMessage", messageText);
        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(GroupChatActivity.this, "Send error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
    }
}