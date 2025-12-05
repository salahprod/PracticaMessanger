package com.example.androidmessage1.message;

import android.util.Log;

public class MyLogger {
    private static final String DEFAULT_TAG = "MyLogger";

    // Метод для логирования с тегом по умолчанию
    public static void d(String message) {
        Log.d(DEFAULT_TAG, message);
    }

    // Метод для логирования с кастомным тегом
    public static void d(String tag, String message) {
        Log.d(tag, message);
    }

    // Метод для логирования ошибок с тегом по умолчанию
    public static void e(String message) {
        Log.e(DEFAULT_TAG, message);
    }

    // Метод для логирования ошибок с кастомным тегом
    public static void e(String tag, String message) {
        Log.e(tag, message);
    }

    // Метод для информационного логирования
    public static void i(String tag, String message) {
        Log.i(tag, message);
    }

    // Метод для предупреждений
    public static void w(String tag, String message) {
        Log.w(tag, message);
    }

    // Метод для подробного логирования
    public static void v(String tag, String message) {
        Log.v(tag, message);
    }

    // Метод с исключением
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
    }
}