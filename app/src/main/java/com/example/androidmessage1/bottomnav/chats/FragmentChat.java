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
import androidx.recyclerview.widget.DividerItemDecoration;
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
import java.util.List;
import java.util.Map;

public class FragmentChat extends Fragment {
    private FragmentChatsBinding binding;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chat> chats = new ArrayList<>();
    private Map<String, ValueEventListener> chatListeners = new HashMap<>();
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
        loadChats();

        return binding.getRoot();
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        binding.chatsRv.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL);
        binding.chatsRv.addItemDecoration(dividerItemDecoration);

        chatsAdapter = new ChatsAdapter(chats);
        binding.chatsRv.setAdapter(chatsAdapter);
    }

    private void loadChats() {
        FirebaseDatabase.getInstance().getReference("Chats")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d("FragmentChat", "Chats data changed, total chats: " + snapshot.getChildrenCount());

                        // Удаляем старые слушатели
                        for (ValueEventListener listener : chatListeners.values()) {
                            FirebaseDatabase.getInstance().getReference("Chats").removeEventListener(listener);
                        }
                        chatListeners.clear();

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String chatId = chatSnapshot.getKey();
                            if (chatId == null) continue;

                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            Log.d("FragmentChat", "Processing chat: " + chatId + ", user1: " + user1 + ", user2: " + user2);

                            if (user1 == null || user2 == null) continue;

                            // Проверяем, принадлежит ли чат текущему пользователю
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

                    // Загружаем информацию о пользователе и подсчитываем непрочитанные
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
                            chatName = userSnapshot.child("login").getValue(String.class);
                            if (chatName == null || chatName.trim().isEmpty()) {
                                String email = userSnapshot.child("email").getValue(String.class);
                                if (email != null && email.contains("@")) {
                                    chatName = email.substring(0, email.indexOf("@"));
                                }
                            }
                        }

                        // Теперь подсчитываем непрочитанные сообщения
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
                        int unreadCount = 0;
                        String actualLastMessage = lastMessage;
                        long actualLastMessageTime = lastMessageTime;

                        Log.d("FragmentChat", "Counting messages in chat: " + chatId + ", total messages: " + messagesSnapshot.getChildrenCount());

                        // Если lastMessage пустой или нет сообщений в LastMessage, ищем последнее сообщение из списка
                        if ((actualLastMessage == null || actualLastMessage.isEmpty()) && messagesSnapshot.exists()) {
                            long latestTimestamp = 0;
                            String latestMessage = "";

                            for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                                Long timestamp = messageSnapshot.child("timestamp").getValue(Long.class);
                                String text = messageSnapshot.child("text").getValue(String.class);

                                if (timestamp != null && text != null) {
                                    if (timestamp > latestTimestamp) {
                                        latestTimestamp = timestamp;
                                        latestMessage = text;
                                    }
                                }
                            }

                            if (!latestMessage.isEmpty()) {
                                actualLastMessage = latestMessage;
                                actualLastMessageTime = latestTimestamp;
                                Log.d("FragmentChat", "Found last message from messages: " + latestMessage);
                            }
                        }

                        // Считаем непрочитанные сообщения
                        for (DataSnapshot messageSnapshot : messagesSnapshot.getChildren()) {
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);
                            String text = messageSnapshot.child("text").getValue(String.class);

                            // Сообщение непрочитанное если:
                            // 1. От другого пользователя
                            // 2. isRead = false или поле отсутствует
                            if (ownerId != null && ownerId.equals(otherUserId)) {
                                if (isRead == null || !isRead) {
                                    unreadCount++;
                                    Log.d("FragmentChat", "UNREAD MESSAGE FOUND: " + text + ", isRead: " + isRead);
                                }
                            }
                        }

                        Log.d("FragmentChat", "Total unread in chat " + chatName + ": " + unreadCount + ", last message: " + actualLastMessage);

                        // Создаем или обновляем чат
                        Chat chat = new Chat(chatId, otherUserId, currentUserId, chatName);
                        chat.setLastMessage(actualLastMessage != null ? actualLastMessage : "");
                        chat.setLastMessageTime(String.valueOf(actualLastMessageTime));
                        chat.setLastMessageTimestamp(actualLastMessageTime);
                        chat.setUnreadCount(unreadCount);

                        updateOrAddChat(chat);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FragmentChat", "Failed to count messages", error.toException());
                    }
                });
    }

    private void updateOrAddChat(Chat newChat) {
        // Ищем существующий чат
        boolean found = false;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).getChat_id().equals(newChat.getChat_id())) {
                chats.set(i, newChat);
                found = true;
                break;
            }
        }

        if (!found) {
            chats.add(newChat);
        }

        // Сортируем по времени последнего сообщения (новые сверху)
        Collections.sort(chats, (chat1, chat2) ->
                Long.compare(chat2.getLastMessageTimestamp(), chat1.getLastMessageTimestamp()));

        // Обновляем адаптер
        if (chatsAdapter != null) {
            chatsAdapter.notifyDataSetChanged();
        }

        Log.d("FragmentChat", "Chat updated: " + newChat.getChat_name() +
                " | Unread: " + newChat.getUnreadCount() +
                " | Last message: " + newChat.getLastMessage() +
                " | Time: " + newChat.getLastMessageTimestamp());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("FragmentChat", "Fragment resumed - refreshing chats");
        // При возвращении на фрагмент принудительно обновляем все чаты
        if (!chats.isEmpty()) {
            for (Chat chat : chats) {
                // Перезагружаем данные для каждого чата
                setupChatListener(chat.getChat_id(), chat.getOther_user_id());
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Очищаем все слушатели
        for (Map.Entry<String, ValueEventListener> entry : chatListeners.entrySet()) {
            FirebaseDatabase.getInstance().getReference("Chats").child(entry.getKey())
                    .removeEventListener(entry.getValue());
        }
        chatListeners.clear();
        binding = null;
    }
}