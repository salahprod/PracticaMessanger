package com.example.androidmessage1.groups;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.databinding.ActivityAddMembersBinding;
import com.example.androidmessage1.groups.members.AddMemberAdapter;
import com.example.androidmessage1.groups.members.GroupMember;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddMembersActivity extends AppCompatActivity {

    private ActivityAddMembersBinding binding;
    private String groupId;
    private String currentUserId;
    private AddMemberAdapter adapter;
    private List<GroupMember> allUsers = new ArrayList<>();
    private List<GroupMember> filteredUsers = new ArrayList<>();
    private List<String> existingMemberIds = new ArrayList<>();
    private Map<String, GroupMember> selectedUsers = new HashMap<>();
    private String groupOwnerId = "";
    private Map<String, ValueEventListener> customSettingsListeners = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddMembersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupId = getIntent().getStringExtra("groupId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (groupId == null) {
            Toast.makeText(this, "Group ID is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d("AddMembers", "Starting AddMembers for group: " + groupId);

        initializeViews();
        loadGroupOwner();
        loadExistingMembers();
        setupSearch();
    }

    private void initializeViews() {
        binding.backBtn.setOnClickListener(v -> finish());

        // Кнопка добавления
        binding.addButton.setOnClickListener(v -> addSelectedMembersToGroup());

        // Настройка RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.usersRecyclerView.setLayoutManager(layoutManager);

        adapter = new AddMemberAdapter(filteredUsers, new AddMemberAdapter.OnMemberClickListener() {
            @Override
            public void onMemberClick(GroupMember member) {
                toggleUserSelection(member);
            }

            @Override
            public void onMemberLongClick(GroupMember member) {
                // Можно добавить долгое нажатие для просмотра профиля
            }
        });
        binding.usersRecyclerView.setAdapter(adapter);
    }

    private void loadGroupOwner() {
        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("createdBy")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            groupOwnerId = snapshot.getValue(String.class);
                            Log.d("AddMembers", "Group owner: " + groupOwnerId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("AddMembers", "Error loading group owner", error.toException());
                    }
                });
    }

    private void loadExistingMembers() {
        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("members")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        existingMemberIds.clear();
                        if (snapshot.exists()) {
                            for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                                String memberId = memberSnapshot.getValue(String.class);
                                if (memberId != null) {
                                    existingMemberIds.add(memberId);
                                    Log.d("AddMembers", "Existing member: " + memberId);
                                }
                            }
                        }
                        Log.d("AddMembers", "Total existing members: " + existingMemberIds.size());
                        loadAllUsers();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("AddMembers", "Error loading existing members", error.toException());
                        loadAllUsers();
                    }
                });
    }

    private void loadAllUsers() {
        FirebaseDatabase.getInstance().getReference("Users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allUsers.clear();
                        if (snapshot.exists()) {
                            Log.d("AddMembers", "Total users in database: " + snapshot.getChildrenCount());

                            for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                                String userId = userSnapshot.getKey();

                                // Пропускаем текущего пользователя и уже добавленных участников
                                if (userId == null || userId.equals(currentUserId) || existingMemberIds.contains(userId)) {
                                    continue;
                                }

                                // Проверяем наличие логина у пользователя
                                if (hasValidLogin(userSnapshot)) {
                                    // ЗАГРУЖАЕМ ПОЛЬЗОВАТЕЛЯ С КАСТОМНЫМИ НАСТРОЙКАМИ
                                    loadUserWithCustomSettings(userId);
                                } else {
                                    Log.d("AddMembers", "Skipping user without login: " + userId);
                                }
                            }
                        }
                        Log.d("AddMembers", "Total available users to add: " + allUsers.size());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("AddMembers", "Error loading users", error.toException());
                        Toast.makeText(AddMembersActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // НОВЫЙ МЕТОД: Проверка наличия валидного логина у пользователя
    private boolean hasValidLogin(DataSnapshot userSnapshot) {
        // Проверяем различные поля, которые могут содержать логин
        String login = userSnapshot.child("login").getValue(String.class);
        String username = userSnapshot.child("username").getValue(String.class);
        String name = userSnapshot.child("name").getValue(String.class);
        String displayName = userSnapshot.child("displayName").getValue(String.class);
        String email = userSnapshot.child("email").getValue(String.class);

        // Если есть хотя бы одно непустое поле с именем/логином
        if (login != null && !login.trim().isEmpty()) {
            return true;
        }
        if (username != null && !username.trim().isEmpty()) {
            return true;
        }
        if (name != null && !name.trim().isEmpty()) {
            return true;
        }
        if (displayName != null && !displayName.trim().isEmpty()) {
            return true;
        }
        if (email != null && !email.trim().isEmpty()) {
            return true;
        }

        // Если все поля пустые, пользователь невалиден
        return false;
    }

    // НОВЫЙ МЕТОД: Загрузка пользователя с кастомными настройками
    private void loadUserWithCustomSettings(String userId) {
        DatabaseReference customRef = FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(userId);

        ValueEventListener customListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String customName = null;
                String customImage = null;

                if (snapshot.exists()) {
                    customName = snapshot.child("customName").getValue(String.class);
                    customImage = snapshot.child("customImage").getValue(String.class);
                    Log.d("AddMembers", "Found customizations for user " + userId +
                            ": name=" + customName + ", image=" + customImage);
                }

                // Загружаем основные данные пользователя с учетом кастомных настроек
                loadUserBasicData(userId, customName, customImage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AddMembers", "Error loading custom settings for user: " + userId, error.toException());
                // Если ошибка, загружаем без кастомных настроек
                loadUserBasicData(userId, null, null);
            }
        };

        customSettingsListeners.put(userId, customListener);
        customRef.addListenerForSingleValueEvent(customListener);
    }

    // ОБНОВЛЕННЫЙ МЕТОД: Загрузка основных данных с учетом кастомных настроек
    private void loadUserBasicData(String userId, String customName, String customImage) {
        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Проверяем еще раз наличие логина (на всякий случай)
                            if (!hasValidLogin(snapshot)) {
                                Log.d("AddMembers", "Skipping user without login in basic data: " + userId);
                                return;
                            }

                            // Используем кастомное имя если есть, иначе берем из профиля
                            String username = customName != null ? customName : getUsernameFromSnapshot(snapshot);

                            // Используем кастомную аватарку если есть, иначе берем из профиля
                            String profileImage = customImage != null ? customImage : getProfileImageFromSnapshot(snapshot);

                            GroupMember user = new GroupMember(userId, username, profileImage, "member", "online");
                            allUsers.add(user);
                            Log.d("AddMembers", "Added user to list: " + username + " (" + userId + ")" +
                                    ", Custom: " + (customName != null));

                            updateUsersList();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("AddMembers", "Failed to load user details for: " + userId, error.toException());
                    }
                });
    }

    private String getUsernameFromSnapshot(DataSnapshot snapshot) {
        // Пробуем разные поля для имени пользователя
        String username = snapshot.child("login").getValue(String.class);
        if (username == null || username.trim().isEmpty()) {
            username = snapshot.child("username").getValue(String.class);
        }
        if (username == null || username.trim().isEmpty()) {
            username = snapshot.child("name").getValue(String.class);
        }
        if (username == null || username.trim().isEmpty()) {
            username = snapshot.child("displayName").getValue(String.class);
        }
        if (username == null || username.trim().isEmpty()) {
            String email = snapshot.child("email").getValue(String.class);
            if (email != null && email.contains("@")) {
                username = email.substring(0, email.indexOf("@"));
            } else {
                username = "User " + snapshot.getKey().substring(0, 6);
            }
        }
        return username;
    }

    private String getProfileImageFromSnapshot(DataSnapshot snapshot) {
        // Пробуем разные поля для аватарки
        String profileImage = snapshot.child("profileImage").getValue(String.class);
        if (profileImage == null || profileImage.trim().isEmpty()) {
            profileImage = snapshot.child("photoUrl").getValue(String.class);
        }
        if (profileImage == null || profileImage.trim().isEmpty()) {
            profileImage = snapshot.child("image").getValue(String.class);
        }
        if (profileImage == null || profileImage.trim().isEmpty()) {
            profileImage = snapshot.child("avatar").getValue(String.class);
        }
        if (profileImage == null) {
            profileImage = "";
        }
        return profileImage;
    }

    private void updateUsersList() {
        filteredUsers.clear();
        filteredUsers.addAll(allUsers);
        adapter.notifyDataSetChanged();

        if (filteredUsers.isEmpty()) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.usersRecyclerView.setVisibility(View.GONE);
            Log.d("AddMembers", "No users available to show");
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
            binding.usersRecyclerView.setVisibility(View.VISIBLE);
            Log.d("AddMembers", "Showing " + filteredUsers.size() + " users");
        }
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterUsers(String query) {
        filteredUsers.clear();
        if (query.isEmpty()) {
            filteredUsers.addAll(allUsers);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (GroupMember user : allUsers) {
                if (user.getUsername().toLowerCase().contains(lowerCaseQuery)) {
                    filteredUsers.add(user);
                }
            }
        }
        adapter.notifyDataSetChanged();

        if (filteredUsers.isEmpty()) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.usersRecyclerView.setVisibility(View.GONE);
        } else {
            binding.emptyStateText.setVisibility(View.GONE);
            binding.usersRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void toggleUserSelection(GroupMember member) {
        if (selectedUsers.containsKey(member.getUserId())) {
            selectedUsers.remove(member.getUserId());
            Log.d("AddMembers", "User deselected: " + member.getUsername());
        } else {
            selectedUsers.put(member.getUserId(), member);
            Log.d("AddMembers", "User selected: " + member.getUsername());
        }

        updateSelectionUI();
        adapter.notifyDataSetChanged();
    }

    private void updateSelectionUI() {
        int selectedCount = selectedUsers.size();

        // Обновляем заголовок
        binding.headerTitle.setText("Add Members (" + selectedCount + " selected)");

        // Обновляем счетчик
        if (selectedCount > 0) {
            binding.selectedCountText.setText("Selected: " + selectedCount + " user" + (selectedCount != 1 ? "s" : ""));
            binding.selectedCountText.setVisibility(View.VISIBLE);
            binding.addButton.setVisibility(View.VISIBLE);
        } else {
            binding.selectedCountText.setVisibility(View.GONE);
            binding.addButton.setVisibility(View.GONE);
        }
    }

    private void addSelectedMembersToGroup() {
        if (selectedUsers.isEmpty()) {
            Toast.makeText(this, "No users selected", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("AddMembers", "Adding " + selectedUsers.size() + " users to group");

        // Получаем текущий список участников
        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("members")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> updatedMembers = new ArrayList<>();

                        // Добавляем существующих участников
                        if (snapshot.exists()) {
                            for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                                String existingMemberId = memberSnapshot.getValue(String.class);
                                if (existingMemberId != null) {
                                    updatedMembers.add(existingMemberId);
                                }
                            }
                        }

                        // Добавляем новых участников
                        int addedCount = 0;
                        for (GroupMember member : selectedUsers.values()) {
                            if (!updatedMembers.contains(member.getUserId())) {
                                updatedMembers.add(member.getUserId());
                                addedCount++;
                            }
                        }

                        // Обновляем список в Firebase
                        FirebaseDatabase.getInstance().getReference("Groups")
                                .child(groupId)
                                .child("members")
                                .setValue(updatedMembers)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        // Добавляем группу в список групп пользователей
                                        addGroupToUsers(selectedUsers.values());
                                    } else {
                                        Toast.makeText(AddMembersActivity.this, "Failed to add members", Toast.LENGTH_SHORT).show();
                                        Log.e("AddMembers", "Failed to add members to group");
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("AddMembers", "Error adding members", error.toException());
                    }
                });
    }

    private void addGroupToUsers(java.util.Collection<GroupMember> members) {
        int totalUsers = members.size();
        final int[] completed = {0};

        for (GroupMember member : members) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(member.getUserId())
                    .child("groups")
                    .child(groupId)
                    .setValue(true)
                    .addOnCompleteListener(task -> {
                        completed[0]++;
                        if (completed[0] == totalUsers) {
                            // Все пользователи добавлены
                            Toast.makeText(AddMembersActivity.this,
                                    "Added " + totalUsers + " members to group",
                                    Toast.LENGTH_SHORT).show();
                            Log.d("AddMembers", "Successfully added " + totalUsers + " members to group");

                            // Удаляем добавленных пользователей из списка
                            for (GroupMember addedMember : members) {
                                allUsers.remove(addedMember);
                                filteredUsers.remove(addedMember);
                            }
                            selectedUsers.clear();
                            updateSelectionUI();
                            adapter.notifyDataSetChanged();
                            updateUsersList();

                            // Закрываем активность после успешного добавления
                            finish();
                        }
                    });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Очищаем кастомные слушатели
        for (Map.Entry<String, ValueEventListener> entry : customSettingsListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("UserCustomizations")
                        .child(currentUserId)
                        .child("chatContacts")
                        .child(entry.getKey())
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("AddMembers", "Error removing custom settings listener", e);
            }
        }
        customSettingsListeners.clear();

        Log.d("AddMembers", "AddMembersActivity destroyed");
    }
}