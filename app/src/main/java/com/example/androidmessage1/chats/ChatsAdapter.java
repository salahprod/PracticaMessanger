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
    private HashMap<String, String> avatarCache; // Кэш для аватарок
    private HashMap<String, Integer> unreadCountCache; // Кэш для непрочитанных сообщений
    private HashMap<String, Integer> viewHolderPositions; // Отслеживание позиций ViewHolder

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
    }

    public ChatsAdapter(ArrayList<Chat> chats){
        this.chats = chats;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.unreadListeners = new HashMap<>();
        this.avatarCache = new HashMap<>();
        this.unreadCountCache = new HashMap<>();
        this.viewHolderPositions = new HashMap<>();
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
            // Если нет в кэше, устанавливаем 0 и запускаем слушатель
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

        // Загружаем аватарку с кэшированием
        if (chat.isGroup()) {
            loadGroupAvatar(holder, chatId, position);
        } else {
            loadUserAvatar(holder, chat.getOther_user_id(), position);
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

                Log.d("ChatsAdapter", "Checking unread messages in chat: " + chatId);

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        // ВАЖНО: Считаем только сообщения от других пользователей, которые не прочитаны
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
                    // Обновляем UI только если количество изменилось
                    Object currentTag = holder.itemView.getTag();
                    if (currentTag == null || !currentTag.equals(unreadCount)) {
                        updateUnreadBadge(holder, unreadCount);
                        holder.itemView.setTag(unreadCount);
                    }
                }

                Log.d("ChatsAdapter", "Unread messages in chat " + chatId + ": " + unreadCount);
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

                Log.d("ChatsAdapter", "Checking unread messages in group: " + groupId);

                if (snapshot.exists()) {
                    for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                        String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                        Boolean isRead = messageSnapshot.child("isRead").getValue(Boolean.class);

                        // ВАЖНО: Считаем только сообщения от других пользователей, которые не прочитаны
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
                    // Обновляем UI только если количество изменилось
                    Object currentTag = holder.itemView.getTag();
                    if (currentTag == null || !currentTag.equals(unreadCount)) {
                        updateUnreadBadge(holder, unreadCount);
                        holder.itemView.setTag(unreadCount);
                    }
                }

                Log.d("ChatsAdapter", "Unread messages in group " + groupId + ": " + unreadCount);
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

                // Черный цвет и жирный шрифт для непрочитанных
                holder.last_message_tv.setTextColor(0xFF000000);
                holder.last_message_tv.setTypeface(holder.last_message_tv.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                holder.message_count_badge.setVisibility(View.GONE);

                // Серый цвет и обычный шрифт для прочитанных
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

                        // ВАЖНО: Обновляем кэш после отметки как прочитанные
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

                        // ВАЖНО: Обновляем кэш после отметки как прочитанные
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

    private void loadUserAvatar(ChatViewHolder holder, String userId, int position) {
        final int currentPosition = position;

        // Проверяем кэш
        String cachedAvatar = avatarCache.get("user_" + userId);
        if (cachedAvatar != null) {
            // Используем кэшированную аватарку
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
                                    avatarCache.put("user_" + userId, profileImageUrl);
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

    private void loadGroupAvatar(ChatViewHolder holder, String groupId, int position) {
        final int currentPosition = position;

        // Проверяем кэш
        String cachedAvatar = avatarCache.get("group_" + groupId);
        if (cachedAvatar != null) {
            // Используем кэшированную аватарку
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
                                    // Сохраняем в кэш
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
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Кэшируем на диске
                .placeholder(R.drawable.artem)
                .error(R.drawable.artem)
                .dontAnimate() // Отключаем анимацию для предотвращения мерцания
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
        avatarCache.clear(); // Очищаем кэш
        unreadCountCache.clear(); // Очищаем кэш непрочитанных
        viewHolderPositions.clear(); // Очищаем позиции
    }

    @Override
    public void onViewRecycled(@NonNull ChatViewHolder holder) {
        super.onViewRecycled(holder);
        // Очищаем тег при переиспользовании ViewHolder
        holder.itemView.setTag(null);
    }

    // Метод для обновления конкретного чата
    public void updateChat(Chat updatedChat) {
        for (int i = 0; i < chats.size(); i++) {
            Chat chat = chats.get(i);
            if (chat.getChat_id().equals(updatedChat.getChat_id())) {
                chats.set(i, updatedChat);
                notifyItemChanged(i);
                break;
            }
        }
    }

    // Метод для принудительного обновления счетчика непрочитанных
    public void forceRefreshUnreadCount(String chatId) {
        unreadCountCache.remove(chatId);
        Integer position = viewHolderPositions.get(chatId);
        if (position != null && position >= 0 && position < chats.size()) {
            notifyItemChanged(position);
        }
    }
}