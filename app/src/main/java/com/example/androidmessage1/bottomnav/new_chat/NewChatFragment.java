package com.example.androidmessage1.bottomnav.new_chat;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
    private ArrayList<User> filteredUsersList = new ArrayList<>();
    private ArrayList<String> filteredUserIdsList = new ArrayList<>();
    private boolean isKeyboardForcedHidden = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentNewChatBinding.inflate(inflater, container, false);

        setupRecyclerView();
        setupSearch();
        loadUsers();

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        hideKeyboardDelayed();
    }

    @Override
    public void onStop() {
        super.onStop();
        hideKeyboardImmediately();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hideKeyboardImmediately();
        binding = null;
    }

    private void setupRecyclerView() {
        binding.userRv.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.userRv.addItemDecoration(
                new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        UsersAdapter emptyAdapter = new UsersAdapter(new ArrayList<>(), null);
        binding.userRv.setAdapter(emptyAdapter);
    }

    private void setupSearch() {
        // Изначально скрываем кнопку отмены
        binding.searchBtn.setVisibility(View.GONE);

        // ТЕКСТ-ПОДСКАЗКА КОГДА СПИСОК ПУСТОЙ
        binding.emptyStateText.setVisibility(View.VISIBLE);
        binding.emptyStateText.setText("Enter username to search");

        // Обработчик поиска при вводе текста
        binding.searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                filterUsers(query);

                // ПОКАЗЫВАЕМ КНОПКУ ОТМЕНЫ ТОЛЬКО КОГДА ЕСТЬ ТЕКСТ
                if (s.length() > 0) {
                    binding.searchBtn.setVisibility(View.VISIBLE);
                    binding.emptyStateText.setVisibility(View.GONE);
                } else {
                    binding.searchBtn.setVisibility(View.GONE);
                    binding.emptyStateText.setVisibility(View.VISIBLE);
                    binding.emptyStateText.setText("Enter username to search");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // ✅ ИСПРАВЛЕННЫЙ ОБРАБОТЧИК КНОПКИ ОТМЕНЫ
        binding.searchBtn.setOnClickListener(v -> {
            // ✅ СНАЧАЛА СКРЫВАЕМ КЛАВИАТУРУ И СНИМАЕМ ФОКУС
            hideKeyboardAndClearFocus();

            // ✅ ЗАТЕМ ОЧИЩАЕМ ПОЛЕ ПОИСКА
            binding.searchEt.setText("");

            // Скрываем кнопку отмены
            binding.searchBtn.setVisibility(View.GONE);

            // Показываем подсказку
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.emptyStateText.setText("Enter username to search");

            // Очищаем список
            filteredUsersList.clear();
            filteredUserIdsList.clear();
            updateAdapter();
        });

        // ✅ УПРОЩЕННЫЙ ОБРАБОТЧИК ФОКУСА
        binding.searchEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isKeyboardForcedHidden) {
                showKeyboard();
            }
        });

        // ✅ ДОПОЛНИТЕЛЬНО: ОБРАБОТЧИК КЛИКА НА ПОЛЕ ПОИСКА
        binding.searchEt.setOnClickListener(v -> {
            if (isKeyboardForcedHidden) {
                isKeyboardForcedHidden = false;
                showKeyboard();
            }
        });
    }

    // ✅ УЛУЧШЕННЫЙ МЕТОД: Скрытие клавиатуры и снятие фокуса
    private void hideKeyboardAndClearFocus() {
        try {
            if (getActivity() != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

                // ✅ УСТАНАВЛИВАЕМ ФЛАГ ПЕРЕД СКРЫТИЕМ
                isKeyboardForcedHidden = true;

                // ✅ СНИМАЕМ ФОКУС С ПОЛЯ ПОИСКА
                binding.searchEt.clearFocus();

                // ✅ СКРЫВАЕМ КЛАВИАТУРУ
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                imm.hideSoftInputFromWindow(binding.searchEt.getWindowToken(), 0);

                // ✅ ПЕРЕДАЕМ ФОКУС КОРНЕВОМУ LAYOUT
                binding.getRoot().requestFocus();

                // ✅ СБРАСЫВАЕМ ФЛАГ ЧЕРЕЗ НЕКОТОРОЕ ВРЕМЯ
                binding.getRoot().postDelayed(() -> {
                    isKeyboardForcedHidden = false;
                }, 300);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // ✅ В СЛУЧАЕ ОШИБКИ ВСЕГДА СБРАСЫВАЕМ ФЛАГ
            isKeyboardForcedHidden = false;
        }
    }

    // МЕТОД ДЛЯ НЕМЕДЛЕННОГО СКРЫТИЯ КЛАВИАТУРЫ
    private void hideKeyboardImmediately() {
        try {
            if (getActivity() != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                View view = getActivity().getCurrentFocus();
                if (view != null) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
                if (binding != null && binding.searchEt.hasFocus()) {
                    imm.hideSoftInputFromWindow(binding.searchEt.getWindowToken(), 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // МЕТОД ДЛЯ ОТЛОЖЕННОГО СКРЫТИЯ КЛАВИАТУРЫ
    private void hideKeyboardDelayed() {
        try {
            if (getActivity() != null && binding != null) {
                binding.searchEt.postDelayed(() -> {
                    try {
                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        View view = getActivity().getCurrentFocus();
                        if (view != null) {
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }
                        if (binding.searchEt.hasFocus()) {
                            imm.hideSoftInputFromWindow(binding.searchEt.getWindowToken(), 0);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // МЕТОД ДЛЯ ПОКАЗА КЛАВИАТУРЫ
    private void showKeyboard() {
        if (getActivity() != null && binding != null) {
            binding.searchEt.postDelayed(() -> {
                try {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    binding.searchEt.requestFocus();
                    imm.showSoftInput(binding.searchEt, InputMethodManager.SHOW_IMPLICIT);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 100);
        }
    }

    private void filterUsers(String query) {
        if (binding == null) return;

        filteredUsersList.clear();
        filteredUserIdsList.clear();

        if (query.isEmpty()) {
            binding.emptyStateText.setVisibility(View.VISIBLE);
            binding.emptyStateText.setText("Enter username to search");
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (int i = 0; i < usersList.size(); i++) {
                User user = usersList.get(i);
                if (user.getUsername().toLowerCase().contains(lowerCaseQuery)) {
                    filteredUsersList.add(user);
                    filteredUserIdsList.add(userIdsList.get(i));
                }
            }

            if (filteredUsersList.isEmpty()) {
                binding.emptyStateText.setVisibility(View.VISIBLE);
                binding.emptyStateText.setText("\n" + "No users found");
            } else {
                binding.emptyStateText.setVisibility(View.GONE);
            }
        }

        updateAdapter();
    }

    private void updateAdapter() {
        if (binding == null) return;

        UsersAdapter adapter = new UsersAdapter(filteredUsersList, new UsersAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(int position) {
                if (position < 0 || position >= filteredUserIdsList.size()) return;

                String selectedUserId = filteredUserIdsList.get(position);
                checkIfChatExistsBeforeCreate(selectedUserId);
            }
        });
        binding.userRv.setAdapter(adapter);
    }

    private void loadUsers() {
        if (binding == null) return;

        FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;

                        usersList.clear();
                        userIdsList.clear();

                        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

                        for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                            String userId = userSnapshot.getKey();

                            String userLogin = userSnapshot.child("login").getValue(String.class);
                            String userEmail = userSnapshot.child("email").getValue(String.class);

                            if (userLogin == null || userLogin.trim().isEmpty()) {
                                continue;
                            }

                            if (currentUserEmail != null && currentUserEmail.equals(userEmail)) {
                                continue;
                            }

                            String profileImage = userSnapshot.child("profileImage").getValue(String.class);
                            if (profileImage == null) {
                                profileImage = "";
                            }

                            usersList.add(new User(userLogin, profileImage));
                            userIdsList.add(userId);
                        }

                        filteredUsersList.clear();
                        filteredUserIdsList.clear();

                        if (usersList.isEmpty()) {
                            binding.emptyStateText.setText("\n" + "No users found");
                        } else {
                            binding.emptyStateText.setText("Enter username to search");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (binding == null) return;

                        Toast.makeText(getContext(), "Ошибка загрузки", Toast.LENGTH_SHORT).show();
                        binding.emptyStateText.setText("Ошибка загрузки пользователей");
                    }
                });
    }

    private void checkIfChatExistsBeforeCreate(String otherUserId) {
        if (binding == null) return;

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        binding.userRv.setEnabled(false);

        FirebaseDatabase.getInstance().getReference("Chats")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (binding == null) return;

                        boolean chatExists = false;
                        String existingChatId = null;

                        for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                            String user1 = chatSnapshot.child("user1").getValue(String.class);
                            String user2 = chatSnapshot.child("user2").getValue(String.class);

                            if (user1 != null && user2 != null) {
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
                            Toast.makeText(getContext(), "A chat with this user already exists.", Toast.LENGTH_SHORT).show();
                            openChatActivity(existingChatId, otherUserId);
                        } else {
                            createNewChatWithUser(otherUserId);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (binding == null) return;

                        binding.userRv.setEnabled(true);
                        Toast.makeText(getContext(), "Ошибка проверки чата", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createNewChatWithUser(String otherUserId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        String chatId = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .push()
                .getKey();

        long currentTime = System.currentTimeMillis();

        HashMap<String, Object> chatData = new HashMap<>();
        chatData.put("chat_id", chatId);
        chatData.put("user1", currentUserId);
        chatData.put("user2", otherUserId);
        chatData.put("createdAt", currentTime);
        chatData.put("lastMessage", "");
        chatData.put("lastMessageTime", "");
        chatData.put("lastMessageTimestamp", currentTime);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put("Chats/" + chatId, chatData);
        updates.put("Users/" + currentUserId + "/chats/" + chatId, true);
        updates.put("Users/" + otherUserId + "/chats/" + chatId, true);

        FirebaseDatabase.getInstance().getReference()
                .updateChildren(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Chat created successfully!", Toast.LENGTH_SHORT).show();
                        openChatActivity(chatId, otherUserId);
                    } else {
                        Toast.makeText(getContext(), "Ошибка создания чата: " +
                                task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openChatActivity(String chatId, String otherUserId) {
        // TODO: Раскомментируй когда создашь ChatActivity
        // Intent intent = new Intent(getContext(), ChatActivity.class);
        // intent.putExtra("chatId", chatId);
        // intent.putExtra("otherUserId", otherUserId);
        // startActivity(intent);

        Toast.makeText(getContext(),
                "Chat created!\nID: " + chatId +
                        "\nWith user: " + otherUserId,
                Toast.LENGTH_LONG).show();
    }
}