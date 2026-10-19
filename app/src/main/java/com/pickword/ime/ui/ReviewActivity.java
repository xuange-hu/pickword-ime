package com.pickword.ime.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.pickword.ime.R;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;
import com.pickword.ime.vocab.SrsScheduler;
import com.pickword.ime.vocab.Word;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 间隔复习：看词 → 自评 → SM-2 调度下一次复习时间。 */
public class ReviewActivity extends AppCompatActivity {

    private static final int SESSION_LIMIT = 30;

    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());

    private Store mStore;
    private TextView mWordText;
    private TextView mGlossText;
    private TextView mProgress;
    private TextView mDone;
    private Button mGot;
    private Button mMissed;
    private View mCard;

    private final List<Word> mQueue = new ArrayList<>();
    private int mIndex;
    private int mTotal;
    private boolean mRevealed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.applyTheme(Prefs.get(this).getTheme());
        setContentView(R.layout.activity_review);

        mStore = Store.get(this);
        mWordText = findViewById(R.id.review_word);
        mGlossText = findViewById(R.id.review_gloss);
        mProgress = findViewById(R.id.review_progress);
        mDone = findViewById(R.id.review_done);
        mGot = findViewById(R.id.btn_got);
        mMissed = findViewById(R.id.btn_missed);
        mCard = findViewById(R.id.review_card);

        mCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reveal();
            }
        });
        mGot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grade(SrsScheduler.QUALITY_GOOD);
            }
        });
        mMissed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                grade(SrsScheduler.QUALITY_AGAIN);
            }
        });
        loadQueue();
    }

    @Override
    protected void onDestroy() {
        mWorker.shutdownNow();
        super.onDestroy();
    }

    private void loadQueue() {
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                final List<Word> due =
                        mStore.listDueWords(System.currentTimeMillis(), SESSION_LIMIT);
                mMain.post(new Runnable() {
                    @Override
                    public void run() {
                        mQueue.clear();
                        mQueue.addAll(due);
                        mIndex = 0;
                        mTotal = mQueue.size();
                        if (mTotal == 0) {
                            showEmpty();
                        } else {
                            showCurrent();
                        }
                    }
                });
            }
        });
    }

    private void showEmpty() {
        mWordText.setText("");
        mGlossText.setVisibility(View.GONE);
        mProgress.setText(R.string.review_none);
        mGot.setEnabled(false);
        mMissed.setEnabled(false);
        mDone.setVisibility(View.GONE);
    }

    private void showCurrent() {
        Word w = mQueue.get(mIndex);
        mRevealed = false;
        mWordText.setText(w.word);
        mGlossText.setText(w.gloss == null || w.gloss.isEmpty()
                ? getString(R.string.review_none) : w.gloss);
        mGlossText.setVisibility(View.INVISIBLE);
        mProgress.setText(getString(R.string.review_progress, mIndex + 1, mTotal));
        mGot.setEnabled(false);
        mMissed.setEnabled(false);
        mDone.setVisibility(View.GONE);
    }

    private void reveal() {
        if (mRevealed || mQueue.isEmpty()) {
            return;
        }
        mRevealed = true;
        mGlossText.setVisibility(View.VISIBLE);
        mGot.setEnabled(true);
        mMissed.setEnabled(true);
    }

    private void grade(final int quality) {
        if (mQueue.isEmpty() || !mRevealed) {
            return;
        }
        final Word w = mQueue.get(mIndex);
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                mStore.applyReview(w.word, quality);
            }
        });
        mIndex++;
        if (mIndex >= mTotal) {
            mWordText.setText("");
            mGlossText.setVisibility(View.GONE);
            mProgress.setText("");
            mGot.setEnabled(false);
            mMissed.setEnabled(false);
            mDone.setVisibility(View.VISIBLE);
        } else {
            showCurrent();
        }
    }
}
