package com.pickword.ime.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.pickword.ime.theme.Skin;

/**
 * 全局设置。全部只写本地 SharedPreferences，不出设备。
 *
 * <p>分四组：外观（皮肤/字体/形态）、输入（布局/手势/反馈）、学习（译词）、数据。
 */
public final class Prefs {

    private static final String NAME = "pickword";

    // 外观
    private static final String K_SKIN = "skin_id";
    private static final String K_ACCENT = "accent";
    private static final String K_SKIN_DARK = "skin_dark";
    private static final String K_BG_IMAGE = "bg_image";
    private static final String K_BG_ALPHA = "bg_alpha";
    private static final String K_RADIUS = "radius";
    private static final String K_KEY_GAP = "key_gap";
    private static final String K_KEY_TEXT = "key_text_size";
    private static final String K_CAND_TEXT = "cand_text_size";
    private static final String K_GLOSS_TEXT = "gloss_text_size";
    private static final String K_CAND_HEIGHT = "cand_height";
    private static final String K_TYPEFACE = "typeface";
    private static final String K_KEY_HEIGHT = "key_height";
    private static final String K_THEME = "theme";

    // 输入
    private static final String K_LAYOUT = "keyboard_layout";
    private static final String K_NUMBER_ROW = "number_row";
    private static final String K_ONE_HAND = "one_hand";
    private static final String K_SOUND = "sound";
    private static final String K_VIBRATE = "vibrate";
    private static final String K_SWIPE_SPACE = "swipe_space";
    private static final String K_SWIPE_DELETE = "swipe_delete";
    private static final String K_GLOSS = "gloss_enable";
    private static final String K_GLOSS_POS = "gloss_position";
    private static final String K_FLOATING = "floating_candidates";

    // 智能
    private static final String K_FUZZY = "fuzzy_pinyin";
    private static final String K_CONTEXT = "context_predict";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int KEY_HEIGHT_LOW = 0;
    public static final int KEY_HEIGHT_NORMAL = 1;
    public static final int KEY_HEIGHT_HIGH = 2;

    public static final int ONE_HAND_OFF = 0;
    public static final int ONE_HAND_LEFT = 1;
    public static final int ONE_HAND_RIGHT = 2;

    public static final String LAYOUT_QWERTY = "qwerty";
    public static final String LAYOUT_T9 = "t9";

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

    // ---------------- 外观 ----------------

    public String getSkinId() {
        return mSp.getString(K_SKIN, Skin.CLASSIC.id);
    }

    public void setSkinId(String id) {
        mSp.edit().putString(K_SKIN, id).apply();
    }

    public int getAccent() {
        return mSp.getInt(K_ACCENT, 0xFF0E7C66);
    }

    public void setAccent(int color) {
        mSp.edit().putInt(K_ACCENT, color).apply();
    }

    public boolean isSkinDark() {
        return mSp.getBoolean(K_SKIN_DARK, false);
    }

    public void setSkinDark(boolean dark) {
        mSp.edit().putBoolean(K_SKIN_DARK, dark).apply();
    }

    /** 当前皮肤：预设直接取，"custom" 由主色派生 */
    public Skin getSkin() {
        String id = getSkinId();
        if ("custom".equals(id)) {
            return Skin.fromAccent("自定义", getAccent(), isSkinDark());
        }
        return Skin.byId(id);
    }

    /** 背景图路径（应用私有目录），空串表示纯色背景 */
    public String getBackgroundImage() {
        return mSp.getString(K_BG_IMAGE, "");
    }

    public void setBackgroundImage(String path) {
        mSp.edit().putString(K_BG_IMAGE, path == null ? "" : path).apply();
    }

    /** 背景图不透明度 0–100 */
    public int getBackgroundAlpha() {
        return mSp.getInt(K_BG_ALPHA, 100);
    }

    public void setBackgroundAlpha(int alpha) {
        mSp.edit().putInt(K_BG_ALPHA, Math.max(0, Math.min(100, alpha))).apply();
    }

    /** 按键圆角（dp） */
    public int getRadiusDp() {
        return mSp.getInt(K_RADIUS, 6);
    }

    public void setRadiusDp(int dp) {
        mSp.edit().putInt(K_RADIUS, Math.max(0, Math.min(14, dp))).apply();
    }

    /** 按键间距（dp） */
    public int getKeyGapDp() {
        return mSp.getInt(K_KEY_GAP, 3);
    }

    public void setKeyGapDp(int dp) {
        mSp.edit().putInt(K_KEY_GAP, Math.max(0, Math.min(8, dp))).apply();
    }

    public int getKeyTextSp() {
        return mSp.getInt(K_KEY_TEXT, 20);
    }

    public void setKeyTextSp(int sp) {
        mSp.edit().putInt(K_KEY_TEXT, Math.max(12, Math.min(30, sp))).apply();
    }

    public int getCandidateTextSp() {
        return mSp.getInt(K_CAND_TEXT, 19);
    }

    public void setCandidateTextSp(int sp) {
        mSp.edit().putInt(K_CAND_TEXT, Math.max(12, Math.min(32, sp))).apply();
    }

    public int getGlossTextSp() {
        return mSp.getInt(K_GLOSS_TEXT, 11);
    }

    public void setGlossTextSp(int sp) {
        mSp.edit().putInt(K_GLOSS_TEXT, Math.max(8, Math.min(20, sp))).apply();
    }

    /** 候选栏高度（dp） */
    public int getCandidateHeightDp() {
        return mSp.getInt(K_CAND_HEIGHT, 44);
    }

    public void setCandidateHeightDp(int dp) {
        mSp.edit().putInt(K_CAND_HEIGHT, Math.max(32, Math.min(72, dp))).apply();
    }

    public String getTypeface() {
        return mSp.getString(K_TYPEFACE, "sans");
    }

    public void setTypeface(String value) {
        mSp.edit().putString(K_TYPEFACE, value).apply();
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
                AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    // ---------------- 输入 ----------------

    public String getKeyboardLayout() {
        return mSp.getString(K_LAYOUT, LAYOUT_QWERTY);
    }

    public void setKeyboardLayout(String layout) {
        mSp.edit().putString(K_LAYOUT, layout).apply();
    }

    public boolean isNumberRowEnabled() {
        return mSp.getBoolean(K_NUMBER_ROW, false);
    }

    public void setNumberRowEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_NUMBER_ROW, enabled).apply();
    }

    public int getOneHand() {
        return mSp.getInt(K_ONE_HAND, ONE_HAND_OFF);
    }

    public void setOneHand(int mode) {
        mSp.edit().putInt(K_ONE_HAND, mode).apply();
    }

    public boolean isSoundEnabled() {
        return mSp.getBoolean(K_SOUND, false);
    }

    public void setSoundEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_SOUND, enabled).apply();
    }

    public boolean isVibrateEnabled() {
        return mSp.getBoolean(K_VIBRATE, false);
    }

    public void setVibrateEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_VIBRATE, enabled).apply();
    }

    /** 空格上左右滑动移动光标 */
    public boolean isSwipeSpaceEnabled() {
        return mSp.getBoolean(K_SWIPE_SPACE, true);
    }

    public void setSwipeSpaceEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_SWIPE_SPACE, enabled).apply();
    }

    /** 退格左滑整词删除 */
    public boolean isSwipeDeleteEnabled() {
        return mSp.getBoolean(K_SWIPE_DELETE, true);
    }

    public void setSwipeDeleteEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_SWIPE_DELETE, enabled).apply();
    }

    public boolean isGlossEnabled() {
        return mSp.getBoolean(K_GLOSS, true);
    }

    public void setGlossEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_GLOSS, enabled).apply();
    }

    /** true = 译词在候选右侧；false = 译词在候选下方 */
    public boolean isGlossOnRight() {
        return !"top".equals(mSp.getString(K_GLOSS_POS, "right"));
    }

    public void setGlossOnRight(boolean onRight) {
        mSp.edit().putString(K_GLOSS_POS, onRight ? "right" : "top").apply();
    }

    /** 候选栏悬浮在输入区上方（走系统候选视图） */
    public boolean isFloatingCandidates() {
        return mSp.getBoolean(K_FLOATING, false);
    }

    public void setFloatingCandidates(boolean floating) {
        mSp.edit().putBoolean(K_FLOATING, floating).apply();
    }

    /** 模糊音：z/zh、an/ang 等易混音节互相映射（仿搜狗/百度智能纠错） */
    public boolean isFuzzyEnabled() {
        return mSp.getBoolean(K_FUZZY, true);
    }

    public void setFuzzyEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_FUZZY, enabled).apply();
    }

    /** 上下文联想：根据上一词预测下一词（本地 bigram，不出设备） */
    public boolean isContextPredictEnabled() {
        return mSp.getBoolean(K_CONTEXT, true);
    }

    public void setContextPredictEnabled(boolean enabled) {
        mSp.edit().putBoolean(K_CONTEXT, enabled).apply();
    }
}
