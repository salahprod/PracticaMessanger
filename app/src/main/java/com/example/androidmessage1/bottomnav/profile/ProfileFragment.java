package com.example.androidmessage1.bottomnav.profile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.androidmessage1.R;
import com.example.androidmessage1.SettingsMainActivity;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

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

                        if (binding == null) return;  // фрагмент уничтожен

                        if (result.getResultCode() == Activity.RESULT_OK &&
                                result.getData() != null &&
                                result.getData().getData() != null) {

                            filePath = result.getData().getData();

                            // ✔ Больше НЕ используем getBitmap — он вызывал вылет
                            // Просто ставим выбранное фото через Glide
                            Glide.with(requireContext())
                                    .load(filePath)
                                    .placeholder(R.drawable.artem)
                                    .into(binding.profileImage);

                            uploadImageSimple();
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

        binding.profileImage.setOnClickListener(v -> selectImage());

        binding.logoutBtn.setOnClickListener(v -> logoutUser());

        binding.userNameEditText.setOnClickListener(v -> showEditNameDialog());

        binding.editNameBtn.setOnClickListener(v -> showEditNameDialog());

        binding.settingsBtn.setOnClickListener(v -> openSettingsActivity());

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

                        if (!snapshot.exists() || binding == null) return;

                        String username = null;
                        if (snapshot.child("login").exists()) username = snapshot.child("login").getValue(String.class);
                        else if (snapshot.child("username").exists()) username = snapshot.child("username").getValue(String.class);

                        if (username != null && !username.isEmpty())
                            binding.userNameEditText.setText(username);
                        else {
                            String email = snapshot.child("email").getValue(String.class);
                            if (email != null && email.contains("@"))
                                binding.userNameEditText.setText(email.substring(0, email.indexOf("@")));
                            else binding.userNameEditText.setText("User");
                        }

                        String email = snapshot.child("email").getValue(String.class);
                        if (email != null && !email.isEmpty())
                            binding.rd6cte99r4eh.setText(email);
                        else {
                            String authEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                            if (authEmail != null) binding.rd6cte99r4eh.setText(authEmail);
                            else binding.rd6cte99r4eh.setText("No email provided");
                        }

                        if (snapshot.child("profileImage").exists()) {
                            String profileImage = snapshot.child("profileImage").getValue(String.class);
                            if (profileImage != null && !profileImage.isEmpty()) {
                                Glide.with(requireContext())
                                        .load(profileImage)
                                        .placeholder(R.drawable.artem)
                                        .error(R.drawable.artem)
                                        .into(binding.profileImage);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Database error: " + error.getMessage());
                    }
                });
    }

    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Edit Name");

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(binding.userNameEditText.getText().toString());
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) updateUserName(newName);
            else Toast.makeText(getContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateUserName(String newName) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("login", newName);

        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child(userId)
                .updateChildren(updates)
                .addOnSuccessListener(unused -> {
                    if (binding != null)
                        binding.userNameEditText.setText(newName);

                    Toast.makeText(getContext(), "Name updated successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(getContext(), SettingsMainActivity.class);
        startActivity(intent);
    }

    private void logoutUser() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (currentUserId != null) {

            Toast.makeText(getContext(), "Logging out...", Toast.LENGTH_SHORT).show();

            setUserOffline(currentUserId);

            FirebaseAuth.getInstance().signOut();
        }

        navigateToLogin();
    }

    private void setUserOffline(String userId) {
        long currentTime = System.currentTimeMillis();

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("isOnline", false);
        updates.put("lastOnline", currentTime);
        updates.put("lastOnlineTime", timeFormat.format(new Date()));
        updates.put("lastOnlineDate", dateFormat.format(new Date()));

        cancelAllOnDisconnect(userId);

        FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .updateChildren(updates);
    }

    private void cancelAllOnDisconnect(String userId) {
        FirebaseDatabase.getInstance().getReference("Users").child(userId).child("isOnline").onDisconnect().cancel();
        FirebaseDatabase.getInstance().getReference("Users").child(userId).child("lastOnline").onDisconnect().cancel();
        FirebaseDatabase.getInstance().getReference("Users").child(userId).child("lastOnlineTime").onDisconnect().cancel();
        FirebaseDatabase.getInstance().getReference("Users").child(userId).child("lastOnlineDate").onDisconnect().cancel();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        if (getActivity() != null) getActivity().finish();
    }

    private void selectImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        pickImageActivityResultLauncher.launch(Intent.createChooser(intent, "Select Image"));
    }

    private void uploadImageSimple() {

        if (filePath == null) {
            Toast.makeText(getContext(), "Error: File not selected", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        StorageReference storageRef = FirebaseStorage.getInstance()
                .getReference()
                .child("images")
                .child(uid)
                .child("profile.jpg");

        UploadTask uploadTask = storageRef.putFile(filePath);

        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return storageRef.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Uri downloadUri = task.getResult();
                saveToDatabaseSimple(uid, downloadUri.toString());
            }
        }).addOnFailureListener(e ->
                Toast.makeText(getContext(), "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void saveToDatabaseSimple(String uid, String imageUrl) {
        FirebaseDatabase.getInstance()
                .getReference()
                .child("Users")
                .child(uid)
                .child("profileImage")
                .setValue(imageUrl)
                .addOnSuccessListener(unused -> {
                    if (binding != null)
                        Glide.with(requireContext()).load(imageUrl).into(binding.profileImage);

                    Toast.makeText(getContext(), "Profile photo updated!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error saving to database", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
