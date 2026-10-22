package com.pickword.ime.ime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.pickword.ime.R;
import com.pickword.ime.data.Dictionary;
import com.pickword.ime.data.Prefs;
import com.pickword.ime.data.Store;
import com.pickword.ime.emoji.EmojiData;
import com.pickword.ime.engine.Candidate;
import com.pickword.ime.engine.PinyinEngine;
import com.pickword.ime.theme.Skin;
import com.pickword.ime.theme.Skinner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拾词输入法主服务（v0.2）。
 *
 * <p>职责：
 * <ul>
 *   <li>拼音输入：字母/九宫格数字 → 音节切分 → 词典查询 → 候选</li>
 *   <li>候选旁显示英文译词（核心功能），可一键收藏到生词本</li>
 *   <li>多面板：全键盘 / 九宫格 / 符号 / 表情 / 更多候选 / 常用语 / 剪贴板</li>
 *   <li>运行时皮肤：配色、圆角、间距、字号、字体、背景图、单手模式</li>
 *   <li>主流交互：空格滑动移光标、退格滑动整词删除、长按连删、英文自动大写</li>
 * </ul>
 * 全部数据在本地，不联网。
 */
public class PickWordImeService extends InputMethodService {

    private static final int MAX_CANDIDATES = 20;
    private static final int MORE_LIMIT = 100;
    private static final int CLIP_KEEP = 60;
    private static final int EMOJI_PER_ROW = 8;

    /** 面板类型 */
    private static final int P_QWERTY = 0;
    private static final int P_T9 = 1;
    private static final int P_SYMBOL = 2;
    private static final int P_EMOJI = 3;
    private static final int P_MORE = 4;
    private static final int P_PHRASE = 5;
    private static final int P_CLIP = 6;

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
    private static final int[] NUMBER_IDS = {
            R.id.n_1, R.id.n_2, R.id.n_3, R.id.n_4, R.id.n_5,
            R.id.n_6, R.id.n_7, R.id.n_8, R.id.n_9, R.id.n_0
    };
    private static final String[] NUMBER_LABELS = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
    };
    /** 九宫格数字键 → 数字 */
    private static final int[] T9_IDS = {
            R.id.t_1, R.id.t_2, R.id.t_3, R.id.t_4, R.id.t_5,
            R.id.t_6, R.id.t_7, R.id.t_8, R.id.t_9
    };
    private static final String[] T9_DIGITS = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9"
    };

    private Prefs mPrefs;
    private Store mStore;
    private PinyinEngine mEngine;

    private FrameLayout mImeRoot;
    private LinearLayout mImeContent;
    private FrameLayout mPanelContainer;
    private View mBackgroundImage;
    private View mDivider;

    private View mEmbeddedBar;
    private View mFloatingBar;

    private TextView mComposition;
    private LinearLayout mCandidateContainer;
    private HorizontalScrollView mCandidateScroll;

    private final StringBuilder mComposing = new StringBuilder();
    private final StringBuilder mDigits = new StringBuilder();
    private List<Candidate> mCandidates = new ArrayList<>();

    private boolean mEnglishMode;
    private int mPanel = P_QWERTY;
    private float mSwipeSlop;

    /** 上下文联想：上一词已上屏的中文词（用于本地 bigram 预测，仿搜狗/百度） */
    private String mLastWord = null;

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
        mSwipeSlop = 24 * getResources().getDisplayMetrics().density;
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
        try {
            mImeRoot = view.findViewById(R.id.ime_root);
            mImeContent = view.findViewById(R.id.ime_content);
            mPanelContainer = view.findViewById(R.id.panel_container);
            mBackgroundImage = view.findViewById(R.id.keyboard_background);
            mDivider = view.findViewById(R.id.ime_divider);
            mEmbeddedBar = view.findViewById(R.id.candidate_bar);
            setupCandidateBar(mEmbeddedBar);
            showKeyboardPanel();
            applyFloatingCandidates();
            refreshCandidates();
        } catch (Throwable ignored) {
            // 任何初始化意外都不应让键盘视图创建失败
        }
        return view;
    }

    /**
     * 候选栏始终由键盘视图内嵌承载（非悬浮）；悬浮模式在 {@link #applyFloatingCandidates()}
     * 中按需 {@code setCandidatesView}。这里返回 null，避免框架在 {@link #onCreateInputView()}
     * 之前抢先创建候选视图而导致 mEmbeddedBar 尚未赋值时空指针崩溃。
     */
    @Override
    public View onCreateCandidatesView() {
        return null;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        mEnglishMode = detectEnglish(attribute);
        clearComposing(false);
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        if (mPrefs == null || mPanelContainer == null) {
            return;
        }
        try {
            // 设置可能在应用里改过，每次弹出都重放一遍
            mEnglishMode = detectEnglish(attribute);
            applyBackground();
            applyOneHand();
            applyFloatingCandidates();
            showKeyboardPanel();
            clearComposing(false);
        } catch (Throwable ignored) {
            // 弹出时的意外不应让输入法崩溃
        }
    }

    @Override
    public void onFinishInput() {
        clearComposing(false);
        super.onFinishInput();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // 横屏时不进入全屏提取模式，键盘始终贴在输入框下方
        return false;
    }

    // ------------------------------------------------------------------
    // 输入框类型 → 是否直接走英文/数字
    // ------------------------------------------------------------------

    private boolean detectEnglish(EditorInfo attribute) {
        if (attribute == null) {
            return false;
        }
        int type = attribute.inputType & InputType.TYPE_MASK_CLASS;
        int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
        return type == InputType.TYPE_CLASS_NUMBER
                || type == InputType.TYPE_CLASS_PHONE
                || variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_URI;
    }

    // ------------------------------------------------------------------
    // 面板
    // ------------------------------------------------------------------

    private void showKeyboardPanel() {
        showPanel(Prefs.LAYOUT_T9.equals(mPrefs.getKeyboardLayout()) ? P_T9 : P_QWERTY);
    }

    private void showPanel(int panel) {
        if (mPanelContainer == null) {
            return;
        }
        try {
            mPanel = panel;
            View view;
            switch (panel) {
                case P_T9:
                    view = getLayoutInflater().inflate(R.layout.keyboard_t9, mPanelContainer, false);
                    break;
                case P_SYMBOL:
                    view = getLayoutInflater().inflate(R.layout.keyboard_symbols, mPanelContainer, false);
                    break;
                case P_EMOJI:
                    view = buildEmojiPanel();
                    break;
                case P_MORE:
                    view = buildMorePanel();
                    break;
                case P_PHRASE:
                    view = buildPhrasePanel();
                    break;
                case P_CLIP:
                    view = buildClipPanel();
                    break;
                case P_QWERTY:
                default:
                    view = getLayoutInflater().inflate(R.layout.keyboard_qwerty, mPanelContainer, false);
                    break;
            }
            if (view == null) {
                return;
            }
            mPanelContainer.removeAllViews();
            mPanelContainer.addView(view);
            Skinner.applyPanel(view, mPrefs);
            if (panel == P_QWERTY) {
                bindLabels(view, QWERTY_IDS, QWERTY_LETTERS);
                bindLabels(view, NUMBER_IDS, NUMBER_LABELS);
            } else if (panel == P_SYMBOL) {
                bindLabels(view, SYMBOL_IDS, SYMBOL_LABELS);
            }
            wireKeys(view);
            applyNumberRow(view);
            applyOneHand();
            refreshCandidates();
        } catch (Throwable ignored) {
            // 面板构建失败不应让输入法整体崩溃
        }
    }

    /** 数字行可见性（仅全键盘有这一行） */
    private void applyNumberRow(View panel) {
        View row = panel.findViewById(R.id.row_number);
        if (row != null) {
            row.setVisibility(mPrefs.isNumberRowEnabled() ? View.VISIBLE : View.GONE);
        }
    }

    // ---------------- 表情面板 ----------------

    private View buildEmojiPanel() {
        final View view = getLayoutInflater().inflate(R.layout.panel_emoji, mPanelContainer, false);
        final LinearLayout tabs = view.findViewById(R.id.emoji_tabs);
        final LinearLayout grid = view.findViewById(R.id.emoji_grid);
        final Skin skin = mPrefs.getSkin();
        for (int i = 0; i < EmojiData.CATEGORIES.length; i++) {
            final int index = i;
            TextView tab = new TextView(this);
            tab.setText(EmojiData.CATEGORIES[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tab.setTextColor(skin.keyText);
            tab.setPadding(dp(10), dp(6), dp(10), dp(6));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback();
                    for (int j = 0; j < tabs.getChildCount(); j++) {
                        tabs.getChildAt(j).setSelected(false);
                    }
                    v.setSelected(true);
                    renderEmoji(grid, index);
                }
            });
            tab.setSelected(i == 0);
            tabs.addView(tab);
        }
        renderEmoji(grid, 0);
        return view;
    }

    private void renderEmoji(LinearLayout grid, int category) {
        grid.removeAllViews();
        String[] items = EmojiData.of(category);
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % EMOJI_PER_ROW == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                grid.addView(row);
            }
            final String text = items[i];
            TextView cell = new TextView(this);
            cell.setText(text);
            cell.setGravity(Gravity.CENTER);
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            cell.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback();
                    commitText(text);
                }
            });
            if (row != null) {
                row.addView(cell);
            }
        }
    }

    // ---------------- 列表面板（更多候选 / 常用语 / 剪贴板） ----------------

    private View buildMorePanel() {
        View view = getLayoutInflater().inflate(R.layout.panel_list, mPanelContainer, false);
        TextView title = view.findViewById(R.id.panel_title);
        ListView list = view.findViewById(R.id.panel_list_view);
        View editor = view.findViewById(R.id.panel_editor);
        title.setText(R.string.panel_more_candidates);
        editor.setVisibility(View.GONE);

        String query = currentQuery();
        List<Candidate> cands = new ArrayList<>();
        if (query.length() > 0) {
            mEngine.setContext(mPrefs.isContextPredictEnabled() ? mLastWord : null);
            mEngine.setFuzzy(mPrefs.isFuzzyEnabled());
            if (isT9Layout()) {
                cands = mEngine.queryT9(query, MORE_LIMIT);
            } else {
                cands = mEngine.query(query, MORE_LIMIT);
            }
        }
        bindCandidateList(list, cands);
        wirePanelBack(view);
        return view;
    }

    private View buildPhrasePanel() {
        final View view = getLayoutInflater().inflate(R.layout.panel_list, mPanelContainer, false);
        TextView title = view.findViewById(R.id.panel_title);
        final ListView list = view.findViewById(R.id.panel_list_view);
        final View editor = view.findViewById(R.id.panel_editor);
        final EditText input = view.findViewById(R.id.panel_input);
        View add = view.findViewById(R.id.panel_add);
        title.setText(R.string.panel_phrases);
        editor.setVisibility(View.VISIBLE);
        input.setShowSoftInputOnFocus(false);

        loadPhrases(list);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedback();
                final String text = input.getText().toString().trim();
                if (text.isEmpty()) {
                    return;
                }
                mWorker.execute(new Runnable() {
                    @Override
                    public void run() {
                        mStore.addPhrase(text);
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                input.setText("");
                                loadPhrases(list);
                                Toast.makeText(PickWordImeService.this,
                                        R.string.phrase_added, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });
        wirePanelBack(view);
        return view;
    }

    private View buildClipPanel() {
        final View view = getLayoutInflater().inflate(R.layout.panel_list, mPanelContainer, false);
        TextView title = view.findViewById(R.id.panel_title);
        final ListView list = view.findViewById(R.id.panel_list_view);
        final View editor = view.findViewById(R.id.panel_editor);
        final EditText input = view.findViewById(R.id.panel_input);
        View add = view.findViewById(R.id.panel_add);
        title.setText(R.string.panel_clipboard);
        editor.setVisibility(View.VISIBLE);
        input.setVisibility(View.GONE);
        if (add instanceof TextView) {
            ((TextView) add).setText(R.string.clip_cleared_action);
        }

        loadClips(list);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                feedback();
                mWorker.execute(new Runnable() {
                    @Override
                    public void run() {
                        mStore.clearClips();
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                loadClips(list);
                                Toast.makeText(PickWordImeService.this,
                                        R.string.clip_cleared, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        });
        wirePanelBack(view);
        return view;
    }

    private void wirePanelBack(View panel) {
        View back = panel.findViewById(R.id.panel_back);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback();
                    showKeyboardPanel();
                }
            });
        }
    }

    private void loadPhrases(final ListView list) {
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                final List<String> items = mStore.listPhrases();
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bindTextList(list, items, R.string.phrase_empty, true);
                    }
                });
            }
        });
    }

    private void loadClips(final ListView list) {
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                final List<String> items = mStore.listClips(CLIP_KEEP);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bindTextList(list, items, R.string.clip_empty, false);
                    }
                });
            }
        });
    }

    /** 文本列表面板：点按上屏，常用语长按删除 */
    private void bindTextList(ListView list, List<String> items, final int emptyRes,
                              final boolean removable) {
        final Skin skin = mPrefs.getSkin();
        ViewGroup parent = list.getParent() instanceof ViewGroup
                ? (ViewGroup) list.getParent() : null;
        // 先清掉上一次的空提示，避免与列表共存
        if (parent != null) {
            View old = parent.findViewById(R.id.panel_empty_hint);
            if (old != null) {
                parent.removeView(old);
            }
        }
        if (items.isEmpty()) {
            list.setAdapter(null);
            if (parent != null) {
                TextView hint = new TextView(this);
                hint.setId(R.id.panel_empty_hint);
                hint.setText(emptyRes);
                hint.setTextColor(skin.gloss);
                hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                hint.setPadding(dp(12), dp(16), dp(12), dp(16));
                parent.addView(hint);
            }
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(skin.keyText);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    tv.setPadding(dp(8), dp(10), dp(8), dp(10));
                }
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof String) {
                    feedback();
                    commitText((String) item);
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!removable) {
                    return false;
                }
                Object item = parent.getItemAtPosition(position);
                if (!(item instanceof String)) {
                    return false;
                }
                final String text = (String) item;
                mWorker.execute(new Runnable() {
                    @Override
                    public void run() {
                        mStore.removePhrase(text);
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showPanel(mPanel);
                            }
                        });
                    }
                });
                return true;
            }
        });
    }

    private void bindCandidateList(ListView list, List<Candidate> cands) {
        final Skin skin = mPrefs.getSkin();
        final boolean gloss = mPrefs.isGlossEnabled();
        if (cands.isEmpty()) {
            list.setAdapter(null);
            return;
        }
        ArrayAdapter<Candidate> adapter = new ArrayAdapter<Candidate>(
                this, android.R.layout.simple_list_item_1, cands) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    Candidate c = getItem(position);
                    TextView tv = (TextView) v;
                    tv.setTextColor(skin.keyText);
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                    tv.setPadding(dp(8), dp(10), dp(8), dp(10));
                    if (c != null && gloss && c.gloss != null && !c.gloss.isEmpty()) {
                        tv.setText(c.word + "   " + c.gloss);
                    }
                }
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof Candidate) {
                    feedback();
                    commitCandidate((Candidate) item);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // 键盘布线
    // ------------------------------------------------------------------

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
                if (v instanceof Button && v.getId() != R.id.panel_add) {
                    // panel_add 由各面板自带的监听处理（避免递归），其余统一走按键分发
                    v.setOnClickListener(mKeyListener);
                    v.setOnTouchListener(mKeyTouchListener);
                }
            }
        }
    }

    private static void bindLabels(View root, int[] ids, String[] labels) {
        for (int i = 0; i < ids.length && i < labels.length; i++) {
            View v = root.findViewById(ids[i]);
            if (v instanceof Button) {
                ((Button) v).setText(labels[i]);
            }
        }
    }

    // ------------------------------------------------------------------
    // 按键分发
    // ------------------------------------------------------------------

    private final View.OnClickListener mKeyListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                onKey(v);
            } catch (Throwable ignored) {
                // 单个按键异常不让整个输入法崩溃
            }
        }
    };

    private void onKey(View v) {
        feedback();
        int id = v.getId();

        // 候选栏工具
        if (id == R.id.btn_more) {
            showPanel(P_MORE);
            return;
        }
        if (id == R.id.btn_emoji) {
            showPanel(P_EMOJI);
            return;
        }
        if (id == R.id.btn_phrase) {
            showPanel(P_PHRASE);
            return;
        }

        // 面板公共键
        if (id == R.id.panel_back) {
            showKeyboardPanel();
            return;
        }
        if (id == R.id.panel_delete) {
            handleDelete();
            return;
        }

        // 删除 / 换行 / 空格
        if (id == R.id.k_delete || id == R.id.s_delete || id == R.id.t_delete) {
            handleDelete();
            return;
        }
        if (id == R.id.k_enter || id == R.id.t_enter) {
            handleEnter();
            return;
        }
        if (id == R.id.k_space || id == R.id.s_space) {
            handleSpace();
            return;
        }
        if (id == R.id.t_0) {
            if (mDigits.length() == 0) {
                commitText(" ");
            } else {
                commitFirstCandidate();
            }
            return;
        }

        // 面板切换
        if (id == R.id.k_symbol || id == R.id.t_symbol || id == R.id.t_1) {
            showPanel(P_SYMBOL);
            return;
        }
        if (id == R.id.s_back) {
            showKeyboardPanel();
            return;
        }
        if (id == R.id.t_abc) {
            mPrefs.setKeyboardLayout(Prefs.LAYOUT_QWERTY);
            showKeyboardPanel();
            return;
        }
        if (id == R.id.k_lang || id == R.id.t_lang) {
            mEnglishMode = !mEnglishMode;
            clearComposing(true);
            Toast.makeText(this, mEnglishMode ? R.string.subtype_en : R.string.subtype_zh,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 标点
        if (id == R.id.k_comma || id == R.id.s_comma || id == R.id.t_comma) {
            commitText("，");
            return;
        }
        if (id == R.id.k_period || id == R.id.s_period || id == R.id.t_period) {
            commitText("。");
            return;
        }

        // 九宫格数字
        String digit = t9Digit(id);
        if (digit != null) {
            if (mEnglishMode) {
                commitText(digit);
            } else {
                appendDigit(digit);
            }
            return;
        }

        String label = v instanceof Button ? ((Button) v).getText().toString() : "";
        if (label.isEmpty()) {
            return;
        }

        // 数字行：直接上屏
        if (isNumberRowKey(id)) {
            commitText(label);
            return;
        }
        // 符号面板：直接上屏
        if (mPanel == P_SYMBOL) {
            commitText(label);
            return;
        }
        // 英文模式：直接上屏（句首自动大写）
        if (mEnglishMode) {
            commitText(shouldCapitalize() ? label.toUpperCase(Locale.US) : label);
            return;
        }
        // 拼音模式
        appendLetter(label.toLowerCase(Locale.US));
    }

    private String t9Digit(int id) {
        for (int i = 0; i < T9_IDS.length; i++) {
            if (T9_IDS[i] == id) {
                return T9_DIGITS[i];
            }
        }
        return null;
    }

    private boolean isNumberRowKey(int id) {
        for (int value : NUMBER_IDS) {
            if (value == id) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 输入逻辑
    // ------------------------------------------------------------------

    private boolean isT9Layout() {
        return mPanel == P_T9;
    }

    private String currentQuery() {
        return isT9Layout() ? mDigits.toString() : mComposing.toString();
    }

    private void appendLetter(String letter) {
        if (mComposing.length() >= 24) {
            return;
        }
        mComposing.append(letter);
        updateCompositionText();
        refreshCandidates();
    }

    private void appendDigit(String digit) {
        if (mDigits.length() >= 12) {
            return;
        }
        mDigits.append(digit);
        updateCompositionText();
        refreshCandidates();
    }

    private void handleSpace() {
        if (currentQuery().length() == 0) {
            commitText(" ");
            return;
        }
        commitFirstCandidate();
    }

    private void commitFirstCandidate() {
        if (!mCandidates.isEmpty()) {
            commitCandidate(mCandidates.get(0));
            return;
        }
        String query = currentQuery();
        if (query.length() > 0) {
            commitText(query);
        }
    }

    private void handleEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (currentQuery().length() > 0) {
            // 拼音串/数字串直接上屏（想打英文用）
            commitText(currentQuery());
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
        if (mDigits.length() > 0) {
            mDigits.deleteCharAt(mDigits.length() - 1);
            updateCompositionText();
            refreshCandidates();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.deleteSurroundingText(1, 0);
        }
    }

    /** 退格左滑：整词删除 */
    private void deleteWordBeforeCursor() {
        if (mComposing.length() > 0 || mDigits.length() > 0) {
            clearComposing(true);
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(48, 0);
        if (before == null || before.length() == 0) {
            ic.deleteSurroundingText(1, 0);
            return;
        }
        int n = 0;
        for (int i = before.length() - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (Character.isLetterOrDigit(c) || c >= 0x2E80) {
                n++;
            } else {
                break;
            }
        }
        if (n == 0) {
            n = 1;
        }
        ic.deleteSurroundingText(n, 0);
    }

    /** 空格左右滑：移动光标 */
    private void moveCursor(int dir) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        int key = dir > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
    }

    /** 是否含中日韩表意汉字（用于判断上下文联想的「词」） */
    private static boolean containsCJK(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /** 英文模式句首自动大写 */
    private boolean shouldCapitalize() {        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return false;
        }
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) {
            return true;
        }
        char c = before.charAt(before.length() - 1);
        return c == '\n' || c == '.' || c == '!' || c == '?' || c == '。' || c == '！'
                || c == '？';
    }

    private void commitCandidate(final Candidate candidate) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
            ic.commitText(candidate.word, 1);
        }
        recordClip(candidate.word);
        final String word = candidate.word;
        // 先在主线程捕获「上一词」：若在后台任务里再读 mLastWord，
        // 主线程可能已把它更新为当前词，导致 prev==word 被跳过、共现永远记不上
        final String prevWord = mLastWord;
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                mStore.bumpFrequency(word);
                // 上下文联想：记录「上一词 -> 本词」共现（仅记中文词）
                if (containsCJK(word)) {
                    mStore.bumpCooc(prevWord, word);
                }
            }
        });
        // 更新上下文：中文词作为下一词的「上一词」
        if (containsCJK(word)) {
            mLastWord = word;
        } else {
            // 标点/英文等打断上下文，下一词不从它联想
            mLastWord = null;
        }
        clearComposing(true);
    }

    private void commitText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
        recordClip(text);
        clearComposing(true);
    }

    /** 上屏内容进剪贴板历史（本地保存，最长 CLIP_KEEP 条） */
    private void recordClip(final String text) {
        if (text == null) {
            return;
        }
        final String t = text.trim();
        if (t.length() < 2) {
            return;
        }
        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                mStore.addClip(t, CLIP_KEEP);
            }
        });
    }

    private void clearComposing(boolean updateConnection) {
        mComposing.setLength(0);
        mDigits.setLength(0);
        InputConnection ic = getCurrentInputConnection();
        if (updateConnection && ic != null) {
            ic.finishComposingText();
        }
        updateCompositionText();
    }

    private void updateCompositionText() {
        InputConnection ic = getCurrentInputConnection();
        String query = currentQuery();
        if (ic != null) {
            if (query.length() > 0) {
                ic.setComposingText(query, 1);
            } else {
                ic.finishComposingText();
            }
        }
        if (mComposition != null) {
            mComposition.setText(query.length() == 0
                    ? getString(R.string.candidates_empty) : query);
        }
    }

    // ------------------------------------------------------------------
    // 候选栏
    // ------------------------------------------------------------------

    private void refreshCandidates() {
        if (mCandidateContainer == null) {
            return;
        }
        try {
            List<Candidate> list = new ArrayList<>();
            String query = currentQuery();
            if (query.length() > 0) {
                mEngine.setContext(mPrefs.isContextPredictEnabled() ? mLastWord : null);
                mEngine.setFuzzy(mPrefs.isFuzzyEnabled());
                if (isT9Layout()) {
                    list = mEngine.queryT9(query, MAX_CANDIDATES);
                } else {
                    list = mEngine.query(query, MAX_CANDIDATES);
                }
            }
            mCandidates = list;
            renderCandidates(list);
        } catch (Throwable ignored) {
            // 词典/查询异常降级为空候选，不影响键盘本身
        }
    }

    private void renderCandidates(List<Candidate> list) {
        if (mCandidateContainer == null) {
            return;
        }
        try {
            mCandidateContainer.removeAllViews();
            mCandidateViews.clear();
            boolean glossEnabled = mPrefs.isGlossEnabled();
            int layout = mPrefs.isGlossOnRight()
                    ? R.layout.item_candidate_right : R.layout.item_candidate;
            LayoutInflater inflater = getLayoutInflater();
            for (Candidate c : list) {
                View item = inflater.inflate(layout, mCandidateContainer, false);
                Skinner.applyCandidateItem(item, mPrefs);
                TextView word = item.findViewById(R.id.candidate_word);
                TextView gloss = item.findViewById(R.id.candidate_gloss);
                TextView star = item.findViewById(R.id.candidate_star);
                if (word != null) {
                    word.setText(c.word);
                }
                if (gloss != null) {
                    if (glossEnabled && c.gloss != null && !c.gloss.isEmpty()) {
                        gloss.setText(c.gloss);
                        gloss.setVisibility(View.VISIBLE);
                    } else {
                        gloss.setVisibility(View.GONE);
                    }
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
                if (star != null) {
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
                }
                mCandidateContainer.addView(item);
                mCandidateViews.add(item);
            }
            if (mCandidateScroll != null) {
                mCandidateScroll.scrollTo(0, 0);
            }
        } catch (Throwable ignored) {
            // 任何渲染意外都不应让键盘崩掉
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
    // 候选栏：内嵌 / 悬浮
    // ------------------------------------------------------------------

    private void setupCandidateBar(View bar) {
        if (bar == null) {
            return;
        }
        View more = bar.findViewById(R.id.btn_more);
        View emoji = bar.findViewById(R.id.btn_emoji);
        View phrase = bar.findViewById(R.id.btn_phrase);
        if (more != null) {
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback();
                    showPanel(P_MORE);
                }
            });
        }
        if (emoji != null) {
            emoji.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback();
                    showPanel(P_EMOJI);
                }
            });
        }
        if (phrase != null) {
            phrase.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    feedback();
                    showPanel(P_CLIP);
                }
            });
        }
        Skinner.applyCandidateBar(bar, mPrefs);
        bindCandidateViews(bar);
    }

    private void bindCandidateViews(View bar) {
        if (bar == null) {
            return;
        }
        mComposition = bar.findViewById(R.id.composition);
        mCandidateScroll = bar.findViewById(R.id.candidate_scroll);
        mCandidateContainer = bar.findViewById(R.id.candidate_container);
        Skin skin = mPrefs.getSkin();
        if (mComposition != null) {
            mComposition.setTextColor(skin.keyText);
            mComposition.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            mComposition.setTypeface(Skinner.typeface(mPrefs));
        }
        View moreBtn = bar.findViewById(R.id.btn_more);
        if (moreBtn instanceof TextView) {
            ((TextView) moreBtn).setTextColor(skin.keyTextSpecial);
        }
        View emojiBtn = bar.findViewById(R.id.btn_emoji);
        if (emojiBtn instanceof TextView) {
            ((TextView) emojiBtn).setTextColor(skin.keyTextSpecial);
        }
        View phraseBtn = bar.findViewById(R.id.btn_phrase);
        if (phraseBtn instanceof TextView) {
            ((TextView) phraseBtn).setTextColor(skin.keyTextSpecial);
        }
    }

    private void applyFloatingCandidates() {
        if (mPrefs == null) {
            return;
        }
        boolean floating = mPrefs.isFloatingCandidates();
        if (mEmbeddedBar != null) {
            mEmbeddedBar.setVisibility(floating ? View.GONE : View.VISIBLE);
        }
        if (mDivider != null) {
            mDivider.setVisibility(floating ? View.GONE : View.VISIBLE);
        }
        if (floating) {
            if (mFloatingBar == null) {
                mFloatingBar = getLayoutInflater().inflate(R.layout.candidate_bar, null);
                setupCandidateBar(mFloatingBar);
            }
            setCandidatesView(mFloatingBar);
            setCandidatesViewShown(true);
            bindCandidateViews(mFloatingBar);
        } else {
            setCandidatesViewShown(false);
            if (mEmbeddedBar != null) {
                bindCandidateViews(mEmbeddedBar);
            }
        }
        refreshCandidates();
    }

    // ------------------------------------------------------------------
    // 皮肤 / 形态
    // ------------------------------------------------------------------

    /** 自定义背景图：贴到根视图上（BitmapDrawable 默认铺满） */
    private void applyBackground() {
        if (mImeRoot == null) {
            return;
        }
        String path = mPrefs.getBackgroundImage();
        Bitmap bmp = null;
        if (path != null && !path.isEmpty()) {
            bmp = BitmapFactory.decodeFile(path);
        }
        if (bmp == null) {
            mImeRoot.setBackground(null);
            if (mBackgroundImage != null) {
                mBackgroundImage.setVisibility(View.GONE);
            }
            return;
        }
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
        int alpha = (int) (mPrefs.getBackgroundAlpha() / 100f * 255);
        drawable.setAlpha(Math.max(0, Math.min(255, alpha)));
        mImeRoot.setBackground(drawable);
    }

    /** 单手模式：键盘收窄到左右下角 */
    private void applyOneHand() {
        if (mImeContent == null) {
            return;
        }
        ViewGroup.LayoutParams lp = mImeContent.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
        int mode = mPrefs.getOneHand();
        if (mode == Prefs.ONE_HAND_OFF) {
            flp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            flp.gravity = Gravity.TOP | Gravity.START;
        } else {
            flp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
            flp.gravity = Gravity.BOTTOM
                    | (mode == Prefs.ONE_HAND_LEFT ? Gravity.START : Gravity.END);
        }
        mImeContent.setLayoutParams(flp);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    // 触感反馈与手势
    // ------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void feedback() {
        if (mPrefs.isSoundEnabled()) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
            }
        }
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

    private final Runnable mRepeatDelete = new Runnable() {
        @Override
        public void run() {
            handleDelete();
            mMainHandler.postDelayed(this, 60);
        }
    };

    /**
     * 按键手势：自绘触摸流程，保证滑动与长按连删可靠，同时保留点击与按下态。
     */
    private final View.OnTouchListener mKeyTouchListener = new View.OnTouchListener() {
        private float mDownX;
        private float mDownY;
        private boolean mConsumed;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int id = v.getId();
            boolean isSpace = id == R.id.k_space || id == R.id.s_space || id == R.id.t_0;
            boolean isDelete = id == R.id.k_delete || id == R.id.s_delete || id == R.id.t_delete
                    || id == R.id.panel_delete;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = event.getX();
                    mDownY = event.getY();
                    mConsumed = false;
                    v.setPressed(true);
                    if (isDelete) {
                        mMainHandler.postDelayed(mRepeatDelete, 400);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!mConsumed) {
                        float dx = event.getX() - mDownX;
                        float dy = event.getY() - mDownY;
                        if (Math.abs(dx) > mSwipeSlop && Math.abs(dx) > Math.abs(dy)) {
                            if (isSpace && mPrefs.isSwipeSpaceEnabled()) {
                                moveCursor(dx > 0 ? 1 : -1);
                                mConsumed = true;
                                mMainHandler.removeCallbacks(mRepeatDelete);
                            } else if (isDelete && mPrefs.isSwipeDeleteEnabled()) {
                                deleteWordBeforeCursor();
                                mConsumed = true;
                                mMainHandler.removeCallbacks(mRepeatDelete);
                            }
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    mMainHandler.removeCallbacks(mRepeatDelete);
                    v.setPressed(false);
                    if (!mConsumed) {
                        v.performClick();
                    }
                    mConsumed = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    mMainHandler.removeCallbacks(mRepeatDelete);
                    v.setPressed(false);
                    mConsumed = false;
                    return true;
                default:
                    return false;
            }
        }
    };
}
