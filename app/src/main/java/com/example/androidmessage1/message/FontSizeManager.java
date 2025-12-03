package com.example.androidmessage1.message;

import android.content.Context;
import android.content.SharedPreferences;

public class FontSizeManager {
    private static final String PREF_NAME = "font_size_prefs";
    private static final String KEY_FONT_SIZE = "font_size_sp";
    private static final float DEFAULT_FONT_SIZE = 14f;

    public static void saveFontSize(Context context, float fontSize) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat(KEY_FONT_SIZE, fontSize);
        editor.apply();
    }

    public static float getFontSize(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return preferences.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }

    public static float getDefaultFontSize() {
        return DEFAULT_FONT_SIZE;
    }
}