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
        }

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
}