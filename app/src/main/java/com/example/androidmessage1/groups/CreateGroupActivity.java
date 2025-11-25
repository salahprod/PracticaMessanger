package com.example.androidmessage1.groups;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.ActivityCreateGroupBinding;
import com.example.androidmessage1.users.User;
import com.example.androidmessage1.users.UsersAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CreateGroupActivity extends AppCompatActivity {

    private ActivityCreateGroupBinding binding;
    private ArrayList<User> usersList = new ArrayList<>();
    private ArrayList<String> userIdsList = new ArrayList<>();
    private ArrayList<String> selectedUsers = new ArrayList<>();
    private UsersAdapter usersAdapter;
    private Uri selectedImageUri;
    private String currentUserId;

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String TAG = "CreateGroupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateGroupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.d(TAG, "onCreate: Activity started");

        // Проверка аутентификации
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Log.e(TAG, "onCreate: User not authenticated");
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d(TAG, "onCreate: Current user ID: " + currentUserId);

        setupRecyclerView();
        setupClickListeners();
        loadUsers();
    }

    private void setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: Setting up recycler view");
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.usersRv.setLayoutManager(layoutManager);

        // Добавляем разделительную линию между элементами
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        binding.usersRv.addItemDecoration(dividerItemDecoration);

        usersAdapter = new UsersAdapter(usersList, new UsersAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(int position) {
                Log.d(TAG, "onUserClick: Position " + position);
                toggleUserSelection(position);
            }
        });
        binding.usersRv.setAdapter(usersAdapter);
    }

    private void setupClickListeners() {
        binding.backBtn.setOnClickListener(v -> {
            Log.d(TAG, "Back button clicked");
            finish();
        });

        binding.groupImageLayout.setOnClickListener(v -> {
            Log.d(TAG, "Group image layout clicked");
            openImagePicker();
        });

        binding.createGroupBtn.setOnClickListener(v -> {
            Log.d(TAG, "Create group button clicked");
            createGroup();
        });

        binding.searchEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.d(TAG, "Search text changed: " + s);
                filterUsers(s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void openImagePicker() {
        try {
            Log.d(TAG, "openImagePicker: Opening image picker");
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Group Image"), PICK_IMAGE_REQUEST);
        } catch (Exception e) {
            Log.e(TAG, "openImagePicker: Error opening image picker", e);
            Toast.makeText(this, "Error opening image picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            Log.d(TAG, "onActivityResult: Image selected: " + selectedImageUri);
            try {
                Glide.with(this)
                        .load(selectedImageUri)
                        .placeholder(R.drawable.artem)
                        .into(binding.groupImage);
            } catch (Exception e) {
                Log.e(TAG, "onActivityResult: Error loading image", e);
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void toggleUserSelection(int position) {
        Log.d(TAG, "toggleUserSelection: Position " + position + ", usersList size: " + usersList.size() + ", userIdsList size: " + userIdsList.size());

        if (position < 0 || position >= userIdsList.size()) {
            Log.e(TAG, "toggleUserSelection: Invalid position: " + position);
            return;
        }

        String userId = userIdsList.get(position);
        Log.d(TAG, "toggleUserSelection: User ID: " + userId);

        if (selectedUsers.contains(userId)) {
            selectedUsers.remove(userId);
            Log.d(TAG, "toggleUserSelection: User removed from selection");
        } else {
            selectedUsers.add(userId);
            Log.d(TAG, "toggleUserSelection: User added to selection");
        }

        updateCreateButtonVisibility();
        updateSelectedCount();
        usersAdapter.notifyItemChanged(position);

        Log.d(TAG, "toggleUserSelection: Selected users count: " + selectedUsers.size());
    }

    private void updateCreateButtonVisibility() {
        boolean shouldShow = selectedUsers.size() >= 1;
        Log.d(TAG, "updateCreateButtonVisibility: Should show button: " + shouldShow);
        binding.createGroupBtn.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private void updateSelectedCount() {
        Log.d(TAG, "updateSelectedCount: Selected users: " + selectedUsers.size());
        if (selectedUsers.size() == 0) {
            binding.selectedCount.setText("Select at least 1 member");
            binding.selectedCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            binding.selectedCount.setText(selectedUsers.size() + " member" + (selectedUsers.size() > 1 ? "s" : "") + " selected");
            binding.selectedCount.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        }
    }

    private void filterUsers(String query) {
        Log.d(TAG, "filterUsers: Query: " + query);
        ArrayList<User> filteredList = new ArrayList<>();
        ArrayList<String> filteredIds = new ArrayList<>();

        if (TextUtils.isEmpty(query)) {
            filteredList.addAll(usersList);
            filteredIds.addAll(userIdsList);
            Log.d(TAG, "filterUsers: Showing all users, count: " + filteredList.size());
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (int i = 0; i < usersList.size(); i++) {
                User user = usersList.get(i);
                if (user.getUsername().toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(user);
                    filteredIds.add(userIdsList.get(i));
                }
            }
            Log.d(TAG, "filterUsers: Filtered users count: " + filteredList.size());
        }

        UsersAdapter filteredAdapter = new UsersAdapter(filteredList, new UsersAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(int position) {
                Log.d(TAG, "filterUsers onUserClick: Position " + position);
                if (position >= 0 && position < filteredIds.size()) {
                    String userId = filteredIds.get(position);
                    int originalPosition = userIdsList.indexOf(userId);
                    Log.d(TAG, "filterUsers onUserClick: User ID: " + userId + ", original position: " + originalPosition);
                    if (originalPosition != -1) {
                        toggleUserSelection(originalPosition);
                    }
                }
            }
        });

        // Добавляем разделительную линию для отфильтрованного списка
        binding.usersRv.setAdapter(filteredAdapter);
    }

    private void loadUsers() {
        Log.d(TAG, "loadUsers: Starting to load users");
        binding.progressBar.setVisibility(View.VISIBLE);

        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d(TAG, "loadUsers onDataChange: Snapshot exists: " + snapshot.exists());
                        usersList.clear();
                        userIdsList.clear();
                        selectedUsers.clear();

                        int userCount = 0;
                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey();
                            Log.d(TAG, "loadUsers: Processing user ID: " + userId);

                            // Пропускаем текущего пользователя и null ключи
                            if (userId == null || userId.equals(currentUserId)) {
                                Log.d(TAG, "loadUsers: Skipping user (current or null)");
                                continue;
                            }

                            String userLogin = userSnapshot.child("login").getValue(String.class);
                            String profileImage = userSnapshot.child("profileImage").getValue(String.class);

                            Log.d(TAG, "loadUsers: User login: " + userLogin + ", profile image: " + profileImage);

                            if (TextUtils.isEmpty(userLogin)) {
                                Log.d(TAG, "loadUsers: Skipping user with empty login");
                                continue;
                            }

                            if (profileImage == null) {
                                profileImage = "";
                            }

                            usersList.add(new User(userLogin, profileImage));
                            userIdsList.add(userId);
                            userCount++;
                            Log.d(TAG, "loadUsers: Added user: " + userLogin);
                        }

                        Log.d(TAG, "loadUsers: Total users loaded: " + userCount);
                        usersAdapter.notifyDataSetChanged();
                        binding.progressBar.setVisibility(View.GONE);
                        updateSelectedCount();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "loadUsers onCancelled: " + error.getMessage(), error.toException());
                        Toast.makeText(CreateGroupActivity.this, "Failed to load users: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        binding.progressBar.setVisibility(View.GONE);
                    }
                });
    }

    private void createGroup() {
        Log.d(TAG, "createGroup: Starting group creation");
        String groupName = binding.groupNameEt.getText().toString().trim();
        Log.d(TAG, "createGroup: Group name: " + groupName);

        if (TextUtils.isEmpty(groupName)) {
            Log.e(TAG, "createGroup: Group name is empty");
            binding.groupNameEt.setError("Group name is required");
            binding.groupNameEt.requestFocus();
            return;
        }

        if (selectedUsers.size() < 1) {
            Log.e(TAG, "createGroup: No users selected");
            Toast.makeText(this, "Select at least 1 member", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "createGroup: Selected users count: " + selectedUsers.size());
        binding.createGroupBtn.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        // Добавляем текущего пользователя в участники
        ArrayList<String> allMembers = new ArrayList<>(selectedUsers);
        if (!allMembers.contains(currentUserId)) {
            allMembers.add(currentUserId);
        }
        Log.d(TAG, "createGroup: All members count (including current user): " + allMembers.size());

        if (selectedImageUri != null) {
            Log.d(TAG, "createGroup: Uploading image first");
            uploadImageAndCreateGroup(groupName, allMembers);
        } else {
            Log.d(TAG, "createGroup: Creating group without image");
            createGroupInDatabase(groupName, "", allMembers);
        }
    }

    private void uploadImageAndCreateGroup(String groupName, ArrayList<String> allMembers) {
        Log.d(TAG, "uploadImageAndCreateGroup: Starting image upload");
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("group_images")
                .child(System.currentTimeMillis() + ".jpg");

        storageRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "uploadImageAndCreateGroup: Image upload successful");
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        Log.d(TAG, "uploadImageAndCreateGroup: Got download URL: " + uri);
                        createGroupInDatabase(groupName, uri.toString(), allMembers);
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "uploadImageAndCreateGroup: Failed to get download URL", e);
                        Toast.makeText(CreateGroupActivity.this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        binding.createGroupBtn.setEnabled(true);
                        binding.progressBar.setVisibility(View.GONE);
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "uploadImageAndCreateGroup: Failed to upload image", e);
                    Toast.makeText(this, "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.createGroupBtn.setEnabled(true);
                    binding.progressBar.setVisibility(View.GONE);
                });
    }

    private void createGroupInDatabase(String groupName, String imageUrl, ArrayList<String> allMembers) {
        String groupId = FirebaseDatabase.getInstance()
                .getReference("Groups")
                .push()
                .getKey();

        if (groupId == null) {
            Toast.makeText(this, "Error creating group: null group ID", Toast.LENGTH_SHORT).show();
            binding.createGroupBtn.setEnabled(true);
            binding.progressBar.setVisibility(View.GONE);
            return;
        }

        // Создаем группу
        Group group = new Group(groupId, groupName, imageUrl, currentUserId);
        group.setMembers(allMembers);

        // Создаем приветственное сообщение как системное уведомление
        HashMap<String, Object> welcomeMessage = new HashMap<>();
        welcomeMessage.put("ownerId", "system");
        welcomeMessage.put("senderName", "System");
        welcomeMessage.put("senderAvatar", "");
        welcomeMessage.put("date", getCurrentTime());
        welcomeMessage.put("timestamp", System.currentTimeMillis());
        welcomeMessage.put("isRead", true);
        welcomeMessage.put("isSystemMessage", true);

        // Подготавливаем данные для записи
        HashMap<String, Object> groupData = new HashMap<>();

        // Основные данные группы
        groupData.put("Groups/" + groupId + "/groupId", groupId);
        groupData.put("Groups/" + groupId + "/groupName", groupName);
        groupData.put("Groups/" + groupId + "/groupImage", imageUrl);
        groupData.put("Groups/" + groupId + "/createdBy", currentUserId);
        groupData.put("Groups/" + groupId + "/createdAt", System.currentTimeMillis());
        groupData.put("Groups/" + groupId + "/lastMessage", "Group created");
        groupData.put("Groups/" + groupId + "/lastMessageSender", "System");
        groupData.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

        // Участники группы
        for (int i = 0; i < allMembers.size(); i++) {
            groupData.put("Groups/" + groupId + "/members/" + i, allMembers.get(i));
        }

        // Приветственное сообщение
        groupData.put("Groups/" + groupId + "/messages/message_welcome", welcomeMessage);

        // Добавляем группу в список групп каждого участника
        for (String memberId : allMembers) {
            groupData.put("Users/" + memberId + "/groups/" + groupId, true);
        }

        Log.d(TAG, "createGroupInDatabase: Updating database with " + groupData.size() + " entries");

        // Записываем данные в базу
        FirebaseDatabase.getInstance().getReference()
                .updateChildren(groupData)
                .addOnCompleteListener(task -> {
                    Log.d(TAG, "createGroupInDatabase: Update completed, success: " + task.isSuccessful());
                    binding.progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "createGroupInDatabase: Group created successfully");
                        Toast.makeText(this, "Group created successfully!", Toast.LENGTH_SHORT).show();

                        // Сразу открываем групповой чат
                        Intent intent = new Intent(this, GroupChatActivity.class);
                        intent.putExtra("groupId", groupId);
                        intent.putExtra("groupName", groupName);
                        startActivity(intent);
                        finish();
                    } else {
                        Exception exception = task.getException();
                        Log.e(TAG, "createGroupInDatabase: Failed to create group", exception);
                        String errorMessage = "Failed to create group";
                        if (exception != null) {
                            errorMessage += ": " + exception.getMessage();
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        binding.createGroupBtn.setEnabled(true);
                    }
                });
    }

    private String getCurrentUserName() {
        // Здесь можно получить имя текущего пользователя из Firebase
        // Пока возвращаем "User" как заглушку
        return "User";
    }

    private String getCurrentTime() {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
        return dateFormat.format(new java.util.Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Activity destroyed");
        binding = null;
    }
}