package com.example.androidmessage1.groups;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.ActivityGroupSettingsBinding;
import com.example.androidmessage1.groups.members.GroupMember;
import com.example.androidmessage1.groups.members.GroupMemberAdapter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupSettingsActivity extends AppCompatActivity {

    private ActivityGroupSettingsBinding binding;
    private String groupId;
    private String currentUserId;
    private GroupMemberAdapter memberAdapter;
    private ArrayList<GroupMember> members = new ArrayList<>();
    private ValueEventListener groupInfoListener;
    private ValueEventListener membersListener;
    private ValueEventListener rolesListener;
    private boolean isOwner = false;
    private boolean isAdmin = false;
    private DatabaseReference groupRef;
    private String groupOwnerId = "";
    private Map<String, ValueEventListener> customSettingsListeners = new HashMap<>();

    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupId = getIntent().getStringExtra("groupId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (groupId == null) {
            Toast.makeText(this, "Group ID is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Log.d("GroupSettings", "Starting GroupSettings for group: " + groupId);
        Log.d("GroupSettings", "Current user ID: " + currentUserId);

        groupRef = FirebaseDatabase.getInstance().getReference("Groups").child(groupId);

        initializeViews();
        loadGroupInfo();
        setupMembersList();
        setupRolesListener();
        checkUserRole();
        setupClickListeners();
    }

    private void initializeViews() {
        binding.backBtn.setOnClickListener(v -> finish());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.membersListLayout.setLayoutManager(layoutManager);

        memberAdapter = new GroupMemberAdapter(members, new GroupMemberAdapter.OnMemberClickListener() {
            @Override
            public void onMemberClick(GroupMember member) {
                // ОБНОВЛЕННЫЙ МЕТОД: Для всех пользователей - открываем чат
                if (!member.getUserId().equals(currentUserId)) {
                    openUserChat(member);
                }
            }

            @Override
            public void onMemberLongClick(GroupMember member) {
                // ОБНОВЛЕННЫЙ МЕТОД: Для админов - показываем опции управления
                if ((isOwner || isAdmin) && !member.getUserId().equals(currentUserId)) {
                    showAdvancedMemberOptions(member);
                }
            }
        });
        binding.membersListLayout.setAdapter(memberAdapter);
    }

    // НОВЫЙ МЕТОД: Открытие чата с пользователем
    private void openUserChat(GroupMember member) {
        Log.d("GroupSettings", "Opening chat with user: " + member.getUsername());

        // Создаем или находим существующий чат с пользователем
        findOrCreateChat(member.getUserId(), member.getUsername());
    }

    // НОВЫЙ МЕТОД: Поиск или создание чата с пользователем
    private void findOrCreateChat(String otherUserId, String otherUsername) {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("Chats");

        chatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String existingChatId = null;

                // Ищем существующий чат между текущим пользователем и выбранным пользователем
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String user1 = chatSnapshot.child("user1").getValue(String.class);
                    String user2 = chatSnapshot.child("user2").getValue(String.class);

                    if ((user1 != null && user2 != null) &&
                            ((user1.equals(currentUserId) && user2.equals(otherUserId)) ||
                                    (user1.equals(otherUserId) && user2.equals(currentUserId)))) {
                        existingChatId = chatSnapshot.getKey();
                        break;
                    }
                }

                if (existingChatId != null) {
                    // Открываем существующий чат
                    openExistingChat(existingChatId, otherUserId, otherUsername);
                } else {
                    // Создаем новый чат
                    createNewChat(otherUserId, otherUsername);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error finding chat", error.toException());
                Toast.makeText(GroupSettingsActivity.this, "Error opening chat", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // НОВЫЙ МЕТОД: Открытие существующего чата
    private void openExistingChat(String chatId, String otherUserId, String otherUsername) {
        try {
            Intent intent = new Intent(GroupSettingsActivity.this, com.example.androidmessage1.ChatActivity.class);
            intent.putExtra("chatId", chatId);
            intent.putExtra("otherUserId", otherUserId);
            intent.putExtra("otherUsername", otherUsername);
            startActivity(intent);
            Log.d("GroupSettings", "Opening existing chat: " + chatId);
        } catch (Exception e) {
            Log.e("GroupSettings", "Error opening existing chat", e);
            Toast.makeText(this, "Error opening chat", Toast.LENGTH_SHORT).show();
        }
    }

    // НОВЫЙ МЕТОД: Создание нового чата
    private void createNewChat(String otherUserId, String otherUsername) {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("Chats");
        String newChatId = chatsRef.push().getKey();

        if (newChatId == null) {
            Toast.makeText(this, "Error creating chat", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаем структуру чата
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("user1", currentUserId);
        chatData.put("user2", otherUserId);
        chatData.put("LastMessage", "");
        chatData.put("LastMessageTime", System.currentTimeMillis());

        chatsRef.child(newChatId).setValue(chatData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Открываем новый чат
                        openExistingChat(newChatId, otherUserId, otherUsername);
                        Log.d("GroupSettings", "New chat created: " + newChatId);
                    } else {
                        Toast.makeText(GroupSettingsActivity.this, "Failed to create chat", Toast.LENGTH_SHORT).show();
                        Log.e("GroupSettings", "Failed to create new chat");
                    }
                });
    }

    private void setupRolesListener() {
        rolesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("GroupSettings", "Roles updated");
                loadMembersWithRoles();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error loading roles", error.toException());
            }
        };
        groupRef.child("roles").addValueEventListener(rolesListener);
    }

    private void checkUserRole() {
        groupRef.child("createdBy").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    groupOwnerId = snapshot.getValue(String.class);
                    if (groupOwnerId != null && groupOwnerId.equals(currentUserId)) {
                        isOwner = true;
                        isAdmin = true;
                        Log.d("GroupSettings", "User is owner (creator) of the group");
                    } else {
                        checkIfUserIsAdmin();
                    }
                }
                showAdminFeatures();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error checking user role", error.toException());
            }
        });
    }

    private void checkIfUserIsAdmin() {
        groupRef.child("roles").child(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String role = snapshot.getValue(String.class);
                    isAdmin = "admin".equals(role) || "owner".equals(role);
                    Log.d("GroupSettings", "User admin status: " + isAdmin + ", role: " + role);
                } else {
                    isAdmin = false;
                }
                showAdminFeatures();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error checking admin status", error.toException());
            }
        });
    }

    private void showAdminFeatures() {
        if (isOwner || isAdmin) {
            binding.addMembersButton.setVisibility(View.VISIBLE);
            binding.groupImage.setClickable(true);
            binding.groupName.setClickable(true);
            Log.d("GroupSettings", "Showing admin features");
        } else {
            binding.addMembersButton.setVisibility(View.GONE);
            binding.groupImage.setClickable(false);
            binding.groupName.setClickable(false);
            Log.d("GroupSettings", "Hiding admin features - user is regular member");
        }

        if (isOwner) {
            binding.leaveText.setText("Delete Group");
        } else {
            binding.leaveText.setText("Leave Group");
        }
    }

    private void setupClickListeners() {
        binding.groupImage.setOnClickListener(v -> {
            if (isOwner || isAdmin) {
                changeGroupImage();
            } else {
                Toast.makeText(this, "Only admins can change group image", Toast.LENGTH_SHORT).show();
            }
        });

        binding.groupName.setOnClickListener(v -> {
            if (isOwner || isAdmin) {
                showEditGroupNameDialog();
            } else {
                Toast.makeText(this, "Only admins can change group name", Toast.LENGTH_SHORT).show();
            }
        });

        binding.addMembersButton.setOnClickListener(v -> {
            if (isOwner || isAdmin) {
                openAddMembersActivity();
            } else {
                Toast.makeText(this, "Only admins can add members", Toast.LENGTH_SHORT).show();
            }
        });

        binding.leaveButton.setOnClickListener(v -> showLeaveGroupConfirmation());
    }

    private void loadGroupInfo() {
        groupInfoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("GroupSettings", "Group info snapshot exists: " + snapshot.exists());

                if (snapshot.exists()) {
                    String groupName = snapshot.child("groupName").getValue(String.class);
                    String groupImage = snapshot.child("groupImage").getValue(String.class);

                    Log.d("GroupSettings", "Group name: " + groupName);
                    Log.d("GroupSettings", "Group image: " + groupImage);

                    if (groupName != null) {
                        binding.groupName.setText(groupName);
                    } else {
                        binding.groupName.setText("Unnamed Group");
                    }

                    if (groupImage != null && !groupImage.isEmpty()) {
                        Glide.with(GroupSettingsActivity.this)
                                .load(groupImage)
                                .placeholder(R.drawable.artem)
                                .error(R.drawable.artem)
                                .into(binding.groupImage);
                    } else {
                        binding.groupImage.setImageResource(R.drawable.artem);
                    }
                } else {
                    Toast.makeText(GroupSettingsActivity.this, "Group not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Failed to load group info", error.toException());
                Toast.makeText(GroupSettingsActivity.this, "Failed to load group info", Toast.LENGTH_SHORT).show();
            }
        };

        groupRef.addValueEventListener(groupInfoListener);
    }

    private void setupMembersList() {
        membersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                loadMembersWithRoles();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Failed to load members", error.toException());
                Toast.makeText(GroupSettingsActivity.this, "Failed to load members", Toast.LENGTH_SHORT).show();
            }
        };

        groupRef.child("members").addValueEventListener(membersListener);
    }

    private void loadMembersWithRoles() {
        groupRef.child("roles").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot rolesSnapshot) {
                members.clear();

                groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot membersSnapshot) {
                        final int memberCount = (int) membersSnapshot.getChildrenCount();
                        Log.d("GroupSettings", "Total members in group: " + memberCount);

                        for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                            String memberId = memberSnapshot.getValue(String.class);
                            Log.d("GroupSettings", "Found member ID: " + memberId);

                            if (memberId != null && !memberId.isEmpty()) {
                                String role = "member";
                                if (rolesSnapshot.child(memberId).exists()) {
                                    role = rolesSnapshot.child(memberId).getValue(String.class);
                                    if (role == null) role = "member";
                                } else if (memberId.equals(groupOwnerId)) {
                                    role = "owner";
                                    if (!rolesSnapshot.child(memberId).exists()) {
                                        groupRef.child("roles").child(memberId).setValue("owner");
                                    }
                                }

                                // ИСПРАВЛЕННЫЙ ВЫЗОВ: Используем метод с кастомными настройками
                                loadMemberDetailsWithCustomSettings(memberId, role);
                            }
                        }
                        updateMembersCount(memberCount);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupSettings", "Error loading members", error.toException());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error loading roles", error.toException());
            }
        });
    }

    // НОВЫЙ МЕТОД: Загрузка участника с кастомными настройками
    private void loadMemberDetailsWithCustomSettings(String memberId, String role) {
        DatabaseReference customRef = FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(memberId);

        ValueEventListener customListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String customName = null;
                String customImage = null;

                if (snapshot.exists()) {
                    customName = snapshot.child("customName").getValue(String.class);
                    customImage = snapshot.child("customImage").getValue(String.class);
                    Log.d("GroupSettings", "Found customizations for user " + memberId +
                            ": name=" + customName + ", image=" + customImage);
                }

                // Загружаем основные данные пользователя с учетом кастомных настроек
                loadUserBasicData(memberId, role, customName, customImage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error loading customizations for user: " + memberId, error.toException());
                // Если ошибка, загружаем без кастомных настроек
                loadUserBasicData(memberId, role, null, null);
            }
        };

        customSettingsListeners.put(memberId, customListener);
        customRef.addListenerForSingleValueEvent(customListener);
    }

    // ОБНОВЛЕННЫЙ МЕТОД: Загрузка основных данных с учетом кастомных настроек
    private void loadUserBasicData(String memberId, String role, String customName, String customImage) {
        FirebaseDatabase.getInstance().getReference("Users")
                .child(memberId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Используем кастомное имя если есть, иначе берем из профиля
                            String username = customName != null ? customName : getUsernameFromSnapshot(snapshot);

                            // Используем кастомную аватарку если есть, иначе берем из профиля
                            String profileImage = customImage != null ? customImage : getProfileImageFromSnapshot(snapshot);

                            String status = getStatusFromSnapshot(snapshot);

                            Log.d("GroupSettings", "User data loaded - ID: " + memberId +
                                    ", Name: " + username + ", Image: " + profileImage + ", Role: " + role +
                                    ", Custom: " + (customName != null));

                            GroupMember member = new GroupMember(memberId, username, profileImage, role, status);
                            updateOrAddMember(member);
                        } else {
                            Log.e("GroupSettings", "User not found in database: " + memberId);
                            // Используем кастомные данные даже если пользователь не найден
                            String username = customName != null ? customName : "Unknown User";
                            String profileImage = customImage != null ? customImage : "";
                            GroupMember member = new GroupMember(memberId, username, profileImage, role, "offline");
                            updateOrAddMember(member);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupSettings", "Failed to load user details for: " + memberId, error.toException());
                        // Используем кастомные данные при ошибке
                        String username = customName != null ? customName : "Error Loading User";
                        String profileImage = customImage != null ? customImage : "";
                        GroupMember member = new GroupMember(memberId, username, profileImage, role, "offline");
                        updateOrAddMember(member);
                    }
                });
    }

    private void updateMembersCount(int count) {
        String membersText = count + " member" + (count != 1 ? "s" : "");
        binding.membersCount.setText(membersText);
        Log.d("GroupSettings", "Updated members count: " + membersText);
    }

    private String getUsernameFromSnapshot(DataSnapshot snapshot) {
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

    private String getStatusFromSnapshot(DataSnapshot snapshot) {
        Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
        Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
        return formatUserStatus(isOnline, lastOnline);
    }

    private String formatUserStatus(Boolean isOnline, Long lastOnline) {
        if (isOnline != null && isOnline) {
            return "online";
        } else if (lastOnline != null) {
            long currentTime = System.currentTimeMillis();
            long diff = currentTime - lastOnline;
            long minutes = diff / (60 * 1000);
            long hours = diff / (60 * 60 * 1000);
            long days = diff / (60 * 60 * 1000 * 24);

            if (minutes < 1) return "just now";
            else if (minutes < 60) return minutes + " min ago";
            else if (hours < 24) return hours + " hours ago";
            else if (days == 1) return "yesterday";
            else return days + " days ago";
        } else {
            return "offline";
        }
    }

    private void updateOrAddMember(GroupMember newMember) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getUserId().equals(newMember.getUserId())) {
                members.remove(i);
                break;
            }
        }

        // Сортируем: владелец первый, затем админы, затем обычные участники
        if ("owner".equals(newMember.getRole())) {
            members.add(0, newMember);
        } else if ("admin".equals(newMember.getRole())) {
            int adminPosition = 0;
            for (int i = 0; i < members.size(); i++) {
                if ("owner".equals(members.get(i).getRole())) {
                    adminPosition = i + 1;
                } else if ("member".equals(members.get(i).getRole())) {
                    break;
                }
            }
            members.add(adminPosition, newMember);
        } else {
            members.add(newMember);
        }

        updateMembersDisplay();
    }

    private void updateMembersDisplay() {
        Log.d("GroupSettings", "Updating display with " + members.size() + " members");
        runOnUiThread(() -> {
            memberAdapter.notifyDataSetChanged();
        });
    }

    private void showEditGroupNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Group Name");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(50, 30, 50, 30);

        TextInputEditText nameInput = new TextInputEditText(this);
        nameInput.setText(binding.groupName.getText().toString());
        nameInput.setHint("Enter group name");
        nameInput.setSingleLine(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameInput.setLayoutParams(params);

        container.addView(nameInput);
        builder.setView(container);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                updateGroupName(newName);
            } else {
                Toast.makeText(GroupSettingsActivity.this, "Group name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        nameInput.requestFocus();
        nameInput.selectAll();
    }

    private void updateGroupName(String newName) {
        groupRef.child("groupName").setValue(newName)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Group name updated", Toast.LENGTH_SHORT).show();
                        binding.groupName.setText(newName);
                        Log.d("GroupSettings", "Group name updated to: " + newName);
                    } else {
                        Toast.makeText(this, "Failed to update group name", Toast.LENGTH_SHORT).show();
                        Log.e("GroupSettings", "Failed to update group name");
                    }
                });
    }

    private void changeGroupImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                uploadGroupImage(imageUri);
            }
        }
    }

    private void uploadGroupImage(Uri imageUri) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();
        Log.d("GroupSettings", "Uploading group image");

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("group_images")
                .child(groupId + ".jpg");

        storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateGroupImage(uri.toString());
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("GroupSettings", "Upload failed", e);
                });
    }

    private void updateGroupImage(String imageUrl) {
        groupRef.child("groupImage").setValue(imageUrl)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Group image updated", Toast.LENGTH_SHORT).show();
                        Glide.with(GroupSettingsActivity.this)
                                .load(imageUrl)
                                .placeholder(R.drawable.artem)
                                .error(R.drawable.artem)
                                .into(binding.groupImage);
                        Log.d("GroupSettings", "Group image updated successfully");
                    } else {
                        Toast.makeText(this, "Failed to update group image", Toast.LENGTH_SHORT).show();
                        Log.e("GroupSettings", "Failed to update group image");
                    }
                });
    }

    private void openAddMembersActivity() {
        Intent intent = new Intent(this, AddMembersActivity.class);
        intent.putExtra("groupId", groupId);
        startActivity(intent);
        Log.d("GroupSettings", "Opening AddMembersActivity for group: " + groupId);
    }

    private void showAdvancedMemberOptions(GroupMember member) {
        if (!isOwner && !isAdmin) {
            return;
        }

        if ("owner".equals(member.getRole())) {
            Toast.makeText(this, "You cannot manage the group owner", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> options = new ArrayList<>();

        if (isOwner) {
            if ("admin".equals(member.getRole())) {
                options.add("Remove Admin Role");
            } else {
                options.add("Make Admin");
            }
        }

        options.add("Remove from Group");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manage " + member.getUsername());
        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            String selectedOption = options.get(which);
            switch (selectedOption) {
                case "Make Admin":
                    makeAdmin(member.getUserId(), member.getUsername());
                    break;
                case "Remove Admin Role":
                    removeAdmin(member.getUserId(), member.getUsername());
                    break;
                case "Remove from Group":
                    removeMember(member.getUserId());
                    break;
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void makeAdmin(String memberId, String username) {
        if (!isOwner) {
            Toast.makeText(this, "Only group owner can assign admins", Toast.LENGTH_SHORT).show();
            return;
        }

        groupRef.child("roles").child(memberId).setValue("admin")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        recordAdminAction(memberId, "promoted", username);
                        Toast.makeText(this, username + " promoted to admin", Toast.LENGTH_SHORT).show();
                        Log.d("GroupSettings", "User promoted to admin: " + memberId);
                    } else {
                        Toast.makeText(this, "Failed to promote user", Toast.LENGTH_SHORT).show();
                        Log.e("GroupSettings", "Failed to promote user: " + memberId);
                    }
                });
    }

    private void removeAdmin(String memberId, String username) {
        if (!isOwner) {
            Toast.makeText(this, "Only group owner can remove admins", Toast.LENGTH_SHORT).show();
            return;
        }

        groupRef.child("roles").child(memberId).setValue("member")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        recordAdminAction(memberId, "demoted", username);
                        Toast.makeText(this, username + " demoted to member", Toast.LENGTH_SHORT).show();
                        Log.d("GroupSettings", "User demoted to member: " + memberId);
                    } else {
                        Toast.makeText(this, "Failed to demote user", Toast.LENGTH_SHORT).show();
                        Log.e("GroupSettings", "Failed to demote user: " + memberId);
                    }
                });
    }

    private void recordAdminAction(String targetUserId, String action, String targetUsername) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        HashMap<String, Object> adminAction = new HashMap<>();
        adminAction.put("userId", targetUserId);
        adminAction.put("username", targetUsername);
        adminAction.put("action", action);
        adminAction.put("byUserId", currentUserId);
        adminAction.put("byUsername", getCurrentUsername());
        adminAction.put("timestamp", timestamp);

        groupRef.child("adminHistory").child(timestamp).setValue(adminAction);
    }

    private String getCurrentUsername() {
        return "Current User";
    }

    private void removeMember(String memberId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remove Member");
        builder.setMessage("Are you sure you want to remove this member from the group?");
        builder.setPositiveButton("Remove", (dialog, which) -> {
            groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<String> updatedMembers = new ArrayList<>();
                    for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                        String existingMemberId = memberSnapshot.getValue(String.class);
                        if (existingMemberId != null && !existingMemberId.equals(memberId)) {
                            updatedMembers.add(existingMemberId);
                        }
                    }

                    groupRef.child("members").setValue(updatedMembers)
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    groupRef.child("roles").child(memberId).removeValue();

                                    FirebaseDatabase.getInstance().getReference("Users")
                                            .child(memberId)
                                            .child("groups")
                                            .child(groupId)
                                            .removeValue()
                                            .addOnCompleteListener(task2 -> {
                                                Toast.makeText(GroupSettingsActivity.this, "Member removed", Toast.LENGTH_SHORT).show();
                                                Log.d("GroupSettings", "Member removed: " + memberId);

                                                // ОБНОВЛЕНИЕ: Если удаляем текущего пользователя, переходим в чаты
                                                if (memberId.equals(currentUserId)) {
                                                    navigateToChatsFragment();
                                                }
                                            });
                                } else {
                                    Toast.makeText(GroupSettingsActivity.this, "Failed to remove member", Toast.LENGTH_SHORT).show();
                                    Log.e("GroupSettings", "Failed to remove member: " + memberId);
                                }
                            });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("GroupSettings", "Error removing member", error.toException());
                }
            });
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showLeaveGroupConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (isOwner) {
            builder.setTitle("Delete Group");
            builder.setMessage("Are you sure you want to delete this group? This action cannot be undone.");
            builder.setPositiveButton("Delete", (dialog, which) -> deleteGroup());
        } else {
            builder.setTitle("Leave Group");
            builder.setMessage("Are you sure you want to leave this group?");
            builder.setPositiveButton("Leave", (dialog, which) -> leaveGroup());
        }

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void leaveGroup() {
        groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> updatedMembers = new ArrayList<>();
                for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                    String existingMemberId = memberSnapshot.getValue(String.class);
                    if (existingMemberId != null && !existingMemberId.equals(currentUserId)) {
                        updatedMembers.add(existingMemberId);
                    }
                }

                groupRef.child("members").setValue(updatedMembers)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                groupRef.child("roles").child(currentUserId).removeValue();

                                FirebaseDatabase.getInstance().getReference("Users")
                                        .child(currentUserId)
                                        .child("groups")
                                        .child(groupId)
                                        .removeValue()
                                        .addOnCompleteListener(task2 -> {
                                            Toast.makeText(GroupSettingsActivity.this, "You left the group", Toast.LENGTH_SHORT).show();
                                            Log.d("GroupSettings", "User left group: " + groupId);

                                            // ОБНОВЛЕННЫЙ КОД: Переход в список чатов для всех пользователей
                                            navigateToChatsFragment();
                                        });
                            } else {
                                Toast.makeText(GroupSettingsActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                                Log.e("GroupSettings", "Failed to leave group: " + groupId);
                            }
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error leaving group", error.toException());
            }
        });
    }

    private void deleteGroup() {
        // Сначала получаем список всех участников для уведомления
        groupRef.child("members").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> allMembers = new ArrayList<>();
                for (DataSnapshot memberSnapshot : snapshot.getChildren()) {
                    String memberId = memberSnapshot.getValue(String.class);
                    if (memberId != null) {
                        allMembers.add(memberId);
                    }
                }

                // Удаляем группу из базы данных
                groupRef.removeValue()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Удаляем группу у всех участников
                                DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
                                for (String memberId : allMembers) {
                                    usersRef.child(memberId)
                                            .child("groups")
                                            .child(groupId)
                                            .removeValue();
                                }

                                Toast.makeText(GroupSettingsActivity.this, "Group deleted", Toast.LENGTH_SHORT).show();
                                Log.d("GroupSettings", "Group deleted: " + groupId);

                                // ОБНОВЛЕННЫЙ КОД: Переход в список чатов для всех пользователей
                                navigateToChatsFragment();
                            } else {
                                Toast.makeText(GroupSettingsActivity.this, "Failed to delete group", Toast.LENGTH_SHORT).show();
                                Log.e("GroupSettings", "Failed to delete group: " + groupId);
                            }
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupSettings", "Error getting members for deletion", error.toException());
                // Если не удалось получить список участников, все равно удаляем группу
                groupRef.removeValue()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(GroupSettingsActivity.this, "Group deleted", Toast.LENGTH_SHORT).show();
                                navigateToChatsFragment();
                            } else {
                                Toast.makeText(GroupSettingsActivity.this, "Failed to delete group", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }

    // ОБНОВЛЕННЫЙ МЕТОД: Переход в FragmentChat (список чатов) для всех случаев
    private void navigateToChatsFragment() {
        try {
            // Создаем Intent для возврата к основному активити с фрагментом чатов
            Intent intent = new Intent(GroupSettingsActivity.this, com.example.androidmessage1.MainActivity.class);

            // Устанавливаем флаги для очистки стека активити и запуска MainActivity как новой задачи
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // Добавляем extra чтобы указать какой фрагмент открыть
            intent.putExtra("openFragment", "chats");

            startActivity(intent);
            finish(); // Завершаем текущую активити

            Log.d("GroupSettings", "Navigating back to chats fragment");
        } catch (Exception e) {
            Log.e("GroupSettings", "Error navigating to chats fragment", e);

            // Альтернативный способ: просто завершаем активити
            try {
                finish();
            } catch (Exception ex) {
                Log.e("GroupSettings", "Error finishing activity", ex);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (groupInfoListener != null && groupId != null) {
            groupRef.removeEventListener(groupInfoListener);
        }
        if (membersListener != null && groupId != null) {
            groupRef.child("members").removeEventListener(membersListener);
        }
        if (rolesListener != null && groupId != null) {
            groupRef.child("roles").removeEventListener(rolesListener);
        }

        // Очищаем кастомные слушатели
        for (Map.Entry<String, ValueEventListener> entry : customSettingsListeners.entrySet()) {
            try {
                FirebaseDatabase.getInstance().getReference("UserCustomizations")
                        .child(currentUserId)
                        .child("chatContacts")
                        .child(entry.getKey())
                        .removeEventListener(entry.getValue());
            } catch (Exception e) {
                Log.e("GroupSettings", "Error removing custom settings listener", e);
            }
        }
        customSettingsListeners.clear();

        Log.d("GroupSettings", "GroupSettingsActivity destroyed");
    }
}