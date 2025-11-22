package com.example.androidmessage1;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.databinding.ActivityChatBinding;
import com.example.androidmessage1.message.Message;
import com.example.androidmessage1.message.MessageAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadMessages(); // Добавлен вызов метода
    }

    private void loadMessages(){
        String chatId = getIntent().getStringExtra("chatId");
        if(chatId == null) return;

        FirebaseDatabase.getInstance().getReference().child("Chats")
                .child(chatId).child("messages").addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if(!snapshot.exists()) return;

                        List<Message> messages = new ArrayList<>();
                        for (DataSnapshot messageSnapshot : snapshot.getChildren()){
                            String messageId = messageSnapshot.getKey();
                            String ownerId = messageSnapshot.child("ownerId").getValue(String.class);
                            String text = messageSnapshot.child("text").getValue(String.class);
                            String date = messageSnapshot.child("date").getValue(String.class);

                            messages.add(new Message(messageId, ownerId, text, date));
                        }

                        binding.messagesRv.setLayoutManager(new LinearLayoutManager(ChatActivity.this)); // Исправлен контекст
                        binding.messagesRv.setAdapter(new MessageAdapter(messages));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }
}