package com.example.androidmessage1.bottomnav.new_chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.databinding.FragmentNewChatBinding;
import com.example.androidmessage1.users.User;
import com.example.androidmessage1.users.UsersAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class NewChatFragment extends Fragment {

    private FragmentNewChatBinding binding;
    private ArrayList<User> usersList = new ArrayList<>();
    private ArrayList<String> userIdsList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNewChatBinding.inflate(inflater, container, false);

        binding.userRv.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.userRv.addItemDecoration(
                new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        loadUsers();

        return binding.getRoot();
    }

    private void loadUsers() {
        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        usersList.clear();
                        userIdsList.clear();

                        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                        System.out.println("Текущий пользователь Email: " + currentUserEmail);

                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey();

                            String userLogin = userSnapshot.child("login").getValue(String.class);
                            String userEmail = userSnapshot.child("email").getValue(String.class);

                            if (userLogin == null || userLogin.trim().isEmpty()) {
                                continue;
                            }

                            if (currentUserEmail != null && currentUserEmail.equals(userEmail)) {
                                System.out.println("Пропущен свой аккаунт: " + userLogin);
                                continue;
                            }

                            String profileImage = userSnapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null) {
                                profileImage = "";
                            }

                            usersList.add(new User(userLogin, profileImage));
                            userIdsList.add(userId);

                            System.out.println("Загружен пользователь: " + userLogin + " | ID: " + userId);
                        }

                        UsersAdapter adapter = new UsersAdapter(usersList, new UsersAdapter.OnUserClickListener() {
                            @Override
                            public void onUserClick(int position) {
                                String selectedUserId = userIdsList.get(position);
                                checkIfChatExistsBeforeCreate(selectedUserId);
                            }
                        });
                        binding.userRv.setAdapter(adapter);

                        if (usersList.isEmpty()) {
                            Toast.makeText(getContext(), "Другие пользователи не найдены", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Загружено пользователей: " + usersList.size(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                        System.out.println("Ошибка БД: " + error.getMessage());
                    }
                });
    }

    // ✅ ИСПРАВЛЕННЫЙ МЕТОД: Проверяем чат в обе стороны (user1-user2 и user2-user1)
    private void checkIfChatExistsBeforeCreate(String otherUserId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Блокируем кнопку на время проверки
        binding.userRv.setEnabled(false);

        FirebaseDatabase.getInstance().getReference("Chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean chatExists = false;
                        String existingChatId = null;

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            if (user1 != null && user2 != null) {
                                // Проверяем оба варианта: current-user1 + other-user2 И current-user2 + other-user1
                                if ((user1.equals(currentUserId) && user2.equals(otherUserId)) ||
                                        (user1.equals(otherUserId) && user2.equals(currentUserId))) {
                                    chatExists = true;
                                    existingChatId = chatSnapshot.getKey();
                                    break;
                                }
                            }
                        }

                        binding.userRv.setEnabled(true);

                        if (chatExists) {
                            // Чат уже существует
                            Toast.makeText(getContext(), "Чат с этим пользователем уже существует", Toast.LENGTH_SHORT).show();
                            openChatActivity(existingChatId, otherUserId);
                        } else {
                            // Создаем новый чат
                            createNewChatWithUser(otherUserId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.userRv.setEnabled(true);
                        Toast.makeText(getContext(), "Ошибка проверки чата", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ✅ СОЗДАНИЕ НОВОГО ЧАТА С lastMessageTimestamp
    private void createNewChatWithUser(String otherUserId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Генерируем ID чата
        String chatId = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .push()
                .getKey();

        // ✅ ТЕКУЩЕЕ ВРЕМЯ ДЛЯ СОРТИРОВКИ
        long currentTime = System.currentTimeMillis();

        // Данные чата
        HashMap<String, Object> chatData = new HashMap<>();
        chatData.put("chat_id", chatId);
        chatData.put("user1", currentUserId);
        chatData.put("user2", otherUserId);
        chatData.put("createdAt", currentTime);
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTime", "");
        chatData.put("lastMessageTimestamp", currentTime); // ✅ ДОБАВЛЯЕМ ВРЕМЕННУЮ МЕТКУ

        // Данные для обновления в разных местах базы
        HashMap<String, Object> updates = new HashMap<>();

        // 1. Записываем сам чат
        updates.put("Chats/" + chatId, chatData);

        // 2. Добавляем ссылки на чаты у пользователей
        updates.put("Users/" + currentUserId + "/chats/" + chatId, true);
        updates.put("Users/" + otherUserId + "/chats/" + chatId, true);

        // Выполняем все обновления атомарно
        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Чат создан успешно!", Toast.LENGTH_SHORT).show();
                        openChatActivity(chatId, otherUserId);
                    } else {
                        Toast.makeText(getContext(), "Ошибка создания чата: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openChatActivity(String chatId, String otherUserId) {
        // TODO: Создай ChatActivity и раскомментируй
        // Intent intent = new Intent(getContext(), ChatActivity.class);
        // intent.putExtra("chatId", chatId);
        // intent.putExtra("otherUserId", otherUserId);
        // startActivity(intent);

        Toast.makeText(getContext(),
                "Чат создан!\nID: " + chatId +
                        "\nС пользователем: " + otherUserId,
                Toast.LENGTH_LONG).show();

        System.out.println("Чат создан: " + chatId + " с пользователем: " + otherUserId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}