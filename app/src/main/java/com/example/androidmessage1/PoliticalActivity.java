package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PoliticalActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.political_activity);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //Находим кнопку назад
        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> {
            //Получаем откуда пришли
            String from = getIntent().getStringExtra("from");

            Intent intent;
            if ("login".equals(from)) {
                intent = new Intent(PoliticalActivity.this, LoginActivity.class);
            } else if ("register".equals(from)) {
                intent = new Intent(PoliticalActivity.this, RegisterActivity.class);
            } else {
                //Просто закрываем текущую Activity
                finish();
                return;
            }

            startActivity(intent);
            finish(); //Чтобы убрать PoliticalActivity из стека
        });
    }
}
