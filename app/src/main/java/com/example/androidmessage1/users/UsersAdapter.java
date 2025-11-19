package com.example.androidmessage1.users;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;

import java.util.ArrayList;

public class UsersAdapter extends RecyclerView.Adapter<UserViewHolder> {
    private ArrayList<User> users = new ArrayList<>();
    private OnUserClickListener listener;

    // ✅ ДОБАВЬ этот интерфейс
    public interface OnUserClickListener {
        void onUserClick(int position);
    }

    // ✅ ОБНОВИ конструктор
    public UsersAdapter(ArrayList<User> users, OnUserClickListener listener) {
        this.users = users;
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
        User user = users.get(position);
        holder.username_tv.setText(user.username);

        if(!user.profileImage.isEmpty()){
            Glide.with(holder.itemView.getContext()).load(user.profileImage).into(holder.profileImage_iv);
        }

        // ✅ ОБНОВИ: добавляем обработчик клика
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }
}