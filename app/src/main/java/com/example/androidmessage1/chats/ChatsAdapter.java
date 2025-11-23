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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class ChatsAdapter extends RecyclerView.Adapter<ChatViewHolder> {

    private ArrayList<Chat> chats;

    public ChatsAdapter(ArrayList<Chat> chats){
        this.chats = chats;
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

        // Устанавливаем имя чата
        holder.username_tv.setText(chat.getChat_name());

        // Отображаем количество непрочитанных сообщений
        int unreadCount = chat.getUnreadCount();
        if (unreadCount > 0) {
            holder.message_count_badge.setVisibility(View.VISIBLE);
            holder.message_count_badge.setText(String.valueOf(unreadCount));
            if (unreadCount > 99) {
                holder.message_count_badge.setText("99+");
            }
        } else {
            holder.message_count_badge.setVisibility(View.GONE);
        }

        // Сбрасываем аватарку перед загрузкой
        holder.profile_iv.setImageResource(R.drawable.artem);

        String userId;
        if (!chat.getOther_user_id().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
            userId = chat.getOther_user_id();
        } else {
            userId = chat.getCurrent_user_id();
        }

        final int currentPosition = position;
        final String currentUserId = userId;

        // Загрузка аватарки
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

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(holder.itemView.getContext(), ChatActivity.class);
            intent.putExtra("chatId", chat.getChat_id());
            intent.putExtra("otherUserId", chat.getOther_user_id());
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }
}