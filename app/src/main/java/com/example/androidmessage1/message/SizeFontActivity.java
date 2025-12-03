package com.example.androidmessage1.message;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.androidmessage1.R;

public class SizeFontActivity extends AppCompatActivity {

    private ImageView sliderCircle;
    private FrameLayout scaleArea;
    private View verticalLine;
    private View markTop;
    private View markBottom;
    private View scaleLine;

    private float minY = 0f;
    private float maxY = 0f;
    private float initialY;
    private float circleTopMargin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.size_font);

        Log.d("SizeFontActivity", "Activity created");

        // Инициализация View элементов
        initializeViews();

        // Проверяем, что все элементы найдены
        if (sliderCircle == null) {
            Log.e("SizeFontActivity", "sliderCircle is NULL!");
            return;
        }

        // Кнопка назад
        ImageView backArrow = findViewById(R.id.back_arrow);
        if (backArrow != null) {
            backArrow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        // Ждем отрисовки layout
        if (scaleArea != null) {
            scaleArea.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    scaleArea.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    setupSlider();
                }
            });
        }
    }

    private void initializeViews() {
        try {
            sliderCircle = findViewById(R.id.slider_circle);
            scaleArea = findViewById(R.id.scale_area);
            verticalLine = findViewById(R.id.vertical_line);
            markTop = findViewById(R.id.mark_top);
            markBottom = findViewById(R.id.mark_bottom);
            scaleLine = findViewById(R.id.scale_line);

            Log.d("SizeFontActivity", "Views initialized: " +
                    "sliderCircle=" + (sliderCircle != null) +
                    ", scaleArea=" + (scaleArea != null) +
                    ", markTop=" + (markTop != null) +
                    ", markBottom=" + (markBottom != null));

        } catch (Exception e) {
            Log.e("SizeFontActivity", "Error finding views: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupSlider() {
        if (markTop == null || markBottom == null || sliderCircle == null) {
            Log.e("SizeFontActivity", "Some views are null, cannot setup slider");
            Toast.makeText(this, "Error: Cannot setup slider", Toast.LENGTH_LONG).show();
            return;
        }

        // Получаем позиции меток относительно контейнера scaleArea
        int topPos = markTop.getTop();
        int bottomPos = markBottom.getTop();

        Log.d("SizeFontActivity", "Top position: " + topPos + ", Bottom position: " + bottomPos);

        // Устанавливаем границы движения ползунка
        minY = topPos;
        maxY = bottomPos - sliderCircle.getHeight();

        Log.d("SizeFontActivity", "MinY: " + minY + ", MaxY: " + maxY + ", Slider height: " + sliderCircle.getHeight());

        // Загружаем сохраненный размер шрифта
        float savedFontSize = FontSizeManager.getFontSize(this);
        Log.d("SizeFontActivity", "Saved font size: " + savedFontSize);

        // Устанавливаем начальную позицию ползунка
        setSliderPositionFromFontSize(savedFontSize);

        // Настраиваем обработчики касания
        setupTouchListeners();
    }

    private void setSliderPositionFromFontSize(float fontSize) {
        if (minY == 0 && maxY == 0) {
            Log.e("SizeFontActivity", "minY or maxY not initialized");
            return;
        }

        // Ограничиваем размер шрифта
        if (fontSize < 10) fontSize = 10;
        if (fontSize > 24) fontSize = 24;

        // Конвертируем шрифт в позицию (10-24sp в диапазон minY-maxY)
        float normalized = (fontSize - 10) / 14f; // 10-24 = 14 единиц
        float position = minY + normalized * (maxY - minY);

        // Устанавливаем позицию
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sliderCircle.getLayoutParams();
        params.topMargin = (int) position;
        sliderCircle.setLayoutParams(params);

        Log.d("SizeFontActivity", "Set slider position to: " + position + " for font size: " + fontSize);
    }

    private void setupTouchListeners() {
        // Обработчик для ползунка
        sliderCircle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sliderCircle.getLayoutParams();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialY = event.getRawY();
                        circleTopMargin = params.topMargin;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - initialY;
                        float newPosition = circleTopMargin + deltaY;

                        // Ограничиваем движение
                        if (newPosition < minY) newPosition = minY;
                        if (newPosition > maxY) newPosition = maxY;

                        // Обновляем позицию
                        params.topMargin = (int) newPosition;
                        sliderCircle.setLayoutParams(params);

                        // Обновляем размер шрифта
                        float fontSize = getFontSizeFromPosition(newPosition);
                        FontSizeManager.saveFontSize(SizeFontActivity.this, fontSize);
                        break;

                    case MotionEvent.ACTION_UP:
                        float finalPosition = params.topMargin;
                        float finalFontSize = getFontSizeFromPosition(finalPosition);
                        FontSizeManager.saveFontSize(SizeFontActivity.this, finalFontSize);

                        Toast.makeText(SizeFontActivity.this,
                                "Размер шрифта: " + (int)finalFontSize + "sp",
                                Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });

        // Обработчик для вертикальной линии
        if (verticalLine != null) {
            verticalLine.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    // Получаем координаты касания внутри контейнера scaleArea
                    float touchY = event.getY();

                    // Рассчитываем новую позицию для ползунка (центрируем)
                    float newPosition = touchY - sliderCircle.getHeight() / 2f;

                    if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                        // Ограничиваем движение
                        if (newPosition < minY) newPosition = minY;
                        if (newPosition > maxY) newPosition = maxY;

                        // Обновляем позицию
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) sliderCircle.getLayoutParams();
                        params.topMargin = (int) newPosition;
                        sliderCircle.setLayoutParams(params);

                        // Сохраняем размер шрифта
                        float fontSize = getFontSizeFromPosition(newPosition);
                        FontSizeManager.saveFontSize(SizeFontActivity.this, fontSize);

                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        float finalFontSize = getFontSizeFromPosition(newPosition);
                        FontSizeManager.saveFontSize(SizeFontActivity.this, finalFontSize);

                        Toast.makeText(SizeFontActivity.this,
                                "Размер шрифта: " + (int)finalFontSize + "sp",
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private float getFontSizeFromPosition(float position) {
        if (minY == maxY) {
            Log.e("SizeFontActivity", "minY == maxY, returning default 14sp");
            return 14f;
        }

        float normalized = (position - minY) / (maxY - minY);
        float fontSize = 10 + normalized * 14;

        // Округляем до целого
        fontSize = Math.round(fontSize);

        Log.d("SizeFontActivity", "Position " + position + " -> Font size " + fontSize);
        return fontSize;
    }
}