package com.example.androidmessage1.message;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.androidmessage1.R;
import com.example.androidmessage1.message.FontSizeManager;

public class SizeFontActivity extends AppCompatActivity {

    private ImageView sliderCircle;
    private float initialY;
    private float circleTopMargin;
    private float minY = 567.5f; // Минимальная позиция Y (самый маленький шрифт)
    private float maxY = 888.5f; // Максимальная позиция Y (самый большой шрифт)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.size_font);

        sliderCircle = findViewById(R.id.slider_circle);
        ImageView backArrow = findViewById(R.id.back_arrow);

        // Начальная позиция ползунка из сохраненных настроек
        float savedFontSize = FontSizeManager.getFontSize(this);
        float initialPosition = getPositionFromFontSize(savedFontSize);
        updateSliderPosition(initialPosition);

        // Обработчик нажатия на кнопку "Назад"
        backArrow.setOnClickListener(v -> onBackPressed());

        // Обработчик перемещения ползунка
        sliderCircle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        circleTopMargin = getCurrentSliderPosition();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - initialY;
                        float newPosition = circleTopMargin + deltaY;

                        // Ограничиваем движение ползунка
                        if (newPosition < minY) newPosition = minY;
                        if (newPosition > maxY) newPosition = maxY;

                        updateSliderPosition(newPosition);

                        // Обновляем размер шрифта в реальном времени
                        float fontSize = getFontSizeFromPosition(newPosition);
                        FontSizeManager.saveFontSize(SizeFontActivity.this, fontSize);
                        break;
                    case MotionEvent.ACTION_UP:
                        // Сохраняем окончательное значение
                        float finalPosition = getCurrentSliderPosition();
                        float finalFontSize = getFontSizeFromPosition(finalPosition);
                        FontSizeManager.saveFontSize(SizeFontActivity.this, finalFontSize);

                        // Показываем уведомление
                        Toast.makeText(SizeFontActivity.this,
                                "Font size set to " + String.format("%.0f", finalFontSize) + "sp",
                                Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });
    }

    private float getCurrentSliderPosition() {
        // Получаем текущую позицию из параметров макета
        return ((android.widget.FrameLayout.LayoutParams) sliderCircle.getLayoutParams()).topMargin;
    }

    private void updateSliderPosition(float position) {
        // Обновляем позицию ползунка
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) sliderCircle.getLayoutParams();
        params.topMargin = Math.round(position);
        sliderCircle.setLayoutParams(params);
    }

    private float getFontSizeFromPosition(float position) {
        // Конвертируем позицию Y в размер шрифта
        // Диапазон позиций: 327.5 (min) - 615.5 (max)
        // Диапазон шрифтов: 10sp (min) - 24sp (max)
        float normalized = (position - minY) / (maxY - minY);
        return 10 + normalized * 14; // 10-24sp
    }

    private float getPositionFromFontSize(float fontSize) {
        // Конвертируем размер шрифта в позицию Y
        // Ограничиваем размер шрифта в диапазоне 10-24sp
        if (fontSize < 10) fontSize = 10;
        if (fontSize > 24) fontSize = 24;

        float normalized = (fontSize - 10) / 14;
        return minY + normalized * (maxY - minY);
    }
}