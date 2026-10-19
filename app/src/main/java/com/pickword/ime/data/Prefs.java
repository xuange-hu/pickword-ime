package com.pickword.ime.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** 全局设置项。全部保存在应用私有 SharedPreferences，不落云端。 */
public final class Prefs {

    private static final String NAME = "pickword";
    private static final String K_GLOSS = "gloss_enable";
    private static final String K_GLOSS_POS = "gloss_position";
    private static final String K_VIBRATE = "vibrate";
    private static final String K_THEME = "theme";
    private static final String K_KEY_HEIGHT = "key_height";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int KEY_HEIGHT_LOW = 0;
    public static final int KEY_HEIGHT_NORMAL = 1;
    public static final int KEY_HEIGHT_HIGH = 2;

    private static volatile Prefs sInstance;
    private final SharedPreferences mSp;

    private Prefs(Context context) {
        mSp = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static Prefs get(Context context) {
        if (sInstance == null) {
            synchronized (Prefs.class) {
                if (sInstance == null) {
                    sInstance = new Prefs(context);
                }
            }
        }
        return sInstance;
    }

    // ---- 译词 ----

    public boolean isGlossEnabled() {
        return mSp.getBoolean(K_GLOSS, true);
    }

    public void setGlossEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_GLOSS, enabled).apply();
    }

    /** true = 译词显示在候选右侧；false = 显示在候选上方（候选区更高更窄） */
    public boolean isGlossOnRight() {
        return !"top".equals(mSp.getString(K_GLOSS_POS, "right"));
    }

    public void setGlossOnRight(boolean onRight) {
        mSp.edit().putString(K_GLOSS_POS, onRight ? "right" : "top").apply();
    }

    // ---- 手感 ----

    public boolean isVibrateEnabled() {
        return mSp.getBoolean(K_VIBRATE, false);
    }

    public void setVibrateEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_VIBRATE, enabled).apply();
    }

    // ---- 外观 ----

    public int getTheme() {
        return mSp.getInt(K_THEME, THEME_SYSTEM);
    }

    public void setTheme(int theme) {
        mSp.edit().putInt(K_THEME, theme).apply();
        applyTheme(theme);
    }

    public static void applyTheme(int theme) {
        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public int getKeyHeight() {
        return mSp.getInt(K_KEY_HEIGHT, KEY_HEIGHT_NORMAL);
    }

    public void setKeyHeight(int height) {
        mSp.edit().putInt(K_KEY_HEIGHT, height).apply();
    }

    /** 单行按键高度（dp）：矮 40 / 标准 48 / 高 56 */
    public float getKeyHeightDp() {
        switch (getKeyHeight()) {
            case KEY_HEIGHT_LOW:
                return 40f;
            case KEY_HEIGHT_HIGH:
                return 56f;
            default:
                return 48f;
        }
    }
}
