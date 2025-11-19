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
    private ArrayList<String> userIdsList = new ArrayList<>(); // ✅ ДОБАВИЛ: для хранения ID пользователей

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
                        userIdsList.clear(); // ✅ ДОБАВИЛ: очищаем список ID

                        // Получаем данные текущего пользователя
                        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                        System.out.println("Текущий пользователь Email: " + currentUserEmail);

                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey(); // ✅ ПОЛУЧАЕМ ID пользователя

                            // Получаем логин и email пользователя из базы
                            String userLogin = userSnapshot.child("login").getValue(String.class);
                            String userEmail = userSnapshot.child("email").getValue(String.class);

                            // Пропускаем пользователей БЕЗ ЛОГИНА
                            if (userLogin == null || userLogin.trim().isEmpty()) {
                                continue;
                            }

                            // Пропускаем СВОЙ аккаунт (проверяем по email)
                            if (currentUserEmail != null && currentUserEmail.equals(userEmail)) {
                                System.out.println("Пропущен свой аккаунт: " + userLogin);
                                continue;
                            }

                            // Получаем profileImage
                            String profileImage = userSnapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null) {
                                profileImage = "";
                            }

                            // Добавляем пользователя в список
                            usersList.add(new User(userLogin, profileImage));
                            userIdsList.add(userId); // ✅ ДОБАВИЛ: сохраняем ID

                            System.out.println("Загружен пользователь: " + userLogin + " | ID: " + userId);
                        }

                        // ✅ ИЗМЕНИЛ: передаем listener в адаптер
                        UsersAdapter adapter = new UsersAdapter(usersList, new UsersAdapter.OnUserClickListener() {
                            @Override
                            public void onUserClick(int position) {
                                // Получаем ID выбранного пользователя
                                String selectedUserId = userIdsList.get(position);
                                // ✅ ВЫЗЫВАЕМ: создание чата
                                createChatWithUser(selectedUserId);
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

    // ✅ ДОБАВИЛ: метод создания чата с пользователем
    private void createChatWithUser(String otherUserId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // ✅ АЛЬТЕРНАТИВНЫЙ ВАРИАНТ: Автоматическая генерация ID чата
        String chatId = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .push()
                .getKey();

        System.out.println("Создаем чат с ID: " + chatId);

        // Проверяем существует ли уже чат между этими пользователями
        checkIfChatExists(chatId, currentUserId, otherUserId);
    }

    // ✅ ДОБАВИЛ: проверка существования чата
    private void checkIfChatExists(String chatId, String currentUserId, String otherUserId) {
        // Ищем чаты где оба пользователя являются участниками
        FirebaseDatabase.getInstance().getReference("Chats")
                .orderByChild("user1")
                .equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean chatExists = false;
                        String existingChatId = null;

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String user2 = chatSnapshot.child("user2").getValue(String.class);
                            if (otherUserId.equals(user2)) {
                                chatExists = true;
                                existingChatId = chatSnapshot.getKey();
                                break;
                            }
                        }

                        if (chatExists) {
                            // Чат уже существует
                            Toast.makeText(getContext(), "Chat already exists", Toast.LENGTH_SHORT).show();
                            openChatActivity(existingChatId, otherUserId);
                        } else {
                            // Создаем новый чат
                            createNewChat(chatId, currentUserId, otherUserId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Error checking chat", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ✅ ДОБАВИЛ: создание нового чата в Firebase (АЛЬТЕРНАТИВНЫЙ ВАРИАНТ)
    private void createNewChat(String chatId, String currentUserId, String otherUserId) {
        // Данные чата
        HashMap<String, Object> chatData = new HashMap<>();
        chatData.put("chat_id", chatId);
        chatData.put("user1", currentUserId);
        chatData.put("user2", otherUserId);
        chatData.put("createdAt", System.currentTimeMillis());
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTime", 0);

        // Данные для обновления в разных местах базы
        HashMap<String, Object> updates = new HashMap<>();

        // 1. Записываем сам чат
        updates.put("Chats/" + chatId, chatData);

        // 2. ✅ АЛЬТЕРНАТИВНЫЙ ВАРИАНТ: Добавляем ссылки на чаты у пользователей
        updates.put("Users/" + currentUserId + "/chats/" + chatId, true);
        updates.put("Users/" + otherUserId + "/chats/" + chatId, true);

        // Выполняем все обновления атомарно
        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Chat created successfully!", Toast.LENGTH_SHORT).show();
                        openChatActivity(chatId, otherUserId);
                    } else {
                        Toast.makeText(getContext(), "Failed to create chat: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ✅ ДОБАВИЛ: переход в активность чата
    private void openChatActivity(String chatId, String otherUserId) {
        // TODO: Создай ChatActivity и раскомментируй
        // Intent intent = new Intent(getContext(), ChatActivity.class);
        // intent.putExtra("chatId", chatId);
        // intent.putExtra("otherUserId", otherUserId);
        // startActivity(intent);

        // Временно показываем информацию о созданном чате
        Toast.makeText(getContext(),
                "Chat created!\nID: " + chatId +
                        "\nWith user: " + otherUserId,
                Toast.LENGTH_LONG).show();

        System.out.println("Чат создан: " + chatId + " с пользователем: " + otherUserId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}