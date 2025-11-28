package com.example.androidmessage1.chats;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.androidmessage1.ChatActivity;
import com.example.androidmessage1.R;
import com.example.androidmessage1.groups.GroupChatActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatsAdapter extends RecyclerView.Adapter<ChatViewHolder> {

    private ArrayList<Chat> chats;
    private String currentUserId;
    private OnChatClickListener listener;
    private HashMap<String, ValueEventListener> unreadListeners;
    private HashMap<String, String> avatarCache;
    private HashMap<String, Integer> unreadCountCache;
    private HashMap<String, Integer> viewHolderPositions;

    // Кэши для микрообновлений
    private HashMap<String, String> previousChatNames = new HashMap<>();
    private HashMap<String, String> previousChatImages = new HashMap<>();
    private HashMap<String, String> previousLastMessages = new HashMap<>();

    public interface OnChatClickListener {
        void onChatClick(int position);
    }

    public ChatsAdapter(ArrayList<Chat> chats, OnChatClickListener listener){
        this.chats = chats;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.listener = listener;
        this.unreadListeners = new HashMap<>();
        this.avatarCache = new HashMap<>();
        this.unreadCountCache = new HashMap<>();
        this.viewHolderPositions = new HashMap<>();
        initializePreviousData();
    }

    public ChatsAdapter(ArrayList<Chat> chats){
        this.chats = chats;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.unreadListeners = new HashMap<>();
        this.avatarCache = new HashMap<>();
        this.unreadCountCache = new HashMap<>();
        this.viewHolderPositions = new HashMap<>();
        initializePreviousData();
    }

    // Инициализация предыдущих данных для сравнения
    private void initializePreviousData() {
        for (Chat chat : chats) {
            String chatId = chat.getChat_id();
            previousChatNames.put(chatId, chat.getChat_name());
            previousChatImages.put(chatId, chat.getProfileImage());
            previousLastMessages.put(chatId, chat.getLastMessage());
        }
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.person_item_rv, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);
        String chatId = chat.getChat_id();

        // Сохраняем позицию ViewHolder для этого чата
        viewHolderPositions.put(chatId, position);

        // Устанавливаем имя чата (уже содержит кастомное имя если есть)
        holder.username_tv.setText(chat.getChat_name());

        String lastMessage = chat.getLastMessage();
        if (lastMessage != null && !lastMessage.isEmpty()) {
            holder.last_message_tv.setText(lastMessage);
            holder.last_message_tv.setVisibility(View.VISIBLE);
        } else {
            holder.last_message_tv.setText("No messages");
            holder.last_message_tv.setVisibility(View.VISIBLE);
        }

        // Сначала скрываем бейдж
        holder.message_count_badge.setVisibility(View.GONE);
        holder.last_message_tv.setTextColor(0xFF757575);
        holder.last_message_tv.setTypeface(holder.last_message_tv.getTypeface(), android.graphics.Typeface.NORMAL);

        // Проверяем кэш непрочитанных сообщений
        Integer cachedUnreadCount = unreadCountCache.get(chatId);
        if (cachedUnreadCount != null) {
            updateUnreadBadge(holder, cachedUnreadCount);
        } else {
            updateUnreadBadge(holder, 0);
        }

        // Настраиваем слушатели для непрочитанных сообщений
        if (!chat.isGroup()) {
            setupUnreadMessagesListener(chatId, holder);
        } else {
            setupGroupUnreadMessagesListener(chatId, holder);
        }

        // Устанавливаем placeholder аватарку
        holder.profile_iv.setImageResource(R.drawable.artem);

        // Загружаем аватарку с учетом кастомных данных
        if (chat.isGroup()) {
            loadGroupAvatar(holder, chatId, position);
        } else {
            loadUserAvatarWithCustomizations(holder, chat, position);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatClick(position);
            } else {
                if (chat.isGroup()) {
                    Intent intent = new Intent(holder.itemView.getContext(), GroupChatActivity.class);
                    intent.putExtra("groupId", chatId);
                    intent.putExtra("groupName", chat.getChat_name());
                    holder.itemView.getContext().startActivity(intent);
                } else {
                    Intent intent = new Intent(holder.itemView.getContext(), ChatActivity.class);
                    intent.putExtra("chatId", chatId);
                    intent.putExtra("otherUserId", chat.getOther_user_id());
                    holder.itemView.getContext().startActivity(intent);
                }
            }

            markMessagesAsRead(chat);
        });
    }

    // МЕТОД ДЛЯ МИКРООБНОВЛЕНИЯ ОДНОГО ЭЛЕМЕНТА
    public void updateSingleItem(int position) {
        if (position >= 0 && position < chats.size()) {
            Chat chat = chats.get(position);
            String chatId = chat.getChat_id();

            // Сравниваем с предыдущими данными
            String previousName = previousChatNames.get(chatId);
            String previousImage = previousChatImages.get(chatId);
            String previousLastMessage = previousLastMessages.get(chatId);

            String currentName = chat.getChat_name();
            String currentImage = chat.getProfileImage();
            String currentLastMessage = chat.getLastMessage();

            boolean needsUpdate = !isEqual(previousName, currentName) ||
                    !isEqual(previousImage, currentImage) ||
                    !isEqual(previousLastMessage, currentLastMessage);

            if (needsUpdate) {
                Log.d("ChatsAdapter", "Micro-updating chat at position: " + position +
                        ", chatId: " + chatId);

                // Обновляем предыдущие данные
                previousChatNames.put(chatId, currentName);
                previousChatImages.put(chatId, currentImage);
                previousLastMessages.put(chatId, currentLastMessage);

                // Обновляем только этот элемент
                notifyItemChanged(position);
            }
        }
    }

    // МЕТОД ДЛЯ ОБНОВЛЕНИЯ ИЛИ ДОБАВЛЕНИЯ ЧАТА С МИКРООБНОВЛЕНИЕМ
    public void updateOrAddChatWithMicroUpdate(Chat updatedChat) {
        String chatId = updatedChat.getChat_id();

        for (int i = 0; i < chats.size(); i++) {
            Chat existingChat = chats.get(i);
            if (existingChat.getChat_id().equals(chatId)) {
                // Нашли существующий чат - проверяем изменения
                String previousName = previousChatNames.get(chatId);
                String previousImage = previousChatImages.get(chatId);
                String previousLastMessage = previousLastMessages.get(chatId);

                String currentName = updatedChat.getChat_name();
                String currentImage = updatedChat.getProfileImage();
                String currentLastMessage = updatedChat.getLastMessage();

                boolean needsUpdate = !isEqual(previousName, currentName) ||
                        !isEqual(previousImage, currentImage) ||
                        !isEqual(previousLastMessage, currentLastMessage);

                if (needsUpdate) {
                    Log.d("ChatsAdapter", "Micro-updating existing chat: " + chatId);

                    // Обновляем данные
                    chats.set(i, updatedChat);
                    previousChatNames.put(chatId, currentName);
                    previousChatImages.put(chatId, currentImage);
                    previousLastMessages.put(chatId, currentLastMessage);

                    // Микрообновление
                    notifyItemChanged(i);
                }
                return;
            }
        }

        // Чат не найден - добавляем новый
        Log.d("ChatsAdapter", "Adding new chat: " + chatId);
        chats.add(updatedChat);
        previousChatNames.put(chatId, updatedChat.getChat_name());
        previousChatImages.put(chatId, updatedChat.getProfileImage());
        previousLastMessages.put(chatId, updatedChat.getLastMessage());
        notifyItemInserted(chats.size() - 1);
    }

    // Вспомогательный метод для сравнения строк
    private boolean isEqual(String str1, String str2) {
        if (str1 == null && str2 == null) return true;
        if (str1 == null || str2 == null) return false;
        return str1.equals(str2);
    }

    private void loadUserAvatarWithCustomizations(ChatViewHolder holder, Chat chat, int position) {
        final int currentPosition = position;
        String userId = chat.getOther_user_id();

        // Сначала проверяем есть ли кастомная аватарка в объекте Chat
        if (chat.getProfileImage() != null && !chat.getProfileImage().isEmpty()) {
            loadAvatarWithGlide(holder, chat.getProfileImage(), currentPosition);
            return;
        }

        // Проверяем кэш кастомных аватарок
        String customAvatarCacheKey = "custom_user_" + userId;
        String cachedCustomAvatar = avatarCache.get(customAvatarCacheKey);
        if (cachedCustomAvatar != null) {
            loadAvatarWithGlide(holder, cachedCustomAvatar, currentPosition);
            return;
        }

        // Загружаем кастомные данные из Firebase
        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(userId)
                .child("customImage")
                .get()
                .addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DataSnapshot> task) {
                        try {
                            if (holder.getAdapterPosition() != currentPosition) {
                                return;
                            }

                            if (task.isSuccessful() && task.getResult() != null && task.getResult().getValue() != null) {
                                String customImageUrl = task.getResult().getValue().toString();

                                if (customImageUrl != null && !customImageUrl.isEmpty()) {
                                    // Сохраняем в кэш кастомных аватарок
                                    avatarCache.put(customAvatarCacheKey, customImageUrl);
                                    loadAvatarWithGlide(holder, customImageUrl, currentPosition);
                                } else {
                                    // Если нет кастомной аватарки, загружаем оригинальную
                                    loadOriginalUserAvatar(holder, userId, currentPosition);
                                }
                            } else {
                                // Если нет кастомных данных, загружаем оригинальную аватарку
                                loadOriginalUserAvatar(holder, userId, currentPosition);
                            }
                        } catch (Exception e) {
                            loadOriginalUserAvatar(holder, userId, currentPosition);
                        }
                    }
                });
    }

    private void loadOriginalUserAvatar(ChatViewHolder holder, String userId, int position) {
        final int currentPosition = position;

        // Проверяем кэш оригинальных аватарок
        String originalAvatarCacheKey = "user_" + userId;
        String cachedAvatar = avatarCache.get(originalAvatarCacheKey);
        if (cachedAvatar != null) {
            loadAvatarWithGlide(holder, cachedAvatar, currentPosition);
            return;
        }

        FirebaseDatabase.getInstance().getReference().child("Users").child(userId)
                .child("profileImage").get()
                .addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DataSnapshot> task) {
                        try {
                            if (holder.getAdapterPosition() != currentPosition) {
                                return;
                            }

                            if (task.isSuccessful() && task.getResult() != null && task.getResult().getValue() != null) {
                                String profileImageUrl = task.getResult().getValue().toString();

                                if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                    // Сохраняем в кэш
                                    avatarCache.put(originalAvatarCacheKey, profileImageUrl);
                                    loadAvatarWithGlide(holder, profileImageUrl, currentPosition);
                                } else {
                                    holder.profile_iv.setImageResource(R.drawable.artem);
                                }
                            } else {
                                holder.profile_iv.setImageResource(R.drawable.artem);
                            }
                        } catch (Exception e) {
                            holder.profile_iv.setImageResource(R.drawable.artem);
                        }
                    }
                });
    }

    private void setupUnreadMessagesListener(String chatId, ChatViewHolder holder) {
        // Удаляем предыдущий слушатель если есть
        if (unreadListeners.containsKey(chatId)) {
            FirebaseDatabase.getInstance().getReference("Chats")
                    .child(chatId)
                    .child("messages")
                    .removeEventListener(unreadListeners.get(chatId));
        }

        ValueEventListener unreadMessagesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int unreadCount = 0;

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        if (ownerId != null &&
                                !ownerId.equals(currentUserId) &&
                                (isRead == null || !isRead)) {
                            unreadCount++;
                        }
                    }
                }

                // Сохраняем в кэш
                unreadCountCache.put(chatId, unreadCount);

                // Обновляем UI только если ViewHolder все еще на правильной позиции
                Integer currentPosition = viewHolderPositions.get(chatId);
                if (currentPosition != null && holder.getAdapterPosition() == currentPosition) {
                    Object currentTag = holder.itemView.getTag();
                    if (currentTag == null || !currentTag.equals(unreadCount)) {
                        updateUnreadBadge(holder, unreadCount);
                        holder.itemView.setTag(unreadCount);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatsAdapter", "Unread messages listener cancelled", error.toException());
                updateUnreadBadge(holder, 0);
                unreadCountCache.put(chatId, 0);
            }
        };

        FirebaseDatabase.getInstance().getReference("Chats")
                .child(chatId)
                .child("messages")
                .addValueEventListener(unreadMessagesListener);

        unreadListeners.put(chatId, unreadMessagesListener);
    }

    private void setupGroupUnreadMessagesListener(String groupId, ChatViewHolder holder) {
        String listenerKey = "group_" + groupId;

        if (unreadListeners.containsKey(listenerKey)) {
            FirebaseDatabase.getInstance().getReference("Groups")
                    .child(groupId)
                    .child("messages")
                    .removeEventListener(unreadListeners.get(listenerKey));
        }

        ValueEventListener groupUnreadListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int unreadCount = 0;

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        if (ownerId != null &&
                                !ownerId.equals(currentUserId) &&
                                (isRead == null || !isRead)) {
                            unreadCount++;
                        }
                    }
                }

                // Сохраняем в кэш
                unreadCountCache.put(groupId, unreadCount);

                // Обновляем UI только если ViewHolder все еще на правильной позиции
                Integer currentPosition = viewHolderPositions.get(groupId);
                if (currentPosition != null && holder.getAdapterPosition() == currentPosition) {
                    Object currentTag = holder.itemView.getTag();
                    if (currentTag == null || !currentTag.equals(unreadCount)) {
                        updateUnreadBadge(holder, unreadCount);
                        holder.itemView.setTag(unreadCount);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("ChatsAdapter", "Group unread messages listener cancelled", error.toException());
                updateUnreadBadge(holder, 0);
                unreadCountCache.put(groupId, 0);
            }
        };

        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("messages")
                .addValueEventListener(groupUnreadListener);

        unreadListeners.put(listenerKey, groupUnreadListener);
    }

    private void updateUnreadBadge(ChatViewHolder holder, int unreadCount) {
        try {
            if (unreadCount > 0) {
                holder.message_count_badge.setVisibility(View.VISIBLE);
                holder.message_count_badge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));

                holder.last_message_tv.setTextColor(0xFF000000);
                holder.last_message_tv.setTypeface(holder.last_message_tv.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                holder.message_count_badge.setVisibility(View.GONE);

                holder.last_message_tv.setTextColor(0xFF757575);
                holder.last_message_tv.setTypeface(holder.last_message_tv.getTypeface(), android.graphics.Typeface.NORMAL);
            }
        } catch (Exception e) {
            Log.e("ChatsAdapter", "Error updating badge", e);
        }
    }

    private void markMessagesAsRead(Chat chat) {
        if (chat.isGroup()) {
            markGroupMessagesAsRead(chat.getChat_id());
        } else {
            markChatMessagesAsRead(chat.getChat_id());
        }
    }

    private void markChatMessagesAsRead(String chatId) {
        FirebaseDatabase.getInstance().getReference("Chats")
                .child(chatId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            if (ownerId != null && !ownerId.equals(currentUserId) && (isRead == null || !isRead)) {
                                messageSnapshot.getRef().child("isRead").setValue(true);
                            }
                        }

                        // Обновляем кэш после отметки как прочитанные
                        unreadCountCache.put(chatId, 0);

                        // Принудительно обновляем UI для этого чата
                        Integer position = viewHolderPositions.get(chatId);
                        if (position != null && position >= 0 && position < chats.size()) {
                            notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("ChatsAdapter", "Error marking messages as read", error.toException());
                    }
                });
    }

    private void markGroupMessagesAsRead(String groupId) {
        FirebaseDatabase.getInstance().getReference("Groups")
                .child(groupId)
                .child("messages")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                            if (ownerId != null && !ownerId.equals(currentUserId) && (isRead == null || !isRead)) {
                                messageSnapshot.getRef().child("isRead").setValue(true);
                            }
                        }

                        // Обновляем кэш после отметки как прочитанные
                        unreadCountCache.put(groupId, 0);

                        // Принудительно обновляем UI для этой группы
                        Integer position = viewHolderPositions.get(groupId);
                        if (position != null && position >= 0 && position < chats.size()) {
                            notifyItemChanged(position);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("ChatsAdapter", "Error marking group messages as read", error.toException());
                    }
                });
    }

    private void loadGroupAvatar(ChatViewHolder holder, String groupId, int position) {
        final int currentPosition = position;

        // Проверяем кэш
        String cachedAvatar = avatarCache.get("group_" + groupId);
        if (cachedAvatar != null) {
            loadAvatarWithGlide(holder, cachedAvatar, currentPosition);
            return;
        }

        FirebaseDatabase.getInstance().getReference().child("Groups").child(groupId)
                .child("groupImage").get()
                .addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DataSnapshot> task) {
                        try {
                            if (holder.getAdapterPosition() != currentPosition) {
                                return;
                            }

                            if (task.isSuccessful() && task.getResult() != null && task.getResult().getValue() != null) {
                                String groupImageUrl = task.getResult().getValue().toString();

                                if (groupImageUrl != null && !groupImageUrl.isEmpty()) {
                                    avatarCache.put("group_" + groupId, groupImageUrl);
                                    loadAvatarWithGlide(holder, groupImageUrl, currentPosition);
                                } else {
                                    holder.profile_iv.setImageResource(R.drawable.artem);
                                }
                            } else {
                                holder.profile_iv.setImageResource(R.drawable.artem);
                            }
                        } catch (Exception e) {
                            holder.profile_iv.setImageResource(R.drawable.artem);
                        }
                    }
                });
    }

    private void loadAvatarWithGlide(ChatViewHolder holder, String imageUrl, int position) {
        if (holder.getAdapterPosition() != position) {
            return;
        }

        Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .circleCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.artem)
                .error(R.drawable.artem)
                .dontAnimate()
                .into(holder.profile_iv);
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public void cleanup() {
        for (ValueEventListener listener : unreadListeners.values()) {
            try {
                FirebaseDatabase.getInstance().getReference().removeEventListener(listener);
            } catch (Exception e) {
                Log.e("ChatsAdapter", "Error removing listener", e);
            }
        }
        unreadListeners.clear();
        avatarCache.clear();
        unreadCountCache.clear();
        viewHolderPositions.clear();
        previousChatNames.clear();
        previousChatImages.clear();
        previousLastMessages.clear();
    }

    @Override
    public void onViewRecycled(@NonNull ChatViewHolder holder) {
        super.onViewRecycled(holder);
        holder.itemView.setTag(null);
    }

    public void updateChat(Chat updatedChat) {
        updateOrAddChatWithMicroUpdate(updatedChat);
    }

    public void forceRefreshUnreadCount(String chatId) {
        unreadCountCache.remove(chatId);
        Integer position = viewHolderPositions.get(chatId);
        if (position != null && position >= 0 && position < chats.size()) {
            notifyItemChanged(position);
        }
    }

    // Обновляем предыдущие данные при полном обновлении списка
    public void updateAllChats(ArrayList<Chat> newChats) {
        this.chats = newChats;
        initializePreviousData();
        notifyDataSetChanged();
    }
}