package com.example.androidmessage1.groups.members;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.PersonItemRvBinding;

import java.util.List;

public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.MemberViewHolder> {

    private List<GroupMember> members;
    private OnMemberClickListener listener;

    public interface OnMemberClickListener {
        void onMemberClick(GroupMember member);
        void onMemberLongClick(GroupMember member);
    }

    public GroupMemberAdapter(List<GroupMember> members, OnMemberClickListener listener) {
        this.members = members;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PersonItemRvBinding binding = PersonItemRvBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MemberViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        GroupMember member = members.get(position);
        holder.bind(member);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberClick(member);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onMemberLongClick(member);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return members != null ? members.size() : 0;
    }

    public void updateMembers(List<GroupMember> newMembers) {
        this.members = newMembers;
        notifyDataSetChanged();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        private PersonItemRvBinding binding;

        public MemberViewHolder(@NonNull PersonItemRvBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(GroupMember member) {
            if (member.getUsername() != null && !member.getUsername().isEmpty()) {
                binding.usernameTv.setText(member.getUsername());
            } else {
                binding.usernameTv.setText("Unknown User");
            }

            String roleText = getRoleDisplayText(member.getRole());
            String statusText = member.getStatus() != null ? member.getStatus() : "offline";

            if (!roleText.isEmpty()) {
                binding.lastMessageTv.setText(roleText + " • " + statusText);
            } else {
                binding.lastMessageTv.setText(statusText);
            }

            binding.lastMessageTv.setVisibility(View.VISIBLE);
            binding.messageCountBadge.setVisibility(View.GONE);

            if (member.getProfileImage() != null && !member.getProfileImage().isEmpty()) {
                Glide.with(binding.getRoot().getContext())
                        .load(member.getProfileImage())
                        .placeholder(R.drawable.artem)
                        .error(R.drawable.artem)
                        .into(binding.profileIv);
            } else {
                binding.profileIv.setImageResource(R.drawable.artem);
            }
        }

        private String getRoleDisplayText(String role) {
            if (role == null) return "";

            switch (role) {
                case "owner":
                    return "👑 Owner";
                case "admin":
                    return "⭐ Admin";
                case "member":
                default:
                    return "";
            }
        }
    }
}