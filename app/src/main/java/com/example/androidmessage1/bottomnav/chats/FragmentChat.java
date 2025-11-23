package com.example.androidmessage1.bottomnav.chats;

import android.os.Bundle;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FragmentChat extends Fragment {
    private FragmentChatsBinding binding;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chat> chats = new ArrayList<>();
    private Map<String, String> userNamesCache = new HashMap<>();
    private Map<String, Long> chatTimestamps = new HashMap<>(); // ✅ Кэш временных меток чатов

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);

        binding.chatsRv.setLayoutManager(new LinearLayoutManager(getContext()));
        chatsAdapter = new ChatsAdapter(chats);
        binding.chatsRv.setAdapter(chatsAdapter);

        loadChats();

        return binding.getRoot();
    }

    private void loadChats() {
        String currentUid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        FirebaseDatabase.getInstance().getReference("Chats")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ArrayList<ChatData> tempChatsData = new ArrayList<>();

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String chatId = chatSnapshot.getKey();
                            if (chatId == null) continue;

                            String userId1 = chatSnapshot.child("user1").getValue(String.class);
                            String userId2 = chatSnapshot.child("user2").getValue(String.class);

                            if (userId1 == null || userId2 == null) continue;

                            if (userId1.equals(currentUid) || userId2.equals(currentUid)) {
                                String otherUserId = userId1.equals(currentUid) ? userId2 : userId1;

                                ChatData chatData = new ChatData();
                                chatData.chatId = chatId;
                                chatData.userId1 = userId1;
                                chatData.userId2 = userId2;
                                chatData.otherUserId = otherUserId;

                                // ✅ Получаем временную метку последнего сообщения
                                long lastMessageTimestamp = getSafeLong(chatSnapshot, "lastMessageTimestamp");
                                chatData.lastMessageTimestamp = lastMessageTimestamp;

                                // ✅ Сохраняем в кэш временных меток
                                chatTimestamps.put(chatId, lastMessageTimestamp);

                                tempChatsData.add(chatData);
                            }
                        }

                        // ✅ СОРТИРУЕМ ПО ВРЕМЕНИ ПОСЛЕДНЕГО СООБЩЕНИЯ (НОВЫЕ СВЕРХУ)
                        Collections.sort(tempChatsData, (chat1, chat2) ->
                                Long.compare(chat2.lastMessageTimestamp, chat1.lastMessageTimestamp));

                        loadUserNamesForChats(tempChatsData);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Ошибка загрузки чатов: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static class ChatData {
        String chatId;
        String userId1;
        String userId2;
        String otherUserId;
        long lastMessageTimestamp = 0L;
    }

    private void loadUserNamesForChats(ArrayList<ChatData> tempChatsData) {
        ArrayList<Chat> newChats = new ArrayList<>();

        for (ChatData chatData : tempChatsData) {
            String otherUserId = chatData.otherUserId;

            if (userNamesCache.containsKey(otherUserId)) {
                String chatName = userNamesCache.get(otherUserId);
                Chat chat = createChatFromData(chatData, chatName);
                newChats.add(chat);
            } else {
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

                                userNamesCache.put(otherUserId, chatName);
                                Chat chat = createChatFromData(chatData, chatName);
                                addChatToMainList(chat);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                Chat chat = createChatFromData(chatData, "Unknown User");
                                addChatToMainList(chat);
                            }
                        });
            }
        }

        if (!newChats.isEmpty()) {
            chats.clear();
            chats.addAll(newChats);
            sortAndUpdateChats();
        }
    }

    private Chat createChatFromData(ChatData chatData, String chatName) {
        Chat chat = new Chat(chatData.chatId, chatData.userId2, chatData.userId1, chatName);
        // ✅ УСТАНАВЛИВАЕМ ВРЕМЕННУЮ МЕТКУ
        chat.setLastMessageTimestamp(chatData.lastMessageTimestamp);
        return chat;
    }

    private void addChatToMainList(Chat chat) {
        boolean exists = false;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).getChat_id().equals(chat.getChat_id())) {
                // ✅ ОБНОВЛЯЕМ СУЩЕСТВУЮЩИЙ ЧАТ С НОВОЙ ВРЕМЕННОЙ МЕТКОЙ
                chats.get(i).setLastMessageTimestamp(chat.getLastMessageTimestamp());
                exists = true;
                break;
            }
        }

        if (!exists) {
            chats.add(chat);
        }

        sortAndUpdateChats();
    }

    private void sortAndUpdateChats() {
        // ✅ СОРТИРУЕМ ПО ВРЕМЕНИ ПОСЛЕДНЕГО СООБЩЕНИЯ (НОВЫЕ СВЕРХУ)
        Collections.sort(chats, (chat1, chat2) ->
                Long.compare(chat2.getLastMessageTimestamp(), chat1.getLastMessageTimestamp()));

        chatsAdapter.notifyDataSetChanged();
    }

    private String getSafeString(DataSnapshot snapshot, String child) {
        if (snapshot.hasChild(child)) {
            String value = snapshot.child(child).getValue(String.class);
            return value != null ? value : "";
        }
        return "";
    }

    private long getSafeLong(DataSnapshot snapshot, String child) {
        if (snapshot.hasChild(child)) {
            Long value = snapshot.child(child).getValue(Long.class);
            return value != null ? value : 0L;
        }
        return 0L;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}