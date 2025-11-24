package com.example.androidmessage1.users;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserViewHolder> {

    private ArrayList<User> usersList;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(int position);
    }

    public UsersAdapter(ArrayList<User> usersList, OnUserClickListener listener) {
        this.usersList = usersList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.person_item_rv, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = usersList.get(position);

        // Устанавливаем имя пользователя
        holder.username_tv.setText(user.getUsername());

        // Устанавливаем последнее сообщение (если есть)
        if (user.getLastMessage() != null && !user.getLastMessage().isEmpty()) {
            holder.last_message_tv.setText(user.getLastMessage());
            holder.last_message_tv.setVisibility(View.VISIBLE);
        } else {
            holder.last_message_tv.setText("");
            holder.last_message_tv.setVisibility(View.VISIBLE);
        }

        // Устанавливаем количество непрочитанных сообщений
        if (user.getUnreadCount() > 0) {
            holder.message_count_badge.setVisibility(View.VISIBLE);
            holder.message_count_badge.setText(String.valueOf(user.getUnreadCount()));
            if (user.getUnreadCount() > 99) {
                holder.message_count_badge.setText("99+");
            }
        } else {
            holder.message_count_badge.setVisibility(View.GONE);
        }

        // Загружаем аватарку
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(user.getProfileImage())
                    .placeholder(R.drawable.artem)
                    .error(R.drawable.artem)
                    .into(holder.profile_iv);
        } else {
            holder.profile_iv.setImageResource(R.drawable.artem);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return usersList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        CircleImageView profile_iv;
        TextView username_tv;
        TextView last_message_tv;
        TextView message_count_badge;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            profile_iv = itemView.findViewById(R.id.profile_iv);
            username_tv = itemView.findViewById(R.id.username_tv);
            last_message_tv = itemView.findViewById(R.id.last_message_tv);
            message_count_badge = itemView.findViewById(R.id.message_count_badge);
        }
    }
}