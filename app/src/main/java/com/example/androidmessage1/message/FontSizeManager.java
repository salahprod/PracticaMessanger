package com.example.androidmessage1.message;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class FontSizeManager {
    private static final String PREF_NAME = "font_size_prefs";
    private static final String KEY_FONT_SIZE_PREFIX = "font_size_";
    private static final float DEFAULT_FONT_SIZE = 14f;

    // Получаем ID текущего пользователя
    private static String getCurrentUserId(Context context) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            return currentUser.getUid();
        }
        return "anonymous";
    }

    // Генерируем уникальный ключ для текущего пользователя
    private static String getFontSizeKey(Context context) {
        return KEY_FONT_SIZE_PREFIX + getCurrentUserId(context);
    }

    public static void saveFontSize(Context context, float fontSize) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(getFontSizeKey(context), fontSize);
        editor.apply();
    }

    public static float getFontSize(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = getFontSizeKey(context);

        // Для обратной совместимости: если нет настроек для текущего пользователя,
        // пробуем получить старые общие настройки
        if (!preferences.contains(key)) {
            float oldFontSize = preferences.getFloat("font_size_sp", DEFAULT_FONT_SIZE);
            // Сохраняем старые настройки как персональные для пользователя
            if (oldFontSize != DEFAULT_FONT_SIZE) {
                saveFontSize(context, oldFontSize);
            }
            return oldFontSize;
        }

        return preferences.getFloat(key, DEFAULT_FONT_SIZE);
    }

    public static float getDefaultFontSize() {
        return DEFAULT_FONT_SIZE;
    }
}