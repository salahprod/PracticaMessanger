package com.example.androidmessage1.chats;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.ChatActivity;
import com.example.androidmessage1.R;
import com.example.androidmessage1.groups.GroupChatActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class ChatsAdapter extends RecyclerView.Adapter<ChatViewHolder> {

    private ArrayList<Chat> chats;
    private String currentUserId;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(int position);
    }

    public ChatsAdapter(ArrayList<Chat> chats, OnChatClickListener listener){
        this.chats = chats;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.listener = listener;
    }

    // Старый конструктор для обратной совместимости
    public ChatsAdapter(ArrayList<Chat> chats){
        this.chats = chats;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
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

        // Устанавливаем имя чата/группы
        holder.username_tv.setText(chat.getChat_name());

        // Устанавливаем последнее сообщение
        String lastMessage = chat.getLastMessage();
        if (lastMessage != null && !lastMessage.isEmpty()) {
            holder.last_message_tv.setText(lastMessage);
            holder.last_message_tv.setVisibility(View.VISIBLE);
        } else {
            holder.last_message_tv.setText("No messages");
            holder.last_message_tv.setVisibility(View.VISIBLE);
        }

        // Отображаем количество непрочитанных сообщений
        int unreadCount = chat.getUnreadCount();
        if (unreadCount > 0) {
            holder.message_count_badge.setVisibility(View.VISIBLE);
            holder.message_count_badge.setText(String.valueOf(unreadCount));
            if (unreadCount > 99) {
                holder.message_count_badge.setText("99+");
            }

            // Подсвечиваем последнее сообщение если есть непрочитанные
            holder.last_message_tv.setTextColor(holder.itemView.getContext().getColor(android.R.color.black));
            holder.last_message_tv.setTypeface(holder.last_message_tv.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            holder.message_count_badge.setVisibility(View.GONE);

            // Обычный стиль для прочитанных сообщений
            holder.last_message_tv.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
            holder.last_message_tv.setTypeface(holder.last_message_tv.getTypeface(), android.graphics.Typeface.NORMAL);
        }

        // Сбрасываем аватарку перед загрузкой
        holder.profile_iv.setImageResource(R.drawable.artem);

        if (chat.isGroup()) {
            // Для групп загружаем аватарку группы
            loadGroupAvatar(holder, chat.getChat_id(), position);
        } else {
            // Для обычных чатов загружаем аватарку пользователя
            loadUserAvatar(holder, chat.getOther_user_id(), position);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatClick(position);
            } else {
                // Старая логика для обратной совместимости
                if (chat.isGroup()) {
                    Intent intent = new Intent(holder.itemView.getContext(), GroupChatActivity.class);
                    intent.putExtra("groupId", chat.getChat_id());
                    intent.putExtra("groupName", chat.getChat_name());
                    holder.itemView.getContext().startActivity(intent);
                } else {
                    Intent intent = new Intent(holder.itemView.getContext(), ChatActivity.class);
                    intent.putExtra("chatId", chat.getChat_id());
                    intent.putExtra("otherUserId", chat.getOther_user_id());
                    holder.itemView.getContext().startActivity(intent);
                }
            }
        });
    }

    private void loadUserAvatar(ChatViewHolder holder, String userId, int position) {
        final int currentPosition = position;

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
                                    Glide.with(holder.itemView.getContext())
                                            .load(profileImageUrl)
                                            .placeholder(R.drawable.artem)
                                            .error(R.drawable.artem)
                                            .into(holder.profile_iv);
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
                                    Glide.with(holder.itemView.getContext())
                                            .load(groupImageUrl)
                                            .placeholder(R.drawable.artem)
                                            .error(R.drawable.artem)
                                            .into(holder.profile_iv);
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

    @Override
    public int getItemCount() {
        return chats.size();
    }

    // Метод для обновления списка чатов
    public void updateChats(ArrayList<Chat> newChats) {
        this.chats.clear();
        this.chats.addAll(newChats);
        notifyDataSetChanged();
    }
}