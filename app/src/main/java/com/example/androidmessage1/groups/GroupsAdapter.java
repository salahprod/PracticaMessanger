package com.example.androidmessage1.groups;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.PersonItemRvBinding;

import java.util.ArrayList;

public class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {

    private ArrayList<Group> groups;
    private OnGroupClickListener listener;

    public interface OnGroupClickListener {
        void onGroupClick(int position);
    }

    public GroupsAdapter(ArrayList<Group> groups, OnGroupClickListener listener) {
        this.groups = groups;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PersonItemRvBinding binding = PersonItemRvBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new GroupViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        Group group = groups.get(position);

        holder.binding.usernameTv.setText(group.getGroupName());

        // Set last message
        if (group.getLastMessage() != null && !group.getLastMessage().isEmpty()) {
            holder.binding.lastMessageTv.setText(group.getLastMessage());
            holder.binding.lastMessageTv.setVisibility(View.VISIBLE);
        } else {
            holder.binding.lastMessageTv.setText("No messages yet");
            holder.binding.lastMessageTv.setVisibility(View.VISIBLE);
        }

        // Set unread count
        int unreadCount = group.getUnreadCount();
        if (unreadCount > 0) {
            holder.binding.messageCountBadge.setVisibility(View.VISIBLE);
            holder.binding.messageCountBadge.setText(String.valueOf(unreadCount));
            if (unreadCount > 99) {
                holder.binding.messageCountBadge.setText("99+");
            }
        } else {
            holder.binding.messageCountBadge.setVisibility(View.GONE);
        }

        // Load group image
        if (group.getGroupImage() != null && !group.getGroupImage().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(group.getGroupImage())
                    .placeholder(R.drawable.artem)
                    .error(R.drawable.artem)
                    .into(holder.binding.profileIv);
        } else {
            holder.binding.profileIv.setImageResource(R.drawable.artem);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGroupClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    public static class GroupViewHolder extends RecyclerView.ViewHolder {
        PersonItemRvBinding binding;

        public GroupViewHolder(@NonNull PersonItemRvBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}