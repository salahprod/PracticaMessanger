package com.example.androidmessage1.bottomnav.new_chat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.ChatActivity;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.FragmentNewChatBinding;
import com.example.androidmessage1.groups.Group;
import com.example.androidmessage1.groups.GroupChatActivity;
import com.example.androidmessage1.users.User;
import com.example.androidmessage1.users.UsersAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class NewChatFragment extends Fragment {

    private FragmentNewChatBinding binding;
    private ArrayList<User> usersList = new ArrayList<>();
    private ArrayList<String> userIdsList = new ArrayList<>();
    private ArrayList<Group> groupsList = new ArrayList<>();
    private ArrayList<String> groupIdsList = new ArrayList<>();
    private ArrayList<User> searchResults = new ArrayList<>();
    private boolean isKeyboardForcedHidden = false;
    private String currentUserId;

    // HashMap для хранения статусов пользователей
    private HashMap<String, UserStatus> userStatusMap = new HashMap<>();

    // HashMap для хранения непрочитанных сообщений
    private HashMap<String, Integer> unreadCountsMap = new HashMap<>();
    private HashMap<String, ValueEventListener> unreadListeners = new HashMap<>();

    // Слушатели для статусов
    private ValueEventListener userStatusListener;
    private ValueEventListener usersListListener;
    private ValueEventListener groupsListListener;

    private class UserStatus {
        boolean isOnline;
        long lastOnline;
        String lastOnlineTime;
        String lastOnlineDate;
        boolean hasOnlineData;

        UserStatus(boolean isOnline, long lastOnline, String lastOnlineTime, String lastOnlineDate, boolean hasOnlineData) {
            this.isOnline = isOnline;
            this.lastOnline = lastOnline;
            this.lastOnlineTime = lastOnlineTime;
            this.lastOnlineDate = lastOnlineDate;
            this.hasOnlineData = hasOnlineData;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNewChatBinding.inflate(inflater, container, false);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupRecyclerView();
        setupSearch();
        loadUsersAndGroups();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("NewChatFragment", "Fragment resumed");
    }

    @Override
    public void onPause() {
        super.onPause();
        hideKeyboardDelayed();
    }

    @Override
    public void onStop() {
        super.onStop();
        hideKeyboardImmediately();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("NewChatFragment", "onDestroyView called");
        hideKeyboardImmediately();
        cleanupAllListeners();
        binding = null;
    }

    private void cleanupAllListeners() {
        // Очищаем слушатели непрочитанных сообщений
        for (ValueEventListener listener : unreadListeners.values()) {
            try {
                FirebaseDatabase.getInstance().getReference().removeEventListener(listener);
            } catch (Exception e) {
                Log.e("NewChatFragment", "Error removing unread listener", e);
            }
        }
        unreadListeners.clear();

        // Очищаем слушатель статусов
        if (userStatusListener != null) {
            try {
                FirebaseDatabase.getInstance().getReference("Users").removeEventListener(userStatusListener);
            } catch (Exception e) {
                Log.e("NewChatFragment", "Error removing status listener", e);
            }
            userStatusListener = null;
        }

        // Очищаем слушатели списков
        if (usersListListener != null) {
            try {
                FirebaseDatabase.getInstance().getReference("Users").removeEventListener(usersListListener);
            } catch (Exception e) {
                Log.e("NewChatFragment", "Error removing users list listener", e);
            }
            usersListListener = null;
        }

        if (groupsListListener != null) {
            try {
                FirebaseDatabase.getInstance().getReference("Users").child(currentUserId).child("groups").removeEventListener(groupsListListener);
            } catch (Exception e) {
                Log.e("NewChatFragment", "Error removing groups list listener", e);
            }
            groupsListListener = null;
        }

        unreadCountsMap.clear();
        userStatusMap.clear();
    }

    private void setupRecyclerView() {
        binding.userRv.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.userRv.addItemDecoration(
                new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        UsersAdapter emptyAdapter = new UsersAdapter(new ArrayList<>(), null);
        binding.userRv.setAdapter(emptyAdapter);
    }

    private void setupSearch() {
        binding.searchBtn.setVisibility(View.GONE);
        binding.emptyStateText.setVisibility(View.VISIBLE);
        binding.emptyStateText.setText("Enter username or group name to search");

        binding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                performSearch(query);

                if (s.length() > 0) {
                    binding.searchBtn.setVisibility(View.VISIBLE);
                    binding.emptyStateText.setVisibility(View.GONE);
                } else {
                    binding.searchBtn.setVisibility(View.GONE);
                    binding.emptyStateText.setVisibility(View.VISIBLE);
                    binding.emptyStateText.setText("Enter username or group name to search");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.searchBtn.setOnClickListener(v -> {
            hideKeyboardAndClearFocus();
            binding.searchEt.setText("");
            binding.searchBtn.setVisibility(View.GONE);
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.emptyStateText.setText("Enter username or group name to search");
            searchResults.clear();
            updateAdapter();
        });

        binding.searchEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isKeyboardForcedHidden) {
                showKeyboard();
            }
        });

        binding.searchEt.setOnClickListener(v -> {
            if (isKeyboardForcedHidden) {
                isKeyboardForcedHidden = false;
                showKeyboard();
            }
        });
    }

    private void performSearch(String query) {
        if (binding == null) return;

        searchResults.clear();

        if (query.isEmpty()) {
            updateAdapter();
            return;
        }

        String lowerCaseQuery = query.toLowerCase();

        // Поиск пользователей
        for (int i = 0; i < usersList.size(); i++) {
            User user = usersList.get(i);
            if (user.getUsername().toLowerCase().contains(lowerCaseQuery)) {
                // Добавляем статус пользователю
                String userId = userIdsList.get(i);
                UserStatus status = userStatusMap.get(userId);
                if (status != null) {
                    user.setLastMessage(formatUserStatus(status));
                    // Устанавливаем цвет статуса
                    user.setStatusColor(getStatusColor(status.isOnline));
                } else {
                    user.setLastMessage("offline");
                    user.setStatusColor(Color.GRAY);
                }

                // Добавляем количество непрочитанных сообщений
                Integer unreadCount = unreadCountsMap.get(userId);
                if (unreadCount != null && unreadCount > 0) {
                    user.setUnreadCount(unreadCount);
                } else {
                    user.setUnreadCount(0);
                }

                // Устанавливаем ID пользователя
                user.setUserId(userId);

                searchResults.add(user);
            }
        }

        // Поиск групп, в которых пользователь состоит
        for (int i = 0; i < groupsList.size(); i++) {
            Group group = groupsList.get(i);
            if (group.getGroupName().toLowerCase().contains(lowerCaseQuery)) {
                // Создаем User объект для отображения группы
                User groupAsUser = new User(
                        group.getGroupName(),
                        group.getGroupImage()
                );

                // Показываем количество участников в lastMessage
                int membersCount = group.getMembers().size();
                String membersText = membersCount + " participant" + (membersCount > 1 ? "s" : "");
                groupAsUser.setLastMessage(membersText);
                groupAsUser.setStatusColor(Color.GRAY);

                // Добавляем количество непрочитанных сообщений в группе
                Integer unreadCount = unreadCountsMap.get("group_" + group.getGroupId());
                if (unreadCount != null && unreadCount > 0) {
                    groupAsUser.setUnreadCount(unreadCount);
                } else {
                    groupAsUser.setUnreadCount(0);
                }

                // Устанавливаем специальный идентификатор для группы
                groupAsUser.setUserId("group_" + group.getGroupId());

                searchResults.add(groupAsUser);
            }
        }

        if (searchResults.isEmpty()) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.emptyStateText.setText("\n" + "No users or groups found");
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
        }

        updateAdapter();
    }

    private int getStatusColor(boolean isOnline) {
        if (isOnline) {
            return ContextCompat.getColor(requireContext(), R.color.online_green);
        } else {
            return Color.GRAY;
        }
    }

    private String formatUserStatus(UserStatus status) {
        if (!status.hasOnlineData) {
            return "offline";
        }

        if (status.isOnline) {
            return "online";
        } else {
            return formatLastSeen(status.lastOnline, status.lastOnlineTime, status.lastOnlineDate);
        }
    }

    private String formatLastSeen(long lastOnlineTimestamp, String lastOnlineTime, String lastOnlineDate) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastOnlineTimestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);

        java.util.Calendar currentCal = java.util.Calendar.getInstance();
        java.util.Calendar lastOnlineCal = java.util.Calendar.getInstance();
        lastOnlineCal.setTimeInMillis(lastOnlineTimestamp);

        int currentDay = currentCal.get(java.util.Calendar.DAY_OF_YEAR);
        int currentYear = currentCal.get(java.util.Calendar.YEAR);
        int lastOnlineDay = lastOnlineCal.get(java.util.Calendar.DAY_OF_YEAR);
        int lastOnlineYear = lastOnlineCal.get(java.util.Calendar.YEAR);

        boolean isYesterday = (currentDay - lastOnlineDay == 1 && currentYear == lastOnlineYear) ||
                (currentDay == 1 && lastOnlineDay >= 365 && currentYear - lastOnlineYear == 1);

        boolean isMoreThanTwoDays = (currentDay - lastOnlineDay > 1 && currentYear == lastOnlineYear) ||
                (currentYear - lastOnlineYear > 0);

        if (minutes < 1) {
            return "was online just now";
        } else if (minutes < 60) {
            return "was online " + minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else if (hours < 24) {
            return "was online " + hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (isYesterday) {
            return "was online yesterday at " + (lastOnlineTime != null ? lastOnlineTime : "unknown time");
        } else if (isMoreThanTwoDays) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy 'at' HH:mm", Locale.getDefault());
            return "was online " + dateFormat.format(new Date(lastOnlineTimestamp));
        } else {
            return "was online " + (lastOnlineTime != null ? lastOnlineTime : "unknown time") + " " + (lastOnlineDate != null ? lastOnlineDate : "");
        }
    }

    private void updateAdapter() {
        if (binding == null) return;

        UsersAdapter adapter = new UsersAdapter(searchResults, new UsersAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(int position) {
                if (position < 0 || position >= searchResults.size()) return;

                User selectedItem = searchResults.get(position);

                // Проверяем, это группа или пользователь
                if (selectedItem.getUserId() != null && selectedItem.getUserId().startsWith("group_")) {
                    // Это группа
                    String groupId = selectedItem.getUserId().substring(6); // Убираем "group_" префикс
                    String groupName = selectedItem.getUsername();
                    if (groupId != null) {
                        openGroupChat(groupId, groupName);
                    }
                } else {
                    // Это пользователь
                    String selectedUserId = selectedItem.getUserId();
                    if (selectedUserId != null && !selectedUserId.isEmpty()) {
                        checkIfChatExistsBeforeCreate(selectedUserId);
                    }
                }
            }
        });
        binding.userRv.setAdapter(adapter);
    }

    private void loadUsersAndGroups() {
        if (binding == null) return;

        loadUsers();
        loadUserGroups();
    }

    private void loadUsers() {
        if (usersListListener != null) {
            FirebaseDatabase.getInstance().getReference("Users").removeEventListener(usersListListener);
        }

        usersListListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;

                usersList.clear();
                userIdsList.clear();
                userStatusMap.clear();

                String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    if (userId == null) continue;

                    String userLogin = userSnapshot.child("login").getValue(String.class);
                    String userEmail = userSnapshot.child("email").getValue(String.class);

                    if (userLogin == null || userLogin.trim().isEmpty()) {
                        continue;
                    }

                    if (currentUserEmail != null && currentUserEmail.equals(userEmail)) {
                        continue;
                    }

                    String profileImage = userSnapshot.child("profileImage").getValue(String.class);
                    if (profileImage == null) {
                        profileImage = "";
                    }

                    // Загружаем статус пользователя
                    Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);
                    Long lastOnline = userSnapshot.child("lastOnline").getValue(Long.class);
                    String lastOnlineTime = userSnapshot.child("lastOnlineTime").getValue(String.class);
                    String lastOnlineDate = userSnapshot.child("lastOnlineDate").getValue(String.class);

                    boolean hasOnlineData = (isOnline != null || lastOnline != null ||
                            lastOnlineTime != null || lastOnlineDate != null);

                    if (!hasOnlineData) {
                        isOnline = false;
                        lastOnline = 0L;
                        lastOnlineTime = "";
                        lastOnlineDate = "";
                    } else {
                        if (isOnline == null) isOnline = false;
                        if (lastOnline == null) lastOnline = 0L;
                        if (lastOnlineTime == null) lastOnlineTime = "";
                        if (lastOnlineDate == null) lastOnlineDate = "";
                    }

                    userStatusMap.put(userId, new UserStatus(isOnline, lastOnline, lastOnlineTime, lastOnlineDate, hasOnlineData));

                    User user = new User(userLogin, profileImage);
                    user.setLastMessage(formatUserStatus(userStatusMap.get(userId)));
                    user.setStatusColor(getStatusColor(isOnline));
                    user.setUserId(userId);

                    usersList.add(user);
                    userIdsList.add(userId);

                    // Настраиваем слушатель непрочитанных сообщений для этого пользователя
                    setupUnreadMessagesListener(userId);
                }

                Log.d("NewChatFragment", "Loaded " + usersList.size() + " users with status");

                setupUserStatusListener();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (binding == null) return;
                Toast.makeText(getContext(), "Error loading users", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance().getReference("Users").addValueEventListener(usersListListener);
    }

    private void setupUnreadMessagesListener(String userId) {
        // Ищем существующий чат между текущим пользователем и этим пользователем
        FirebaseDatabase.getInstance().getReference("Chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String chatId = null;
                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            if (user1 != null && user2 != null) {
                                if ((user1.equals(currentUserId) && user2.equals(userId)) ||
                                        (user1.equals(userId) && user2.equals(currentUserId))) {
                                    chatId = chatSnapshot.getKey();
                                    break;
                                }
                            }
                        }

                        if (chatId != null) {
                            setupChatUnreadListener(chatId, userId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("NewChatFragment", "Error finding chat for user: " + userId, error.toException());
                    }
                });
    }

    private void setupChatUnreadListener(String chatId, String userId) {
        // Удаляем предыдущий слушатель если есть
        if (unreadListeners.containsKey(chatId)) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(unreadListeners.get(chatId));
        }

        ValueEventListener unreadMessagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int unreadCount = 0;

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        if (ownerId != null &&
                                !ownerId.equals(currentUserId) &&
                                (isRead == null || !isRead)) {
                            unreadCount++;
                        }
                    }
                }

                // Сохраняем в кэш
                unreadCountsMap.put(userId, unreadCount);

                // Обновляем UI если этот пользователь в результатах поиска
                if (binding != null && !binding.searchEt.getText().toString().trim().isEmpty()) {
                    performSearch(binding.searchEt.getText().toString().trim());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NewChatFragment", "Unread messages listener cancelled", error.toException());
                unreadCountsMap.put(userId, 0);
            }
        };

        FirebaseDatabase.getInstance().getReference("Chats")
                .child(chatId)
                .child("messages")
                .addValueEventListener(unreadMessagesListener);

        unreadListeners.put(chatId, unreadMessagesListener);
    }

    private void setupGroupUnreadListener(String groupId) {
        String listenerKey = "group_" + groupId;

        if (unreadListeners.containsKey(listenerKey)) {
            FirebaseDatabase.getInstance().getReference("Groups")
                    .child(groupId)
                    .child("messages")
                    .removeEventListener(unreadListeners.get(listenerKey));
        }

        ValueEventListener groupUnreadListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int unreadCount = 0;

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        if (ownerId != null &&
                                !ownerId.equals(currentUserId) &&
                                (isRead == null || !isRead)) {
                            unreadCount++;
                        }
                    }
                }

                // Сохраняем в кэш
                unreadCountsMap.put("group_" + groupId, unreadCount);

                // Обновляем UI если эта группа в результатах поиска
                if (binding != null && !binding.searchEt.getText().toString().trim().isEmpty()) {
                    performSearch(binding.searchEt.getText().toString().trim());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NewChatFragment", "Group unread messages listener cancelled", error.toException());
                unreadCountsMap.put("group_" + groupId, 0);
            }
        };

        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("messages")
                .addValueEventListener(groupUnreadListener);

        unreadListeners.put(listenerKey, groupUnreadListener);
    }

    private void setupUserStatusListener() {
        if (userStatusListener != null) {
            FirebaseDatabase.getInstance().getReference("Users").removeEventListener(userStatusListener);
        }

        userStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    if (userId == null) continue;

                    if (userIdsList.contains(userId)) {
                        Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);
                        Long lastOnline = userSnapshot.child("lastOnline").getValue(Long.class);
                        String lastOnlineTime = userSnapshot.child("lastOnlineTime").getValue(String.class);
                        String lastOnlineDate = userSnapshot.child("lastOnlineDate").getValue(String.class);

                        boolean hasOnlineData = (isOnline != null || lastOnline != null ||
                                lastOnlineTime != null || lastOnlineDate != null);

                        if (!hasOnlineData) {
                            isOnline = false;
                            lastOnline = 0L;
                            lastOnlineTime = "";
                            lastOnlineDate = "";
                        } else {
                            if (isOnline == null) isOnline = false;
                            if (lastOnline == null) lastOnline = 0L;
                            if (lastOnlineTime == null) lastOnlineTime = "";
                            if (lastOnlineDate == null) lastOnlineDate = "";
                        }

                        userStatusMap.put(userId, new UserStatus(isOnline, lastOnline, lastOnlineTime, lastOnlineDate, hasOnlineData));

                        int userIndex = userIdsList.indexOf(userId);
                        if (userIndex != -1 && userIndex < usersList.size()) {
                            User user = usersList.get(userIndex);
                            user.setLastMessage(formatUserStatus(userStatusMap.get(userId)));
                            user.setStatusColor(getStatusColor(isOnline));
                        }
                    }
                }

                if (binding != null && !binding.searchEt.getText().toString().trim().isEmpty()) {
                    performSearch(binding.searchEt.getText().toString().trim());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NewChatFragment", "Failed to listen to user status updates", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Users").addValueEventListener(userStatusListener);
    }

    private void loadUserGroups() {
        if (groupsListListener != null) {
            FirebaseDatabase.getInstance().getReference("Users").child(currentUserId).child("groups").removeEventListener(groupsListListener);
        }

        groupsListListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (binding == null) return;

                groupsList.clear();
                groupIdsList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot groupSnapshot : snapshot.getChildren()) {
                        String groupId = groupSnapshot.getKey();
                        if (groupId != null) {
                            loadGroupDetails(groupId);
                        }
                    }
                }
                Log.d("NewChatFragment", "User is member of " + snapshot.getChildrenCount() + " groups");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NewChatFragment", "Failed to load user groups", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Users").child(currentUserId).child("groups").addValueEventListener(groupsListListener);
    }

    private void loadGroupDetails(String groupId) {
        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;

                        if (snapshot.exists()) {
                            Group group = snapshot.getValue(Group.class);
                            if (group != null) {
                                group.setGroupId(groupId);

                                boolean exists = false;
                                for (Group existingGroup : groupsList) {
                                    if (existingGroup.getGroupId().equals(groupId)) {
                                        exists = true;
                                        break;
                                    }
                                }

                                if (!exists) {
                                    groupsList.add(group);
                                    groupIdsList.add(groupId);

                                    // Настраиваем слушатель непрочитанных сообщений для группы
                                    setupGroupUnreadListener(groupId);

                                    Log.d("NewChatFragment", "Loaded group: " + group.getGroupName());
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("NewChatFragment", "Failed to load group details", error.toException());
                    }
                });
    }

    private void checkIfChatExistsBeforeCreate(String otherUserId) {
        if (binding == null) return;

        binding.userRv.setEnabled(false);

        FirebaseDatabase.getInstance().getReference("Chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;

                        boolean chatExists = false;
                        String existingChatId = null;

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            if (user1 != null && user2 != null) {
                                if ((user1.equals(currentUserId) && user2.equals(otherUserId)) ||
                                        (user1.equals(otherUserId) && user2.equals(currentUserId))) {
                                    chatExists = true;
                                    existingChatId = chatSnapshot.getKey();
                                    break;
                                }
                            }
                        }

                        binding.userRv.setEnabled(true);

                        if (chatExists) {
                            Toast.makeText(getContext(), "Opening existing chat", Toast.LENGTH_SHORT).show();
                            openChatActivity(existingChatId, otherUserId);
                        } else {
                            createNewChatWithUser(otherUserId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (binding == null) return;
                        binding.userRv.setEnabled(true);
                        Toast.makeText(getContext(), "Error checking chat", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createNewChatWithUser(String otherUserId) {
        String chatId = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .push()
                .getKey();

        long currentTime = System.currentTimeMillis();

        HashMap<String, Object> chatData = new HashMap<>();
        chatData.put("user1", currentUserId);
        chatData.put("user2", otherUserId);
        chatData.put("createdAt", currentTime);
        chatData.put("LastMessage", "");
        chatData.put("LastMessageTime", currentTime);
        chatData.put("lastMessageTimestamp", currentTime);

        HashMap<String, Object> messagesData = new HashMap<>();
        chatData.put("messages", messagesData);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Chats/" + chatId, chatData);

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Chat created successfully!", Toast.LENGTH_SHORT).show();
                        openChatActivity(chatId, otherUserId);
                    } else {
                        Toast.makeText(getContext(), "Error creating chat: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openChatActivity(String chatId, String otherUserId) {
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("otherUserId", otherUserId);
        startActivity(intent);
        hideKeyboardImmediately();
    }

    private void openGroupChat(String groupId, String groupName) {
        Intent intent = new Intent(getContext(), GroupChatActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("groupName", groupName);
        startActivity(intent);
        hideKeyboardImmediately();
    }

    // Методы для управления клавиатурой
    private void hideKeyboardAndClearFocus() {
        try {
            if (getActivity() != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                isKeyboardForcedHidden = true;
                binding.searchEt.clearFocus();
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                imm.hideSoftInputFromWindow(binding.searchEt.getWindowToken(), 0);
                binding.getRoot().requestFocus();
                binding.getRoot().postDelayed(() -> {
                    isKeyboardForcedHidden = false;
                }, 300);
            }
        } catch (Exception e) {
            e.printStackTrace();
            isKeyboardForcedHidden = false;
        }
    }

    private void hideKeyboardImmediately() {
        try {
            if (getActivity() != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                if (binding != null && binding.searchEt.hasFocus()) {
                    imm.hideSoftInputFromWindow(binding.searchEt.getWindowToken(), 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideKeyboardDelayed() {
        try {
            if (getActivity() != null && binding != null) {
                binding.searchEt.postDelayed(() -> {
                    try {
                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        View view = getActivity().getCurrentFocus();
                        if (view != null) {
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }
                        if (binding.searchEt.hasFocus()) {
                            imm.hideSoftInputFromWindow(binding.searchEt.getWindowToken(), 0);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showKeyboard() {
        if (getActivity() != null && binding != null) {
            binding.searchEt.postDelayed(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    binding.searchEt.requestFocus();
                    imm.showSoftInput(binding.searchEt, InputMethodManager.SHOW_IMPLICIT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 100);
        }
    }
}