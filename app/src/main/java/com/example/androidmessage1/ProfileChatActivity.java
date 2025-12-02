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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_chat);

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            otherUserId = intent.getStringExtra("otherUserId");
            chatId = intent.getStringExtra("chatId");
        }

        if (otherUserId == null) {
            Toast.makeText(this, "Error: User ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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

        // Клик на аватарку для выбора фото
        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageChooser();
            }
        });

        // Кнопка выхода - сохраняет изменения и возвращает в чат
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAllChangesAndExit();
            }
        });
    }

    private void setupOnBackPressedCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // При нажатии системной кнопки назад также сохраняем изменения
                saveAllChangesAndExit();
            }
        });
    }

    private void openImageChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            isImageChanged = true;

            // Показываем выбранное изображение сразу
            Glide.with(this)
                    .load(selectedImageUri)
                    .placeholder(R.drawable.artem)
                    .error(R.drawable.artem)
                    .into(profileImage);
        }
    }

    private void loadUserData() {
        // Загружаем оригинальные данные пользователя
        FirebaseDatabase.getInstance().getReference("Users")
                .child(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Получаем оригинальное имя пользователя
                            originalUsername = snapshot.child("login").getValue(String.class);
                            originalProfileImage = snapshot.child("profileImage").getValue(String.class);

                            // Если login не найден, пробуем username
                            if (originalUsername == null) {
                                originalUsername = snapshot.child("username").getValue(String.class);
                            }

                            // Если все еще нет имени, используем часть email
                            if (originalUsername == null) {
                                String email = snapshot.child("email").getValue(String.class);
                                if (email != null && email.contains("@")) {
                                    originalUsername = email.substring(0, email.indexOf("@"));
                                } else {
                                    originalUsername = "User";
                                }
                            }

                            // Загружаем кастомные настройки
                            loadCustomizations();
                        } else {
                            Toast.makeText(ProfileChatActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ProfileChatActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void loadCustomizations() {
        userCustomizationsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String displayName = originalUsername;
                String displayImage = originalProfileImage;

                if (snapshot.exists()) {
                    // Загружаем кастомные настройки
                    String customName = snapshot.child("customName").getValue(String.class);
                    String customImage = snapshot.child("customImage").getValue(String.class);

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

                // Устанавливаем аватар
                if (displayImage != null && !displayImage.isEmpty()) {
                    Glide.with(ProfileChatActivity.this)
                            .load(displayImage)
                            .placeholder(R.drawable.artem)
                            .error(R.drawable.artem)
                            .into(profileImage);
                }

                isDataLoaded = true;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileChatActivity.this, "Failed to load customizations", Toast.LENGTH_SHORT).show();
                isDataLoaded = true;

                // Устанавливаем оригинальные данные в случае ошибки
                userNameEditText.setText(originalUsername);

                if (originalProfileImage != null && !originalProfileImage.isEmpty()) {
                    Glide.with(ProfileChatActivity.this)
                            .load(originalProfileImage)
                            .placeholder(R.drawable.artem)
                            .error(R.drawable.artem)
                            .into(profileImage);
                }
            }
        });
    }

    private void saveAllChangesAndExit() {
        if (isSaving) {
            // Уже сохраняем, предотвращаем двойное сохранение
            return;
        }

        if (!isDataLoaded) {
            // Если данные еще не загружены, просто выходим
            finish();
            return;
        }

        isSaving = true;

        // Сохраняем изменения имени
        saveCustomName();

        // Если было изменено изображение, загружаем его
        if (isImageChanged && selectedImageUri != null) {
            uploadImageToFirebase(selectedImageUri);
        } else {
            // Если изображение не менялось, просто выходим
            Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void saveCustomName() {
        String customName = userNameEditText.getText().toString().trim();

        // Если имя пустое, устанавливаем оригинальное имя
        if (TextUtils.isEmpty(customName)) {
            customName = originalUsername;
            userNameEditText.setText(customName);
        }

        // Если имя равно оригинальному, удаляем кастомное имя
        if (customName.equals(originalUsername)) {
            // Удаляем только кастомное имя
            userCustomizationsRef.child("customName").removeValue();
        } else {
            // Сохраняем кастомное имя
            HashMap<String, Object> updates = new HashMap<>();
            updates.put("customName", customName);

            userCustomizationsRef.updateChildren(updates)
                    .addOnCompleteListener(task -> {
                        // Логируем, но не показываем Toast здесь, чтобы не дублировать
                        if (!task.isSuccessful()) {
                            Log.e("ProfileChat", "Failed to save custom name", task.getException());
                        }
                    });
        }
    }

    private void uploadImageToFirebase(Uri imageUri) {
        // Показываем уведомление о загрузке
        Toast.makeText(this, "Saving image...", Toast.LENGTH_SHORT).show();

        String filename = "custom_avatar_" + UUID.randomUUID().toString() + ".jpg";
        StorageReference fileRef = storageReference.child("custom_avatars/" + currentUserId + "/" + otherUserId + "/" + filename);

        UploadTask uploadTask = fileRef.putFile(imageUri);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String imageUrl = uri.toString();
                saveCustomImage(imageUrl);
            }).addOnFailureListener(e -> {
                Toast.makeText(ProfileChatActivity.this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                isSaving = false;
                finish();
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(ProfileChatActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
            isSaving = false;
            finish();
        });
    }

    private void saveCustomImage(String imageUrl) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("customImage", imageUrl);

        // Добавляем имя, если оно было изменено
        String customName = userNameEditText.getText().toString().trim();
        if (!TextUtils.isEmpty(customName) && !customName.equals(originalUsername)) {
            updates.put("customName", customName);
        }

        userCustomizationsRef.updateChildren(updates)
                .addOnCompleteListener(task -> {
                    isSaving = false;
                    if (task.isSuccessful()) {
                        Toast.makeText(ProfileChatActivity.this, "Changes saved", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileChatActivity.this, "Failed to save changes", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    isSaving = false;
                    Toast.makeText(ProfileChatActivity.this, "Failed to save changes", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // Не переопределяем onBackPressed, используем OnBackPressedDispatcher
}