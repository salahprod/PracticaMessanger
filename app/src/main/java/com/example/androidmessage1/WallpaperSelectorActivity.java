package com.example.androidmessage1;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidmessage1.databinding.ActivityWallpaperSelectorBinding;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.util.ArrayList;
import java.util.List;

public class WallpaperSelectorActivity extends AppCompatActivity implements WallpaperAdapter.OnWallpaperClickListener {

    private ActivityWallpaperSelectorBinding binding;
    private WallpaperAdapter wallpaperAdapter;
    private List<WallpaperItem> wallpaperItems;
    private String chatId;
    private SharedPreferences sharedPreferences;

    private static final String WALLPAPER_PREFS = "chat_wallpaper_prefs";
    private static final String WALLPAPER_KEY_PREFIX = "wallpaper_";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWallpaperSelectorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatId = getIntent().getStringExtra("chatId");
        if (chatId == null) {
            Toast.makeText(this, "Chat ID not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sharedPreferences = getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE);

        initializeViews();
        loadWallpapers();
        loadSelectedWallpaper();
    }

    private void initializeViews() {
        // Настройка RecyclerView
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        binding.wallpaperGridRv.setLayoutManager(layoutManager);

        // Создаем список обоев
        wallpaperItems = new ArrayList<>();
        wallpaperAdapter = new WallpaperAdapter(wallpaperItems, this);
        binding.wallpaperGridRv.setAdapter(wallpaperAdapter);

        // Кнопка назад
        binding.backBtn.setOnClickListener(v -> finish());

        // Кнопка очистки обоев
        binding.clearBtn.setOnClickListener(v -> {
            clearChatWallpaper(); // Исправлено имя метода
            Toast.makeText(this, "Wallpaper cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadWallpapers() {
        // Добавляем все доступные обои (wall_paper1.svg - wall_paper10.svg)
        wallpaperItems.clear();

        String[] wallpaperNames = {
                "Wallpaper 1", "Wallpaper 2", "Wallpaper 3", "Wallpaper 4", "Wallpaper 5",
                "Wallpaper 6", "Wallpaper 7", "Wallpaper 8", "Wallpaper 9", "Wallpaper 10"
        };

        String[] drawableNames = {
                "wall_paper1", "wall_paper2", "wall_paper3", "wall_paper4", "wall_paper5",
                "wall_paper6", "wall_paper7", "wall_paper8", "wall_paper9", "wall_paper10"
        };

        for (int i = 0; i < wallpaperNames.length; i++) {
            String drawableName = drawableNames[i];
            int resId = getResources().getIdentifier(drawableName, "drawable", getPackageName());

            if (resId != 0) {
                wallpaperItems.add(new WallpaperItem(wallpaperNames[i], resId, drawableName));
            }
        }

        wallpaperAdapter.notifyDataSetChanged();
    }

    private void loadSelectedWallpaper() {
        String selectedWallpaper = sharedPreferences.getString(WALLPAPER_KEY_PREFIX + chatId, null);
        if (selectedWallpaper != null) {
            // Находим позицию выбранных обоев
            for (int i = 0; i < wallpaperItems.size(); i++) {
                if (wallpaperItems.get(i).getResourceName().equals(selectedWallpaper)) {
                    wallpaperAdapter.setSelectedPosition(i);
                    binding.clearBtn.setVisibility(View.VISIBLE);
                    break;
                }
            }
        }
    }

    @Override
    public void onWallpaperClick(WallpaperItem wallpaperItem, int position) {
        // Сохраняем выбор обоев
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(WALLPAPER_KEY_PREFIX + chatId, wallpaperItem.getResourceName());
        editor.apply();

        wallpaperAdapter.setSelectedPosition(position);
        binding.clearBtn.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Wallpaper set: " + wallpaperItem.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClearWallpaper() {
        clearChatWallpaper(); // Исправлено имя метода
    }

    // ИЗМЕНЕНО ИМЯ МЕТОДА (было clearWallpaper, стало clearChatWallpaper)
    private void clearChatWallpaper() {
        // Очищаем сохраненные обои
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(WALLPAPER_KEY_PREFIX + chatId);
        editor.apply();

        wallpaperAdapter.clearSelection();
        binding.clearBtn.setVisibility(View.GONE);

        Toast.makeText(this, "Wallpaper cleared", Toast.LENGTH_SHORT).show();
    }
}