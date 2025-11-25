package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.androidmessage1.bottomnav.chats.FragmentChat;
import com.example.androidmessage1.bottomnav.new_chat.NewChatFragment;
import com.example.androidmessage1.bottomnav.profile.ProfileFragment;
import com.example.androidmessage1.databinding.ActivityMainBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;

        });

        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Устанавливаем пользователя онлайн при запуске приложения
        setUserOnline();

        getSupportFragmentManager().beginTransaction().replace(binding.fragmentContainer.getId(),new FragmentChat()).commit();
        binding.bottomNav.setSelectedItemId(R.id.chats);

        Map<Integer,Fragment> fragmentMap = new HashMap<>();
        fragmentMap.put(R.id.chats,new FragmentChat());
        fragmentMap.put(R.id.new_chat,new NewChatFragment());
        fragmentMap.put(R.id.profile,new ProfileFragment());

        binding.bottomNav.setOnItemSelectedListener(item-> {
            Fragment fragment = fragmentMap.get(item.getItemId());
            getSupportFragmentManager().beginTransaction().replace(binding.fragmentContainer.getId(),fragment).commit();
            return true;
        });
    }

    private void setUserOnline() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (currentUserId != null) {
            // Устанавливаем статус онлайн
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .setValue(true);

            // Устанавливаем автоматическое отключение при разрыве соединения
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .setValue(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем статус онлайн при возвращении в приложение
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            setUserOnline();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Не устанавливаем offline здесь, onDisconnect позаботится об этом
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // При выходе из приложения отменяем onDisconnect и устанавливаем offline
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            // Отменяем onDisconnect
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .onDisconnect()
                    .cancel();

            // Устанавливаем статус offline
            FirebaseDatabase.getInstance().getReference("Users")
                    .child(currentUserId)
                    .child("isOnline")
                    .setValue(false);
        }
    }
}