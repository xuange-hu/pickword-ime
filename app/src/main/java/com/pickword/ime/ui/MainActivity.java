package com.pickword.ime.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pickword.ime.BuildConfig;
import com.pickword.ime.R;
import com.pickword.ime.data.Dictionary;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;
import com.pickword.ime.vocab.WordListIo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 主界面：启用向导 + 试打 + 功能入口。 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_IMPORT = 1001;
    private static final int REQ_EXPORT = 1002;

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());

    private TextView mStatusText;
    private Store mStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.applyTheme(Prefs.get(this).getTheme());
        setContentView(R.layout.activity_main);

        mStore = Store.get(this);
        mStatusText = findViewById(R.id.status_text);

        Button enable = findViewById(R.id.btn_enable);
        Button choose = findViewById(R.id.btn_choose);
        Button wordbook = findViewById(R.id.btn_wordbook);
        Button settings = findViewById(R.id.btn_settings);
        Button importBtn = findViewById(R.id.btn_import);
        Button exportBtn = findViewById(R.id.btn_export);
        Button about = findViewById(R.id.btn_about);

        enable.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        });
        choose.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            }
        });
        wordbook.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, WordBookActivity.class));
            }
        });
        settings.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        importBtn.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startImport();
            }
        });
        exportBtn.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startExport();
            }
        });
        about.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                showAbout();
            }
        });

        // 提前把词库解到私有目录，首次弹出键盘就不用等
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                Dictionary.prepare(getApplicationContext());
                try {
                    mStore.getWritableDatabase();
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        mWorker.shutdownNow();
        super.onDestroy();
    }

    private void refreshStatus() {
        boolean enabled = false;
        boolean current = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        String pkg = getPackageName();
        if (imm != null) {
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            for (InputMethodInfo info : list) {
                if (info.getPackageName().equals(pkg)) {
                    enabled = true;
                    break;
                }
            }
        }
        String selected = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        current = selected != null && selected.startsWith(pkg);

        if (mStatusText == null) {
            return;
        }
        if (!enabled) {
            mStatusText.setText(R.string.home_status_disabled);
        } else if (current) {
            mStatusText.setText(R.string.home_status_enabled);
        } else {
            mStatusText.setText(R.string.home_status_enabled_not_current);
        }
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Intent chooser = Intent.createChooser(intent, getString(R.string.wordbook_action_import));
        startActivityForResult(chooser, REQ_IMPORT);
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "pickword-words.json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        WordListIo.Callback callback = new WordListIo.Callback() {
            @Override
            public void onDone(int count) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.import_done, count), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.toast_failed, message), Toast.LENGTH_SHORT).show();
            }
        };
        if (requestCode == REQ_IMPORT) {
            WordListIo.importCsv(this, mStore, data.getData(), mWorker, mMain, callback);
            // 导入后刷新结果提示用的文案统一为导入条数
        } else if (requestCode == REQ_EXPORT) {
            WordListIo.Callback exportCallback = new WordListIo.Callback() {
                @Override
                public void onDone(int count) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.export_done, count), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.toast_failed, message), Toast.LENGTH_SHORT).show();
                }
            };
            WordListIo.exportJson(this, mStore, data.getData(), mWorker, mMain, exportCallback);
        }
    }

    private void showAbout() {
        String text = getString(R.string.about_text, BuildConfig.VERSION_NAME);
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(text)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
