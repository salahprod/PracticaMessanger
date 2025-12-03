package com.example.androidmessage1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.HashMap;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

public class ProfileChatActivity extends AppCompatActivity {

    private CircleImageView profileImage;
    private EditText userNameEditText;
    private ImageButton exitBtn;
    private String otherUserId;
    private String chatId;
    private String currentUserId;
    private DatabaseReference userCustomizationsRef;
    private StorageReference storageReference;
    private static final int PICK_IMAGE_REQUEST = 1;
    private String originalUsername = "";
    private String originalProfileImage = "";
    private boolean isDataLoaded = false;
    private boolean isImageChanged = false;
    private Uri selectedImageUri;
    private boolean isSaving = false;

    private static final String TAG = "ProfileChatActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_chat);

        Log.d(TAG, "ProfileChatActivity created");

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            otherUserId = intent.getStringExtra("otherUserId");
            chatId = intent.getStringExtra("chatId");
            Log.d(TAG, "Received otherUserId: " + otherUserId + ", chatId: " + chatId);
        }

        if (otherUserId == null) {
            Toast.makeText(this, "Ошибка: ID пользователя не найден", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "otherUserId is null");
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (currentUserId == null) {
            Toast.makeText(this, "Ошибка: Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "currentUserId is null");
            finish();
            return;
        }

        Log.d(TAG, "Current user ID: " + currentUserId + ", Other user ID: " + otherUserId);

        // Используем правильный путь для кастомных настроек
        userCustomizationsRef = FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(otherUserId);

        storageReference = FirebaseStorage.getInstance().getReference();

        initializeViews();
        loadUserData();

        // Настраиваем обработчик системной кнопки назад с использованием OnBackPressedDispatcher
        setupOnBackPressedCallback();
    }

    private void initializeViews() {
        profileImage = findViewById(R.id.profileImage);
        userNameEditText = findViewById(R.id.userNameEditText);
        exitBtn = findViewById(R.id.exit_btn);

        // Устанавливаем дефолтное изображение на случай если загрузка не удастся
        profileImage.setImageResource(R.drawable.artem);

        // Клик на аватарку для выбора фото
        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Profile image clicked");
                openImageChooser();
            }
        });

        // Кнопка выхода - сохраняет изменения и возвращает в чат
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Exit button clicked");
                saveAllChangesAndExit();
            }
        });
    }

    private void setupOnBackPressedCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.d(TAG, "Back button pressed");
                // При нажатии системной кнопки назад также сохраняем изменения
                saveAllChangesAndExit();
            }
        });
    }

    private void openImageChooser() {
        try {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Выберите изображение"), PICK_IMAGE_REQUEST);
            Log.d(TAG, "Image chooser opened");
        } catch (Exception e) {
            Log.e(TAG, "Error opening image chooser: " + e.getMessage());
            Toast.makeText(this, "Ошибка при выборе изображения", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult - requestCode: " + requestCode + ", resultCode: " + resultCode);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                selectedImageUri = data.getData();
                isImageChanged = true;

                Log.d(TAG, "Image selected, URI: " + selectedImageUri);

                try {
                    // Показываем выбранное изображение сразу с использованием try-catch
                    Glide.with(this)
                            .load(selectedImageUri)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .placeholder(R.drawable.artem)
                            .error(R.drawable.artem)
                            .into(profileImage);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading selected image: " + e.getMessage());
                    profileImage.setImageResource(R.drawable.artem);
                }
            } else {
                Log.d(TAG, "No image data received");
                Toast.makeText(this, "Не удалось получить изображение", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "Image selection cancelled");
        }
    }

    private void loadUserData() {
        Log.d(TAG, "Loading user data for: " + otherUserId);

        // Сначала устанавливаем дефолтные значения
        originalUsername = "Пользователь";
        userNameEditText.setText(originalUsername);

        // Загружаем оригинальные данные пользователя
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(otherUserId);
        Log.d(TAG, "User reference: " + userRef.toString());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "User data snapshot exists: " + snapshot.exists());

                if (snapshot.exists()) {
                    // Получаем оригинальное имя пользователя
                    originalUsername = snapshot.child("login").getValue(String.class);
                    if (originalUsername == null || originalUsername.isEmpty()) {
                        originalUsername = snapshot.child("username").getValue(String.class);
                    }

                    // Если все еще нет имени, используем часть email
                    if (originalUsername == null || originalUsername.isEmpty()) {
                        String email = snapshot.child("email").getValue(String.class);
                        if (email != null && email.contains("@")) {
                            originalUsername = email.substring(0, email.indexOf("@"));
                        }
                    }

                    if (originalUsername == null || originalUsername.isEmpty()) {
                        originalUsername = "Пользователь";
                    }

                    // Получаем оригинальное изображение профиля
                    originalProfileImage = snapshot.child("profileImage").getValue(String.class);

                    Log.d(TAG, "Original username: " + originalUsername + ", profileImage: " + originalProfileImage);

                    // Загружаем кастомные настройки
                    loadCustomizations();
                } else {
                    Log.w(TAG, "User not found in database");
                    Toast.makeText(ProfileChatActivity.this, "Пользователь не найден", Toast.LENGTH_SHORT).show();

                    // Все равно загружаем кастомные настройки, если они есть
                    loadCustomizations();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load user data: " + error.getMessage());
                Toast.makeText(ProfileChatActivity.this, "Ошибка загрузки данных пользователя", Toast.LENGTH_SHORT).show();

                // Все равно загружаем кастомные настройки
                loadCustomizations();
            }
        });
    }

    private void loadCustomizations() {
        Log.d(TAG, "Loading customizations from: " + userCustomizationsRef.toString());

        userCustomizationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Customizations snapshot exists: " + snapshot.exists());

                String displayName = originalUsername;
                String displayImage = originalProfileImage;

                if (snapshot.exists()) {
                    // Загружаем кастомные настройки
                    String customName = snapshot.child("customName").getValue(String.class);
                    String customImage = snapshot.child("customImage").getValue(String.class);

                    Log.d(TAG, "Custom name: " + customName + ", custom image: " + customImage);

                    // Устанавливаем кастомное имя если есть
                    if (customName != null && !customName.isEmpty()) {
                        displayName = customName;
                    }

                    // Устанавливаем кастомное фото если есть
                    if (customImage != null && !customImage.isEmpty()) {
                        displayImage = customImage;
                    }
                }

                // Устанавливаем данные в UI
                userNameEditText.setText(displayName);
                Log.d(TAG, "Setting display name: " + displayName);

                // Устанавливаем аватар
                if (displayImage != null && !displayImage.isEmpty()) {
                    Log.d(TAG, "Loading profile image: " + displayImage);
                    try {
                        Glide.with(ProfileChatActivity.this)
                                .load(displayImage)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .placeholder(R.drawable.artem)
                                .error(R.drawable.artem)
                                .into(profileImage);
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading profile image: " + e.getMessage());
                        profileImage.setImageResource(R.drawable.artem);
                    }
                } else {
                    Log.d(TAG, "No profile image to load, using default");
                    profileImage.setImageResource(R.drawable.artem);
                }

                isDataLoaded = true;
                Log.d(TAG, "Data loaded successfully");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load customizations: " + error.getMessage());
                Toast.makeText(ProfileChatActivity.this, "Ошибка загрузки настроек", Toast.LENGTH_SHORT).show();

                // Устанавливаем оригинальные данные в случае ошибки
                userNameEditText.setText(originalUsername);
                profileImage.setImageResource(R.drawable.artem);

                isDataLoaded = true;
                Log.d(TAG, "Data loaded with fallback");
            }
        });
    }

    private void saveAllChangesAndExit() {
        Log.d(TAG, "saveAllChangesAndExit called, isSaving: " + isSaving + ", isDataLoaded: " + isDataLoaded);

        if (isSaving) {
            Log.d(TAG, "Already saving, skipping");
            // Уже сохраняем, предотвращаем двойное сохранение
            return;
        }

        if (!isDataLoaded) {
            Log.d(TAG, "Data not loaded yet, exiting");
            // Если данные еще не загружены, просто выходим
            finish();
            return;
        }

        isSaving = true;
        Log.d(TAG, "Starting save process");

        // Сохраняем изменения имени
        saveCustomName();

        // Если было изменено изображение, загружаем его
        if (isImageChanged && selectedImageUri != null) {
            Log.d(TAG, "Image changed, uploading...");
            uploadImageToFirebase(selectedImageUri);
        } else {
            Log.d(TAG, "No image changes, finishing");
            // Если изображение не менялось, просто выходим
            Toast.makeText(this, "Изменения сохранены", Toast.LENGTH_SHORT).show();
            isSaving = false;
            finish();
        }
    }

    private void saveCustomName() {
        String customName = userNameEditText.getText().toString().trim();
        Log.d(TAG, "Saving custom name: " + customName + ", original: " + originalUsername);

        // Если имя пустое, устанавливаем оригинальное имя
        if (TextUtils.isEmpty(customName)) {
            customName = originalUsername;
            userNameEditText.setText(customName);
            Log.d(TAG, "Name was empty, using original: " + customName);
        }

        // Если имя равно оригинальному, удаляем кастомное имя
        if (customName.equals(originalUsername)) {
            Log.d(TAG, "Name is same as original, removing custom name");
            // Удаляем только кастомное имя
            userCustomizationsRef.child("customName").removeValue()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Custom name removed successfully");
                        } else {
                            Log.e(TAG, "Failed to remove custom name", task.getException());
                        }
                    });
        } else {
            Log.d(TAG, "Saving custom name to database");
            // Сохраняем кастомное имя
            HashMap<String, Object> updates = new HashMap<>();
            updates.put("customName", customName);

            userCustomizationsRef.updateChildren(updates)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Custom name saved successfully");
                        } else {
                            Log.e(TAG, "Failed to save custom name", task.getException());
                        }
                    });
        }
    }

    private void uploadImageToFirebase(Uri imageUri) {
        Log.d(TAG, "Uploading image to Firebase, URI: " + imageUri);

        // Показываем уведомление о загрузке
        Toast.makeText(this, "Сохранение изображения...", Toast.LENGTH_SHORT).show();

        String filename = "custom_avatar_" + UUID.randomUUID().toString() + ".jpg";
        String storagePath = "custom_avatars/" + currentUserId + "/" + otherUserId + "/" + filename;
        StorageReference fileRef = storageReference.child(storagePath);

        Log.d(TAG, "Storage path: " + storagePath);

        UploadTask uploadTask = fileRef.putFile(imageUri);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            Log.d(TAG, "Image uploaded successfully, getting download URL");

            fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String imageUrl = uri.toString();
                Log.d(TAG, "Download URL obtained: " + imageUrl);
                saveCustomImage(imageUrl);
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed to get download URL: " + e.getMessage());
                Toast.makeText(ProfileChatActivity.this, "Ошибка получения URL изображения", Toast.LENGTH_SHORT).show();
                isSaving = false;
                finish();
            });
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to upload image: " + e.getMessage());
            Toast.makeText(ProfileChatActivity.this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
            isSaving = false;
            finish();
        }).addOnProgressListener(snapshot -> {
            double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
            Log.d(TAG, "Upload progress: " + progress + "%");
        });
    }

    private void saveCustomImage(String imageUrl) {
        Log.d(TAG, "Saving custom image URL: " + imageUrl);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("customImage", imageUrl);

        // Добавляем имя, если оно было изменено
        String customName = userNameEditText.getText().toString().trim();
        if (!TextUtils.isEmpty(customName) && !customName.equals(originalUsername)) {
            updates.put("customName", customName);
            Log.d(TAG, "Also saving custom name: " + customName);
        }

        userCustomizationsRef.updateChildren(updates)
                .addOnCompleteListener(task -> {
                    isSaving = false;
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Image and name saved successfully");
                        Toast.makeText(ProfileChatActivity.this, "Изменения сохранены", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "Failed to save image and name", task.getException());
                        Toast.makeText(ProfileChatActivity.this, "Ошибка сохранения изменений", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    isSaving = false;
                    Log.e(TAG, "Failed to save image and name: " + e.getMessage());
                    Toast.makeText(ProfileChatActivity.this, "Ошибка сохранения изменений", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ProfileChatActivity destroyed");
    }
}