package com.pickword.ime.ime;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pickword.ime.R;
import com.pickword.ime.data.Dictionary;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;
import com.pickword.ime.engine.Candidate;
import com.pickword.ime.engine.PinyinEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拾词输入法主服务。
 *
 * <p>职责：
 * <ul>
 *   <li>拼音输入：字母 → 音节切分 → 词典查询 → 候选</li>
 *   <li>候选旁显示英文译词（核心功能），可一键收藏到生词本</li>
 *   <li>中/英切换、符号键盘、空格选词、回车上屏</li>
 * </ul>
 * 全部数据在本地，不联网。
 */
public class PickWordImeService extends InputMethodService {

    private static final int MAX_CANDIDATES = 20;

    private static final int[] QWERTY_IDS = {
            R.id.k_q, R.id.k_w, R.id.k_e, R.id.k_r, R.id.k_t, R.id.k_y, R.id.k_u, R.id.k_i,
            R.id.k_o, R.id.k_p,
            R.id.k_a, R.id.k_s, R.id.k_d, R.id.k_f, R.id.k_g, R.id.k_h, R.id.k_j, R.id.k_k,
            R.id.k_l,
            R.id.k_z, R.id.k_x, R.id.k_c, R.id.k_v, R.id.k_b, R.id.k_n, R.id.k_m
    };
    private static final String[] QWERTY_LETTERS = {
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l",
            "z", "x", "c", "v", "b", "n", "m"
    };
    private static final int[] SYMBOL_IDS = {
            R.id.s_1, R.id.s_2, R.id.s_3, R.id.s_4, R.id.s_5, R.id.s_6, R.id.s_7, R.id.s_8,
            R.id.s_9, R.id.s_0,
            R.id.s_at, R.id.s_hash, R.id.s_usd, R.id.s_pct, R.id.s_and, R.id.s_star,
            R.id.s_minus, R.id.s_plus, R.id.s_eq, R.id.s_slash,
            R.id.s_lp, R.id.s_rp, R.id.s_lb, R.id.s_rb, R.id.s_lc, R.id.s_rc, R.id.s_em,
            R.id.s_en, R.id.s_que, R.id.s_exc
    };
    private static final String[] SYMBOL_LABELS = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "@", "#", "$", "%", "&", "*", "-", "+", "=", "/",
            "(", ")", "[", "]", "{", "}", "—", "…", "？", "！"
    };

    private Prefs mPrefs;
    private Store mStore;
    private PinyinEngine mEngine;

    private TextView mComposition;
    private LinearLayout mCandidateContainer;
    private HorizontalScrollView mCandidateScroll;
    private FrameLayout mKeyboardContainer;

    private final StringBuilder mComposing = new StringBuilder();
    private List<Candidate> mCandidates = new ArrayList<>();

    private boolean mEnglishMode;
    private boolean mSymbolsLayer;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final List<View> mCandidateViews = new ArrayList<>();

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = Prefs.get(this);
        mStore = Store.get(this);
        mEngine = new PinyinEngine(mStore);
        // 词库解压与建库放到后台，避免拖慢键盘首次弹出
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                Dictionary.prepare(getApplicationContext());
                try {
                    mStore.getWritableDatabase();
                } catch (Throwable ignored) {
                    // 首次写库失败不阻塞输入
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshCandidates();
                    }
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        mWorker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.ime_view, null);
        mComposition = view.findViewById(R.id.composition);
        mCandidateContainer = view.findViewById(R.id.candidate_container);
        mCandidateScroll = view.findViewById(R.id.candidate_scroll);
        mKeyboardContainer = view.findViewById(R.id.keyboard_container);
        showKeyboard();
        refreshCandidates();
        return view;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        // 密码框等场景直接切英文，避免把拼音串写进密码输入框
        if (attribute != null) {
            int type = attribute.inputType & InputType.TYPE_MASK_CLASS;
            int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
            mEnglishMode = type == InputType.TYPE_CLASS_NUMBER
                    || type == InputType.TYPE_CLASS_PHONE
                    || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == InputType.TYPE_TEXT_VARIATION_URI;
        }
        clearComposing(false);
    }

    @Override
    public void onFinishInput() {
        clearComposing(false);
        super.onFinishInput();
    }

    // ------------------------------------------------------------------
    // 键盘
    // ------------------------------------------------------------------

    private void showKeyboard() {
        int layout = mSymbolsLayer ? R.layout.keyboard_symbols : R.layout.keyboard_qwerty;
        View kb = getLayoutInflater().inflate(layout, mKeyboardContainer, false);
        mKeyboardContainer.removeAllViews();
        mKeyboardContainer.addView(kb);
        if (mSymbolsLayer) {
            bindLabels(kb, SYMBOL_IDS, SYMBOL_LABELS);
        } else {
            bindLabels(kb, QWERTY_IDS, QWERTY_LETTERS);
        }
        wireKeys(kb);
        applyKeyHeight(kb);
    }

    private static void bindLabels(View root, int[] ids, String[] labels) {
        for (int i = 0; i < ids.length && i < labels.length; i++) {
            View v = root.findViewById(ids[i]);
            if (v instanceof Button) {
                ((Button) v).setText(labels[i]);
            }
        }
    }

    private void wireKeys(View root) {
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup vg = (ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View row = vg.getChildAt(i);
            if (!(row instanceof ViewGroup)) {
                continue;
            }
            ViewGroup rg = (ViewGroup) row;
            for (int j = 0; j < rg.getChildCount(); j++) {
                View v = rg.getChildAt(j);
                if (v instanceof Button) {
                    v.setOnClickListener(mKeyListener);
                }
            }
        }
    }

    private void applyKeyHeight(View root) {
        int px = (int) (mPrefs.getKeyHeightDp() * getResources().getDisplayMetrics().density);
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup vg = (ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View row = vg.getChildAt(i);
            if (row instanceof LinearLayout) {
                ViewGroup.LayoutParams lp = row.getLayoutParams();
                lp.height = px;
                row.setLayoutParams(lp);
            }
        }
    }

    private final View.OnClickListener mKeyListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            feedback();
            int id = v.getId();
            if (id == R.id.k_delete || id == R.id.s_delete) {
                handleDelete();
                return;
            }
            if (id == R.id.k_enter) {
                handleEnter();
                return;
            }
            if (id == R.id.k_space || id == R.id.s_space) {
                handleSpace();
                return;
            }
            if (id == R.id.k_symbol) {
                mSymbolsLayer = true;
                showKeyboard();
                return;
            }
            if (id == R.id.s_back) {
                mSymbolsLayer = false;
                showKeyboard();
                return;
            }
            if (id == R.id.k_lang) {
                mEnglishMode = !mEnglishMode;
                clearComposing(true);
                return;
            }
            if (id == R.id.k_comma || id == R.id.s_comma) {
                commitText("，");
                return;
            }
            if (id == R.id.k_period || id == R.id.s_period) {
                commitText("。");
                return;
            }
            String label = v instanceof Button ? ((Button) v).getText().toString() : "";
            if (label.isEmpty()) {
                return;
            }
            if (mSymbolsLayer) {
                commitText(label);
                return;
            }
            if (mEnglishMode) {
                commitText(label.toLowerCase(Locale.US));
                return;
            }
            appendLetter(label.toLowerCase(Locale.US));
        }
    };

    // ------------------------------------------------------------------
    // 输入逻辑
    // ------------------------------------------------------------------

    private void appendLetter(String letter) {
        if (mComposing.length() >= 24) {
            return;
        }
        mComposing.append(letter);
        updateCompositionText();
        refreshCandidates();
    }

    private void handleSpace() {
        if (mComposing.length() == 0) {
            commitText(" ");
            return;
        }
        if (!mCandidates.isEmpty()) {
            commitCandidate(mCandidates.get(0));
        } else {
            commitText(mComposing.toString());
            clearComposing(true);
        }
    }

    private void handleEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (mComposing.length() > 0) {
            // 拼音串直接上屏（想打英文用）
            commitText(mComposing.toString());
            clearComposing(true);
            return;
        }
        if (ic == null) {
            return;
        }
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            int action = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action != EditorInfo.IME_ACTION_NONE) {
                ic.performEditorAction(action);
                return;
            }
        }
        ic.commitText("\n", 1);
    }

    private void handleDelete() {
        if (mComposing.length() > 0) {
            mComposing.deleteCharAt(mComposing.length() - 1);
            updateCompositionText();
            refreshCandidates();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void commitCandidate(final Candidate candidate) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
            ic.commitText(candidate.word, 1);
        }
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                mStore.bumpFrequency(candidate.word);
            }
        });
        clearComposing(true);
    }

    private void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
        clearComposing(true);
    }

    private void clearComposing(boolean updateConnection) {
        mComposing.setLength(0);
        InputConnection ic = getCurrentInputConnection();
        if (updateConnection && ic != null) {
            ic.finishComposingText();
        }
        if (mComposition != null) {
            mComposition.setText(R.string.candidates_empty);
        }
        if (mCandidateContainer != null) {
            refreshCandidates();
        }
    }

    private void updateCompositionText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(mComposing.toString(), 1);
        }
        if (mComposition != null) {
            mComposition.setText(mComposing.length() == 0
                    ? getString(R.string.candidates_empty) : mComposing.toString());
        }
    }

    // ------------------------------------------------------------------
    // 候选栏
    // ------------------------------------------------------------------

    private void refreshCandidates() {
        if (mCandidateContainer == null) {
            return;
        }
        List<Candidate> list;
        if (mComposing.length() == 0) {
            list = new ArrayList<>();
        } else {
            list = mEngine.query(mComposing.toString(), MAX_CANDIDATES);
        }
        mCandidates = list;
        renderCandidates(list);
    }

    private void renderCandidates(List<Candidate> list) {
        mCandidateContainer.removeAllViews();
        mCandidateViews.clear();
        boolean glossEnabled = mPrefs.isGlossEnabled();
        int layout = mPrefs.isGlossOnRight()
                ? R.layout.item_candidate_right : R.layout.item_candidate;
        LayoutInflater inflater = getLayoutInflater();
        for (Candidate c : list) {
            View item = inflater.inflate(layout, mCandidateContainer, false);
            TextView word = item.findViewById(R.id.candidate_word);
            TextView gloss = item.findViewById(R.id.candidate_gloss);
            TextView star = item.findViewById(R.id.candidate_star);
            word.setText(c.word);
            if (glossEnabled && c.gloss != null && !c.gloss.isEmpty()) {
                gloss.setText(c.gloss);
                gloss.setVisibility(View.VISIBLE);
            } else {
                gloss.setVisibility(View.GONE);
            }
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Candidate target = (Candidate) v.getTag();
                    if (target != null) {
                        feedback();
                        commitCandidate(target);
                    }
                }
            });
            item.setTag(c);
            star.setTag(c);
            star.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Candidate target = (Candidate) v.getTag();
                    if (target == null) {
                        return;
                    }
                    collect(target);
                }
            });
            mCandidateContainer.addView(item);
            mCandidateViews.add(item);
        }
        if (mCandidateScroll != null) {
            mCandidateScroll.scrollTo(0, 0);
        }
    }

    /** 收藏到生词本（在后台写库，主线程只弹提示） */
    private void collect(final Candidate candidate) {
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                final long id = mStore.addWord(candidate.word, candidate.gloss);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        int res = id > 0 ? R.string.wordbook_added : R.string.wordbook_already;
                        Toast.makeText(PickWordImeService.this,
                                getString(res, candidate.word), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------
    // 触感反馈
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void feedback() {
        if (!mPrefs.isVibrateEnabled()) {
            return;
        }
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(12,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(12);
        }
    }
}
