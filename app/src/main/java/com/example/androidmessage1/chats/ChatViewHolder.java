package com.example.androidmessage1.chats;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidmessage1.R;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChatViewHolder extends RecyclerView.ViewHolder {

    public CircleImageView profile_iv;
    public TextView username_tv;
    public TextView message_count_badge;

    public ChatViewHolder(@NonNull View itemView) {
        super(itemView);
        profile_iv = itemView.findViewById(R.id.profile_iv);
        username_tv = itemView.findViewById(R.id.username_tv);
        message_count_badge = itemView.findViewById(R.id.message_count_badge);
    }
}