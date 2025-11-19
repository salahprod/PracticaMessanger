package com.example.androidmessage1.bottomnav.chats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.chats.Chat;
import com.example.androidmessage1.chats.ChatsAdapter;
import com.example.androidmessage1.databinding.FragmentChatsBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Objects;

public class FragmentChat extends Fragment {
    private FragmentChatsBinding binding;
    private ChatsAdapter chatsAdapter;
    private ArrayList<Chat> chats = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);

        // Инициализация RecyclerView
        binding.chatsRv.setLayoutManager(new LinearLayoutManager(getContext()));
        chatsAdapter = new ChatsAdapter(chats);
        binding.chatsRv.setAdapter(chatsAdapter);

        loadChats();

        return binding.getRoot();
    }

    private void loadChats() {
        String currentUid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        FirebaseDatabase.getInstance().getReference("Chats").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chats.clear(); // Очищаем список перед загрузкой новых данных

                // ✅ ИСПРАВЛЕНИЕ: Ищем чаты где текущий пользователь является участником
                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatId = chatSnapshot.getKey();

                    // Получаем участников чата
                    String userId1 = chatSnapshot.child("user1").getValue(String.class);
                    String userId2 = chatSnapshot.child("user2").getValue(String.class);

                    // Проверяем что данные существуют
                    if (userId1 == null || userId2 == null) {
                        continue;
                    }

                    // ✅ Проверяем участвует ли текущий пользователь в этом чате
                    if (userId1.equals(currentUid) || userId2.equals(currentUid)) {
                        // Определяем ID собеседника
                        String otherUserId = userId1.equals(currentUid) ? userId2 : userId1;

                        // Загружаем данные собеседника
                        loadOtherUserData(chatId, userId1, userId2, otherUserId);
                    }
                }

                // Если после проверки всех чатов список пуст
                if (chats.isEmpty()) {
                    Toast.makeText(getContext(), "No chats found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadOtherUserData(String chatId, String userId1, String userId2, String otherUserId) {
        FirebaseDatabase.getInstance().getReference("Users").child(otherUserId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                        if (!userSnapshot.exists()) {
                            return;
                        }

                        // ✅ Получаем логин собеседника
                        String chatName = userSnapshot.child("login").getValue(String.class);
                        if (chatName == null || chatName.trim().isEmpty()) {
                            // Если нет логина, используем email
                            String email = userSnapshot.child("email").getValue(String.class);
                            if (email != null && email.contains("@")) {
                                chatName = email.substring(0, email.indexOf("@"));
                            } else {
                                chatName = "Unknown User";
                            }
                        }

                        // ✅ Получаем аватарку
                        String profileImage = userSnapshot.child("profileImage").getValue(String.class);
                        if (profileImage == null) {
                            profileImage = "";
                        }

                        // ✅ Создаем объект чата
                        // Конструктор: Chat(String chat_id, String userId2, String userId1, String chat_name)
                        Chat chat = new Chat(chatId, userId2, userId1, chatName);

                        // Добавляем в список и обновляем адаптер
                        chats.add(chat);
                        chatsAdapter.notifyDataSetChanged();

                        System.out.println("Загружен чат: " + chatName + " | ID: " + chatId);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        System.out.println("Ошибка загрузки пользователя: " + otherUserId);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}