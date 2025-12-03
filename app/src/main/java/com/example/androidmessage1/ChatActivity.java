package com.example.androidmessage1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.example.androidmessage1.databinding.ActivityChatBinding;
import com.example.androidmessage1.message.ChatTimeTracker;
import com.example.androidmessage1.message.Message;
import com.example.androidmessage1.message.MessageAdapter;
import com.example.androidmessage1.message.FontSizeManager;
import com.example.androidmessage1.message.SizeFontActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private String chatId;
    private String otherUserId;
    private MessageAdapter messageAdapter;
    private List<Message> messages = new ArrayList<>();
    private ValueEventListener messagesListener;
    private ValueEventListener userStatusListener;
    private ValueEventListener customSettingsListener;
    private String currentUserId;
    private Handler statusUpdateHandler;
    private Runnable statusUpdateRunnable;
    private DatabaseReference chatRef;

    // Переменные для хранения оригинальных данных
    private String originalUsername = "";
    private String originalProfileImage = "";

    // Константы для выбора файлов
    private static final int PICK_FILE_REQUEST = 1001;
    private static final int FONT_SIZE_SETTINGS_REQUEST = 1002;
    private static final int WALLPAPER_SELECTOR_REQUEST = 1003;

    // Переменные для хранения выбранных файлов
    private List<Uri> selectedFiles = new ArrayList<>();
    private boolean isSendingFiles = false;
    private int totalFilesToSend = 0;
    private int successfullySentFiles = 0;

    // Переменные для обоев чата
    private static final String WALLPAPER_PREFS = "chat_wallpaper_prefs";
    private static final String WALLPAPER_KEY_PREFIX = "wallpaper_";
    private SharedPreferences wallpaperPrefs;

    // Трекер времени в чатах
    private ChatTimeTracker chatTimeTracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("otherUserId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (chatId == null && otherUserId == null) {
            Toast.makeText(this, "Chat data is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Инициализируем SharedPreferences для обоев
        wallpaperPrefs = getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE);

        // Инициализируем трекер времени
        chatTimeTracker = ChatTimeTracker.getInstance(this);

        // Если chatId не передан, но есть otherUserId, находим или создаем чат
        if (chatId == null && otherUserId != null) {
            findOrCreateChat();
            return;
        }

        // Обновляем онлайн статус текущего пользователя
        updateUserOnlineStatus();

        initializeViews();

        if (otherUserId == null) {
            getOtherUserIdFromChat();
        } else {
            loadOtherUserData();
            loadCustomSettings(); // Загружаем кастомные настройки
            loadMessages();
            setupKeyboardBehavior();
            markAllMessagesAsRead();
            startUserStatusTracking();
        }

        // Загружаем обои при создании активности
        loadChatWallpaper();
    }

    // Метод для загрузки обоев чата
    // Метод для загрузки обоев чата с поддержкой SVG
    private void loadChatWallpaper() {
        if (chatId == null) return;

        String wallpaperResourceName = wallpaperPrefs.getString(WALLPAPER_KEY_PREFIX + chatId, null);

        if (wallpaperResourceName != null) {
            // Для SVG файлов
            int wallpaperResId = getResources().getIdentifier(wallpaperResourceName, "drawable", getPackageName());

            if (wallpaperResId != 0) {
                try {
                    // Загружаем SVG файл
                    SVG svg = SVG.getFromResource(this, wallpaperResId);

                    // Создаем Picture из SVG
                    android.graphics.Picture picture = svg.renderToPicture();

                    // Создаем Drawable из Picture
                    android.graphics.drawable.PictureDrawable drawable =
                            new android.graphics.drawable.PictureDrawable(picture);

                    // Устанавливаем фон
                    binding.main.setBackground(drawable);

                } catch (SVGParseException e) {
                    Log.e("ChatActivity", "Error parsing SVG wallpaper: " + wallpaperResourceName, e);
                    // В случае ошибки пробуем загрузить как обычный drawable
                    try {
                        binding.main.setBackgroundResource(wallpaperResId);
                    } catch (Exception ex) {
                        // Если и это не работает, используем белый фон
                        binding.main.setBackgroundColor(getResources().getColor(android.R.color.white));
                    }
                }
            } else {
                // Если ресурс не найден, используем белый фон
                binding.main.setBackgroundColor(getResources().getColor(android.R.color.white));
            }
        } else {
            // Если обои не выбраны, используем белый фон
            binding.main.setBackgroundColor(getResources().getColor(android.R.color.white));
        }
    }

    // Метод для открытия селектора обоев
    private void openWallpaperSelector() {
        if (chatId == null) {
            Toast.makeText(this, "Chat not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(ChatActivity.this, WallpaperSelectorActivity.class);
        intent.putExtra("chatId", chatId);
        startActivityForResult(intent, WALLPAPER_SELECTOR_REQUEST);
    }

    // Метод для настройки кнопки выбора обоев
    private void setupWallpaperButton() {
        // Добавляем кнопку в тулбар или используем долгое нажатие на аватар
        binding.chatUserAvatar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showWallpaperOptionsMenu();
                return true;
            }
        });

        // Или добавляем пункт меню в диалоговое окно
        binding.chatUserName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showWallpaperOptionsMenu();
                return true;
            }
        });
    }

    // Метод для показа меню выбора обоев
    private void showWallpaperOptionsMenu() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Обои чата");
        builder.setItems(new String[]{"Изменить обои", "Удалить обои", "Отмена"}, (dialog, which) -> {
            switch (which) {
                case 0:
                    openWallpaperSelector();
                    break;
                case 1:
                    clearChatWallpaper();
                    break;
                case 2:
                    dialog.dismiss();
                    break;
            }
        });
        builder.show();
    }

    // Метод для очистки обоев
    private void clearChatWallpaper() {
        if (chatId == null) return;

        SharedPreferences.Editor editor = wallpaperPrefs.edit();
        editor.remove(WALLPAPER_KEY_PREFIX + chatId);
        editor.apply();

        // Сбрасываем фон на белый
        binding.main.setBackgroundColor(getResources().getColor(android.R.color.white));

        Toast.makeText(this, "Wallpaper cleared", Toast.LENGTH_SHORT).show();
    }

    // Метод для обновления онлайн статуса
    private void updateUserOnlineStatus() {
        if (currentUserId != null) {
            // Устанавливаем текущего пользователя онлайн
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .setValue(true);

            // Устанавливаем время последней активности
            long currentTime = System.currentTimeMillis();
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

            String currentTimeStr = timeFormat.format(new Date(currentTime));
            String currentDateStr = dateFormat.format(new Date(currentTime));

            HashMap<String, Object> updateData = new HashMap<>();
            updateData.put("lastOnline", currentTime);
            updateData.put("lastOnlineTime", currentTimeStr);
            updateData.put("lastOnlineDate", currentDateStr);

            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .updateChildren(updateData);

            // Устанавливаем слушатель для автоматического установки офлайн статуса при выходе
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .setValue(false);
        }
    }

    private void findOrCreateChat() {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("Chats");

        chatsRef.orderByChild("participants/" + currentUserId).equalTo(true)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String foundChatId = null;

                        // Ищем существующий чат между двумя пользователями
                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            if ((user1 != null && user2 != null) &&
                                    ((user1.equals(currentUserId) && user2.equals(otherUserId)) ||
                                            (user2.equals(currentUserId) && user1.equals(otherUserId)))) {
                                foundChatId = chatSnapshot.getKey();
                                break;
                            }
                        }

                        if (foundChatId != null) {
                            // Чат найден, используем существующий
                            chatId = foundChatId;
                            initializeAfterChatFound();
                        } else {
                            // Чат не найден, создаем новый
                            createNewChat();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Error finding chat", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void createNewChat() {
        chatRef = FirebaseDatabase.getInstance().getReference("Chats").push();
        chatId = chatRef.getKey();

        HashMap<String, Object> chatData = new HashMap<>();
        chatData.put("user1", currentUserId);
        chatData.put("user2", otherUserId);
        chatData.put("LastMessage", "");
        chatData.put("LastMessageTime", System.currentTimeMillis());
        chatData.put("lastMessageTimestamp", System.currentTimeMillis());

        // Создаем структуру участников для удобного поиска
        HashMap<String, Object> participants = new HashMap<>();
        participants.put(currentUserId, true);
        participants.put(otherUserId, true);
        chatData.put("participants", participants);

        chatRef.setValue(chatData).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    // Также обновляем информацию о чатах у пользователей
                    updateUserChatLists();
                    initializeAfterChatFound();
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to create chat", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
    }

    private void updateUserChatLists() {
        // Добавляем информацию о чате для обоих пользователей
        DatabaseReference userChatsRef1 = FirebaseDatabase.getInstance().getReference("UserChats")
                .child(currentUserId)
                .child(chatId);
        userChatsRef1.setValue(true);

        DatabaseReference userChatsRef2 = FirebaseDatabase.getInstance().getReference("UserChats")
                .child(otherUserId)
                .child(chatId);
        userChatsRef2.setValue(true);
    }

    private void initializeAfterChatFound() {
        updateUserOnlineStatus();
        initializeViews();
        loadOtherUserData();
        loadCustomSettings(); // Загружаем кастомные настройки
        loadMessages();
        setupKeyboardBehavior();
        markAllMessagesAsRead();
        startUserStatusTracking();

        // Загружаем обои
        loadChatWallpaper();
    }

    private void initializeViews() {
        binding.messagesRv.setLayoutManager(new LinearLayoutManager(this));

        // Создаем адаптер с поддержкой кликов и контекстом
        messageAdapter = new MessageAdapter(messages, chatId, otherUserId, this);
        binding.messagesRv.setAdapter(messageAdapter);

        // Настраиваем обработчик кликов для сообщений
        setupMessageClickListener();

        messageAdapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                scrollToBottom();
            }
        });

        binding.sendMessageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!selectedFiles.isEmpty()) {
                    sendFiles();
                } else {
                    sendMessage();
                }
            }
        });

        binding.exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitToMainActivity();
            }
        });

        binding.sendVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFilePicker();
            }
        });

        binding.chatUserAvatar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openProfileSettings();
            }
        });

        binding.chatUserName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openProfileSettings();
            }
        });

        // Добавляем кнопку для настроек шрифта
        setupFontSizeButton();

        // Добавляем кнопку/меню для выбора обоев
        setupWallpaperButton();

        // Настройка RecyclerView для превью выбранных файлов
        setupSelectedFilesPreview();
    }

    // Метод для настройки кнопки изменения размера шрифта
    private void setupFontSizeButton() {
        // Можно добавить кнопку в меню или использовать существующую
        // Например, добавим в тулбар или как отдельную кнопку
        // Для примера, добавим обработчик долгого нажатия на заголовок

        binding.chatUserName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openFontSizeSettings();
                return true;
            }
        });

        // Или добавим кнопку в меню настроек чата
        // Создаем меню с пунктом "Font Size"
    }

    // Метод для открытия настроек размера шрифта
    private void openFontSizeSettings() {
        Intent intent = new Intent(ChatActivity.this, SizeFontActivity.class);
        startActivityForResult(intent, FONT_SIZE_SETTINGS_REQUEST);
    }

    // ДОБАВЛЕННЫЙ МЕТОД: Открытие настроек профиля чата
    private void openProfileSettings() {
        if (otherUserId != null && chatId != null) {
            Intent intent = new Intent(ChatActivity.this, ProfileChatActivity.class);
            intent.putExtra("otherUserId", otherUserId);
            intent.putExtra("chatId", chatId);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Chat data not available", Toast.LENGTH_SHORT).show();
        }
    }

    // Метод для открытия ProfileChatActivity (оставлен для совместимости)
    private void openProfileChatActivity() {
        openProfileSettings();
    }

    // ИСПРАВЛЕННЫЙ МЕТОД: Загрузка кастомных настроек из правильного пути
    private void loadCustomSettings() {
        if (otherUserId == null || currentUserId == null) return;

        // Удаляем предыдущий слушатель если есть
        if (customSettingsListener != null) {
            FirebaseDatabase.getInstance().getReference("UserCustomizations")
                    .child(currentUserId)
                    .child("chatContacts")
                    .child(otherUserId)
                    .removeEventListener(customSettingsListener);
        }

        customSettingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String displayName = originalUsername;
                String displayImage = originalProfileImage;

                if (snapshot.exists()) {
                    String customName = snapshot.child("customName").getValue(String.class);
                    String customImage = snapshot.child("customImage").getValue(String.class);

                    // Используем кастомное имя если есть
                    if (customName != null && !customName.isEmpty()) {
                        displayName = customName;
                    }

                    // Используем кастомное фото если есть
                    if (customImage != null && !customImage.isEmpty()) {
                        displayImage = customImage;
                    }
                }

                // Применяем настройки в UI
                updateUserDisplay(displayName, displayImage);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatActivity", "Failed to load custom settings", error.toException());
                // В случае ошибки используем оригинальные данные
                updateUserDisplay(originalUsername, originalProfileImage);
            }
        };

        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(otherUserId)
                .addValueEventListener(customSettingsListener);
    }

    // Метод для обновления отображения пользователя
    private void updateUserDisplay(String displayName, String displayImage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Устанавливаем имя
                if (displayName != null && !displayName.isEmpty()) {
                    binding.chatUserName.setText(displayName);
                } else {
                    binding.chatUserName.setText(originalUsername);
                }

                // Устанавливаем аватарку
                if (displayImage != null && !displayImage.isEmpty()) {
                    Glide.with(ChatActivity.this)
                            .load(displayImage)
                            .placeholder(R.drawable.artem)
                            .error(R.drawable.artem)
                            .into(binding.chatUserAvatar);
                } else if (originalProfileImage != null && !originalProfileImage.isEmpty()) {
                    Glide.with(ChatActivity.this)
                            .load(originalProfileImage)
                            .placeholder(R.drawable.artem)
                            .error(R.drawable.artem)
                            .into(binding.chatUserAvatar);
                } else {
                    binding.chatUserAvatar.setImageResource(R.drawable.artem);
                }
            }
        });
    }

    // МЕТОД: Настройка обработчика кликов для сообщений
    private void setupMessageClickListener() {
        binding.messagesRv.addOnItemTouchListener(new RecyclerItemClickListener(this, binding.messagesRv, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position >= 0 && position < messages.size()) {
                    Message message = messages.get(position);
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
    private void handleMessageClick(Message message) {
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
    private void openImageFullScreen(Message message) {
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
    private void playVideo(Message message) {
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
    private void downloadFile(Message message) {
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
            } else if (requestCode == FONT_SIZE_SETTINGS_REQUEST) {
                // При возвращении из настроек шрифта обновляем сообщения
                if (messageAdapter != null) {
                    messageAdapter.updateFontSize();
                }
            } else if (requestCode == WALLPAPER_SELECTOR_REQUEST) {
                // При возвращении из селектора обоев обновляем фон
                loadChatWallpaper();
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
            showSelectedFilesPreview(); // ПОКАЗЫВАЕМ ПРЕВЬЮ
            Toast.makeText(this, "Selected " + selectedFiles.size() + " files. Press send to upload.", Toast.LENGTH_SHORT).show();
        }
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

    private void sendFiles() {
        if (isSendingFiles) {
            Toast.makeText(this, "Files are already being sent", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (chatId == null) {
            Toast.makeText(this, "Chat not initialized", Toast.LENGTH_SHORT).show();
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
        String fileName = "file_" + System.currentTimeMillis() + "_" + currentUserId;
        StorageReference fileRef = FirebaseStorage.getInstance().getReference()
                .child("chat_files")
                .child(chatId)
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

        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                fileRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        sendFileMessage(uri.toString(), messageType, getFileName(fileUri));
                        successfullySentFiles++;
                        checkAllFilesSent();
                    }
                });
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(ChatActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                Log.e("ChatActivity", "Error getting file name", e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void sendFileMessage(String fileUrl, String messageType, String fileName) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        String date = dateFormat.format(new Date());

        final String currentChatId = this.chatId;

        String messageKey = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .push()
                .getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageText = getFileMessageText(messageType, fileName);

        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("id", messageKey);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false);
        messageInfo.put("messageType", messageType);
        messageInfo.put("fileUrl", fileUrl);
        messageInfo.put("fileName", fileName);
        messageInfo.put("text", messageText);

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .child(messageKey)
                .setValue(messageInfo)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            String lastMessageText = getLastMessageText(messageType, fileName);
                            updateLastMessageInChat(lastMessageText, System.currentTimeMillis());

                            // Отслеживаем отправку файла для статистики
                            trackMessageSentForStatistics(fileName);

                            Log.d("ChatActivity", "File message sent: " + messageType);
                        } else {
                            Toast.makeText(ChatActivity.this, "Send error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void trackMessageSentForStatistics(String fileName) {
        if (otherUserId != null) {
            // Определяем, группа это или обычный чат
            boolean isGroup = false; // Обычный чат
            String chatName = otherUserId; // Используем имя пользователя или имя чата

            // Получаем имя чата из intent или из других данных
            chatName = getIntent().getStringExtra("chatName");
            if (chatName == null) {
                // Если имя не передано, используем ID
                chatName = "Chat " + otherUserId.substring(0, 8);
            }

            chatTimeTracker.trackMessageSent(chatId, chatName, isGroup);
        }
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
                            Toast.makeText(ChatActivity.this, "Files sent successfully", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }, 1000);
        }
    }

    private void startUserStatusTracking() {
        if (otherUserId == null) return;

        // Удаляем предыдущий слушатель, если он есть
        if (userStatusListener != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(otherUserId)
                    .removeEventListener(userStatusListener);
        }

        userStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
                    Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
                    String lastOnlineTime = snapshot.child("lastOnlineTime").getValue(String.class);
                    String lastOnlineDate = snapshot.child("lastOnlineDate").getValue(String.class);

                    updateUserStatusDisplay(isOnline, lastOnline, lastOnlineTime, lastOnlineDate);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatActivity", "Failed to load user status", error.toException());
            }
        };

        FirebaseDatabase.getInstance().getReference("Users")
                .child(otherUserId)
                .addValueEventListener(userStatusListener);

        // Запускаем периодическое обновление статуса
        startPeriodicStatusUpdate();
    }

    private void startPeriodicStatusUpdate() {
        statusUpdateHandler = new Handler();
        statusUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Принудительно обновляем статус каждую минуту для актуальности
                if (otherUserId != null) {
                    FirebaseDatabase.getInstance().getReference("Users")
                            .child(otherUserId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (snapshot.exists()) {
                                        Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
                                        Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
                                        String lastOnlineTime = snapshot.child("lastOnlineTime").getValue(String.class);
                                        String lastOnlineDate = snapshot.child("lastOnlineDate").getValue(String.class);

                                        updateUserStatusDisplay(isOnline, lastOnline, lastOnlineTime, lastOnlineDate);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e("ChatActivity", "Failed to update user status", error.toException());
                                }
                            });
                }
                statusUpdateHandler.postDelayed(this, 60000); // Обновляем каждую минуту
            }
        };
        statusUpdateHandler.post(statusUpdateRunnable);
    }

    private void updateUserStatusDisplay(Boolean isOnline, Long lastOnline, String lastOnlineTime, String lastOnlineDate) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isOnline != null && isOnline) {
                    binding.userStatus.setText("online");
                    binding.userStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else {
                    if (lastOnline != null) {
                        String statusText = formatLastSeen(lastOnline, lastOnlineTime, lastOnlineDate);
                        binding.userStatus.setText(statusText);
                    } else {
                        binding.userStatus.setText("offline");
                    }
                    binding.userStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                }
            }
        });
    }

    private String formatLastSeen(long lastOnlineTimestamp, String lastOnlineTime, String lastOnlineDate) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastOnlineTimestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);

        // Получаем текущую дату и дату последней активности
        java.util.Calendar currentCal = java.util.Calendar.getInstance();
        java.util.Calendar lastOnlineCal = java.util.Calendar.getInstance();
        lastOnlineCal.setTimeInMillis(lastOnlineTimestamp);

        int currentDay = currentCal.get(java.util.Calendar.DAY_OF_YEAR);
        int currentYear = currentCal.get(java.util.Calendar.YEAR);
        int lastOnlineDay = lastOnlineCal.get(java.util.Calendar.DAY_OF_YEAR);
        int lastOnlineYear = lastOnlineCal.get(java.util.Calendar.YEAR);

        // Проверяем, был ли пользователь онлайн вчера
        boolean isYesterday = (currentDay - lastOnlineDay == 1 && currentYear == lastOnlineYear) ||
                (currentDay == 1 && lastOnlineDay >= 365 && currentYear - lastOnlineYear == 1);

        // Проверяем, был ли пользователь онлайн позавчера или раньше
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
            // Для активности старше 2 дней показываем полную дату и время
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy 'at' HH:mm", Locale.getDefault());
            return "was online " + dateFormat.format(new Date(lastOnlineTimestamp));
        } else {
            return "was online " + (lastOnlineTime != null ? lastOnlineTime : "unknown time") + " " + (lastOnlineDate != null ? lastOnlineDate : "");
        }
    }

    // ВАЖНО: Метод для отметки всех сообщений как прочитанных
    private void markAllMessagesAsRead() {
        if (chatId == null || otherUserId == null) {
            Log.e("ChatActivity", "chatId or otherUserId is null");
            return;
        }

        final String currentChatId = this.chatId;
        final String currentOtherUserId = this.otherUserId;

        Log.d("ChatActivity", "Marking all messages as read in chat: " + currentChatId);

        FirebaseDatabase.getInstance().getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        HashMap<String, Object> updates = new HashMap<>();
                        final int[] markedAsRead = {0};

                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String messageId = messageSnapshot.getKey();
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            // Отмечаем как прочитанные сообщения от другого пользователя, которые еще не прочитаны
                            if (messageId != null && ownerId != null &&
                                    ownerId.equals(currentOtherUserId) &&
                                    (isRead == null || !isRead)) {

                                updates.put("Chats/" + currentChatId + "/messages/" + messageId + "/isRead", true);
                                markedAsRead[0]++;
                                Log.d("ChatActivity", "Marking message as read: " + messageId);
                            }
                        }

                        if (!updates.isEmpty()) {
                            FirebaseDatabase.getInstance().getReference()
                                    .updateChildren(updates)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            if (task.isSuccessful()) {
                                                Log.d("ChatActivity", "Successfully marked " + markedAsRead[0] + " messages as read");
                                            } else {
                                                Log.e("ChatActivity", "Failed to mark messages as read", task.getException());
                                            }
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("ChatActivity", "Failed to mark messages as read", error.toException());
                    }
                });
    }

    private void setupKeyboardBehavior() {
        binding.messageEt.postDelayed(new Runnable() {
            @Override
            public void run() {
                binding.messageEt.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(binding.messageEt, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 200);

        final ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private int previousHeight = 0;

            @Override
            public void onGlobalLayout() {
                int heightDiff = binding.getRoot().getRootView().getHeight() - binding.getRoot().getHeight();
                if (Math.abs(heightDiff - previousHeight) > 100) {
                    previousHeight = heightDiff;

                    if (heightDiff > 400) {
                        binding.messagesRv.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                scrollToBottom();
                            }
                        }, 100);
                    }
                }
            }
        };

        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

        binding.messageEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    binding.messagesRv.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scrollToBottom();
                        }
                    }, 200);
                }
            }
        });
    }

    private void scrollToBottom() {
        if (messages.size() > 0) {
            binding.messagesRv.scrollToPosition(messages.size() - 1);
        }
    }

    // ВАЖНО: Исправленный метод отправки сообщения
    private void sendMessage() {
        String messageText = binding.messageEt.getText().toString().trim();
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        String date = dateFormat.format(new Date());

        binding.messageEt.setText("");

        final String currentChatId = this.chatId;

        String messageKey = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .push()
                .getKey();

        if (messageKey == null) {
            Toast.makeText(this, "Error creating message", Toast.LENGTH_SHORT).show();
            return;
        }

        // ВАЖНО: При отправке сообщения устанавливаем isRead = true только для отправителя
        // Для получателя сообщение будет непрочитанным (isRead = false)
        HashMap<String, Object> messageInfo = new HashMap<>();
        messageInfo.put("id", messageKey);
        messageInfo.put("text", messageText);
        messageInfo.put("ownerId", currentUserId);
        messageInfo.put("date", date);
        messageInfo.put("timestamp", System.currentTimeMillis());
        messageInfo.put("isRead", false); // ВАЖНО: по умолчанию сообщение непрочитанное
        messageInfo.put("messageType", "text");

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .child(messageKey)
                .setValue(messageInfo)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            updateLastMessageInChat(messageText, System.currentTimeMillis());

                            // Отслеживаем отправку сообщения для статистики
                            trackMessageSentForStatistics("text message");

                            Log.d("ChatActivity", "Message sent with isRead = false (will be marked as read by receiver)");
                        } else {
                            Toast.makeText(ChatActivity.this, "Send error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void updateLastMessageInChat(String lastMessage, long timestamp) {
        final String currentChatId = this.chatId;

        HashMap<String, Object> updateData = new HashMap<>();
        updateData.put("LastMessage", lastMessage);
        updateData.put("LastMessageTime", timestamp);
        updateData.put("lastMessageTimestamp", timestamp);

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .updateChildren(updateData)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d("ChatActivity", "Last message updated: " + lastMessage);
                        } else {
                            Log.e("ChatActivity", "Failed to update last message", task.getException());
                        }
                    }
                });
    }

    private void loadOtherUserData() {
        if (otherUserId == null) return;

        final String currentOtherUserId = this.otherUserId;

        FirebaseDatabase.getInstance().getReference("Users").child(currentOtherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Сохраняем оригинальные данные
                            originalUsername = snapshot.child("login").getValue(String.class);
                            if (originalUsername == null) {
                                String email = snapshot.child("email").getValue(String.class);
                                if (email != null && email.contains("@")) {
                                    originalUsername = email.substring(0, email.indexOf("@"));
                                } else {
                                    originalUsername = "Unknown User";
                                }
                            }

                            originalProfileImage = snapshot.child("profileImage").getValue(String.class);
                            if (originalProfileImage == null) {
                                originalProfileImage = "";
                            }

                            // Сначала устанавливаем оригинальные данные
                            binding.chatUserName.setText(originalUsername);

                            if (originalProfileImage != null && !originalProfileImage.isEmpty()) {
                                Glide.with(ChatActivity.this)
                                        .load(originalProfileImage)
                                        .placeholder(R.drawable.artem)
                                        .error(R.drawable.artem)
                                        .into(binding.chatUserAvatar);
                            } else {
                                binding.chatUserAvatar.setImageResource(R.drawable.artem);
                            }

                            // Теперь загружаем кастомные настройки (они перезапишут оригинальные если есть)
                            loadCustomSettings();

                            // Загружаем начальный статус
                            Boolean isOnline = snapshot.child("isOnline").getValue(Boolean.class);
                            Long lastOnline = snapshot.child("lastOnline").getValue(Long.class);
                            String lastOnlineTime = snapshot.child("lastOnlineTime").getValue(String.class);
                            String lastOnlineDate = snapshot.child("lastOnlineDate").getValue(String.class);

                            updateUserStatusDisplay(isOnline, lastOnline, lastOnlineTime, lastOnlineDate);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void getOtherUserIdFromChat() {
        final String currentChatId = this.chatId;
        final String currentCurrentUserId = this.currentUserId;

        FirebaseDatabase.getInstance().getReference("Chats").child(currentChatId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String userId1 = snapshot.child("user1").getValue(String.class);
                            String userId2 = snapshot.child("user2").getValue(String.class);

                            if (userId1 != null && userId2 != null) {
                                String newOtherUserId = userId1.equals(currentCurrentUserId) ? userId2 : userId1;
                                otherUserId = newOtherUserId;

                                // Обновляем данные в адаптере
                                if (messageAdapter != null) {
                                    messageAdapter.updateChatData(chatId, otherUserId);
                                }

                                loadOtherUserData();
                                loadMessages();
                                setupKeyboardBehavior();
                                markAllMessagesAsRead();
                                startUserStatusTracking();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ChatActivity.this, "Failed to load chat data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadMessages() {
        if (chatId == null || otherUserId == null) return;

        final String currentChatId = this.chatId;
        final String currentOtherUserId = this.otherUserId;

        if (messagesListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(currentChatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
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
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);
                        String messageType = messageSnapshot.child("messageType").getValue(String.class);
                        String fileUrl = messageSnapshot.child("fileUrl").getValue(String.class);
                        String fileName = messageSnapshot.child("fileName").getValue(String.class);

                        if (messageId != null && ownerId != null && date != null) {
                            Message message = new Message(messageId, ownerId, text, date);
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

                            // Автоматически помечаем входящие сообщения как прочитанные при загрузке
                            if (ownerId.equals(currentOtherUserId) && (isRead == null || !isRead)) {
                                markSingleMessageAsRead(messageId);
                            }
                        }
                    }

                    // Сортируем сообщения по времени
                    Collections.sort(messages, (m1, m2) -> {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
                            Date date1 = sdf.parse(m1.getDate());
                            Date date2 = sdf.parse(m2.getDate());
                            return date1.compareTo(date2);
                        } catch (Exception e) {
                            return 0;
                        }
                    });
                }

                messageAdapter.notifyDataSetChanged();
                scrollToBottom();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
            }
        };

        FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .addValueEventListener(messagesListener);
    }

    private void markSingleMessageAsRead(String messageId) {
        final String currentChatId = this.chatId;

        FirebaseDatabase.getInstance().getReference("Chats")
                .child(currentChatId)
                .child("messages")
                .child(messageId)
                .child("isRead")
                .setValue(true)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d("ChatActivity", "Message marked as read: " + messageId);
                        } else {
                            Log.e("ChatActivity", "Failed to mark message as read: " + messageId);
                        }
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
            // При возвращении в чат обновляем кастомные настройки
            loadCustomSettings();

            // Обновляем размер шрифта при возвращении в чат
            if (messageAdapter != null) {
                messageAdapter.updateFontSize();
            }

            // Обновляем обои при возвращении в чат
            loadChatWallpaper();

            // Начинаем отслеживать время в чате
            if (otherUserId != null) {
                // Определяем, группа это или обычный чат
                boolean isGroup = false; // Обычный чат
                String chatName = otherUserId; // Используем имя пользователя или имя чата

                // Получаем имя чата из intent или из других данных
                chatName = getIntent().getStringExtra("chatName");
                if (chatName == null) {
                    // Если имя не передано, используем ID
                    chatName = "Chat " + otherUserId.substring(0, 8);
                }

                chatTimeTracker.trackChatEnter(chatId, chatName, isGroup);
            }
        }
        // Обновляем онлайн статус при возвращении в приложение
        updateUserOnlineStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }
        // Прекращаем отслеживать время в чате
        chatTimeTracker.trackChatExit();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }
    }

    private void exitToMainActivity() {
        if (chatId != null && otherUserId != null) {
            markAllMessagesAsRead();
        }

        // Прекращаем отслеживать время в чате
        chatTimeTracker.trackChatExit();

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && binding.messageEt.hasFocus()) {
            imm.hideSoftInputFromWindow(binding.messageEt.getWindowToken(), 0);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Очищаем onDisconnect при выходе
        if (currentUserId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .cancel();
        }

        // Очищаем слушатели
        if (messagesListener != null && chatId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(messagesListener);
        }

        if (userStatusListener != null && otherUserId != null) {
            FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(otherUserId)
                    .removeEventListener(userStatusListener);
        }

        if (customSettingsListener != null && otherUserId != null && currentUserId != null) {
            FirebaseDatabase.getInstance().getReference("UserCustomizations")
                    .child(currentUserId)
                    .child("chatContacts")
                    .child(otherUserId)
                    .removeEventListener(customSettingsListener);
        }

        if (statusUpdateHandler != null && statusUpdateRunnable != null) {
            statusUpdateHandler.removeCallbacks(statusUpdateRunnable);
        }
    }
}