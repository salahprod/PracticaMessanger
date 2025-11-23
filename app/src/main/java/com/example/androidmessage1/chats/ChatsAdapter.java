package com.example.androidmessage1.chats;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.androidmessage1.ChatActivity;
import com.example.androidmessage1.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class ChatsAdapter extends RecyclerView.Adapter<ChatViewHolder> {

    private ArrayList<Chat> chats;

    public ChatsAdapter(ArrayList<Chat> chats){
        this.chats = chats;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.person_item_rv,parent,false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Chat chat = chats.get(position);

        // ✅ Устанавливаем имя чата
        holder.chat_name_tv.setText(chat.getChat_name());

        // ✅ СБРАСЫВАЕМ АВАТАРКУ ПЕред загрузкой
        holder.chat_iv.setImageResource(R.drawable.artem);

        String userId;
        if(!chat.getUserId1().equals(FirebaseAuth.getInstance().getCurrentUser().getUid())){
            userId = chat.getUserId1();
        }
        else {
            userId = chat.getUserId2();
        }

        // ✅ Сохраняем позицию в final переменную
        final int currentPosition = position;
        final String currentUserId = userId;

        // ✅ Безопасная загрузка аватарки с ПРОВЕРКОЙ ПОЗИЦИИ
        FirebaseDatabase.getInstance().getReference().child("Users").child(userId)
                .child("profileImage").get()
                .addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DataSnapshot> task) {
                        try {
                            // ✅ ПРОВЕРЯЕМ ЧТО ЭТОТ HOLDER ВСЕ ЕЩЕ НА ТОЙ ЖЕ ПОЗИЦИИ
                            if (holder.getAdapterPosition() != currentPosition) {
                                return;
                            }

                            if (task.isSuccessful() && task.getResult() != null && task.getResult().getValue() != null) {
                                String profileImageUrl = task.getResult().getValue().toString();

                                if(profileImageUrl != null && !profileImageUrl.isEmpty()) {
                                    // ✅ ЗАГРУЖАЕМ АВАТАРКУ С ОБРАБОТКОЙ ОШИБОК
                                    Glide.with(holder.itemView.getContext())
                                            .load(profileImageUrl)
                                            .placeholder(R.drawable.artem)
                                            .error(R.drawable.artem)
                                            .into(holder.chat_iv);
                                } else {
                                    // ✅ ЕСЛИ ССЫЛКА ПУСТАЯ - СТАВИМ ДЕФОЛТНУЮ
                                    holder.chat_iv.setImageResource(R.drawable.artem);
                                }
                            } else {
                                // ✅ ЕСЛИ ДАННЫХ НЕТ - СТАВИМ ДЕФОЛТНУЮ
                                holder.chat_iv.setImageResource(R.drawable.artem);
                            }
                        } catch (Exception e) {
                            // ✅ ПРИ ЛЮБОЙ ОШИБКЕ - СТАВИМ ДЕФОЛТНУЮ
                            holder.chat_iv.setImageResource(R.drawable.artem);
                            System.out.println("Failed to load profile image: " + e.getMessage());
                        }
                    }
                });

        holder.itemView.setOnClickListener(v ->{
            Intent intent = new Intent(holder.itemView.getContext(), ChatActivity.class);
            intent.putExtra("chatId", chat.getChat_id());
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }
}