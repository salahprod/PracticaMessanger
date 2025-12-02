package com.example.androidmessage1.groups;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.R;
import com.example.androidmessage1.databinding.PersonItemRvBinding;
import com.example.androidmessage1.message.FontSizeManager;

import java.util.ArrayList;

public class GroupsAdapter extends RecyclerView.Adapter<GroupsAdapter.GroupViewHolder> {

    private ArrayList<Group> groups;
    private OnGroupClickListener listener;
    private Context context;
    private float currentFontSize;

    public interface OnGroupClickListener {
        void onGroupClick(int position);
    }

    public GroupsAdapter(ArrayList<Group> groups, OnGroupClickListener listener, Context context) {
        this.groups = groups;
        this.listener = listener;
        this.context = context;
        this.currentFontSize = FontSizeManager.getFontSize(context);
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

        // Применяем размер шрифта к тексту сообщения
        holder.binding.lastMessageTv.setTextSize(currentFontSize);

        // Set last message with file type support
        String lastMessage = group.getLastMessage();
        String lastMessageSender = group.getLastMessageSender();

        if (lastMessage != null && !lastMessage.isEmpty()) {
            // Форматируем последнее сообщение с учетом типа контента
            String displayMessage = formatLastMessage(lastMessage, lastMessageSender);
            holder.binding.lastMessageTv.setText(displayMessage);
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

        // Set last message time if available
        if (group.getLastMessage() != null && !group.getLastMessage().isEmpty()) {
            holder.binding.lastMessageTv.setText(group.getLastMessage());
            holder.binding.lastMessageTv.setVisibility(View.VISIBLE);
        } else {
            holder.binding.lastMessageTv.setText("No messages yet");
            holder.binding.lastMessageTv.setVisibility(View.VISIBLE);
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

    /**
     * Форматирует последнее сообщение для отображения в списке чатов
     * с поддержкой различных типов контента
     */
    private String formatLastMessage(String message, String sender) {
        if (sender != null && !sender.isEmpty()) {
            // Если сообщение содержит эмодзи файлов (фото, видео, файлы), показываем тип контента
            if (message.contains("📷") || message.contains("🎥") || message.contains("📎")) {
                return sender + ": " + getMessageTypeDisplay(message);
            }
            return sender + ": " + message;
        }

        // Для сообщений без отправителя, просто возвращаем сообщение
        if (message.contains("📷") || message.contains("🎥") || message.contains("📎")) {
            return getMessageTypeDisplay(message);
        }

        return message;
    }

    /**
     * Возвращает читаемое описание типа сообщения для файлов
     */
    private String getMessageTypeDisplay(String message) {
        if (message.contains("📷")) {
            if (message.contains("Photo") || message.contains("Image")) {
                return "Photo";
            }
            return "Image";
        } else if (message.contains("🎥")) {
            if (message.contains("Video")) {
                return "Video";
            }
            return "Video";
        } else if (message.contains("📎")) {
            if (message.contains("File:")) {
                // Извлекаем имя файла если есть
                int fileIndex = message.indexOf("File:");
                if (fileIndex != -1) {
                    String fileName = message.substring(fileIndex + 5).trim();
                    if (fileName.length() > 15) {
                        fileName = fileName.substring(0, 12) + "...";
                    }
                    return "File: " + fileName;
                }
            }
            return "File";
        }
        return message;
    }

    /**
     * Форматирует время сообщения для отображения
     */
    private String formatMessageTime(long timestamp) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - timestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "now";
        } else if (minutes < 60) {
            return minutes + "m";
        } else if (hours < 24) {
            return hours + "h";
        } else if (days < 7) {
            return days + "d";
        } else {
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault());
            return dateFormat.format(new java.util.Date(timestamp));
        }
    }

    /**
     * Обновляет список групп
     */
    public void updateGroups(ArrayList<Group> newGroups) {
        this.groups = newGroups;
        notifyDataSetChanged();
    }

    /**
     * Добавляет группу в список
     */
    public void addGroup(Group group) {
        groups.add(group);
        notifyItemInserted(groups.size() - 1);
    }

    /**
     * Удаляет группу из списка
     */
    public void removeGroup(int position) {
        if (position >= 0 && position < groups.size()) {
            groups.remove(position);
            notifyItemRemoved(position);
        }
    }

    /**
     * Обновляет конкретную группу
     */
    public void updateGroup(int position, Group group) {
        if (position >= 0 && position < groups.size()) {
            groups.set(position, group);
            notifyItemChanged(position);
        }
    }

    /**
     * Возвращает группу по позиции
     */
    public Group getGroup(int position) {
        if (position >= 0 && position < groups.size()) {
            return groups.get(position);
        }
        return null;
    }

    /**
     * Очищает все группы
     */
    public void clearGroups() {
        int size = groups.size();
        groups.clear();
        notifyItemRangeRemoved(0, size);
    }

    // Метод для обновления размера шрифта
    public void updateFontSize() {
        this.currentFontSize = FontSizeManager.getFontSize(context);
        notifyDataSetChanged(); // Обновляем все сообщения с новым размером шрифта
    }

    public static class GroupViewHolder extends RecyclerView.ViewHolder {
        PersonItemRvBinding binding;

        public GroupViewHolder(@NonNull PersonItemRvBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // Добавляем возможность долгого нажатия
            itemView.setOnLongClickListener(v -> {
                // Можно добавить обработку долгого нажатия при необходимости
                return true;
            });
        }
    }
}