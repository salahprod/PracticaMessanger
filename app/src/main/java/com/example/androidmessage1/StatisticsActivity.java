package com.example.androidmessage1;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.androidmessage1.message.ChatStatistics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StatisticsActivity extends AppCompatActivity {

    private LinearLayout statsContainer;
    private static final int MAX_NAME_LENGTH = 15;
    private List<ChatStatsItem> allStatsList = new ArrayList<>();
    private int loadedItemsCount = 0;
    private int totalItemsToLoad = 0;

    // Константа для тега логов
    private static final String TAG = "StatisticsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.statistics);

        Log.d(TAG, "StatisticsActivity onCreate");

        // Инициализация контейнера для статистики
        statsContainer = findViewById(R.id.rp329a8aob7l);

        // Находим кнопку "Назад"
        ImageView backButton = findViewById(R.id.r7d3enlcz75r);

        // Обработчик нажатия на кнопку "Назад"
        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        // Обработчик жеста "назад"
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isEnabled()) {
                    finish();
                }
            }
        });

        // Сначала очищаем контейнер от статических элементов
        statsContainer.removeAllViews();

        // Загружаем статистику чатов и групп
        loadAllStatistics();
    }

    private void loadAllStatistics() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        Log.d(TAG, "loadAllStatistics, currentUserId: " + currentUserId);

        if (currentUserId == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show();
            displayNoStatistics();
            return;
        }

        // Сбрасываем счетчики
        allStatsList.clear();
        loadedItemsCount = 0;
        totalItemsToLoad = 0;

        // Загружаем статистику чатов
        loadChatStatistics(currentUserId);
    }

    private void loadChatStatistics(String currentUserId) {
        DatabaseReference chatStatsRef = FirebaseDatabase.getInstance()
                .getReference("UserStatistics")
                .child(currentUserId)
                .child("chatStatistics");

        Log.d(TAG, "Loading chat statistics from: " + chatStatsRef.toString());

        chatStatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "Chat statistics dataSnapshot exists: " + dataSnapshot.exists());
                Log.d(TAG, "Chat statistics children count: " + dataSnapshot.getChildrenCount());

                if (dataSnapshot.exists()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String chatId = snapshot.getKey();
                        ChatStatistics stats = snapshot.getValue(ChatStatistics.class);
                        Log.d(TAG, "Found chat stat - ID: " + chatId + ", stats: " + (stats != null ? "not null" : "null"));

                        if (stats != null) {
                            stats.setChatId(chatId); // Убедимся, что ID установлен
                            totalItemsToLoad++;
                            loadChatInfo(stats, "chat");
                        }
                    }
                }

                // После загрузки чатов, загружаем статистику групп
                loadGroupStatistics(currentUserId);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading chat statistics: " + databaseError.getMessage());
                Toast.makeText(StatisticsActivity.this,
                        "Ошибка загрузки статистики чатов: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();
                loadGroupStatistics(currentUserId);
            }
        });
    }

    private void loadGroupStatistics(String currentUserId) {
        DatabaseReference groupStatsRef = FirebaseDatabase.getInstance()
                .getReference("UserStatistics")
                .child(currentUserId)
                .child("groupStatistics");

        Log.d(TAG, "Loading group statistics from: " + groupStatsRef.toString());

        groupStatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "Group statistics dataSnapshot exists: " + dataSnapshot.exists());
                Log.d(TAG, "Group statistics children count: " + dataSnapshot.getChildrenCount());

                if (dataSnapshot.exists()) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String groupId = snapshot.getKey();
                        ChatStatistics stats = snapshot.getValue(ChatStatistics.class);
                        Log.d(TAG, "Found group stat - ID: " + groupId + ", stats: " + (stats != null ? "not null" : "null"));

                        if (stats != null) {
                            stats.setChatId(groupId); // Убедимся, что ID установлен
                            totalItemsToLoad++;
                            loadChatInfo(stats, "group");
                        }
                    }
                } else {
                    Log.d(TAG, "No group statistics found in database");
                }

                Log.d(TAG, "Total items to load: " + totalItemsToLoad);

                // Если нет статистики вообще
                if (totalItemsToLoad == 0) {
                    displayNoStatistics();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading group statistics: " + databaseError.getMessage());
                Toast.makeText(StatisticsActivity.this,
                        "Ошибка загрузки статистики групп: " + databaseError.getMessage(),
                        Toast.LENGTH_SHORT).show();

                // Если что-то уже загрузилось, показываем
                if (allStatsList.size() > 0) {
                    displayCombinedStatistics();
                } else {
                    displayNoStatistics();
                }
            }
        });
    }

    private void loadChatInfo(ChatStatistics stats, String type) {
        String chatId = stats.getChatId();
        Log.d(TAG, "loadChatInfo - type: " + type + ", chatId: " + chatId);

        if ("group".equals(type)) {
            // Для групп загружаем название группы
            loadGroupName(chatId, stats);
        } else {
            // Для чатов загружаем имя собеседника
            loadChatPartnerName(chatId, stats);
        }
    }

    private void loadGroupName(String groupId, ChatStatistics stats) {
        Log.d(TAG, "Loading group name for groupId: " + groupId);

        DatabaseReference groupRef = FirebaseDatabase.getInstance()
                .getReference("Groups")
                .child(groupId)
                .child("groupName");

        groupRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String groupName = "Группа";
                Log.d(TAG, "Group name dataSnapshot exists: " + dataSnapshot.exists());

                if (dataSnapshot.exists()) {
                    String name = dataSnapshot.getValue(String.class);
                    Log.d(TAG, "Raw group name from DB: " + name);

                    if (name != null && !name.trim().isEmpty()) {
                        groupName = name;
                    }
                }

                Log.d(TAG, "Final group name: " + groupName);

                // Добавляем в список
                ChatStatsItem item = new ChatStatsItem(
                        "👥 " + groupName,
                        stats.getFormattedTime(),
                        stats.getTotalTimeSpent(),
                        "group",
                        stats.getChatId()
                );
                allStatsList.add(item);

                Log.d(TAG, "Added group to list. Total items: " + allStatsList.size());

                checkAllItemsLoaded();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading group name: " + databaseError.getMessage());

                // Добавляем с дефолтным именем
                ChatStatsItem item = new ChatStatsItem(
                        "👥 Группа",
                        stats.getFormattedTime(),
                        stats.getTotalTimeSpent(),
                        "group",
                        stats.getChatId()
                );
                allStatsList.add(item);

                Log.d(TAG, "Added default group to list. Total items: " + allStatsList.size());

                checkAllItemsLoaded();
            }
        });
    }

    private void loadChatPartnerName(String chatId, ChatStatistics stats) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d(TAG, "Loading chat partner name for chatId: " + chatId);

        // Получаем информацию о собеседнике из структуры чата
        DatabaseReference chatRef = FirebaseDatabase.getInstance()
                .getReference("Chats")
                .child(chatId);

        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String partnerId = null;
                Log.d(TAG, "Chat dataSnapshot exists: " + dataSnapshot.exists());

                if (dataSnapshot.exists()) {
                    // Ищем ID собеседника
                    String user1 = dataSnapshot.child("user1").getValue(String.class);
                    String user2 = dataSnapshot.child("user2").getValue(String.class);

                    Log.d(TAG, "Chat users - user1: " + user1 + ", user2: " + user2);
                    Log.d(TAG, "Current user ID: " + currentUserId);

                    if (user1 != null && user2 != null) {
                        if (user1.equals(currentUserId)) {
                            partnerId = user2;
                        } else if (user2.equals(currentUserId)) {
                            partnerId = user1;
                        }
                    }
                }

                Log.d(TAG, "Found partnerId: " + partnerId);

                if (partnerId != null) {
                    // Загружаем имя пользователя
                    loadUserName(partnerId, stats, chatId);
                } else {
                    // Если не нашли собеседника, используем дефолтное имя
                    Log.d(TAG, "Partner not found, using default name");
                    addChatItem("👤 Пользователь", stats, chatId);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading chat info: " + databaseError.getMessage());
                addChatItem("👤 Пользователь", stats, chatId);
            }
        });
    }

    private void loadUserName(String userId, ChatStatistics stats, String chatId) {
        Log.d(TAG, "Loading user name for userId: " + userId);

        // Сначала проверяем кастомное имя
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference customRef = FirebaseDatabase.getInstance()
                .getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(userId);

        customRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Log.d(TAG, "Custom name dataSnapshot exists: " + dataSnapshot.exists());

                if (dataSnapshot.exists()) {
                    String customName = dataSnapshot.child("customName").getValue(String.class);
                    Log.d(TAG, "Custom name from DB: " + customName);

                    if (customName != null && !customName.trim().isEmpty()) {
                        addChatItem("👤 " + customName, stats, chatId);
                        return;
                    }
                }

                // Если нет кастомного имени, загружаем оригинальное
                loadOriginalUserName(userId, stats, chatId);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading custom name: " + databaseError.getMessage());
                loadOriginalUserName(userId, stats, chatId);
            }
        });
    }

    private void loadOriginalUserName(String userId, ChatStatistics stats, String chatId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userId);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String userName = "Пользователь";
                Log.d(TAG, "Original user name dataSnapshot exists: " + dataSnapshot.exists());

                if (dataSnapshot.exists()) {
                    // Пробуем получить логин
                    String login = dataSnapshot.child("login").getValue(String.class);
                    Log.d(TAG, "Login from DB: " + login);

                    if (login != null && !login.trim().isEmpty()) {
                        userName = login;
                    } else {
                        // Если логина нет, пробуем получить из email
                        String email = dataSnapshot.child("email").getValue(String.class);
                        Log.d(TAG, "Email from DB: " + email);

                        if (email != null && email.contains("@")) {
                            userName = email.substring(0, email.indexOf("@"));
                        }
                    }
                }

                Log.d(TAG, "Final user name: " + userName);
                addChatItem("👤 " + userName, stats, chatId);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error loading original user name: " + databaseError.getMessage());
                addChatItem("👤 Пользователь", stats, chatId);
            }
        });
    }

    private void addChatItem(String name, ChatStatistics stats, String chatId) {
        ChatStatsItem item = new ChatStatsItem(
                name,
                stats.getFormattedTime(),
                stats.getTotalTimeSpent(),
                "chat",
                chatId
        );
        allStatsList.add(item);

        Log.d(TAG, "Added chat to list. Total items: " + allStatsList.size());

        checkAllItemsLoaded();
    }

    private void checkAllItemsLoaded() {
        loadedItemsCount++;

        Log.d(TAG, "checkAllItemsLoaded - loaded: " + loadedItemsCount + "/" + totalItemsToLoad);

        // Если все элементы загружены, отображаем статистику
        if (loadedItemsCount >= totalItemsToLoad) {
            Log.d(TAG, "All items loaded. Displaying combined statistics. Total items: " + allStatsList.size());
            displayCombinedStatistics();
        }
    }

    private void displayCombinedStatistics() {
        Log.d(TAG, "Displaying combined statistics. Items count: " + allStatsList.size());

        // Очищаем контейнер
        statsContainer.removeAllViews();

        if (allStatsList.isEmpty()) {
            Log.d(TAG, "No statistics to display");
            displayNoStatistics();
            return;
        }

        // Сортируем по времени (по убыванию)
        Collections.sort(allStatsList, new Comparator<ChatStatsItem>() {
            @Override
            public int compare(ChatStatsItem o1, ChatStatsItem o2) {
                return Long.compare(o2.totalTime, o1.totalTime);
            }
        });

        // Отображаем все элементы
        for (int i = 0; i < allStatsList.size(); i++) {
            ChatStatsItem item = allStatsList.get(i);
            Log.d(TAG, "Item " + (i+1) + ": " + item.name + " (" + item.type + "), time: " + item.formattedTime);

            // Создаем новый элемент статистики
            View statsItemView = createStatsItemView(item, i + 1);
            statsContainer.addView(statsItemView);

            // Добавляем разделитель между элементами (кроме последнего)
            if (i < allStatsList.size() - 1) {
                addSeparator();
            }
        }

        Toast.makeText(this, "Загружено элементов: " + allStatsList.size(), Toast.LENGTH_SHORT).show();
    }

    private View createStatsItemView(ChatStatsItem item, int position) {
        // Создаем новый LinearLayout для элемента статистики
        LinearLayout itemLayout = new LinearLayout(this);
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Устанавливаем отступы и фон как в оригинальном макете
        int horizontalMargin = dpToPx(11);
        int bottomMargin = dpToPx(15);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) itemLayout.getLayoutParams();
        layoutParams.setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin);
        itemLayout.setLayoutParams(layoutParams);

        itemLayout.setBackgroundResource(R.drawable.cr21bffffff);
        itemLayout.setPadding(dpToPx(20), dpToPx(25), dpToPx(20), dpToPx(25));
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);

        // Создаем TextView для названия чата/группы
        TextView nameTextView = new TextView(this);
        nameTextView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        ));

        // Настраиваем внешний вид
        nameTextView.setTextColor(0xFF000000);
        nameTextView.setTextSize(15);
        nameTextView.setTypeface(null, android.graphics.Typeface.BOLD);

        // Устанавливаем текст с обрезкой если слишком длинный
        String displayName = item.name;
        if (displayName.length() > MAX_NAME_LENGTH) {
            displayName = displayName.substring(0, MAX_NAME_LENGTH - 3) + "...";
        }
        nameTextView.setText(displayName);

        // Создаем TextView для времени
        TextView timeTextView = new TextView(this);
        timeTextView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Настраиваем внешний вид
        timeTextView.setTextColor(0xFFAA68FC);
        timeTextView.setTextSize(15);
        timeTextView.setText(item.formattedTime);

        // Добавляем TextView в layout
        itemLayout.addView(nameTextView);
        itemLayout.addView(timeTextView);

        return itemLayout;
    }

    private void addSeparator() {
        // Создаем разделитель между элементами
        View separator = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );

        int horizontalMargin = dpToPx(11);
        int bottomMargin = dpToPx(15);
        params.setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin);
        separator.setLayoutParams(params);
        separator.setBackgroundColor(0xFFEEEEEE); // Светло-серый цвет разделителя

        statsContainer.addView(separator);
    }

    private void displayNoStatistics() {
        Log.d(TAG, "Displaying 'No statistics' message");
        // Очищаем контейнер
        statsContainer.removeAllViews();

        // Создаем элемент с сообщением "Нет данных"
        LinearLayout noDataLayout = new LinearLayout(this);
        noDataLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Устанавливаем отступы и фон как в оригинальном макете
        int horizontalMargin = dpToPx(11);
        int bottomMargin = dpToPx(15);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) noDataLayout.getLayoutParams();
        layoutParams.setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin);
        noDataLayout.setLayoutParams(layoutParams);

        noDataLayout.setBackgroundResource(R.drawable.cr21bffffff);
        noDataLayout.setPadding(dpToPx(20), dpToPx(25), dpToPx(20), dpToPx(25));
        noDataLayout.setOrientation(LinearLayout.HORIZONTAL);

        // Создаем TextView для сообщения
        TextView messageTextView = new TextView(this);
        messageTextView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Настраиваем внешний вид
        messageTextView.setTextColor(0xFF000000);
        messageTextView.setTextSize(15);
        messageTextView.setTypeface(null, android.graphics.Typeface.BOLD);
        messageTextView.setText("Нет данных о статистике");
        messageTextView.setGravity(android.view.Gravity.CENTER);

        noDataLayout.addView(messageTextView);
        statsContainer.addView(noDataLayout);
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // Вспомогательный класс для хранения данных статистики
    private static class ChatStatsItem {
        String name;
        String formattedTime;
        long totalTime;
        String type; // "chat" или "group"
        String id;

        public ChatStatsItem(String name, String formattedTime, long totalTime, String type, String id) {
            this.name = name;
            this.formattedTime = formattedTime;
            this.totalTime = totalTime;
            this.type = type;
            this.id = id;
        }
    }
}