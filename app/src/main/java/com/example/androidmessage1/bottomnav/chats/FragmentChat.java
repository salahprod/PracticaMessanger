package com.example.androidmessage1.bottomnav.chats;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.chats.Chat;
import com.example.androidmessage1.chats.ChatsAdapter;
import com.example.androidmessage1.databinding.FragmentChatsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FragmentChat extends Fragment {
    private FragmentChatsBinding binding;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chat> chats = new ArrayList<>();
    private Map<String, String> userNamesCache = new HashMap<>();
    private Map<String, ValueEventListener> unreadListeners = new HashMap<>();
    private ValueEventListener chatsListener;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);

        try {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } catch (Exception e) {
            Log.e("FragmentChat", "Error getting current user: " + e.getMessage());
            Toast.makeText(getContext(), "Authentication error", Toast.LENGTH_SHORT).show();
            return binding.getRoot();
        }

        binding.chatsRv.setLayoutManager(new LinearLayoutManager(getContext()));
        chatsAdapter = new ChatsAdapter(chats);
        binding.chatsRv.setAdapter(chatsAdapter);

        loadChats();

        return binding.getRoot();
    }

    private void loadChats() {
        if (chatsListener != null) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .removeEventListener(chatsListener);
        }

        chatsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    ArrayList<ChatData> tempChatsData = new ArrayList<>();

                    for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                        String chatId = chatSnapshot.getKey();
                        if (chatId == null) continue;

                        String userId1 = chatSnapshot.child("user1").getValue(String.class);
                        String userId2 = chatSnapshot.child("user2").getValue(String.class);

                        if (userId1 == null || userId2 == null) continue;

                        if (userId1.equals(currentUserId) || userId2.equals(currentUserId)) {
                            String otherUserId = userId1.equals(currentUserId) ? userId2 : userId1;

                            ChatData chatData = new ChatData();
                            chatData.chatId = chatId;
                            chatData.userId1 = userId1;
                            chatData.userId2 = userId2;
                            chatData.otherUserId = otherUserId;
                            chatData.lastMessage = getSafeString(chatSnapshot, "lastMessage");
                            chatData.lastMessageTime = getSafeString(chatSnapshot, "lastMessageTime");
                            chatData.lastMessageTimestamp = getSafeLong(chatSnapshot, "lastMessageTimestamp");

                            tempChatsData.add(chatData);
                        }
                    }

                    Collections.sort(tempChatsData, (chat1, chat2) ->
                            Long.compare(chat2.lastMessageTimestamp, chat1.lastMessageTimestamp));

                    loadUserNamesForChats(tempChatsData);

                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing chats: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Database error: " + error.getMessage());
                Toast.makeText(getContext(), "Ошибка загрузки чатов", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance().getReference("Chats")
                .addValueEventListener(chatsListener);
    }

    private static class ChatData {
        String chatId;
        String userId1;
        String userId2;
        String otherUserId;
        String lastMessage;
        String lastMessageTime;
        long lastMessageTimestamp = 0L;
    }

    private void loadUserNamesForChats(ArrayList<ChatData> tempChatsData) {
        for (ChatData chatData : tempChatsData) {
            String otherUserId = chatData.otherUserId;

            if (userNamesCache.containsKey(otherUserId)) {
                String chatName = userNamesCache.get(otherUserId);
                Chat chat = createChatFromData(chatData, chatName);
                addOrUpdateChatInList(chat);
                loadUnreadMessagesCount(chat);
            } else {
                FirebaseDatabase.getInstance().getReference("Users").child(otherUserId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                try {
                                    String chatName = "Unknown User";

                                    if (userSnapshot.exists()) {
                                        chatName = userSnapshot.child("login").getValue(String.class);
                                        if (chatName == null || chatName.trim().isEmpty()) {
                                            String email = userSnapshot.child("email").getValue(String.class);
                                            if (email != null && email.contains("@")) {
                                                chatName = email.substring(0, email.indexOf("@"));
                                            }
                                        }
                                    }

                                    userNamesCache.put(otherUserId, chatName);
                                    Chat chat = createChatFromData(chatData, chatName);
                                    addOrUpdateChatInList(chat);
                                    loadUnreadMessagesCount(chat);
                                } catch (Exception e) {
                                    Log.e("FragmentChat", "Error loading user data: " + e.getMessage());
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Chat chat = createChatFromData(chatData, "Unknown User");
                                addOrUpdateChatInList(chat);
                                loadUnreadMessagesCount(chat);
                            }
                        });
            }
        }
    }

    private Chat createChatFromData(ChatData chatData, String chatName) {
        Chat chat = new Chat(chatData.chatId, chatData.otherUserId, currentUserId, chatName);
        chat.setLastMessage(chatData.lastMessage);
        chat.setLastMessageTime(chatData.lastMessageTime);
        chat.setLastMessageTimestamp(chatData.lastMessageTimestamp);
        return chat;
    }

    private void loadUnreadMessagesCount(Chat chat) {
        String chatId = chat.getChat_id();

        if (unreadListeners.containsKey(chatId)) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(unreadListeners.get(chatId));
        }

        ValueEventListener unreadListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    int unreadCount = 0;

                    if (snapshot.exists()) {
                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            if (ownerId != null && !ownerId.equals(currentUserId)) {
                                if (isRead == null || !isRead) {
                                    unreadCount++;
                                }
                            }
                        }
                    }

                    chat.setUnreadCount(unreadCount);
                    updateChatInList(chat);

                } catch (Exception e) {
                    Log.e("FragmentChat", "Error counting unread messages: " + e.getMessage());
                    chat.setUnreadCount(0);
                    updateChatInList(chat);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Unread count cancelled: " + error.getMessage());
                chat.setUnreadCount(0);
                updateChatInList(chat);
            }
        };

        unreadListeners.put(chatId, unreadListener);
        FirebaseDatabase.getInstance().getReference("Chats")
                .child(chatId)
                .child("messages")
                .addValueEventListener(unreadListener);
    }

    private void addOrUpdateChatInList(Chat chat) {
        try {
            boolean exists = false;
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).getChat_id().equals(chat.getChat_id())) {
                    Chat existingChat = chats.get(i);
                    existingChat.setChat_name(chat.getChat_name());
                    existingChat.setLastMessage(chat.getLastMessage());
                    existingChat.setLastMessageTime(chat.getLastMessageTime());
                    existingChat.setLastMessageTimestamp(chat.getLastMessageTimestamp());
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                chats.add(chat);
            }

            sortAndUpdateChats();
        } catch (Exception e) {
            Log.e("FragmentChat", "Error adding chat to list: " + e.getMessage());
        }
    }

    private void updateChatInList(Chat updatedChat) {
        try {
            for (int i = 0; i < chats.size(); i++) {
                if (chats.get(i).getChat_id().equals(updatedChat.getChat_id())) {
                    chats.get(i).setUnreadCount(updatedChat.getUnreadCount());
                    sortAndUpdateChats();
                    break;
                }
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error updating chat in list: " + e.getMessage());
        }
    }

    private void sortAndUpdateChats() {
        try {
            Collections.sort(chats, (chat1, chat2) ->
                    Long.compare(chat2.getLastMessageTimestamp(), chat1.getLastMessageTimestamp()));

            if (chatsAdapter != null) {
                chatsAdapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error sorting chats: " + e.getMessage());
        }
    }

    private String getSafeString(DataSnapshot snapshot, String child) {
        try {
            if (snapshot.hasChild(child)) {
                String value = snapshot.child(child).getValue(String.class);
                return value != null ? value : "";
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error getting string: " + e.getMessage());
        }
        return "";
    }

    private long getSafeLong(DataSnapshot snapshot, String child) {
        try {
            if (snapshot.hasChild(child)) {
                Long value = snapshot.child(child).getValue(Long.class);
                return value != null ? value : 0L;
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error getting long: " + e.getMessage());
        }
        return 0L;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (chatsListener != null) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .removeEventListener(chatsListener);
        }

        for (Map.Entry<String, ValueEventListener> entry : unreadListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("Chats")
                        .child(entry.getKey())
                        .child("messages")
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing listener: " + e.getMessage());
            }
        }
        unreadListeners.clear();

        binding = null;
    }
}