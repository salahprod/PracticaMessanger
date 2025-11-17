package com.example.androidmessage1.bottomnav.profile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.LoginActivity;
import com.example.androidmessage1.databinding.FragmentProfileBinding;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private Uri filePath;
    private static final String TAG = "ProfileFragment";

    private ActivityResultLauncher<Intent> pickImageActivityResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pickImageActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK &&
                                result.getData() != null &&
                                result.getData().getData() != null) {
                            filePath = result.getData().getData();
                            try {
                                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                                        requireContext().getContentResolver(), filePath);
                                binding.profileImageView.setImageBitmap(bitmap);
                                uploadImageSimple(); // запускаем загрузку
                            } catch (IOException e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(getContext(), LoginActivity.class));
            requireActivity().finish();
            return binding.getRoot();
        }

        loadUserInfo();

        binding.profileImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImage();
            }
        });

        binding.logoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(getContext(), LoginActivity.class));
                requireActivity().finish();
            }
        });

        return binding.getRoot();
    }

    private void loadUserInfo() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;

                        if (snapshot.child("username").exists()) {
                            String username = snapshot.child("username").getValue(String.class);
                            if (username != null) binding.usernameTv.setText(username);
                        }

                        if (snapshot.child("profileImage").exists()) {
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage != null && !profileImage.isEmpty()) {
                                Glide.with(requireContext())
                                        .load(profileImage)
                                        .into(binding.profileImageView);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database error: " + error.getMessage());
                    }
                });
    }

    private void selectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        pickImageActivityResultLauncher.launch(Intent.createChooser(intent, "Select Image"));
    }

    private void uploadImageSimple() {
        if (filePath == null) {
            Toast.makeText(getContext(), "Ошибка: файл не выбран", Toast.LENGTH_SHORT).show();
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Пользователь не аутентифицирован", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // фиксированное имя файла, чтобы всегда перезаписывать одно и то же место
        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("images")
                .child(uid)
                .child("profile.jpg");

        Toast.makeText(getContext(), "Начало загрузки...", Toast.LENGTH_SHORT).show();

        UploadTask uploadTask = storageRef.putFile(filePath);

        // Надёжный способ: после успешной загрузки получить downloadUrl через continueWithTask
        uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
            @Override
            public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                if (!task.isSuccessful()) {
                    throw task.getException() != null ? task.getException() : new Exception("Upload failed");
                }
                // request download url
                return storageRef.getDownloadUrl();
            }
        }).addOnCompleteListener(new OnCompleteListener<Uri>() {
            @Override
            public void onComplete(@NonNull Task<Uri> task) {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    if (downloadUri != null) {
                        String downloadUrl = downloadUri.toString();
                        Log.d(TAG, "Download URL obtained: " + downloadUrl);
                        saveToDatabaseSimple(uid, downloadUrl);
                    } else {
                        Log.e(TAG, "Download URI is null");
                        Toast.makeText(getContext(), "Не удалось получить ссылку на файл", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Exception e = task.getException();
                    Log.e(TAG, "Failed to get download URL: " + (e != null ? e.getMessage() : "unknown"));
                    Toast.makeText(getContext(), "Ошибка получения ссылки: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "Upload failed: " + e.getMessage());
                Toast.makeText(getContext(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    private String getFileExtension(Uri uri) {
        try {
            String mimeType = requireContext().getContentResolver().getType(uri);
            if (mimeType != null) {
                switch (mimeType) {
                    case "image/jpeg":
                    case "image/jpg":
                        return "jpg";
                    case "image/png":
                        return "png";
                    case "image/webp":
                        return "webp";
                    case "image/gif":
                        return "gif";
                    case "image/bmp":
                        return "bmp";
                    default:
                        return "jpg"; // по умолчанию
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file extension: " + e.getMessage());
        }
        return "jpg"; // расширение по умолчанию
    }

    private void saveToDatabaseSimple(String uid, String imageUrl) {
        FirebaseDatabase.getInstance()
                .getReference()
                .child("Users")
                .child(uid)
                .child("profileImage")
                .setValue(imageUrl)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "Profile image updated in database");
                        Toast.makeText(getContext(), "Фото профиля обновлено!", Toast.LENGTH_SHORT).show();

                        // Обновляем изображение в UI
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .into(binding.profileImageView);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Failed to update database: " + e.getMessage());
                        Toast.makeText(getContext(), "Ошибка сохранения в базе", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
