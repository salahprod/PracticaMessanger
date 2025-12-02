package com.example.androidmessage1.groups;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import com.example.androidmessage1.SelectedFilesAdapter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.ActivityGroupChatBinding;
import com.example.androidmessage1.groups.messages.GroupMessage;
import com.example.androidmessage1.groups.messages.GroupMessageAdapter;
import com.example.androidmessage1.message.FontSizeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class GroupChatActivity extends AppCompatActivity {

    private ActivityGroupChatBinding binding;
    private String groupId;
    private String groupName;
    private GroupMessageAdapter messageAdapter;
    private List<GroupMessage> messages = new ArrayList<>();
    private ValueEventListener messagesListener;
    private ValueEventListener groupInfoListener;
    private ValueEventListener usersListener;
    private ValueEventListener userRoleListener;
    private String currentUserId;
    private List<String> groupMembers = new ArrayList<>();
    private DatabaseReference groupRef;
    private boolean isAdmin = false;
    private boolean isOwner = false;

    private int totalMembersCount = 0;
    private int onlineMembersCount = 0;

    // Константы для выбора файлов
    private static final int PICK_FILE_REQUEST = 1001;

    // Переменные для хранения выбранных файлов
    private List<Uri> selectedFiles = new ArrayList<>();
    private boolean isSendingFiles = false;
    private int totalFilesToSend = 0;
    private int successfullySentFiles = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (groupId == null) {
            Toast.makeText(this, "Group ID is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        groupRef = FirebaseDatabase.getInstance().getReference("Groups").child(groupId);

        Log.d("GroupChatActivity", "Opening group: " + groupId);
        Log.d("GroupChatActivity", "Current user: " + currentUserId);

        initializeViews();
        loadGroupInfo();
        loadMessages();
        setupCurrentUserOnlineStatus();
        markAllMessagesAsRead();
        checkUserRole();

        binding.sendMessageBtn.setOnClickListener(v -> {
            if (!selectedFiles.isEmpty()) {
                sendFiles();
            } else {
                sendMessage();
            }
        });

        binding.exitBtn.setOnClickListener(v -> finish());
        binding.sendVideoBtn.setOnClickListener(v -> openFilePicker());
        binding.groupImage.setOnClickListener(v -> openGroupSettings());
        binding.groupName.setOnClickListener(v -> openGroupSettings());

        // Настройка превью выбранных файлов
        setupSelectedFilesPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("GroupChatActivity", "=== ON RESUME ===");
        setupCurrentUserOnlineStatus();
        if (groupId != null) {
            markAllMessagesAsRead();
        }
        // ОБНОВЛЯЕМ РАЗМЕР ШРИФТА ПРИ ВОЗВРАЩЕНИИ НА ЭКРАН
        updateFontSize();
    }

    // МЕТОД ДЛЯ ОБНОВЛЕНИЯ РАЗМЕРА ШРИФТА
    private void updateFontSize() {
        if (messageAdapter != null) {
            messageAdapter.updateFontSize();
        }
    }

    // МЕТОД: Настройка превью выбранных файлов
    private void setupSelectedFilesPreview() {
        // Скрываем превью по умолчанию
        binding.selectedFilesContainer.setVisibility(View.GONE);

        // Настраиваем горизонтальный RecyclerView для превью
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.selectedFilesRv.setLayoutManager(layoutManager);

        // Кнопка очистки выбранных файлов
        binding.clearSelectedFilesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSelectedFiles();
            }
        });
    }

    // МЕТОД: Показать превью выбранных файлов
    private void showSelectedFilesPreview() {
        if (selectedFiles.isEmpty()) {
            binding.selectedFilesContainer.setVisibility(View.GONE);
            return;
        }

        binding.selectedFilesContainer.setVisibility(View.VISIBLE);

        // Создаем адаптер для превью
        SelectedFilesAdapter adapter = new SelectedFilesAdapter(selectedFiles, new SelectedFilesAdapter.OnFileRemoveListener() {
            @Override
            public void onFileRemove(int position) {
                removeFileFromSelection(position);
            }
        });

        binding.selectedFilesRv.setAdapter(adapter);
        binding.selectedFilesCount.setText("Выбрано файлов: " + selectedFiles.size());
    }

    // МЕТОД: Удалить файл из выбранных
    private void removeFileFromSelection(int position) {
        if (position >= 0 && position < selectedFiles.size()) {
            selectedFiles.remove(position);
            showSelectedFilesPreview();
            updateSendButtonState();
        }
    }

    // МЕТОД: Очистить все выбранные файлы
    private void clearSelectedFiles() {
        selectedFiles.clear();
        binding.selectedFilesContainer.setVisibility(View.GONE);
        updateSendButtonState();
        Toast.makeText(this, "Все файлы удалены из выбора", Toast.LENGTH_SHORT).show();
    }

    private void updateSendButtonState() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!selectedFiles.isEmpty()) {
                    binding.sendMessageBtn.setContentDescription("Send " + selectedFiles.size() + " files");
                } else {
                    binding.sendMessageBtn.setContentDescription("Send message");
                }
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select Files"), PICK_FILE_REQUEST);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No file manager available", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_FILE_REQUEST) {
                handleSelectedFiles(data);
            }
        }
    }

    private void handleSelectedFiles(Intent data) {
        selectedFiles.clear();

        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count; i++) {
                Uri fileUri = data.getClipData().getItemAt(i).getUri();
                selectedFiles.add(fileUri);
            }
        } else if (data.getData() != null) {
            selectedFiles.add(data.getData());
        }

        if (!selectedFiles.isEmpty()) {
            updateSendButtonState();
            showSelectedFilesPreview();
            Toast.makeText(this, "Selected " + selectedFiles.size() + " files. Press send to upload.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendFiles() {
        if (isSendingFiles) {
            Toast.makeText(this, "Files are already being sent", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        isSendingFiles = true;
        binding.sendMessageBtn.setEnabled(false);

        binding.sendMessageBtn.setContentDescription("Sending files...");
        Toast.makeText(this, "Sending " + selectedFiles.size() + " files...", Toast.LENGTH_SHORT).show();

        // Скрываем превью перед отправкой
        binding.selectedFilesContainer.setVisibility(View.GONE);

        // Сбрасываем счетчики
        totalFilesToSend = selectedFiles.size();
        successfullySentFiles = 0;

        // Отправляем каждый файл
        for (Uri fileUri : selectedFiles) {
            uploadFileToStorage(fileUri);
        }
    }

    private void uploadFileToStorage(Uri fileUri) {
        String fileName = "group_file_" + System.currentTimeMillis() + "_" + currentUserId;
        StorageReference fileRef = FirebaseStorage.getInstance().getReference()
                .child("group_files")
                .child(groupId)
                .child(fileName);

        String fileType = getContentResolver().getType(fileUri);
        final String messageType;
        if (fileType != null) {
            if (fileType.startsWith("image/")) {
                messageType = "image";
            } else if (fileType.startsWith("video/")) {
                messageType = "video";
            } else {
                messageType = "file";
            }
        } else {
            messageType = "file";
        }

        // Добавляем метаданные для правильного определения типа контента
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType(fileType)
                .build();

        UploadTask uploadTask = fileRef.putFile(fileUri, metadata);

        uploadTask.addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                fileRef.getDownloadUrl().addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        sendFileMessage(uri.toString(), messageType, getFileName(fileUri));
                        successfullySentFiles++;
                        checkAllFilesSent();
                    }
                });
            }
        }).addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(GroupChatActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                successfullySentFiles++;
                checkAllFilesSent();
            }
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e("GroupChatActivity", "Error getting file name", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void sendFileMessage(String fileUrl, String messageType, String fileName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String date = dateFormat.format(new Date());

        String messageKey = groupRef.child("messages").push().getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalMessageKey = messageKey;
        String messageText = getFileMessageText(messageType, fileName);

        loadUserDataWithCustomSettings(currentUserId, new UserDataCallback() {
            @Override
            public void onUserDataLoaded(String userName, String userAvatar) {
                HashMap<String, Object> messageInfo = new HashMap<>();
                messageInfo.put("text", messageText);
                messageInfo.put("ownerId", currentUserId);
                messageInfo.put("senderName", userName);
                messageInfo.put("senderAvatar", userAvatar);
                messageInfo.put("date", date);
                messageInfo.put("timestamp", System.currentTimeMillis());
                messageInfo.put("isRead", false);
                messageInfo.put("messageType", messageType);
                messageInfo.put("fileUrl", fileUrl);
                messageInfo.put("fileName", fileName);

                HashMap<String, Object> updates = new HashMap<>();
                updates.put("Groups/" + groupId + "/messages/" + finalMessageKey, messageInfo);

                String lastMessageText = getLastMessageText(messageType, fileName);
                updates.put("Groups/" + groupId + "/lastMessage", lastMessageText);
                updates.put("Groups/" + groupId + "/lastMessageSender", userName);
                updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

                FirebaseDatabase.getInstance().getReference()
                        .updateChildren(updates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Log.d("GroupChatActivity", "File message sent: " + messageType);
                            } else {
                                Toast.makeText(GroupChatActivity.this, "Send error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            }

            @Override
            public void onError(String error) {
                Log.e("GroupChatActivity", "Failed to load user data: " + error);
                sendBasicFileMessage(fileUrl, messageType, fileName, date, finalMessageKey);
            }
        });
    }

    private void sendBasicFileMessage(String fileUrl, String messageType, String fileName, String date, String messageKey) {
        String messageText = getFileMessageText(messageType, fileName);
        String lastMessageText = getLastMessageText(messageType, fileName);

        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("senderName", "User");
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false);
        messageInfo.put("messageType", messageType);
        messageInfo.put("fileUrl", fileUrl);
        messageInfo.put("fileName", fileName);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Groups/" + groupId + "/messages/" + messageKey, messageInfo);
        updates.put("Groups/" + groupId + "/lastMessage", lastMessageText);
        updates.put("Groups/" + groupId + "/lastMessageSender", "User");
        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates);
    }

    private String getFileMessageText(String messageType, String fileName) {
        switch (messageType) {
            case "image":
                return "📷 Photo";
            case "video":
                return "🎥 Video";
            case "file":
                return "📎 File: " + (fileName != null ? fileName : "File");
            default:
                return "📎 File";
        }
    }

    private String getLastMessageText(String messageType, String fileName) {
        switch (messageType) {
            case "image":
                return "📷 Image";
            case "video":
                return "🎥 Video";
            case "file":
                return "📎 File: " + (fileName != null ? fileName : "File");
            default:
                return "📎 File";
        }
    }

    private void checkAllFilesSent() {
        // Проверяем, все ли файлы обработаны
        if (successfullySentFiles >= totalFilesToSend) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    selectedFiles.clear();
                    isSendingFiles = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.sendMessageBtn.setEnabled(true);
                            updateSendButtonState();
                            Toast.makeText(GroupChatActivity.this, "Files sent successfully", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }, 1000);
        }
    }

    private void checkUserRole() {
        userRoleListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String role = snapshot.getValue(String.class);
                    if (role != null) {
                        if ("owner".equals(role)) {
                            isOwner = true;
                            isAdmin = true;
                        } else if ("admin".equals(role)) {
                            isAdmin = true;
                            isOwner = false;
                        } else {
                            isAdmin = false;
                            isOwner = false;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load user role", error.toException());
            }
        };

        groupRef.child("members").child(currentUserId).addValueEventListener(userRoleListener);
    }

    private void openGroupSettings() {
        Intent intent = new Intent(this, GroupSettingsActivity.class);
        intent.putExtra("groupId", groupId);
        intent.putExtra("isAdmin", isAdmin);
        intent.putExtra("isOwner", isOwner);
        startActivity(intent);
    }

    private void initializeViews() {
        binding.groupName.setText(groupName != null ? groupName : "Group");

        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));

        // Создаем адаптер с поддержкой РАЗМЕРА ШРИФТА (используем новый конструктор с Context)
        messageAdapter = new GroupMessageAdapter(messages, groupId, this);
        binding.messagesRv.setAdapter(messageAdapter);

        // Настраиваем обработчик кликов для сообщений
        setupMessageClickListener();

        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });
    }

    // МЕТОД: Настройка обработчика кликов для сообщений
    private void setupMessageClickListener() {
        binding.messagesRv.addOnItemTouchListener(new RecyclerItemClickListener(this, binding.messagesRv, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position >= 0 && position < messages.size()) {
                    GroupMessage message = messages.get(position);
                    handleMessageClick(message);
                }
            }

            @Override
            public void onLongItemClick(View view, int position) {
                // Обработка долгого нажатия (опционально)
            }
        }));
    }

    // КЛАСС: Обработчик кликов для RecyclerView
    public static class RecyclerItemClickListener implements androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
        private OnItemClickListener mListener;
        private android.view.GestureDetector mGestureDetector;

        public interface OnItemClickListener {
            void onItemClick(View view, int position);
            void onLongItemClick(View view, int position);
        }

        public RecyclerItemClickListener(android.content.Context context, final androidx.recyclerview.widget.RecyclerView recyclerView, OnItemClickListener listener) {
            mListener = listener;
            mGestureDetector = new android.view.GestureDetector(context, new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    View child = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (child != null && mListener != null) {
                        mListener.onLongItemClick(child, recyclerView.getChildAdapterPosition(child));
                    }
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(@NonNull androidx.recyclerview.widget.RecyclerView rv, @NonNull MotionEvent e) {
            View childView = rv.findChildViewUnder(e.getX(), e.getY());
            if (childView != null && mListener != null && mGestureDetector.onTouchEvent(e)) {
                mListener.onItemClick(childView, rv.getChildAdapterPosition(childView));
                return true;
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull androidx.recyclerview.widget.RecyclerView rv, @NonNull MotionEvent e) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    }

    // МЕТОД: Обработка кликов на сообщения
    private void handleMessageClick(GroupMessage message) {
        String messageType = message.getMessageType();
        String fileUrl = message.getFileUrl();

        if (fileUrl != null && !fileUrl.isEmpty()) {
            switch (messageType) {
                case "image":
                    openImageFullScreen(message);
                    break;
                case "video":
                    playVideo(message);
                    break;
                case "file":
                    downloadFile(message);
                    break;
                default:
                    // Для текстовых сообщений ничего не делаем
                    break;
            }
        }
    }

    // МЕТОД: Открытие изображения в полноэкранном режиме
    private void openImageFullScreen(GroupMessage message) {
        if (message.getFileUrl() != null && !message.getFileUrl().isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(message.getFileUrl()), "image/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getFileUrl()));
                startActivity(browserIntent);
            }
        } else {
            Toast.makeText(this, "Image not available", Toast.LENGTH_SHORT).show();
        }
    }

    // МЕТОД: Воспроизведение видео
    private void playVideo(GroupMessage message) {
        if (message.getFileUrl() != null && !message.getFileUrl().isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(message.getFileUrl()), "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No video player app found", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Video not available", Toast.LENGTH_SHORT).show();
        }
    }

    // МЕТОД: Скачивание файла
    private void downloadFile(GroupMessage message) {
        if (message.getFileUrl() != null && !message.getFileUrl().isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(message.getFileUrl()));

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "File not available", Toast.LENGTH_SHORT).show();
        }
    }

    // Остальные методы остаются без изменений...
    private void loadGroupInfo() {
        groupInfoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("groupName").getValue(String.class);
                    String image = snapshot.child("groupImage").getValue(String.class);

                    if (name != null) {
                        binding.groupName.setText(name);
                        groupName = name;
                    }

                    if (image != null && !image.isEmpty()) {
                        Glide.with(GroupChatActivity.this)
                                .load(image)
                                .placeholder(R.drawable.artem)
                                .error(R.drawable.artem)
                                .into(binding.groupImage);
                    } else {
                        binding.groupImage.setImageResource(R.drawable.artem);
                    }

                    loadGroupMembers(snapshot);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load group info", error.toException());
            }
        };

        groupRef.addValueEventListener(groupInfoListener);
    }

    private void loadGroupMembers(@NonNull DataSnapshot groupSnapshot) {
        List<String> newGroupMembers = new ArrayList<>();
        DataSnapshot membersSnapshot = groupSnapshot.child("members");

        Log.d("GroupChatActivity", "=== DEBUG MEMBERS STRUCTURE ===");

        if (membersSnapshot.exists()) {
            for (DataSnapshot memberSnapshot : membersSnapshot.getChildren()) {
                String memberKey = memberSnapshot.getKey();
                Object memberValue = memberSnapshot.getValue();

                Log.d("GroupChatActivity", "Member - Key: " + memberKey + ", Value: " + memberValue + ", Value type: " + (memberValue != null ? memberValue.getClass().getSimpleName() : "null"));

                if (memberValue instanceof String) {
                    String memberId = (String) memberValue;
                    if (isValidUserId(memberId)) {
                        newGroupMembers.add(memberId);
                        Log.d("GroupChatActivity", "✅ Added member from value: " + memberId);
                        continue;
                    }
                }

                if (isValidUserId(memberKey)) {
                    newGroupMembers.add(memberKey);
                    Log.d("GroupChatActivity", "✅ Added member from key: " + memberKey);
                    continue;
                }

                Log.d("GroupChatActivity", "❌ Skipped member - Key: " + memberKey + ", Value: " + memberValue);
            }

            tryAlternativeMemberSources(groupSnapshot, newGroupMembers);

            groupMembers = newGroupMembers;
            totalMembersCount = groupMembers.size();
            updateMembersDisplay();

            Log.d("GroupChatActivity", "=== FINAL MEMBERS LIST ===");
            for (String member : groupMembers) {
                Log.d("GroupChatActivity", "Member: " + member);
            }
            Log.d("GroupChatActivity", "Total valid members: " + totalMembersCount);

            if (totalMembersCount > 0) {
                loadOnlineStatus();
            } else {
                onlineMembersCount = 0;
                updateMembersDisplay();
                Log.w("GroupChatActivity", "No valid member IDs found!");
            }
        } else {
            Log.w("GroupChatActivity", "No members found in group");
            groupMembers.clear();
            totalMembersCount = 0;
            updateMembersDisplay();
        }
    }

    private boolean isValidUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        if (userId.matches("\\d+")) {
            return false;
        }

        if (userId.length() < 10) {
            return false;
        }

        return true;
    }

    private void tryAlternativeMemberSources(@NonNull DataSnapshot groupSnapshot, List<String> membersList) {
        DataSnapshot membersListSnapshot = groupSnapshot.child("membersList");
        if (membersListSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found membersList field");
            for (DataSnapshot memberSnapshot : membersListSnapshot.getChildren()) {
                String memberId = memberSnapshot.getValue(String.class);
                if (isValidUserId(memberId) && !membersList.contains(memberId)) {
                    membersList.add(memberId);
                    Log.d("GroupChatActivity", "✅ Added member from membersList: " + memberId);
                }
            }
        }

        DataSnapshot participantsSnapshot = groupSnapshot.child("participants");
        if (participantsSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found participants field");
            for (DataSnapshot participantSnapshot : participantsSnapshot.getChildren()) {
                String participantId = participantSnapshot.getValue(String.class);
                if (isValidUserId(participantId) && !membersList.contains(participantId)) {
                    membersList.add(participantId);
                    Log.d("GroupChatActivity", "✅ Added participant: " + participantId);
                }
            }
        }

        DataSnapshot usersSnapshot = groupSnapshot.child("users");
        if (usersSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found users field");
            for (DataSnapshot userSnapshot : usersSnapshot.getChildren()) {
                String userId = userSnapshot.getValue(String.class);
                if (isValidUserId(userId) && !membersList.contains(userId)) {
                    membersList.add(userId);
                    Log.d("GroupChatActivity", "✅ Added user: " + userId);
                }
            }
        }

        DataSnapshot memberIdsSnapshot = groupSnapshot.child("memberIds");
        if (memberIdsSnapshot.exists()) {
            Log.d("GroupChatActivity", "Found memberIds field");
            for (DataSnapshot memberIdSnapshot : memberIdsSnapshot.getChildren()) {
                String memberId = memberIdSnapshot.getValue(String.class);
                if (isValidUserId(memberId) && !membersList.contains(memberId)) {
                    membersList.add(memberId);
                    Log.d("GroupChatActivity", "✅ Added member from memberIds: " + memberId);
                }
            }
        }
    }

    private void loadOnlineStatus() {
        if (usersListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .removeEventListener(usersListener);
        }

        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot usersSnapshot) {
                onlineMembersCount = 0;

                Log.d("GroupChatActivity", "=== CHECKING ONLINE STATUS FOR " + totalMembersCount + " MEMBERS ===");

                for (String memberId : groupMembers) {
                    DataSnapshot userSnapshot = usersSnapshot.child(memberId);

                    if (userSnapshot.exists()) {
                        Boolean isOnline = userSnapshot.child("isOnline").getValue(Boolean.class);
                        Long lastOnline = userSnapshot.child("lastOnline").getValue(Long.class);

                        Log.d("GroupChatActivity", "User " + memberId + " - isOnline: " + isOnline + ", lastOnline: " + lastOnline);

                        boolean isUserOnline = isOnline != null && isOnline;

                        if (isUserOnline) {
                            onlineMembersCount++;
                            Log.d("GroupChatActivity", "✅ ONLINE: " + memberId);
                        } else {
                            Log.d("GroupChatActivity", "❌ OFFLINE: " + memberId);
                        }
                    } else {
                        Log.d("GroupChatActivity", "🚫 USER NOT FOUND IN DATABASE: " + memberId);
                    }
                }

                Log.d("GroupChatActivity", "RESULT: " + onlineMembersCount + " online out of " + totalMembersCount);
                updateMembersDisplay();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load users data", error.toException());
                onlineMembersCount = 0;
                updateMembersDisplay();
            }
        };

        FirebaseDatabase.getInstance().getReference("Users")
                .addValueEventListener(usersListener);
    }

    private void updateMembersDisplay() {
        runOnUiThread(() -> {
            String membersText = totalMembersCount + " member" + (totalMembersCount != 1 ? "s" : "");
            binding.membersCount.setText(membersText);

            if (onlineMembersCount > 0) {
                String onlineText = onlineMembersCount + " online";
                binding.onlineCount.setText(onlineText);
                binding.onlineCount.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                binding.onlineCount.setText("offline");
                binding.onlineCount.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        });
    }

    private void setupCurrentUserOnlineStatus() {
        if (currentUserId != null) {
            Log.d("GroupChatActivity", "Setting current user online: " + currentUserId);

            HashMap<String, Object> onlineUpdates = new HashMap<>();
            onlineUpdates.put("isOnline", true);
            onlineUpdates.put("lastOnline", System.currentTimeMillis());

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(onlineUpdates)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("GroupChatActivity", "✅ Current user set to online: " + currentUserId);
                        } else {
                            Log.e("GroupChatActivity", "❌ Failed to set current user online", task.getException());
                        }
                    });
        }
    }

    private void loadMessages() {
        if (messagesListener != null) {
            groupRef.child("messages").removeEventListener(messagesListener);
        }

        messagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messages.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String messageId = messageSnapshot.getKey();
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        String text = messageSnapshot.child("text").getValue(String.class);
                        String date = messageSnapshot.child("date").getValue(String.class);
                        String senderName = messageSnapshot.child("senderName").getValue(String.class);
                        String senderAvatar = messageSnapshot.child("senderAvatar").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);
                        String messageType = messageSnapshot.child("messageType").getValue(String.class);
                        String fileUrl = messageSnapshot.child("fileUrl").getValue(String.class);
                        String fileName = messageSnapshot.child("fileName").getValue(String.class);

                        if (messageId != null && ownerId != null && text != null) {
                            GroupMessage message = new GroupMessage(messageId, ownerId, text, date);

                            if (senderName != null && !senderName.isEmpty()) {
                                message.setSenderName(senderName);
                            }

                            if (senderAvatar != null && !senderAvatar.isEmpty()) {
                                message.setSenderAvatar(senderAvatar);
                            }

                            if (messageType != null) {
                                message.setMessageType(messageType);
                            }

                            if (fileUrl != null) {
                                message.setFileUrl(fileUrl);
                            }

                            if (fileName != null) {
                                message.setFileName(fileName);
                            }

                            messages.add(message);

                            if (!ownerId.equals(currentUserId) && (isRead == null || !isRead)) {
                                markSingleMessageAsRead(messageId);
                            }
                        }
                    }
                }

                messageAdapter.notifyDataSetChanged();
                scrollToBottom();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GroupChatActivity", "Failed to load messages", error.toException());
                Toast.makeText(GroupChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        groupRef.child("messages").addValueEventListener(messagesListener);
    }

    private void sendMessage() {
        String messageText = binding.messageEt.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        String date = dateFormat.format(new Date());

        binding.messageEt.setText("");

        final String finalMessageText = messageText;
        final String finalDate = date;

        String messageKey = groupRef.child("messages").push().getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalMessageKey = messageKey;

        loadUserDataWithCustomSettings(currentUserId, new UserDataCallback() {
            @Override
            public void onUserDataLoaded(String userName, String userAvatar) {
                HashMap<String, Object> messageInfo = new HashMap<>();
                messageInfo.put("text", finalMessageText);
                messageInfo.put("ownerId", currentUserId);
                messageInfo.put("senderName", userName);
                messageInfo.put("senderAvatar", userAvatar);
                messageInfo.put("date", finalDate);
                messageInfo.put("timestamp", System.currentTimeMillis());
                messageInfo.put("isRead", false);
                messageInfo.put("messageType", "text");

                HashMap<String, Object> updates = new HashMap<>();
                updates.put("Groups/" + groupId + "/messages/" + finalMessageKey, messageInfo);
                updates.put("Groups/" + groupId + "/lastMessage", finalMessageText);
                updates.put("Groups/" + groupId + "/lastMessageSender", userName);
                updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

                FirebaseDatabase.getInstance().getReference()
                        .updateChildren(updates);
            }

            @Override
            public void onError(String error) {
                Log.e("GroupChatActivity", "Failed to load user data: " + error);
                sendBasicMessage(finalMessageText, finalDate, finalMessageKey);
            }
        });
    }

    private interface UserDataCallback {
        void onUserDataLoaded(String userName, String userAvatar);
        void onError(String error);
    }

    private void loadUserDataWithCustomSettings(String userId, UserDataCallback callback) {
        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot customSnapshot) {
                        final String[] customName = {null};
                        final String[] customAvatar = {null};

                        if (customSnapshot.exists()) {
                            customName[0] = customSnapshot.child("customName").getValue(String.class);
                            customAvatar[0] = customSnapshot.child("customImage").getValue(String.class);
                        }

                        FirebaseDatabase.getInstance().getReference("Users")
                                .child(userId)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                        String userName = "User";
                                        String userAvatar = "";

                                        if (userSnapshot.exists()) {
                                            String originalName = userSnapshot.child("login").getValue(String.class);
                                            if (originalName == null || originalName.trim().isEmpty()) {
                                                String email = userSnapshot.child("email").getValue(String.class);
                                                if (email != null && email.contains("@")) {
                                                    originalName = email.substring(0, email.indexOf("@"));
                                                } else {
                                                    originalName = "User";
                                                }
                                            }

                                            String originalAvatar = userSnapshot.child("profileImage").getValue(String.class);
                                            if (originalAvatar == null) originalAvatar = "";

                                            if (customName[0] != null && !customName[0].isEmpty()) {
                                                userName = customName[0];
                                            } else {
                                                userName = originalName;
                                            }

                                            if (customAvatar[0] != null && !customAvatar[0].isEmpty()) {
                                                userAvatar = customAvatar[0];
                                            } else {
                                                userAvatar = originalAvatar;
                                            }
                                        }

                                        callback.onUserDataLoaded(userName, userAvatar);
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        callback.onError("Failed to load user data: " + error.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onError("Failed to load custom settings: " + error.getMessage());
                    }
                });
    }

    private void sendBasicMessage(String messageText, String date, String messageKey) {
        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("senderName", "User");
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false);
        messageInfo.put("messageType", "text");

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Groups/" + groupId + "/messages/" + messageKey, messageInfo);
        updates.put("Groups/" + groupId + "/lastMessage", messageText);
        updates.put("Groups/" + groupId + "/lastMessageSender", "User");
        updates.put("Groups/" + groupId + "/lastMessageTime", System.currentTimeMillis());

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates);
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    private void markAllMessagesAsRead() {
        if (groupId == null) return;

        groupRef.child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        HashMap<String, Object> updates = new HashMap<>();

                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            if (messageId != null && ownerId != null &&
                                    !ownerId.equals(currentUserId) &&
                                    (isRead == null || !isRead)) {

                                updates.put("Groups/" + groupId + "/messages/" + messageId + "/isRead", true);
                            }
                        }

                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupChatActivity", "Failed to mark messages as read", error.toException());
                    }
                });
    }

    private void markSingleMessageAsRead(String messageId) {
        groupRef.child("messages").child(messageId).child("isRead").setValue(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("GroupChatActivity", "=== ON PAUSE ===");
        if (groupId != null) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("GroupChatActivity", "=== ON DESTROY ===");

        if (messagesListener != null && groupId != null) {
            groupRef.child("messages").removeEventListener(messagesListener);
        }

        if (groupInfoListener != null && groupId != null) {
            groupRef.removeEventListener(groupInfoListener);
        }

        if (usersListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .removeEventListener(usersListener);
        }

        if (userRoleListener != null && groupId != null) {
            groupRef.child("members").child(currentUserId).removeEventListener(userRoleListener);
        }
    }
}