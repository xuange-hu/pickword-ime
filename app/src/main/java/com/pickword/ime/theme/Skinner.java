package com.pickword.ime.theme;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pickword.ime.R;
import com.pickword.ime.data.Prefs;

/**
 * 把皮肤与形态设置应用到视图上。
 *
 * <p>所有按键背景在运行时生成（圆角 + 按下态），因此用户改皮肤、改圆角、改字号后
 * 不需要重新打资源，切一次面板即可生效。
 */
public final class Skinner {

    private Skinner() {
    }

    /** 圆角纯色 */
    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** 按键背景：普通态 + 按下态 */
    public static StateListDrawable keyBackground(int normal, int pressed, float radiusPx) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed, radiusPx));
        s.addState(new int[]{android.R.attr.state_selected}, rounded(pressed, radiusPx));
        s.addState(new int[]{}, rounded(normal, radiusPx));
        return s;
    }

    /** 候选条背景：普通态 + 选中/按下态 */
    public static StateListDrawable candidateBackground(int normal, int selected,
                                                        float radiusPx) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_selected}, rounded(selected, radiusPx));
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(selected, radiusPx));
        s.addState(new int[]{}, rounded(normal, radiusPx));
        return s;
    }

    public static Typeface typeface(Prefs prefs) {
        String value = prefs.getTypeface();
        if ("serif".equals(value)) {
            return Typeface.SERIF;
        }
        if ("mono".equals(value)) {
            return Typeface.MONOSPACE;
        }
        return Typeface.SANS_SERIF;
    }

    /** 把皮肤与尺寸应用到整个键盘面板（递归处理所有按键与行） */
    public static void applyPanel(View panel, Prefs prefs) {
        if (panel == null) {
            return;
        }
        Skin skin = prefs.getSkin();
        float density = panel.getResources().getDisplayMetrics().density;
        float radius = prefs.getRadiusDp() * density;
        int gap = Math.round(prefs.getKeyGapDp() * density);
        int rowHeight = Math.round(prefs.getKeyHeightDp() * density);
        int pressed = Skin.mix(skin.keyBg, skin.accent, 0.16f);
        int pressedSpecial = Skin.mix(skin.keyBgSpecial, skin.accent, 0.24f);

        // 自定义背景图时把键盘底板调半透明，让图从缝隙里透出来
        int keyboardBg = skin.keyboardBg;
        String bgImage = prefs.getBackgroundImage();
        if (bgImage != null && !bgImage.isEmpty()) {
            keyboardBg = (keyboardBg & 0x00FFFFFF) | 0x99000000;
        }
        panel.setBackgroundColor(keyboardBg);
        walk(panel, skin, prefs, radius, gap, rowHeight, pressed, pressedSpecial);
    }

    private static void walk(View view, Skin skin, Prefs prefs, float radius, int gap,
                             int rowHeight, int pressed, int pressedSpecial) {
        if (view instanceof ViewGroup) {
            Object tag = view.getTag();
            if ("row".equals(tag)) {
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.height = rowHeight;
                    view.setLayoutParams(lp);
                }
            }
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(group.getChildAt(i), skin, prefs, radius, gap, rowHeight,
                        pressed, pressedSpecial);
            }
            return;
        }
        if (!(view instanceof TextView)) {
            return;
        }
        TextView tv = (TextView) view;
        int id = tv.getId();
        if (id == R.id.panel_title) {
            // 面板标题保持无背景，只跟着皮肤换字色
            tv.setTextColor(skin.keyText);
            return;
        }
        if (id == R.id.panel_input) {
            // 常用语输入框保持原生形态
            return;
        }
        boolean special = "special".equals(view.getTag());
        tv.setBackground(keyBackground(special ? skin.keyBgSpecial : skin.keyBg,
                special ? pressedSpecial : pressed, radius));
        tv.setTextColor(special ? skin.keyTextSpecial : skin.keyText);
        tv.setTextSize(prefs.getKeyTextSp());
        tv.setTypeface(typeface(prefs));
        tv.setIncludeFontPadding(false);
        if (gap >= 0) {
            ViewGroup.LayoutParams lp = tv.getLayoutParams();
            if (lp instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
                llp.setMargins(gap, gap, gap, gap);
                tv.setLayoutParams(llp);
            }
        }
    }

    /** 候选栏（含拼音串与背景） */
    public static void applyCandidateBar(View bar, Prefs prefs) {
        if (bar == null) {
            return;
        }
        Skin skin = prefs.getSkin();
        bar.setBackgroundColor(skin.candidateBg);
        ViewGroup.LayoutParams lp = bar.getLayoutParams();
        if (lp != null) {
            lp.height = Math.round(prefs.getCandidateHeightDp()
                    * bar.getResources().getDisplayMetrics().density);
            bar.setLayoutParams(lp);
        }
    }

    /** 单个候选（词 + 译词 + 星标） */
    public static void applyCandidateItem(View item, Prefs prefs) {
        if (item == null) {
            return;
        }
        Skin skin = prefs.getSkin();
        float density = item.getResources().getDisplayMetrics().density;
        item.setBackground(candidateBackground(skin.candidateBg, skin.candidateSelected,
                prefs.getRadiusDp() * density + 2 * density));

        TextView word = item.findViewById(R.id.candidate_word);
        if (word != null) {
            word.setTextColor(skin.keyText);
            word.setTextSize(prefs.getCandidateTextSp());
            word.setTypeface(typeface(prefs));
        }
        TextView gloss = item.findViewById(R.id.candidate_gloss);
        if (gloss != null) {
            gloss.setTextColor(skin.gloss);
            gloss.setTextSize(prefs.getGlossTextSp());
        }
        TextView star = item.findViewById(R.id.candidate_star);
        if (star != null) {
            star.setTextColor(skin.star);
        }
    }
}
