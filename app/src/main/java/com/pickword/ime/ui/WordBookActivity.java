package com.pickword.ime.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pickword.ime.R;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;
import com.pickword.ime.vocab.Word;
import com.pickword.ime.vocab.WordListIo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 生词本：收藏的词 + 到期情况 + 复习入口 + 词表导入导出。 */
public class WordBookActivity extends AppCompatActivity {

    private static final int REQ_IMPORT = 2001;
    private static final int REQ_EXPORT = 2002;

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());

    private Store mStore;
    private ListView mList;
    private TextView mEmpty;
    private final List<Word> mWords = new ArrayList<>();
    private WordAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.applyTheme(Prefs.get(this).getTheme());
        setContentView(R.layout.activity_wordbook);

        mStore = Store.get(this);
        mList = findViewById(R.id.word_list);
        mEmpty = findViewById(R.id.empty_view);
        mAdapter = new WordAdapter();
        mList.setAdapter(mAdapter);
        mList.setEmptyView(mEmpty);

        Button review = findViewById(R.id.btn_review);
        Button importBtn = findViewById(R.id.btn_import);
        Button exportBtn = findViewById(R.id.btn_export);

        review.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(WordBookActivity.this, ReviewActivity.class));
            }
        });
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(
                        Intent.createChooser(intent, getString(R.string.wordbook_action_import)),
                        REQ_IMPORT);
            }
        });
        exportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, "pickword-words.json");
                startActivityForResult(intent, REQ_EXPORT);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        mWorker.shutdownNow();
        super.onDestroy();
    }

    private void load() {
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                final List<Word> words = mStore.listWords();
                final int due = mStore.dueCount(System.currentTimeMillis());
                mMain.post(new Runnable() {
                    @Override
                    public void run() {
                        mWords.clear();
                        mWords.addAll(words);
                        mAdapter.notifyDataSetChanged();
                        setTitle(getString(R.string.title_wordbook)
                                + "（" + getString(R.string.wordbook_due, due) + "）");
                    }
                });
            }
        });
    }

    private void remove(final Word word) {
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                mStore.removeWord(word.id);
                mMain.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WordBookActivity.this,
                                getString(R.string.wordbook_removed, word.word),
                                Toast.LENGTH_SHORT).show();
                        load();
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final boolean isExport = requestCode == REQ_EXPORT;
        WordListIo.Callback callback = new WordListIo.Callback() {
            @Override
            public void onDone(int count) {
                Toast.makeText(WordBookActivity.this,
                        getString(isExport ? R.string.export_done : R.string.import_done, count),
                        Toast.LENGTH_SHORT).show();
                load();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(WordBookActivity.this,
                        getString(R.string.toast_failed, message), Toast.LENGTH_SHORT).show();
            }
        };
        if (isExport) {
            WordListIo.exportJson(this, mStore, data.getData(), mWorker, mMain, callback);
        } else if (requestCode == REQ_IMPORT) {
            WordListIo.importCsv(this, mStore, data.getData(), mWorker, mMain, callback);
        }
    }

    private final class WordAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mWords.size();
        }

        @Override
        public Object getItem(int position) {
            return mWords.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mWords.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_word, parent, false);
            }
            final Word w = mWords.get(position);
            TextView text = view.findViewById(R.id.word_text);
            TextView gloss = view.findViewById(R.id.word_gloss);
            TextView meta = view.findViewById(R.id.word_meta);
            View remove = view.findViewById(R.id.word_remove);

            text.setText(w.word);
            if (w.gloss == null || w.gloss.isEmpty()) {
                gloss.setVisibility(View.GONE);
            } else {
                gloss.setVisibility(View.VISIBLE);
                gloss.setText(w.gloss);
            }
            String date = new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(w.dueTime));
            meta.setText(getString(R.string.wordbook_level, w.level())
                    + " · " + getString(R.string.wordbook_due_time, date));
            remove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    remove(w);
                }
            });
            return view;
        }
    }
}
