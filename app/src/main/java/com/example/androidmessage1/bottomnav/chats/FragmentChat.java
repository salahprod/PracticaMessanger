package com.example.androidmessage1.bottomnav.chats;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.chats.Chat;
import com.example.androidmessage1.chats.ChatsAdapter;
import com.example.androidmessage1.databinding.FragmentChatsBinding;
import com.example.androidmessage1.groups.CreateGroupActivity;
import com.example.androidmessage1.groups.Group;
import com.example.androidmessage1.groups.GroupChatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FragmentChat extends Fragment {
    private FragmentChatsBinding binding;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chat> chats = new ArrayList<>();
    private ArrayList<Chat> combinedChats = new ArrayList<>();
    private Map<String, ValueEventListener> chatListeners = new HashMap<>();
    private Map<String, ValueEventListener> groupListeners = new HashMap<>();
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);

        try {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            Log.d("FragmentChat", "Current user: " + currentUserId);
        } catch (Exception e) {
            Log.e("FragmentChat", "Error getting current user", e);
            return binding.getRoot();
        }

        setupRecyclerView();
        setupCreateGroupButton();
        loadChats();
        loadGroups();

        return binding.getRoot();
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        binding.chatsRv.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL);
        binding.chatsRv.addItemDecoration(dividerItemDecoration);

        chatsAdapter = new ChatsAdapter(combinedChats, new ChatsAdapter.OnChatClickListener() {
            @Override
            public void onChatClick(int position) {
                if (position < 0 || position >= combinedChats.size()) return;

                Chat chat = combinedChats.get(position);
                if (chat.isGroup()) {
                    openGroupChat(chat.getChat_id(), chat.getChat_name());
                } else {
                    openChatActivity(chat.getChat_id(), chat.getOther_user_id());
                }
            }
        });
        binding.chatsRv.setAdapter(chatsAdapter);
    }

    private void setupCreateGroupButton() {
        binding.createGroupBtn.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), CreateGroupActivity.class);
            startActivity(intent);
        });
    }

    private void loadChats() {
        FirebaseDatabase.getInstance().getReference("Chats")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d("FragmentChat", "Chats data changed, total chats: " + snapshot.getChildrenCount());

                        for (ValueEventListener listener : chatListeners.values()) {
                            FirebaseDatabase.getInstance().getReference("Chats").removeEventListener(listener);
                        }
                        chatListeners.clear();

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String chatId = chatSnapshot.getKey();
                            if (chatId == null) continue;

                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            if (user1 == null || user2 == null) continue;

                            if (user1.equals(currentUserId) || user2.equals(currentUserId)) {
                                String otherUserId = user1.equals(currentUserId) ? user2 : user1;
                                setupChatListener(chatId, otherUserId);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FragmentChat", "Failed to load chats", error.toException());
                        Toast.makeText(getContext(), "Failed to load chats", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadGroups() {
        FirebaseDatabase.getInstance().getReference("Groups")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d("FragmentChat", "Groups data changed, total groups: " + snapshot.getChildrenCount());

                        for (ValueEventListener listener : groupListeners.values()) {
                            FirebaseDatabase.getInstance().getReference("Groups").removeEventListener(listener);
                        }
                        groupListeners.clear();

                        for (DataSnapshot groupSnapshot : snapshot.getChildren()) {
                            String groupId = groupSnapshot.getKey();
                            if (groupId == null) continue;

                            setupGroupListener(groupId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FragmentChat", "Failed to load groups", error.toException());
                    }
                });
    }

    private void setupGroupListener(String groupId) {
        ValueEventListener groupListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot groupSnapshot) {
                try {
                    Group group = groupSnapshot.getValue(Group.class);
                    if (group != null && group.getMembers().contains(currentUserId)) {
                        updateOrAddGroup(group);
                    } else {
                        removeGroup(groupId);
                    }
                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing group data", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Group listener cancelled", error.toException());
            }
        };

        groupListeners.put(groupId, groupListener);
        FirebaseDatabase.getInstance().getReference("Groups").child(groupId)
                .addValueEventListener(groupListener);
    }

    private void updateOrAddGroup(Group newGroup) {
        boolean found = false;
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (chat.isGroup() && chat.getChat_id().equals(newGroup.getGroupId())) {
                updateGroupChat(chat, newGroup);
                found = true;
                break;
            }
        }

        if (!found) {
            Chat groupChat = createGroupChat(newGroup);
            combinedChats.add(groupChat);
        }

        sortAndUpdateChats();

        Log.d("FragmentChat", "Group updated: " + newGroup.getGroupName() +
                " | Last message: " + newGroup.getLastMessage());
    }

    private Chat createGroupChat(Group group) {
        Chat groupChat = new Chat(
                group.getGroupId(),
                "group",
                currentUserId,
                group.getGroupName()
        );
        groupChat.setLastMessage(formatGroupLastMessage(group));
        groupChat.setLastMessageTime(String.valueOf(group.getLastMessageTime()));
        groupChat.setLastMessageTimestamp(group.getLastMessageTime());
        groupChat.setUnreadCount(group.getUnreadCount());
        groupChat.setGroup(true);
        return groupChat;
    }

    private void updateGroupChat(Chat groupChat, Group group) {
        groupChat.setLastMessage(formatGroupLastMessage(group));
        groupChat.setLastMessageTime(String.valueOf(group.getLastMessageTime()));
        groupChat.setLastMessageTimestamp(group.getLastMessageTime());
        groupChat.setUnreadCount(group.getUnreadCount());
    }

    private String formatGroupLastMessage(Group group) {
        String lastMessage = group.getLastMessage();
        String lastSender = group.getLastMessageSender();

        if (lastMessage != null && !lastMessage.isEmpty() && !lastMessage.equals("Group created")) {
            if (lastSender != null && !lastSender.equals("system") && !lastSender.equals("System")) {
                // Если последнее сообщение от текущего пользователя, показываем "Вы:"
                if (isCurrentUser(lastSender)) {
                    return "Вы: " + lastMessage;
                } else {
                    return lastSender + ": " + lastMessage;
                }
            } else {
                return lastMessage;
            }
        } else {
            return "Group created";
        }
    }

    private boolean isCurrentUser(String senderName) {
        // Простая проверка - если отправитель содержит часть нашего email
        try {
            String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (currentUserEmail != null && senderName != null) {
                String usernameFromEmail = currentUserEmail.substring(0, currentUserEmail.indexOf("@"));
                return senderName.contains(usernameFromEmail);
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error checking if current user", e);
        }
        return false;
    }

    private void removeGroup(String groupId) {
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (chat.isGroup() && chat.getChat_id().equals(groupId)) {
                combinedChats.remove(i);
                chatsAdapter.notifyDataSetChanged();
                break;
            }
        }
    }

    private void setupChatListener(String chatId, String otherUserId) {
        ValueEventListener chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot chatSnapshot) {
                try {
                    String lastMessage = chatSnapshot.child("LastMessage").getValue(String.class);
                    Long lastMessageTime = chatSnapshot.child("LastMessageTime").getValue(Long.class);

                    if (lastMessageTime == null) {
                        lastMessageTime = 0L;
                    }

                    loadUserInfoAndCountUnread(chatId, otherUserId, lastMessage, lastMessageTime);

                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing chat data", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Chat listener cancelled", error.toException());
            }
        };

        chatListeners.put(chatId, chatListener);
        FirebaseDatabase.getInstance().getReference("Chats").child(chatId)
                .addValueEventListener(chatListener);
    }

    private void loadUserInfoAndCountUnread(String chatId, String otherUserId, String lastMessage, long lastMessageTime) {
        FirebaseDatabase.getInstance().getReference("Users").child(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                        String chatName = "Unknown User";
                        if (userSnapshot.exists()) {
                            try {
                                chatName = userSnapshot.child("login").getValue(String.class);
                                if (chatName == null || chatName.trim().isEmpty()) {
                                    String email = userSnapshot.child("email").getValue(String.class);
                                    if (email != null && email.contains("@")) {
                                        chatName = email.substring(0, email.indexOf("@"));
                                    } else {
                                        chatName = "User";
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("FragmentChat", "Error parsing user data", e);
                                chatName = "User";
                            }
                        }

                        countUnreadMessages(chatId, otherUserId, chatName, lastMessage, lastMessageTime);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FragmentChat", "Failed to load user info", error.toException());
                        countUnreadMessages(chatId, otherUserId, "Unknown User", lastMessage, lastMessageTime);
                    }
                });
    }

    private void countUnreadMessages(String chatId, String otherUserId, String chatName, String lastMessage, long lastMessageTime) {
        FirebaseDatabase.getInstance().getReference("Chats").child(chatId).child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot messagesSnapshot) {
                        try {
                            int unreadCount = 0;
                            String actualLastMessage = lastMessage;
                            long actualLastMessageTime = lastMessageTime;

                            // Определяем, кто написал последнее сообщение
                            String lastMessageOwner = null;
                            long latestTimestamp = 0;

                            if (messagesSnapshot.exists()) {
                                for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                                    Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);
                                    String text = messageSnapshot.child("text").getValue(String.class);
                                    String ownerId = messageSnapshot.child("ownerId").getValue(String.class);

                                    if (timestamp != null && text != null && ownerId != null) {
                                        if (timestamp > latestTimestamp) {
                                            latestTimestamp = timestamp;
                                            actualLastMessage = text;
                                            lastMessageOwner = ownerId;
                                        }
                                    }
                                }

                                if (!actualLastMessage.isEmpty()) {
                                    actualLastMessageTime = latestTimestamp;
                                }
                            }

                            // Форматируем последнее сообщение для отображения в списке чатов
                            String formattedLastMessage = actualLastMessage;
                            if (lastMessageOwner != null && lastMessageOwner.equals(currentUserId)) {
                                formattedLastMessage = "Вы: " + actualLastMessage;
                            }
                            // Для сообщений от других пользователей оставляем как есть

                            // Считаем непрочитанные сообщения
                            for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                                String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                                Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                                if (ownerId != null && ownerId.equals(otherUserId)) {
                                    if (isRead == null || !isRead) {
                                        unreadCount++;
                                    }
                                }
                            }

                            Chat chat = new Chat(chatId, otherUserId, currentUserId, chatName);
                            chat.setLastMessage(formattedLastMessage != null ? formattedLastMessage : "");
                            chat.setLastMessageTime(String.valueOf(actualLastMessageTime));
                            chat.setLastMessageTimestamp(actualLastMessageTime);
                            chat.setUnreadCount(unreadCount);
                            chat.setGroup(false);

                            updateOrAddChat(chat);
                        } catch (Exception e) {
                            Log.e("FragmentChat", "Error counting messages", e);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FragmentChat", "Failed to count messages", error.toException());
                    }
                });
    }

    private void updateOrAddChat(Chat newChat) {
        boolean found = false;
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (!chat.isGroup() && chat.getChat_id().equals(newChat.getChat_id())) {
                combinedChats.set(i, newChat);
                found = true;
                break;
            }
        }

        if (!found) {
            combinedChats.add(newChat);
        }

        sortAndUpdateChats();

        Log.d("FragmentChat", "Chat updated: " + newChat.getChat_name() +
                " | Unread: " + newChat.getUnreadCount() +
                " | Last message: " + newChat.getLastMessage());
    }

    private void sortAndUpdateChats() {
        try {
            Collections.sort(combinedChats, (chat1, chat2) ->
                    Long.compare(chat2.getLastMessageTimestamp(), chat1.getLastMessageTimestamp()));

            if (chatsAdapter != null) {
                chatsAdapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error sorting chats", e);
        }
    }

    private void openChatActivity(String chatId, String otherUserId) {
        Intent intent = new Intent(getContext(), com.example.androidmessage1.ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("otherUserId", otherUserId);
        startActivity(intent);
    }

    private void openGroupChat(String groupId, String groupName) {
        Intent intent = new Intent(getContext(), GroupChatActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("groupName", groupName);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("FragmentChat", "Fragment resumed - refreshing chats and groups");
        loadGroups();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (Map.Entry<String, ValueEventListener> entry : chatListeners.entrySet()) {
            FirebaseDatabase.getInstance().getReference("Chats").child(entry.getKey())
                    .removeEventListener(entry.getValue());
        }
        for (Map.Entry<String, ValueEventListener> entry : groupListeners.entrySet()) {
            FirebaseDatabase.getInstance().getReference("Groups").child(entry.getKey())
                    .removeEventListener(entry.getValue());
        }
        chatListeners.clear();
        groupListeners.clear();
        binding = null;
    }
}