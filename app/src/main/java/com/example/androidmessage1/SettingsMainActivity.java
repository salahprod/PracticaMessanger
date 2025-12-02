package com.example.androidmessage1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.androidmessage1.message.SizeFontActivity;

public class SettingsMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Убедитесь, что имя файла макета совпадает с созданным XML
        setContentView(R.layout.setting_main);

        // Находим элементы
        ImageView backButton = findViewById(R.id.back_button);
        View changeTopicsLayout = findViewById(R.id.change_topics_layout);
        ImageView changeSizeArrow = findViewById(R.id.change_size_arrow);

        View statisticsLayout = findViewById(R.id.statistics_layout);

        // Обработчик нажатия на кнопку "Назад" (совместимый подход)
        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Используем OnBackPressedDispatcher для совместимости
                    getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }

        // Также настраиваем обработчик жеста "назад" через диспетчер
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Выполняем стандартное поведение - закрываем активность
                if (isEnabled()) {
                    finish();
                }
            }
        });

        // Обработчик нажатия на всю область "Change Size Font"
        if (changeTopicsLayout != null) {
            changeTopicsLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    navigateToSizeFontActivity();
                }
            });
        }

        // Обработчик нажатия на стрелку "Change Size Font"
        if (changeSizeArrow != null) {
            changeSizeArrow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    navigateToSizeFontActivity();
                }
            });
        }



        // Обработчик нажатия на "Statistics"
        if (statisticsLayout != null) {
            statisticsLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(SettingsMainActivity.this, "Statistics clicked", Toast.LENGTH_SHORT).show();
                    // Здесь можно добавить логику для статистики
                }
            });
        }
    }

    private void navigateToSizeFontActivity() {
        Intent intent = new Intent(SettingsMainActivity.this, SizeFontActivity.class);
        startActivity(intent);
    }
}