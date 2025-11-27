package com.example.androidmessage1.message;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidmessage1.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder>{

    private List<Message> messages;
    private String chatId;
    private String otherUserId;

    public MessageAdapter(List<Message> messages, String chatId, String otherUserId){
        this.messages = messages;
        this.chatId = chatId;
        this.otherUserId = otherUserId;
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

        // Загружаем кастомные настройки для отображения
        loadCustomSettings(holder, message);

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

    private void loadCustomSettings(@NonNull MessageViewHolder holder, Message message) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Проверяем, что это сообщение от другого пользователя и у нас есть chatId
        if (chatId != null && !message.getOwnerId().equals(currentUserId)) {
            FirebaseDatabase.getInstance().getReference("UserChatSettings")
                    .child(currentUserId)
                    .child(chatId)
                    .child("customSettings")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String customName = snapshot.child("customName").getValue(String.class);
                                String customImage = snapshot.child("customImage").getValue(String.class);

                                // Здесь можно добавить логику для отображения кастомного имени
                                // если в layout сообщения есть TextView для имени
                                // Например:
                                // if (customName != null && !customName.isEmpty()) {
                                //     holder.senderName.setText(customName);
                                // }

                                // Для кастомной аватарки нужно добавить ImageView в layout
                                // и использовать Glide для загрузки изображения
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            // Используем оригинальные данные
                        }
                    });
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

    // Метод для обновления chatId и otherUserId если нужно
    public void updateChatData(String chatId, String otherUserId) {
        this.chatId = chatId;
        this.otherUserId = otherUserId;
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