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

public class NewChatFragment extends Fragment {

    private FragmentNewChatBinding binding;
    private ArrayList<User> usersList = new ArrayList<>();

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

                        // Получаем данные текущего пользователя
                        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                        System.out.println("Текущий пользователь Email: " + currentUserEmail);

                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
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

                            System.out.println("Загружен пользователь: " + userLogin);
                        }

                        binding.userRv.setAdapter(new UsersAdapter(usersList));

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}