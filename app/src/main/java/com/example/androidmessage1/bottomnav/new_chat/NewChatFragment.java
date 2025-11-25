package com.example.androidmessage1.bottomnav.new_chat;

import android.content.Context;
import android.content.Intent;
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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.ChatActivity;
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

import java.util.ArrayList;
import java.util.HashMap;

public class NewChatFragment extends Fragment {

    private FragmentNewChatBinding binding;
    private ArrayList<User> usersList = new ArrayList<>();
    private ArrayList<String> userIdsList = new ArrayList<>();
    private ArrayList<Group> groupsList = new ArrayList<>();
    private ArrayList<String> groupIdsList = new ArrayList<>();
    private ArrayList<User> searchResults = new ArrayList<>();
    private boolean isKeyboardForcedHidden = false;
    private String currentUserId;

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
        hideKeyboardImmediately();
        binding = null;
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

                // Сохраняем ID группы в unreadCount для идентификации
                groupAsUser.setUnreadCount(-1); // -1 означает, что это группа

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

    private void updateAdapter() {
        if (binding == null) return;

        UsersAdapter adapter = new UsersAdapter(searchResults, new UsersAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(int position) {
                if (position < 0 || position >= searchResults.size()) return;

                User selectedItem = searchResults.get(position);

                // Проверяем, это группа или пользователь (unreadCount = -1 означает группу)
                if (selectedItem.getUnreadCount() == -1) {
                    // Это группа - находим ID группы по имени
                    String groupName = selectedItem.getUsername();
                    String groupId = findGroupIdByName(groupName);
                    if (groupId != null) {
                        openGroupChat(groupId, groupName);
                    }
                } else {
                    // Это пользователь - находим его ID
                    int userIndex = usersList.indexOf(selectedItem);
                    if (userIndex != -1 && userIndex < userIdsList.size()) {
                        String selectedUserId = userIdsList.get(userIndex);
                        checkIfChatExistsBeforeCreate(selectedUserId);
                    }
                }
            }
        });
        binding.userRv.setAdapter(adapter);
    }

    private String findGroupIdByName(String groupName) {
        for (Group group : groupsList) {
            if (group.getGroupName().equals(groupName)) {
                return group.getGroupId();
            }
        }
        return null;
    }

    private void loadUsersAndGroups() {
        if (binding == null) return;

        loadUsers();
        loadUserGroups();
    }

    private void loadUsers() {
        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;

                        usersList.clear();
                        userIdsList.clear();

                        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey();

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

                            User user = new User(userLogin, profileImage);
                            usersList.add(user);
                            userIdsList.add(userId);
                        }

                        Log.d("NewChatFragment", "Loaded " + usersList.size() + " users");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (binding == null) return;
                        Toast.makeText(getContext(), "Error loading users", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadUserGroups() {
        FirebaseDatabase.getInstance().getReference("Users")
                .child(currentUserId)
                .child("groups")
                .addListenerForSingleValueEvent(new ValueEventListener() {
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
                });
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