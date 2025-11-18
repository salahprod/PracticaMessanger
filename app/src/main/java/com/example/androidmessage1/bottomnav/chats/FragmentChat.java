package com.example.androidmessage1.bottomnav.chats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.androidmessage1.chats.Chat;
import com.example.androidmessage1.chats.ChatsAdapter;
import com.example.androidmessage1.databinding.FragmentChatsBinding;

import java.util.ArrayList;

public class FragmentChat  extends Fragment {
    private FragmentChatsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChatsBinding.inflate(inflater,container, false);



        loadChats();

        return binding.getRoot();
    }

    private void loadChats(){
        ArrayList<Chat> chats = new ArrayList<Chat>();
        chats.add(new Chat("123","1216783","23445567","Test chat 1"));
        chats.add(new Chat("124","1267583","23244567","Test chat 2"));
        chats.add(new Chat("125","1267843","23344567","Test chat 3"));

        binding.chatsRv.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.chatsRv.setAdapter(new ChatsAdapter(chats));
    }
}
