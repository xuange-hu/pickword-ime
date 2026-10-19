package com.pickword.ime.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.pickword.ime.R;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;

/** 设置：译词、手感、外观、数据。所有设置只写本地。 */
public class SettingsActivity extends AppCompatActivity {

    private Prefs mPrefs;
    private Store mStore;

    private SwitchCompat mGlossSwitch;
    private SwitchCompat mVibrateSwitch;
    private Button mGlossPos;
    private Button mThemeBtn;
    private Button mKeyHeightBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mPrefs = Prefs.get(this);
        mStore = Store.get(this);

        mGlossSwitch = findViewById(R.id.sw_gloss);
        mVibrateSwitch = findViewById(R.id.sw_vibrate);
        mGlossPos = findViewById(R.id.btn_gloss_pos);
        mThemeBtn = findViewById(R.id.btn_theme);
        mKeyHeightBtn = findViewById(R.id.btn_key_height);
        Button clearFreq = findViewById(R.id.btn_clear_freq);

        mGlossSwitch.setChecked(mPrefs.isGlossEnabled());
        mVibrateSwitch.setChecked(mPrefs.isVibrateEnabled());
        refreshLabels();

        mGlossSwitch.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton buttonView,
                                                 boolean isChecked) {
                        mPrefs.setGlossEnabled(isChecked);
                    }
                });
        mVibrateSwitch.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton buttonView,
                                                 boolean isChecked) {
                        mPrefs.setVibrateEnabled(isChecked);
                    }
                });

        mGlossPos.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                mPrefs.setGlossOnRight(!mPrefs.isGlossOnRight());
                refreshLabels();
            }
        });
        mThemeBtn.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                int next = (mPrefs.getTheme() + 1) % 3;
                mPrefs.setTheme(next);
                refreshLabels();
            }
        });
        mKeyHeightBtn.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                int next = (mPrefs.getKeyHeight() + 1) % 3;
                mPrefs.setKeyHeight(next);
                refreshLabels();
            }
        });
        clearFreq.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mStore.clearFrequency();
                    }
                }).start();
                Toast.makeText(SettingsActivity.this,
                        R.string.toast_done, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshLabels() {
        mGlossPos.setText(mPrefs.isGlossOnRight()
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
                mKeyHeightBtn.setText("标准");
                break;
        }
    }
}
