package com.example.androidmessage1.message;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidmessage1.R;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder>{

    private List<Message> messages;

    public MessageAdapter(List<Message> messages){
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.messageTv.setText(message.getText());
        holder.dateTv.setText(message.getDate());

        // ГРУППИРОВКА СООБЩЕНИЙ - УБИРАЕМ ЛИШНИЕ ОТСТУПЫ
        if (position > 0) {
            Message previousMessage = messages.get(position - 1);
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : "";

            // Если предыдущее сообщение от того же пользователя
            if (previousMessage.getOwnerId().equals(message.getOwnerId())) {
                // Убираем отступы между сообщениями от одного пользователя
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
                if (params != null) {
                    params.topMargin = 1; // Минимальный отступ
                    holder.itemView.setLayoutParams(params);
                }
            } else {
                // Сообщения от разных пользователей - обычный отступ
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
                if (params != null) {
                    params.topMargin = 8; // Отступ между сообщениями разных пользователей
                    holder.itemView.setLayoutParams(params);
                }
            }
        } else {
            // Первое сообщение - обычный отступ
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
            if (params != null) {
                params.topMargin = 8;
                holder.itemView.setLayoutParams(params);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        // Ваши сообщения будут использовать message_from_curr_user_rv_item.xml (справа)
        if(messages.get(position).getOwnerId().equals(currentUserId))
            return R.layout.message_from_curr_user_rv_item;
        else
            return R.layout.message_rv_item; // Сообщения других пользователей (слева)
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder{

        TextView messageTv, dateTv;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTv = itemView.findViewById(R.id.message_tv);
            dateTv = itemView.findViewById(R.id.message_date_tv);
        }
    }
}