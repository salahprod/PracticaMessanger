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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FragmentChat extends Fragment {
    private FragmentChatsBinding binding;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chat> combinedChats = new ArrayList<>();
    private Map<String, ValueEventListener> chatListeners = new HashMap<>();
    private Map<String, ValueEventListener> groupListeners = new HashMap<>();
    private Map<String, ValueEventListener> customSettingsListeners = new HashMap<>();
    private Map<String, ValueEventListener> groupCustomSettingsListeners = new HashMap<>();
    private ValueEventListener chatsListListener;
    private ValueEventListener groupsListListener;
    private String currentUserId;
    private boolean isFragmentDestroyed = false;

    // Карта для отслеживания чатов между пользователями
    private Map<String, String> userPairToChatId = new HashMap<>();
    private Set<String> processedChats = new HashSet<>();

    // Кэш для хранения предыдущих данных для микрообновления
    private Map<String, String> previousGroupImages = new HashMap<>();
    private Map<String, String> previousGroupNames = new HashMap<>();
    private Map<String, String> previousChatNames = new HashMap<>();
    private Map<String, String> previousChatImages = new HashMap<>();
    private Map<String, String> previousLastMessages = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        isFragmentDestroyed = false;

        // Очищаем карты при создании вью
        userPairToChatId.clear();
        processedChats.clear();

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
                Log.d("FragmentChat", "Chat clicked at position: " + position);

                if (position < 0 || position >= combinedChats.size()) {
                    Log.e("FragmentChat", "Invalid chat position: " + position);
                    return;
                }

                Chat chat = combinedChats.get(position);
                Log.d("FragmentChat", "Opening chat: " + chat.getChat_name() + ", ID: " + chat.getChat_id());

                if (chat.isGroup()) {
                    openGroupChat(chat.getChat_id(), chat.getChat_name());
                } else {
                    openChatActivity(chat.getChat_id(), chat.getOther_user_id());
                }
            }
        });

        binding.chatsRv.setAdapter(chatsAdapter);
        Log.d("FragmentChat", "RecyclerView setup completed");
    }

    // Метод для микрообновления конкретного элемента
    private void updateSingleItem(int position) {
        if (chatsAdapter != null && position >= 0 && position < combinedChats.size()) {
            chatsAdapter.notifyItemChanged(position);
            Log.d("FragmentChat", "Micro-update for item at position: " + position);
        }
    }

    private void setupCreateGroupButton() {
        binding.createGroupBtn.setOnClickListener(v -> {
            Log.d("FragmentChat", "Create group button clicked");
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
                if (isFragmentDestroyed || binding == null) return;

                Log.d("FragmentChat", "Chats data changed, total chats in DB: " + snapshot.getChildrenCount());

                // Очищаем карту пар пользователей
                userPairToChatId.clear();

                // Собираем все чаты и строим карту пар пользователей
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    if (isFragmentDestroyed) return;

                    String chatId = chatSnapshot.getKey();
                    if (chatId == null) continue;

                    String user1 = chatSnapshot.child("user1").getValue(String.class);
                    String user2 = chatSnapshot.child("user2").getValue(String.class);

                    if (user1 == null || user2 == null) continue;

                    // Создаем уникальный ключ для пары пользователей (сортированный)
                    String userPairKey = getSortedUserPairKey(user1, user2);
                    userPairToChatId.put(userPairKey, chatId);

                    Log.d("FragmentChat", "Mapped user pair " + userPairKey + " to chat: " + chatId);
                }

                // Теперь обрабатываем чаты для текущего пользователя
                processChatsForCurrentUser();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFragmentDestroyed) return;
                Log.e("FragmentChat", "Failed to load chats", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Chats")
                .addValueEventListener(chatsListListener);
    }

    private String getSortedUserPairKey(String user1, String user2) {
        // Сортируем ID пользователей для создания уникального ключа независимо от порядка
        if (user1.compareTo(user2) < 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }

    private void processChatsForCurrentUser() {
        if (currentUserId == null) return;

        Log.d("FragmentChat", "Processing chats for current user: " + currentUserId);

        // Очищаем старые слушатели чатов
        for (ValueEventListener listener : chatListeners.values()) {
            FirebaseDatabase.getInstance().getReference("Chats").removeEventListener(listener);
        }
        chatListeners.clear();

        // Очищаем список обработанных чатов
        processedChats.clear();

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

        // Ищем чаты, где участвует текущий пользователь
        for (Map.Entry<String, String> entry : userPairToChatId.entrySet()) {
            String userPairKey = entry.getKey();
            String chatId = entry.getValue();

            // Проверяем, участвует ли текущий пользователь в этой паре
            if (userPairKey.contains(currentUserId + "_") || userPairKey.endsWith("_" + currentUserId)) {

                // Разбираем пару пользователей
                String[] users = userPairKey.split("_");
                String otherUserId = users[0].equals(currentUserId) ? users[1] : users[0];

                // Если чат уже существует, обновляем его, иначе создаем новый
                Chat existingChat = currentChatsMap.get(chatId);
                if (existingChat != null) {
                    newChats.add(existingChat);
                    processedChats.add(chatId);
                } else if (!processedChats.contains(chatId)) {
                    setupChatListener(chatId, otherUserId);
                    processedChats.add(chatId);
                }
            }
        }

        // Обновляем список
        combinedChats.clear();
        combinedChats.addAll(newChats);
        sortChats();
    }

    private void loadGroups() {
        if (groupsListListener != null) {
            FirebaseDatabase.getInstance().getReference("Groups")
                    .removeEventListener(groupsListListener);
        }

        groupsListListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFragmentDestroyed || binding == null) return;

                Log.d("FragmentChat", "Groups data changed, total groups: " + snapshot.getChildrenCount());

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
                    if (isFragmentDestroyed) return;

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
                sortChats();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFragmentDestroyed) return;
                Log.e("FragmentChat", "Failed to load groups", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Groups")
                .addValueEventListener(groupsListListener);
    }

    private void setupGroupListener(String groupId) {
        if (isFragmentDestroyed) return;

        // Удаляем предыдущий слушатель если есть
        if (groupListeners.containsKey(groupId)) {
            FirebaseDatabase.getInstance().getReference("Groups")
                    .child(groupId)
                    .removeEventListener(groupListeners.get(groupId));
        }

        ValueEventListener groupListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot groupSnapshot) {
                if (isFragmentDestroyed || binding == null) return;

                try {
                    Group group = groupSnapshot.getValue(Group.class);
                    if (group != null && group.getMembers() != null && group.getMembers().contains(currentUserId)) {
                        // Загружаем кастомные настройки для группы
                        loadGroupCustomSettings(groupId, group);
                    } else {
                        removeGroup(groupId);
                    }
                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing group data", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFragmentDestroyed) return;
                Log.e("FragmentChat", "Group listener cancelled", error.toException());
            }
        };

        groupListeners.put(groupId, groupListener);
        FirebaseDatabase.getInstance().getReference("Groups").child(groupId)
                .addValueEventListener(groupListener);
    }

    // МЕТОД: Загрузка кастомных настроек для групп
    private void loadGroupCustomSettings(String groupId, Group group) {
        if (isFragmentDestroyed) return;

        // Удаляем предыдущий слушатель если есть
        if (groupCustomSettingsListeners.containsKey(groupId)) {
            FirebaseDatabase.getInstance().getReference("UserCustomizations")
                    .child(currentUserId)
                    .child("groupContacts")
                    .child(groupId)
                    .removeEventListener(groupCustomSettingsListeners.get(groupId));
        }

        ValueEventListener groupCustomSettingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFragmentDestroyed || binding == null) return;

                String customName = null;
                String customImage = null;

                if (snapshot.exists()) {
                    customName = snapshot.child("customName").getValue(String.class);
                    customImage = snapshot.child("customImage").getValue(String.class);

                    Log.d("FragmentChat", "Group custom settings UPDATED for " + groupId +
                            ": name=" + customName + ", image=" + customImage);
                } else {
                    Log.d("FragmentChat", "No custom settings found for group: " + groupId);
                }

                // Создаем чат группы с учетом кастомных настроек
                createGroupChatWithCustomSettings(groupId, group, customName, customImage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Failed to load group custom settings for: " + groupId, error.toException());
                // При ошибке создаем группу без кастомных настроек
                createGroupChatWithCustomSettings(groupId, group, null, null);
            }
        };

        groupCustomSettingsListeners.put(groupId, groupCustomSettingsListener);
        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("groupContacts")
                .child(groupId)
                .addValueEventListener(groupCustomSettingsListener);

        Log.d("FragmentChat", "Group custom settings listener SETUP for: " + groupId);
    }

    // МЕТОД: Создание чата группы с учетом кастомных настроек
    private void createGroupChatWithCustomSettings(String groupId, Group group, String customName, String customImage) {
        if (isFragmentDestroyed) return;

        String originalGroupName = group.getGroupName() != null ? group.getGroupName() : "";
        String originalGroupImage = group.getGroupImage() != null ? group.getGroupImage() : "";
        String newLastMessage = formatGroupLastMessage(group);

        // ВАЖНО: Применяем кастомные настройки для групп
        // Имя: кастомное имя имеет приоритет над оригинальным
        String displayGroupName;
        if (customName != null && !customName.isEmpty()) {
            displayGroupName = customName;
            Log.d("FragmentChat", "Using CUSTOM group name: " + customName + " for group: " + groupId);
        } else {
            displayGroupName = originalGroupName;
            Log.d("FragmentChat", "Using ORIGINAL group name: " + originalGroupName + " for group: " + groupId);
        }

        // Аватарка: кастомная аватарка имеет приоритет над оригинальной
        String displayGroupImage;
        if (customImage != null && !customImage.isEmpty()) {
            displayGroupImage = customImage;
            Log.d("FragmentChat", "Using CUSTOM group image: " + customImage + " for group: " + groupId);
        } else {
            displayGroupImage = originalGroupImage;
            Log.d("FragmentChat", "Using ORIGINAL group image: " + originalGroupImage + " for group: " + groupId);
        }

        Chat groupChat = new Chat(
                groupId,
                "group",
                currentUserId,
                displayGroupName
        );
        groupChat.setLastMessage(newLastMessage);
        groupChat.setLastMessageTime(String.valueOf(group.getLastMessageTime()));
        groupChat.setLastMessageTimestamp(group.getLastMessageTime());
        groupChat.setUnreadCount(0);
        groupChat.setGroup(true);
        groupChat.setProfileImage(displayGroupImage);

        updateOrAddGroupWithMicroUpdate(groupChat, originalGroupName, originalGroupImage, newLastMessage);
    }

    // МЕТОД: Обновление группы с микрообновлением
    private void updateOrAddGroupWithMicroUpdate(Chat groupChat, String originalGroupName,
                                                 String originalGroupImage, String newLastMessage) {
        if (isFragmentDestroyed) return;

        String groupId = groupChat.getChat_id();
        String newGroupImage = groupChat.getProfileImage() != null ? groupChat.getProfileImage() : "";
        String newGroupName = groupChat.getChat_name() != null ? groupChat.getChat_name() : "";

        int existingIndex = -1;

        // Ищем существующую группу
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (chat.isGroup() && chat.getChat_id().equals(groupId)) {
                existingIndex = i;
                break;
            }
        }

        boolean needsUpdate = false;

        if (existingIndex != -1) {
            // Проверяем, изменились ли данные
            String previousImage = previousGroupImages.get(groupId);
            String previousName = previousGroupNames.get(groupId);
            String previousMessage = previousLastMessages.get(groupId);

            if (previousImage == null || !previousImage.equals(newGroupImage) ||
                    previousName == null || !previousName.equals(newGroupName) ||
                    previousMessage == null || !previousMessage.equals(newLastMessage)) {
                needsUpdate = true;
                Log.d("FragmentChat", "Group data CHANGED: " + newGroupName +
                        ", image: " + newGroupImage + ", message: " + newLastMessage);
            } else {
                Log.d("FragmentChat", "Group data UNCHANGED: " + newGroupName);
            }

            combinedChats.set(existingIndex, groupChat);

            if (needsUpdate) {
                // Микрообновление только этого элемента
                updateSingleItem(existingIndex);
                Log.d("FragmentChat", "Group UI UPDATED at position: " + existingIndex);
            }
        } else {
            combinedChats.add(groupChat);
            needsUpdate = true;
            Log.d("FragmentChat", "New group ADDED: " + newGroupName);
        }

        // Сохраняем текущие данные для будущих сравнений
        previousGroupImages.put(groupId, newGroupImage);
        previousGroupNames.put(groupId, newGroupName);
        previousLastMessages.put(groupId, newLastMessage);

        // Сортируем только если это новая группа или были изменения
        if (existingIndex == -1 || needsUpdate) {
            sortChats();
        }
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

    private void removeGroup(String groupId) {
        if (isFragmentDestroyed) return;

        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (chat.isGroup() && chat.getChat_id().equals(groupId)) {
                combinedChats.remove(i);
                // Удаляем из кэша
                previousGroupImages.remove(groupId);
                previousGroupNames.remove(groupId);
                previousLastMessages.remove(groupId);

                // Удаляем слушатель кастомных настроек
                if (groupCustomSettingsListeners.containsKey(groupId)) {
                    FirebaseDatabase.getInstance().getReference("UserCustomizations")
                            .child(currentUserId)
                            .child("groupContacts")
                            .child(groupId)
                            .removeEventListener(groupCustomSettingsListeners.get(groupId));
                    groupCustomSettingsListeners.remove(groupId);
                    Log.d("FragmentChat", "Removed group custom settings listener for: " + groupId);
                }

                if (chatsAdapter != null) {
                    chatsAdapter.notifyItemRemoved(i);
                }
                Log.d("FragmentChat", "Group REMOVED: " + groupId);
                break;
            }
        }
    }

    private void setupChatListener(String chatId, String otherUserId) {
        if (isFragmentDestroyed) return;

        // Удаляем предыдущий слушатель если есть
        if (chatListeners.containsKey(chatId)) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .child(chatId)
                    .removeEventListener(chatListeners.get(chatId));
        }

        ValueEventListener chatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot chatSnapshot) {
                if (isFragmentDestroyed || binding == null) return;

                try {
                    String lastMessage = chatSnapshot.child("LastMessage").getValue(String.class);
                    Long lastMessageTime = chatSnapshot.child("LastMessageTime").getValue(Long.class);

                    if (lastMessageTime == null) {
                        lastMessageTime = 0L;
                    }

                    // Сначала загружаем кастомные настройки
                    loadCustomSettings(otherUserId, chatId, lastMessage, lastMessageTime);

                } catch (Exception e) {
                    Log.e("FragmentChat", "Error processing chat data", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isFragmentDestroyed) return;
                Log.e("FragmentChat", "Chat listener cancelled", error.toException());
            }
        };

        chatListeners.put(chatId, chatListener);
        FirebaseDatabase.getInstance().getReference("Chats").child(chatId)
                .addValueEventListener(chatListener);

        Log.d("FragmentChat", "Chat listener SETUP for: " + chatId + " with other user: " + otherUserId);
    }

    // МЕТОД: Загрузка кастомных настроек с микрообновлением
    private void loadCustomSettings(String otherUserId, String chatId, String lastMessage, long lastMessageTime) {
        if (isFragmentDestroyed) return;

        // Удаляем предыдущий слушатель если есть
        if (customSettingsListeners.containsKey(otherUserId)) {
            FirebaseDatabase.getInstance().getReference("UserCustomizations")
                    .child(currentUserId)
                    .child("chatContacts")
                    .child(otherUserId)
                    .removeEventListener(customSettingsListeners.get(otherUserId));
        }

        ValueEventListener customSettingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFragmentDestroyed || binding == null) return;

                String customName = null;
                String customImage = null;

                if (snapshot.exists()) {
                    customName = snapshot.child("customName").getValue(String.class);
                    customImage = snapshot.child("customImage").getValue(String.class);

                    Log.d("FragmentChat", "User custom settings UPDATED for " + otherUserId +
                            ": name=" + customName + ", image=" + customImage);
                } else {
                    Log.d("FragmentChat", "No custom settings found for user: " + otherUserId);
                }

                // ВАЖНО: Всегда загружаем оригинальные данные, но передаем кастомные настройки
                loadOriginalUserData(chatId, otherUserId, lastMessage, lastMessageTime, customName, customImage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("FragmentChat", "Failed to load custom settings for user: " + otherUserId, error.toException());
                // При ошибке загружаем оригинальные данные без кастомных настроек
                loadOriginalUserData(chatId, otherUserId, lastMessage, lastMessageTime, null, null);
            }
        };

        customSettingsListeners.put(otherUserId, customSettingsListener);
        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(otherUserId)
                .addValueEventListener(customSettingsListener);

        Log.d("FragmentChat", "User custom settings listener SETUP for: " + otherUserId);
    }

    // МЕТОД: Загрузка оригинальных данных пользователя с учетом кастомных настроек
    private void loadOriginalUserData(String chatId, String otherUserId, String lastMessage,
                                      long lastMessageTime, String customName, String customImage) {
        FirebaseDatabase.getInstance().getReference("Users").child(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                        if (isFragmentDestroyed || binding == null) return;

                        String chatName = "Unknown User";
                        String profileImage = "";

                        if (userSnapshot.exists()) {
                            try {
                                // Получаем оригинальные данные
                                String originalName = userSnapshot.child("login").getValue(String.class);
                                if (originalName == null || originalName.trim().isEmpty()) {
                                    String email = userSnapshot.child("email").getValue(String.class);
                                    if (email != null && email.contains("@")) {
                                        originalName = email.substring(0, email.indexOf("@"));
                                    } else {
                                        originalName = "User";
                                    }
                                }

                                String originalImage = userSnapshot.child("profileImage").getValue(String.class);
                                if (originalImage == null) originalImage = "";

                                // ВАЖНО: Применяем кастомные настройки если они есть
                                // Имя: кастомное имя имеет приоритет над оригинальным
                                if (customName != null && !customName.isEmpty()) {
                                    chatName = customName;
                                    Log.d("FragmentChat", "Using CUSTOM name: " + customName + " for user: " + otherUserId);
                                } else {
                                    chatName = originalName;
                                    Log.d("FragmentChat", "Using ORIGINAL name: " + originalName + " for user: " + otherUserId);
                                }

                                // Аватарка: кастомная аватарка имеет приоритет над оригинальной
                                if (customImage != null && !customImage.isEmpty()) {
                                    profileImage = customImage;
                                    Log.d("FragmentChat", "Using CUSTOM image: " + customImage + " for user: " + otherUserId);
                                } else {
                                    profileImage = originalImage;
                                    Log.d("FragmentChat", "Using ORIGINAL image: " + originalImage + " for user: " + otherUserId);
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
                        chat.setProfileImage(profileImage);

                        updateOrAddChatWithMicroUpdate(chat);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isFragmentDestroyed) return;
                        Log.e("FragmentChat", "Failed to load user info", error.toException());
                    }
                });
    }

    // МЕТОД: Обновление чата с микрообновлением
    private void updateOrAddChatWithMicroUpdate(Chat newChat) {
        if (isFragmentDestroyed) return;

        String chatId = newChat.getChat_id();
        String newChatName = newChat.getChat_name();
        String newProfileImage = newChat.getProfileImage() != null ? newChat.getProfileImage() : "";
        String newLastMessage = newChat.getLastMessage() != null ? newChat.getLastMessage() : "";

        int existingIndex = -1;

        // Ищем чат с таким же ID
        for (int i = 0; i < combinedChats.size(); i++) {
            Chat chat = combinedChats.get(i);
            if (!chat.isGroup() && chat.getChat_id().equals(chatId)) {
                existingIndex = i;
                break;
            }
        }

        boolean needsUpdate = false;

        if (existingIndex != -1) {
            // Проверяем, изменились ли данные
            String previousName = previousChatNames.get(chatId);
            String previousImage = previousChatImages.get(chatId);
            String previousMessage = previousLastMessages.get(chatId);

            if (previousName == null || !previousName.equals(newChatName) ||
                    previousImage == null || !previousImage.equals(newProfileImage) ||
                    previousMessage == null || !previousMessage.equals(newLastMessage)) {
                needsUpdate = true;
                Log.d("FragmentChat", "Chat data CHANGED: " + newChatName +
                        ", image: " + newProfileImage + ", message: " + newLastMessage);
            } else {
                Log.d("FragmentChat", "Chat data UNCHANGED: " + newChatName);
            }

            combinedChats.set(existingIndex, newChat);

            if (needsUpdate) {
                // Микрообновление только этого элемента
                updateSingleItem(existingIndex);
                Log.d("FragmentChat", "Chat UI UPDATED at position: " + existingIndex);
            }
        } else {
            // Проверяем, нет ли дубликата чата с тем же другим пользователем
            String otherUserId = newChat.getOther_user_id();
            for (int i = 0; i < combinedChats.size(); i++) {
                Chat chat = combinedChats.get(i);
                if (!chat.isGroup() && chat.getOther_user_id().equals(otherUserId)) {
                    // Нашли дубликат - заменяем старый чат новым
                    combinedChats.set(i, newChat);
                    existingIndex = i;
                    needsUpdate = true;
                    Log.d("FragmentChat", "Replaced duplicate chat for user: " + otherUserId +
                            " with chat ID: " + chatId);
                    break;
                }
            }

            if (existingIndex == -1) {
                combinedChats.add(newChat);
                needsUpdate = true;
                Log.d("FragmentChat", "New chat ADDED: " + newChatName + " for user: " + otherUserId);
            }
        }

        // Сохраняем текущие данные для будущих сравнений
        previousChatNames.put(chatId, newChatName);
        previousChatImages.put(chatId, newProfileImage);
        previousLastMessages.put(chatId, newLastMessage);

        // Сортируем только если это новый чат или были изменения
        if (existingIndex == -1 || needsUpdate) {
            sortChats();
        }
    }

    private void sortChats() {
        if (isFragmentDestroyed || binding == null) return;

        try {
            // Сортируем по времени последнего сообщения (новые сверху)
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
        Log.d("FragmentChat", "Opening chat activity: " + chatId);
        try {
            Intent intent = new Intent(getContext(), com.example.androidmessage1.ChatActivity.class);
            intent.putExtra("chatId", chatId);
            intent.putExtra("otherUserId", otherUserId);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("FragmentChat", "Error opening chat", e);
            Toast.makeText(getContext(), "Error opening chat", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGroupChat(String groupId, String groupName) {
        Log.d("FragmentChat", "Opening group chat: " + groupId);
        try {
            Intent intent = new Intent(getContext(), GroupChatActivity.class);
            intent.putExtra("groupId", groupId);
            intent.putExtra("groupName", groupName);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("FragmentChat", "Error opening group chat", e);
            Toast.makeText(getContext(), "Error opening group chat", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("FragmentChat", "Fragment resumed - forcing refresh");
        // При возвращении на фрагмент принудительно обновляем данные
        if (chatsAdapter != null) {
            chatsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;

        if (chatsAdapter != null) {
            chatsAdapter.cleanup();
        }

        if (chatsListListener != null) {
            try {
                FirebaseDatabase.getInstance().getReference("Chats")
                        .removeEventListener(chatsListListener);
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing chats list listener", e);
            }
        }

        if (groupsListListener != null) {
            try {
                FirebaseDatabase.getInstance().getReference("Groups")
                        .removeEventListener(groupsListListener);
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing groups list listener", e);
            }
        }

        for (Map.Entry<String, ValueEventListener> entry : chatListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("Chats").child(entry.getKey())
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing chat listener", e);
            }
        }

        for (Map.Entry<String, ValueEventListener> entry : groupListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("Groups").child(entry.getKey())
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing group listener", e);
            }
        }

        for (Map.Entry<String, ValueEventListener> entry : customSettingsListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("UserCustomizations")
                        .child(currentUserId)
                        .child("chatContacts")
                        .child(entry.getKey())
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing custom settings listener", e);
            }
        }

        for (Map.Entry<String, ValueEventListener> entry : groupCustomSettingsListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("UserCustomizations")
                        .child(currentUserId)
                        .child("groupContacts")
                        .child(entry.getKey())
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("FragmentChat", "Error removing group custom settings listener", e);
            }
        }

        chatListeners.clear();
        groupListeners.clear();
        customSettingsListeners.clear();
        groupCustomSettingsListeners.clear();
        userPairToChatId.clear();
        processedChats.clear();

        Log.d("FragmentChat", "All listeners and maps cleared");
        binding = null;
    }
}