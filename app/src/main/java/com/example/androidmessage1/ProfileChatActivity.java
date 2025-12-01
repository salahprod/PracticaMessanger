package com.example.androidmessage1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

import java.util.HashMap;
import java.util.UUID;

public class ProfileChatActivity extends AppCompatActivity {

    private ImageView profileImage;
    private EditText userNameEditText;
    private TextView emailTextView; // Новое поле для email
    private ImageButton exitBtn;
    private String otherUserId;
    private String currentUserId;
    private DatabaseReference userCustomizationsRef;
    private StorageReference storageReference;
    private static final int PICK_IMAGE_REQUEST = 1;
    private String originalUsername = "";
    private String originalProfileImage = "";
    private String originalEmail = ""; // Добавляем поле для email
    private boolean isDataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_chat);

        // Получаем данные из Intent
        Intent intent = getIntent();
        if (intent != null) {
            otherUserId = intent.getStringExtra("otherUserId");
        }

        if (otherUserId == null) {
            Toast.makeText(this, "Error: User ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // ИСПРАВЛЕННЫЙ ПУТЬ: Используем правильный путь для кастомных настроек
        userCustomizationsRef = FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(otherUserId);

        storageReference = FirebaseStorage.getInstance().getReference();

        initializeViews();
        loadUserData();
    }

    private void initializeViews() {
        profileImage = findViewById(R.id.profileImage);
        userNameEditText = findViewById(R.id.userNameEditText);
        emailTextView = findViewById(R.id.rd6cte99r4eh); // Находим TextView для email по id
        exitBtn = findViewById(R.id.exit_btn);

        // Клик на аватарку для выбора фото
        profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImageChooser();
            }
        });

        // Сохраняем изменения при редактировании имени
        userNameEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus && isDataLoaded) {
                    saveCustomizations();
                }
            }
        });

        // Кнопка выхода
        exitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCustomizations();
                finish();
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
            Uri imageUri = data.getData();

            // Показываем выбранное изображение сразу
            Glide.with(this)
                    .load(imageUri)
                    .circleCrop()
                    .into(profileImage);

            // Загружаем изображение в Firebase Storage
            uploadImageToFirebase(imageUri);
        }
    }

    private void uploadImageToFirebase(Uri imageUri) {
        String filename = UUID.randomUUID().toString();
        StorageReference fileRef = storageReference.child("custom_avatars/" + currentUserId + "/" + otherUserId + "/" + filename);

        fileRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        saveCustomImage(imageUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ProfileChatActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveCustomImage(String imageUrl) {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("customImage", imageUrl);

        userCustomizationsRef.updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(ProfileChatActivity.this, "Avatar updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileChatActivity.this, "Failed to save avatar", Toast.LENGTH_SHORT).show();
                    }
                });
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
                            originalEmail = snapshot.child("email").getValue(String.class); // Получаем email

                            // Если login не найден, пробуем username
                            if (originalUsername == null) {
                                originalUsername = snapshot.child("username").getValue(String.class);
                            }

                            // Если все еще нет имени, используем часть email
                            if (originalUsername == null && originalEmail != null && originalEmail.contains("@")) {
                                originalUsername = originalEmail.substring(0, originalEmail.indexOf("@"));
                            } else if (originalUsername == null) {
                                originalUsername = "User";
                            }

                            // Устанавливаем email в TextView
                            if (originalEmail != null && !originalEmail.isEmpty()) {
                                emailTextView.setText(originalEmail);
                            } else {
                                emailTextView.setText("No email available");
                            }

                            // Загружаем кастомные настройки
                            loadCustomizations();
                        } else {
                            Toast.makeText(ProfileChatActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ProfileChatActivity.this, "Failed to load user data", Toast.LENGTH_SHORT).show();
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

                // Устанавливаем email (email всегда оригинальный, не меняется кастомно)
                if (originalEmail != null && !originalEmail.isEmpty()) {
                    emailTextView.setText(originalEmail);
                }

                // Устанавливаем аватар
                if (displayImage != null && !displayImage.isEmpty()) {
                    Glide.with(ProfileChatActivity.this)
                            .load(displayImage)
                            .circleCrop()
                            .placeholder(R.drawable.artem)
                            .into(profileImage);
                } else {
                    profileImage.setImageResource(R.drawable.artem);
                }

                isDataLoaded = true;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileChatActivity.this, "Failed to load customizations", Toast.LENGTH_SHORT).show();
                isDataLoaded = true;
            }
        });
    }

    private void saveCustomizations() {
        if (!isDataLoaded) return;

        String customName = userNameEditText.getText().toString().trim();

        // Если имя пустое или равно оригинальному, удаляем кастомное имя
        if (TextUtils.isEmpty(customName) || customName.equals(originalUsername)) {
            // Удаляем кастомное имя, но сохраняем структуру
            userCustomizationsRef.child("customName").removeValue();
        } else {
            // Сохраняем кастомное имя
            HashMap<String, Object> updates = new HashMap<>();
            updates.put("customName", customName);

            // Убедимся, что документ существует
            userCustomizationsRef.updateChildren(updates);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCustomizations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveCustomizations();
    }
}