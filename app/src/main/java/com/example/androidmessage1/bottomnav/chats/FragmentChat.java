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
import androidx.recyclerview.widget.RecyclerView;

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
    private ArrayList<Chat> combinedChats = new ArrayList<>();
    private Map<String, ValueEventListener> chatListeners = new HashMap<>();
    private Map<String, ValueEventListener> groupListeners = new HashMap<>();
    private ValueEventListener chatsListListener;
    private ValueEventListener groupsListListener;
    private String currentUserId;
    private int scrollPosition = 0;
    private boolean isFirstLoad = true;

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

        // Сохраняем позицию прокрутки
        binding.chatsRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    scrollPosition = layoutManager.findFirstVisibleItemPosition();
                }
            }
        });

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
        if (chatsListListener != null) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .removeEventListener(chatsListListener);
        }

        chatsListListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("FragmentChat", "Chats data changed, total chats: " + snapshot.getChildrenCount());

                // Сохраняем текущую позицию прокрутки
                saveScrollPosition();

                // Очищаем старые слушатели чатов
                for (ValueEventListener listener : chatListeners.values()) {
                    FirebaseDatabase.getInstance().getReference("Chats").removeEventListener(listener);
                }
                chatListeners.clear();

                // Создаем временный список для новых чатов
                List<Chat> newChats = new ArrayList<>();
                Map<String, Chat> currentChatsMap = new HashMap<>();

                // Собираем текущие приватные чаты
                for (Chat chat : combinedChats) {
                    if (!chat.isGroup()) {
                        currentChatsMap.put(chat.getChat_id(), chat);
                    } else {
                        newChats.add(chat); // Сохраняем группы
                    }
                }

                // Обрабатываем новые чаты
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();
                    if (chatId == null) continue;

                    String user1 = chatSnapshot.child("user1").getValue(String.class);
                    String user2 = chatSnapshot.child("user2").getValue(String.class);

                    if (user1 == null || user2 == null) continue;

                    if (user1.equals(currentUserId) || user2.equals(currentUserId)) {
                        String otherUserId = user1.equals(currentUserId) ? user2 : user1;

                        // Если чат уже существует, обновляем его, иначе создаем новый
                        Chat existingChat = currentChatsMap.get(chatId);
                        if (existingChat != null) {
                            newChats.add(existingChat);
                        } else {
                            setupChatListener(chatId, otherUserId);
                        }
                    }
                }

                // Обновляем список
                combinedChats.clear();
                combinedChats.addAll(newChats);
                sortAndUpdateChatsSilently();

                // Восстанавливаем позицию прокрутки
                restoreScrollPosition();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Failed to load chats", error.toException());
                Toast.makeText(getContext(), "Failed to load chats", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance().getReference("Chats")
                .addValueEventListener(chatsListListener);
    }

    private void loadGroups() {
        if (groupsListListener != null) {
            FirebaseDatabase.getInstance().getReference("Groups")
                    .removeEventListener(groupsListListener);
        }

        groupsListListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("FragmentChat", "Groups data changed, total groups: " + snapshot.getChildrenCount());

                // Сохраняем текущую позицию прокрутки
                saveScrollPosition();

                // Очищаем старые слушатели групп
                for (ValueEventListener listener : groupListeners.values()) {
                    FirebaseDatabase.getInstance().getReference("Groups").removeEventListener(listener);
                }
                groupListeners.clear();

                // Создаем временный список для новых групп
                List<Chat> newChats = new ArrayList<>();
                Map<String, Chat> currentGroupsMap = new HashMap<>();

                // Собираем текущие группы
                for (Chat chat : combinedChats) {
                    if (chat.isGroup()) {
                        currentGroupsMap.put(chat.getChat_id(), chat);
                    } else {
                        newChats.add(chat); // Сохраняем приватные чаты
                    }
                }

                // Обрабатываем новые группы
                for (DataSnapshot groupSnapshot : snapshot.getChildren()) {
                    String groupId = groupSnapshot.getKey();
                    if (groupId == null) continue;

                    // Если группа уже существует, обновляем ее, иначе создаем новую
                    Chat existingGroup = currentGroupsMap.get(groupId);
                    if (existingGroup != null) {
                        newChats.add(existingGroup);
                    } else {
                        setupGroupListener(groupId);
                    }
                }

                // Обновляем список
                combinedChats.clear();
                combinedChats.addAll(newChats);
                sortAndUpdateChatsSilently();

                // Восстанавливаем позицию прокрутки
                restoreScrollPosition();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Failed to load groups", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Groups")
                .addValueEventListener(groupsListListener);
    }

    private void setupGroupListener(String groupId) {
        ValueEventListener groupListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot groupSnapshot) {
                try {
                    Group group = groupSnapshot.getValue(Group.class);
                    if (group != null && group.getMembers() != null && group.getMembers().contains(currentUserId)) {
                        updateOrAddGroupSilently(group);
                    } else {
                        removeGroupSilently(groupId);
                    }
                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing group data for group: " + groupId, e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Group listener cancelled for group: " + groupId, error.toException());
            }
        };

        groupListeners.put(groupId, groupListener);
        FirebaseDatabase.getInstance().getReference("Groups").child(groupId)
                .addValueEventListener(groupListener);
    }

    private void updateOrAddGroupSilently(Group newGroup) {
        String groupId = newGroup.getGroupId();
        int existingIndex = -1;

        // Ищем существующую группу
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (chat.isGroup() && chat.getChat_id().equals(groupId)) {
                existingIndex = i;
                break;
            }
        }

        Chat groupChat = createGroupChat(newGroup);

        if (existingIndex != -1) {
            // Обновляем существующую группу
            Chat existingChat = combinedChats.get(existingIndex);
            if (needsUpdate(existingChat, groupChat)) {
                combinedChats.set(existingIndex, groupChat);
                sortAndUpdateChatsSilently();
            }
        } else {
            // Добавляем новую группу
            combinedChats.add(groupChat);
            sortAndUpdateChatsSilently();
        }
    }

    private boolean needsUpdate(Chat oldChat, Chat newChat) {
        return !oldChat.getLastMessage().equals(newChat.getLastMessage()) ||
                oldChat.getLastMessageTimestamp() != newChat.getLastMessageTimestamp() ||
                !oldChat.getChat_name().equals(newChat.getChat_name());
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
        groupChat.setUnreadCount(0);
        groupChat.setGroup(true);
        return groupChat;
    }

    private String formatGroupLastMessage(Group group) {
        String lastMessage = group.getLastMessage();
        String lastSender = group.getLastMessageSender();

        if (lastMessage != null && !lastMessage.isEmpty() && !lastMessage.equals("Group created")) {
            if (lastSender != null && !lastSender.equals("system") && !lastSender.equals("System")) {
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
        try {
            String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (currentUserEmail != null && senderName != null) {
                String usernameFromEmail = currentUserEmail.substring(0, currentUserEmail.indexOf("@"));
                return senderName.equals(usernameFromEmail) || senderName.contains(usernameFromEmail);
            }
        } catch (Exception e) {
            Log.e("FragmentChat", "Error checking if current user", e);
        }
        return false;
    }

    private void removeGroupSilently(String groupId) {
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (chat.isGroup() && chat.getChat_id().equals(groupId)) {
                combinedChats.remove(i);
                if (chatsAdapter != null) {
                    chatsAdapter.notifyItemRemoved(i);
                }
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

                    loadUserInfo(chatId, otherUserId, lastMessage, lastMessageTime);

                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing chat data for chat: " + chatId, e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Chat listener cancelled for chat: " + chatId, error.toException());
            }
        };

        chatListeners.put(chatId, chatListener);
        FirebaseDatabase.getInstance().getReference("Chats").child(chatId)
                .addValueEventListener(chatListener);
    }

    private void loadUserInfo(String chatId, String otherUserId, String lastMessage, long lastMessageTime) {
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

                        Chat chat = new Chat(chatId, otherUserId, currentUserId, chatName);
                        chat.setLastMessage(lastMessage != null ? lastMessage : "");
                        chat.setLastMessageTime(String.valueOf(lastMessageTime));
                        chat.setLastMessageTimestamp(lastMessageTime);
                        chat.setUnreadCount(0);
                        chat.setGroup(false);

                        updateOrAddChatSilently(chat);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FragmentChat", "Failed to load user info for user: " + otherUserId, error.toException());
                    }
                });
    }

    private void updateOrAddChatSilently(Chat newChat) {
        String chatId = newChat.getChat_id();
        int existingIndex = -1;

        // Ищем существующий чат
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (!chat.isGroup() && chat.getChat_id().equals(chatId)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            // Обновляем существующий чат
            Chat existingChat = combinedChats.get(existingIndex);
            if (needsUpdate(existingChat, newChat)) {
                combinedChats.set(existingIndex, newChat);
                sortAndUpdateChatsSilently();
            }
        } else {
            // Добавляем новый чат
            combinedChats.add(newChat);
            sortAndUpdateChatsSilently();
        }
    }

    private void sortAndUpdateChatsSilently() {
        try {
            // Сохраняем позицию перед сортировкой
            saveScrollPosition();

            // Сортируем по времени последнего сообщения (новые сверху)
            Collections.sort(combinedChats, (chat1, chat2) ->
                    Long.compare(chat2.getLastMessageTimestamp(), chat1.getLastMessageTimestamp()));

            if (chatsAdapter != null) {
                chatsAdapter.notifyDataSetChanged();
            }

            // Восстанавливаем позицию после обновления
            restoreScrollPosition();

            Log.d("FragmentChat", "Chats sorted and updated silently. Total: " + combinedChats.size());
        } catch (Exception e) {
            Log.e("FragmentChat", "Error sorting chats", e);
        }
    }

    private void saveScrollPosition() {
        if (binding != null && binding.chatsRv.getLayoutManager() != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.chatsRv.getLayoutManager();
            scrollPosition = layoutManager.findFirstVisibleItemPosition();
        }
    }

    private void restoreScrollPosition() {
        if (binding != null && binding.chatsRv.getLayoutManager() != null && scrollPosition >= 0) {
            binding.chatsRv.post(() -> {
                LinearLayoutManager layoutManager = (LinearLayoutManager) binding.chatsRv.getLayoutManager();
                if (layoutManager != null && scrollPosition < combinedChats.size()) {
                    layoutManager.scrollToPositionWithOffset(scrollPosition, 0);
                }
            });
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
        Log.d("FragmentChat", "Fragment resumed");

        // При возвращении обновляем счетчики непрочитанных
        if (chatsAdapter != null) {
            chatsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveScrollPosition();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Очищаем адаптер
        if (chatsAdapter != null) {
            chatsAdapter.cleanup();
        }

        // Очищаем слушатели списков
        if (chatsListListener != null) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .removeEventListener(chatsListListener);
        }
        if (groupsListListener != null) {
            FirebaseDatabase.getInstance().getReference("Groups")
                    .removeEventListener(groupsListListener);
        }

        // Очищаем слушатели отдельных чатов и групп
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

        Log.d("FragmentChat", "Fragment destroyed and listeners cleaned up");
    }
}