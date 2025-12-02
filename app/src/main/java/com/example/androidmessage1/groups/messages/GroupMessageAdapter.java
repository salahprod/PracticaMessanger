package com.example.androidmessage1.groups.messages;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.message.FontSizeManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class GroupMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<GroupMessage> messages;
    private String currentUserId;
    private String groupId;
    private Context context;
    private float currentFontSize;

    private static final int TYPE_MY_MESSAGE = 1;
    private static final int TYPE_OTHER_MESSAGE = 2;

    public GroupMessageAdapter(List<GroupMessage> messages, String groupId, Context context){
        this.messages = messages;
        this.groupId = groupId;
        this.context = context;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
        this.currentFontSize = FontSizeManager.getFontSize(context);
    }

    // Старый конструктор для обратной совместимости
    public GroupMessageAdapter(List<GroupMessage> messages, String groupId){
        this.messages = messages;
        this.groupId = groupId;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
        this.currentFontSize = 14; // Значение по умолчанию
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_MY_MESSAGE) {
            // Ваши сообщения - используем message_from_curr_user_rv_item.xml (справа)
            View view = inflater.inflate(R.layout.message_from_curr_user_rv_item, parent, false);
            return new MyMessageViewHolder(view);
        } else {
            // Сообщения других - используем group_message_rv_item.xml (слева с аватаркой)
            View view = inflater.inflate(R.layout.group_message_rv_item, parent, false);
            return new OtherMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupMessage message = messages.get(position);

        if (holder instanceof MyMessageViewHolder) {
            MyMessageViewHolder myHolder = (MyMessageViewHolder) holder;
            myHolder.messageTv.setText(message.getText());
            myHolder.dateTv.setText(message.getDate());

            // ПРИМЕНЯЕМ РАЗМЕР ШРИФТА К ТЕКСТУ СООБЩЕНИЯ
            myHolder.messageTv.setTextSize(currentFontSize);

        } else if (holder instanceof OtherMessageViewHolder) {
            OtherMessageViewHolder otherHolder = (OtherMessageViewHolder) holder;
            otherHolder.messageTv.setText(message.getText());
            otherHolder.dateTv.setText(message.getDate());

            // ПРИМЕНЯЕМ РАЗМЕР ШРИФТА К ТЕКСТУ СООБЩЕНИЯ
            otherHolder.messageTv.setTextSize(currentFontSize);

            // Загружаем данные отправителя с учетом кастомных настроек
            loadSenderDataWithCustomSettings(message.getOwnerId(), message.getSenderName(),
                    message.getSenderAvatar(), otherHolder);

            // Логика отображения аватарки и имени
            boolean showAvatarAndName = shouldShowAvatarAndName(position);

            if (showAvatarAndName) {
                otherHolder.senderAvatarIv.setVisibility(View.VISIBLE);
                otherHolder.senderNameTv.setVisibility(View.VISIBLE);
            } else {
                otherHolder.senderAvatarIv.setVisibility(View.INVISIBLE);
                otherHolder.senderNameTv.setVisibility(View.GONE);
            }
        }
    }

    // Метод для загрузки данных отправителя с учетом кастомных настроек
    private void loadSenderDataWithCustomSettings(String senderId, String originalSenderName,
                                                  String originalSenderAvatar, OtherMessageViewHolder holder) {
        if (senderId == null) {
            holder.senderNameTv.setText("User");
            holder.senderAvatarIv.setImageResource(R.drawable.artem);
            return;
        }

        // Сначала проверяем кастомные настройки
        FirebaseDatabase.getInstance().getReference("UserCustomizations")
                .child(currentUserId)
                .child("chatContacts")
                .child(senderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot customSnapshot) {
                        // Используем final переменные для хранения данных
                        final String[] customName = {null};
                        final String[] customAvatar = {null};

                        if (customSnapshot.exists()) {
                            customName[0] = customSnapshot.child("customName").getValue(String.class);
                            customAvatar[0] = customSnapshot.child("customImage").getValue(String.class);
                            Log.d("GroupMessageAdapter", "Custom settings found for sender " + senderId +
                                    ": name=" + customName[0] + ", avatar=" + customAvatar[0]);
                        }

                        // Применяем кастомные настройки
                        String displayName;
                        String displayAvatar;

                        if (customName[0] != null && !customName[0].isEmpty()) {
                            displayName = customName[0];
                            Log.d("GroupMessageAdapter", "Using CUSTOM name for sender: " + customName[0]);
                        } else if (originalSenderName != null && !originalSenderName.isEmpty()) {
                            displayName = originalSenderName;
                            Log.d("GroupMessageAdapter", "Using ORIGINAL name for sender: " + originalSenderName);
                        } else {
                            displayName = "User";
                            Log.d("GroupMessageAdapter", "Using DEFAULT name for sender");
                        }

                        if (customAvatar[0] != null && !customAvatar[0].isEmpty()) {
                            displayAvatar = customAvatar[0];
                            Log.d("GroupMessageAdapter", "Using CUSTOM avatar for sender: " + customAvatar[0]);
                        } else if (originalSenderAvatar != null && !originalSenderAvatar.isEmpty()) {
                            displayAvatar = originalSenderAvatar;
                            Log.d("GroupMessageAdapter", "Using ORIGINAL avatar for sender: " + originalSenderAvatar);
                        } else {
                            displayAvatar = "";
                            Log.d("GroupMessageAdapter", "Using DEFAULT avatar for sender");
                        }

                        // Обновляем UI
                        holder.senderNameTv.setText(displayName);

                        if (displayAvatar != null && !displayAvatar.isEmpty()) {
                            Glide.with(holder.itemView.getContext())
                                    .load(displayAvatar)
                                    .placeholder(R.drawable.artem)
                                    .error(R.drawable.artem)
                                    .into(holder.senderAvatarIv);
                        } else {
                            holder.senderAvatarIv.setImageResource(R.drawable.artem);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("GroupMessageAdapter", "Failed to load custom settings for sender: " + senderId, error.toException());
                        // При ошибке используем оригинальные данные
                        holder.senderNameTv.setText(originalSenderName != null ? originalSenderName : "User");

                        if (originalSenderAvatar != null && !originalSenderAvatar.isEmpty()) {
                            Glide.with(holder.itemView.getContext())
                                    .load(originalSenderAvatar)
                                    .placeholder(R.drawable.artem)
                                    .error(R.drawable.artem)
                                    .into(holder.senderAvatarIv);
                        } else {
                            holder.senderAvatarIv.setImageResource(R.drawable.artem);
                        }
                    }
                });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public int getItemViewType(int position) {
        GroupMessage message = messages.get(position);

        if (message.getOwnerId().equals(currentUserId)) {
            return TYPE_MY_MESSAGE; // Ваше сообщение - будет справа
        } else {
            return TYPE_OTHER_MESSAGE; // Сообщение другого пользователя - будет слева
        }
    }

    // Логика определения, показывать ли аватарку и имя для чужих сообщений
    private boolean shouldShowAvatarAndName(int position) {
        if (position == 0) {
            return true; // Первое сообщение всегда показываем с аватаркой и именем
        }

        GroupMessage currentMessage = messages.get(position);
        GroupMessage previousMessage = messages.get(position - 1);

        // Если предыдущее сообщение от другого пользователя, показываем аватарку и имя
        if (!previousMessage.getOwnerId().equals(currentMessage.getOwnerId())) {
            return true;
        }

        // Если прошло много времени между сообщениями, показываем аватарку и имя
        // Здесь можно добавить проверку времени, если нужно

        return false;
    }

    // Метод для обновления размера шрифта
    public void updateFontSize() {
        if (context != null) {
            this.currentFontSize = FontSizeManager.getFontSize(context);
        }
        notifyDataSetChanged(); // Обновляем все сообщения с новым размером шрифта
    }

    // ViewHolder для ваших сообщений (справа) - message_from_curr_user_rv_item.xml
    static class MyMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTv, dateTv;

        public MyMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTv = itemView.findViewById(R.id.message_tv);
            dateTv = itemView.findViewById(R.id.message_date_tv);
        }
    }

    // ViewHolder для чужих сообщений (слева) - group_message_rv_item.xml
    static class OtherMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageTv, dateTv, senderNameTv;
        CircleImageView senderAvatarIv;

        public OtherMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTv = itemView.findViewById(R.id.message_tv);
            dateTv = itemView.findViewById(R.id.message_date_tv);
            senderNameTv = itemView.findViewById(R.id.sender_name_tv);
            senderAvatarIv = itemView.findViewById(R.id.sender_avatar_iv);
        }
    }
}