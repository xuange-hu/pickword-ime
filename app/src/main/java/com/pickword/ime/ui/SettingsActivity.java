package com.pickword.ime.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.pickword.ime.R;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;
import com.pickword.ime.theme.Skin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * 设置页：输入习惯、皮肤与形态、数据隐私。
 *
 * <p>所有设置写本地 SharedPreferences / 私有目录，输入法进程每次弹出键盘时重放。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_BG_IMAGE = 1001;

    /** 自定义主色候选（与预设皮肤同风格的几组色） */
    private static final int[] ACCENTS = {
            0xFF0E7C66, 0xFF2E7CF6, 0xFF8E44AD, 0xFFD64545,
            0xFFE67E22, 0xFFC0A32E, 0xFF2E9E5B, 0xFF00838F
    };

    private Prefs mPrefs;
    private Store mStore;

    private LinearLayout mSkinRow;
    private LinearLayout mAccentBox;
    private LinearLayout mAccentRow;
    private SwitchCompat mSkinDark;

    private Button mLayoutBtn;
    private Button mGlossPosBtn;
    private Button mThemeBtn;
    private Button mKeyHeightBtn;
    private Button mOneHandBtn;
    private Button mTypefaceBtn;

    private SwitchCompat mGlossSwitch;
    private SwitchCompat mVibrateSwitch;
    private SwitchCompat mSoundSwitch;
    private SwitchCompat mNumberRowSwitch;
    private SwitchCompat mSwipeSpaceSwitch;
    private SwitchCompat mSwipeDeleteSwitch;
    private SwitchCompat mFloatingSwitch;
    private SwitchCompat mFuzzySwitch;
    private SwitchCompat mContextSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefs = Prefs.get(this);
        mStore = Store.get(this);

        mSkinRow = findViewById(R.id.skin_row);
        mAccentBox = findViewById(R.id.accent_box);
        mAccentRow = findViewById(R.id.accent_row);
        mSkinDark = findViewById(R.id.sw_skin_dark);

        mLayoutBtn = findViewById(R.id.btn_layout);
        mGlossPosBtn = findViewById(R.id.btn_gloss_pos);
        mThemeBtn = findViewById(R.id.btn_theme);
        mKeyHeightBtn = findViewById(R.id.btn_key_height);
        mOneHandBtn = findViewById(R.id.btn_one_hand);
        mTypefaceBtn = findViewById(R.id.btn_typeface);

        mGlossSwitch = findViewById(R.id.sw_gloss);
        mVibrateSwitch = findViewById(R.id.sw_vibrate);
        mSoundSwitch = findViewById(R.id.sw_sound);
        mNumberRowSwitch = findViewById(R.id.sw_number_row);
        mSwipeSpaceSwitch = findViewById(R.id.sw_swipe_space);
        mSwipeDeleteSwitch = findViewById(R.id.sw_swipe_delete);
        mFloatingSwitch = findViewById(R.id.sw_floating);
        mFuzzySwitch = findViewById(R.id.sw_fuzzy);
        mContextSwitch = findViewById(R.id.sw_context);

        buildSkinRow();
        buildAccentRow();
        bindSwitches();
        bindCycleButtons();
        bindSliders();
        bindBackgroundButtons();
        bindClearFreq();

        refreshAll();
    }

    // ------------------------------------------------------------------
    // 皮肤
    // ------------------------------------------------------------------

    private void buildSkinRow() {
        mSkinRow.removeAllViews();
        final Skin[] presets = Skin.presets();
        for (final Skin skin : presets) {
            mSkinRow.addView(makeSkinChip(skin));
        }
        // 自定义（跟随主色）
        mSkinRow.addView(makeSkinChip(mPrefs.getSkin()));
    }

    private View makeSkinChip(final Skin skin) {
        final boolean custom = "custom".equals(skin.id);
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(72), LinearLayout.LayoutParams.WRAP_CONTENT);
        chip.setLayoutParams(lp);

        // 预览：三块色带
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.HORIZONTAL);
        preview.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(34)));
        preview.addView(colorBand(skin.keyBg));
        preview.addView(colorBand(skin.keyBgSpecial));
        preview.addView(colorBand(skin.accent));
        chip.addView(preview);

        TextView name = new TextView(this);
        name.setText(custom ? "自定义" : skin.name);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(6), 0, 0);
        chip.addView(name);

        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (custom) {
                    mPrefs.setSkinId("custom");
                } else {
                    mPrefs.setSkinId(skin.id);
                }
                refreshAll();
                Toast.makeText(SettingsActivity.this, skin.name,
                        Toast.LENGTH_SHORT).show();
            }
        });
        return chip;
    }

    private View colorBand(int color) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        v.setBackgroundColor(color);
        return v;
    }

    private void buildAccentRow() {
        mAccentRow.removeAllViews();
        for (final int color : ACCENTS) {
            View swatch = new View(this);
            int size = dp(30);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            swatch.setLayoutParams(lp);
            GradientDrawable d = new GradientDrawable();
            d.setColor(color);
            d.setCornerRadius(dp(15));
            d.setStroke(dp(2), 0x33000000);
            swatch.setBackground(d);
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPrefs.setAccent(color);
                    mPrefs.setSkinId("custom");
                    refreshAll();
                }
            });
            mAccentRow.addView(swatch);
        }
    }

    // ------------------------------------------------------------------
    // 开关
    // ------------------------------------------------------------------

    private void bindSwitches() {
        mGlossSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setGlossEnabled(isChecked);
            }
        });
        mVibrateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setVibrateEnabled(isChecked);
            }
        });
        mSoundSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setSoundEnabled(isChecked);
            }
        });
        mNumberRowSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setNumberRowEnabled(isChecked);
            }
        });
        mSwipeSpaceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setSwipeSpaceEnabled(isChecked);
            }
        });
        mSwipeDeleteSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setSwipeDeleteEnabled(isChecked);
            }
        });
        mFloatingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setFloatingCandidates(isChecked);
            }
        });
        mFuzzySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setFuzzyEnabled(isChecked);
            }
        });
        mContextSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setContextPredictEnabled(isChecked);
            }
        });
        mSkinDark.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mPrefs.setSkinDark(isChecked);
            }
        });
    }

    // ------------------------------------------------------------------
    // 循环按钮
    // ------------------------------------------------------------------

    private void bindCycleButtons() {
        mLayoutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean t9 = Prefs.LAYOUT_T9.equals(mPrefs.getKeyboardLayout());
                mPrefs.setKeyboardLayout(t9 ? Prefs.LAYOUT_QWERTY : Prefs.LAYOUT_T9);
                refreshLabels();
            }
        });
        mGlossPosBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.setGlossOnRight(!mPrefs.isGlossOnRight());
                refreshLabels();
            }
        });
        mThemeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.setTheme((mPrefs.getTheme() + 1) % 3);
                refreshLabels();
            }
        });
        mKeyHeightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.setKeyHeight((mPrefs.getKeyHeight() + 1) % 3);
                refreshLabels();
            }
        });
        mOneHandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.setOneHand((mPrefs.getOneHand() + 1) % 3);
                refreshLabels();
            }
        });
        mTypefaceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String current = mPrefs.getTypeface();
                if ("serif".equals(current)) {
                    mPrefs.setTypeface("mono");
                } else if ("mono".equals(current)) {
                    mPrefs.setTypeface("sans");
                } else {
                    mPrefs.setTypeface("serif");
                }
                refreshLabels();
            }
        });
    }

    // ------------------------------------------------------------------
    // 滑杆
    // ------------------------------------------------------------------

    private void bindSliders() {
        bindSlider(R.id.sb_key_text, 12, 30, mPrefs.getKeyTextSp(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setKeyTextSp(value);
                    }
                }, R.id.tv_key_text, "sp");
        bindSlider(R.id.sb_cand_text, 12, 32, mPrefs.getCandidateTextSp(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setCandidateTextSp(value);
                    }
                }, R.id.tv_cand_text, "sp");
        bindSlider(R.id.sb_gloss_text, 8, 20, mPrefs.getGlossTextSp(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setGlossTextSp(value);
                    }
                }, R.id.tv_gloss_text, "sp");
        bindSlider(R.id.sb_radius, 0, 14, mPrefs.getRadiusDp(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setRadiusDp(value);
                    }
                }, R.id.tv_radius, "dp");
        bindSlider(R.id.sb_key_gap, 0, 8, mPrefs.getKeyGapDp(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setKeyGapDp(value);
                    }
                }, R.id.tv_key_gap, "dp");
        bindSlider(R.id.sb_cand_height, 32, 72, mPrefs.getCandidateHeightDp(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setCandidateHeightDp(value);
                    }
                }, R.id.tv_cand_height, "dp");
        bindSlider(R.id.sb_bg_alpha, 0, 100, mPrefs.getBackgroundAlpha(),
                new ValueSink() {
                    @Override
                    public void set(int value) {
                        mPrefs.setBackgroundAlpha(value);
                    }
                }, R.id.tv_bg_alpha, "%");
    }

    private interface ValueSink {
        void set(int value);
    }

    private void bindSlider(int seekId, final int min, final int max, int current,
                            final ValueSink sink, final int labelId, final String unit) {
        SeekBar bar = findViewById(seekId);
        final TextView label = findViewById(labelId);
        bar.setMax(max - min);
        bar.setProgress(Math.max(min, Math.min(max, current)) - min);
        label.setText(String.format(Locale.US, "%d%s", current, unit));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                sink.set(value);
                label.setText(String.format(Locale.US, "%d%s", value, unit));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    // ------------------------------------------------------------------
    // 背景图
    // ------------------------------------------------------------------

    private void bindBackgroundButtons() {
        findViewById(R.id.btn_bg_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "选择键盘背景"), REQ_BG_IMAGE);
            }
        });
        findViewById(R.id.btn_bg_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPrefs.setBackgroundImage("");
                Toast.makeText(SettingsActivity.this, R.string.toast_done,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_BG_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }
        final Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File out = new File(getFilesDir(), "keyboard_bg.png");
                    InputStream in = getContentResolver().openInputStream(uri);
                    OutputStream os = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                    os.close();
                    in.close();
                    final String path = out.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPrefs.setBackgroundImage(path);
                            Toast.makeText(SettingsActivity.this, R.string.toast_done,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(SettingsActivity.this,
                                    getString(R.string.toast_failed, "背景图"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------
    // 数据
    // ------------------------------------------------------------------

    private void bindClearFreq() {
        findViewById(R.id.btn_clear_freq).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mStore.clearFrequency();
                    }
                }).start();
                Toast.makeText(SettingsActivity.this, R.string.toast_done,
                        Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_clear_cooc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mStore.clearCooc();
                    }
                }).start();
                Toast.makeText(SettingsActivity.this, R.string.toast_done,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------------
    // 刷新
    // ------------------------------------------------------------------

    private void refreshAll() {
        mGlossSwitch.setChecked(mPrefs.isGlossEnabled());
        mVibrateSwitch.setChecked(mPrefs.isVibrateEnabled());
        mSoundSwitch.setChecked(mPrefs.isSoundEnabled());
        mNumberRowSwitch.setChecked(mPrefs.isNumberRowEnabled());
        mSwipeSpaceSwitch.setChecked(mPrefs.isSwipeSpaceEnabled());
        mSwipeDeleteSwitch.setChecked(mPrefs.isSwipeDeleteEnabled());
        mFloatingSwitch.setChecked(mPrefs.isFloatingCandidates());
        mFuzzySwitch.setChecked(mPrefs.isFuzzyEnabled());
        mContextSwitch.setChecked(mPrefs.isContextPredictEnabled());
        mSkinDark.setChecked(mPrefs.isSkinDark());

        boolean custom = "custom".equals(mPrefs.getSkinId());
        mAccentBox.setVisibility(custom ? View.VISIBLE : View.GONE);
        markSelectedChip();

        refreshLabels();
        refreshSliders();
    }

    /** 给当前选中的皮肤芯片加高亮边框 */
    private void markSelectedChip() {
        String currentId = mPrefs.getSkinId();
        Skin[] all = Skin.presets();
        int selected = -1;
        for (int i = 0; i < all.length; i++) {
            if (all[i].id.equals(currentId)) {
                selected = i;
                break;
            }
        }
        for (int i = 0; i < mSkinRow.getChildCount(); i++) {
            View chip = mSkinRow.getChildAt(i);
            boolean isSelected = (i == selected)
                    || (selected < 0 && i == mSkinRow.getChildCount() - 1);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            if (isSelected) {
                bg.setStroke(dp(2), mPrefs.getAccent());
                bg.setColor(0x22000000 | (mPrefs.getAccent() & 0x00FFFFFF));
            } else {
                bg.setColor(0x00000000);
            }
            chip.setBackground(bg);
        }
    }

    private void refreshLabels() {
        mLayoutBtn.setText(Prefs.LAYOUT_T9.equals(mPrefs.getKeyboardLayout())
                ? R.string.pref_layout_t9 : R.string.pref_layout_qwerty);
        mGlossPosBtn.setText(mPrefs.isGlossOnRight()
                ? R.string.pref_gloss_position_right : R.string.pref_gloss_position_top);
        switch (mPrefs.getTheme()) {
            case Prefs.THEME_LIGHT:
                mThemeBtn.setText(R.string.pref_theme_light);
                break;
            case Prefs.THEME_DARK:
                mThemeBtn.setText(R.string.pref_theme_dark);
                break;
            default:
                mThemeBtn.setText(R.string.pref_theme_system);
                break;
        }
        switch (mPrefs.getKeyHeight()) {
            case Prefs.KEY_HEIGHT_LOW:
                mKeyHeightBtn.setText("矮");
                break;
            case Prefs.KEY_HEIGHT_HIGH:
                mKeyHeightBtn.setText("高");
                break;
            default:
                mKeyHeightBtn.setText(R.string.pref_key_height_normal);
                break;
        }
        switch (mPrefs.getOneHand()) {
            case Prefs.ONE_HAND_LEFT:
                mOneHandBtn.setText(R.string.pref_one_hand_left);
                break;
            case Prefs.ONE_HAND_RIGHT:
                mOneHandBtn.setText(R.string.pref_one_hand_right);
                break;
            default:
                mOneHandBtn.setText(R.string.pref_one_hand_off);
                break;
        }
        String typeface = mPrefs.getTypeface();
        if ("serif".equals(typeface)) {
            mTypefaceBtn.setText(R.string.pref_typeface_serif);
        } else if ("mono".equals(typeface)) {
            mTypefaceBtn.setText(R.string.pref_typeface_mono);
        } else {
            mTypefaceBtn.setText(R.string.pref_typeface_sans);
        }
    }

    private void refreshSliders() {
        setSlider(R.id.sb_key_text, 12, 30, mPrefs.getKeyTextSp(), R.id.tv_key_text, "sp");
        setSlider(R.id.sb_cand_text, 12, 32, mPrefs.getCandidateTextSp(), R.id.tv_cand_text, "sp");
        setSlider(R.id.sb_gloss_text, 8, 20, mPrefs.getGlossTextSp(), R.id.tv_gloss_text, "sp");
        setSlider(R.id.sb_radius, 0, 14, mPrefs.getRadiusDp(), R.id.tv_radius, "dp");
        setSlider(R.id.sb_key_gap, 0, 8, mPrefs.getKeyGapDp(), R.id.tv_key_gap, "dp");
        setSlider(R.id.sb_cand_height, 32, 72, mPrefs.getCandidateHeightDp(),
                R.id.tv_cand_height, "dp");
        setSlider(R.id.sb_bg_alpha, 0, 100, mPrefs.getBackgroundAlpha(), R.id.tv_bg_alpha, "%");
    }

    private void setSlider(int seekId, int min, int max, int value, int labelId, String unit) {
        SeekBar bar = findViewById(seekId);
        bar.setProgress(Math.max(min, Math.min(max, value)) - min);
        TextView label = findViewById(labelId);
        label.setText(String.format(Locale.US, "%d%s", value, unit));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
