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
        String uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        FirebaseDatabase.getInstance().getReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chats.clear(); // Очищаем список перед загрузкой новых данных

                DataSnapshot userChatsSnapshot = snapshot.child("Users").child(uid).child("chats");

                // Проверяем, существует ли у пользователя чаты
                if (!userChatsSnapshot.exists()) {
                    // Можно показать сообщение, что чатов нет
                    return;
                }

                String chatsStr = Objects.requireNonNull(userChatsSnapshot.getValue()).toString();

                // Проверяем, не пустая ли строка с чатами
                if (chatsStr.isEmpty()) {
                    return;
                }

                String[] chatsIds = chatsStr.split(",");

                for (String chatId : chatsIds) {
                    // Пропускаем пустые ID
                    if (chatId.trim().isEmpty()) {
                        continue;
                    }

                    DataSnapshot chatSnapshot = snapshot.child("Chats").child(chatId.trim());

                    // Проверяем, существует ли чат
                    if (!chatSnapshot.exists()) {
                        continue;
                    }

                    String userId1 = Objects.requireNonNull(chatSnapshot.child("user1").getValue()).toString();
                    String userId2 = Objects.requireNonNull(chatSnapshot.child("user2").getValue()).toString();
                    String chatUserId = uid.equals(userId1) ? userId2 : userId1;

                    // Получаем имя пользователя для чата
                    DataSnapshot chatUserSnapshot = snapshot.child("Users").child(chatUserId);
                    if (!chatUserSnapshot.exists()) {
                        continue;
                    }

                    String chatName = Objects.requireNonNull(chatUserSnapshot.child("username").getValue()).toString();

                    Chat chat = new Chat(chatId.trim(), chatName, userId1, userId2);
                    chats.add(chat);
                }

                // Уведомляем адаптер об изменениях
                chatsAdapter.notifyDataSetChanged();

                // Проверяем, есть ли чаты
                if (chats.isEmpty()) {
                    // Можно показать сообщение, что чатов нет
                    Toast.makeText(getContext(), "No chats found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to get user chats: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}