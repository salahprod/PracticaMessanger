package com.example.androidmessage1.groups.messages;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class GroupMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<GroupMessage> messages;
    private String currentUserId;

    private static final int TYPE_MY_MESSAGE = 1;
    private static final int TYPE_OTHER_MESSAGE = 2;

    public GroupMessageAdapter(List<GroupMessage> messages){
        this.messages = messages;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
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

        } else if (holder instanceof OtherMessageViewHolder) {
            OtherMessageViewHolder otherHolder = (OtherMessageViewHolder) holder;
            otherHolder.messageTv.setText(message.getText());
            otherHolder.dateTv.setText(message.getDate());

            // Устанавливаем имя отправителя
            if (message.getSenderName() != null && !message.getSenderName().isEmpty()) {
                otherHolder.senderNameTv.setText(message.getSenderName());
            } else {
                otherHolder.senderNameTv.setText("User");
            }

            // Загружаем аватарку
            if (message.getSenderAvatar() != null && !message.getSenderAvatar().isEmpty()) {
                Glide.with(otherHolder.itemView.getContext())
                        .load(message.getSenderAvatar())
                        .placeholder(R.drawable.artem)
                        .error(R.drawable.artem)
                        .into(otherHolder.senderAvatarIv);
            } else {
                otherHolder.senderAvatarIv.setImageResource(R.drawable.artem);
            }

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