package com.pickword.ime.engine;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.pickword.ime.data.Dictionary;
import com.pickword.ime.data.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 拼音引擎：把用户输入的字母串变成「中文候选 + 英文译词」。
 *
 * <p>匹配优先级（高分在前）：
 * <ol>
 *   <li>整词全拼精确匹配（raw = 输入）</li>
 *   <li>音节切分后的词组精确匹配（pinyin = "wo shi"）</li>
 *   <li>前缀匹配（输入是某词拼音的前缀，边打边出词）</li>
 *   <li>简拼匹配（initials = "ws"）</li>
 * </ol>
 * 最后叠加用户词频（个人常用词会更靠前）。
 */
public final class PinyinEngine {

    private static final int SCORE_EXACT = 1200;
    private static final int SCORE_PHRASE = 1000;
    private static final int SCORE_SINGLE = 950;
    private static final int SCORE_T9_EXACT = 1180;
    private static final int SCORE_T9_PREFIX = 780;
    private static final int SCORE_COMPOSED = 1080;
    private static final int SCORE_PREFIX = 700;
    private static final int SCORE_INITIALS = 500;

    private static final String SELECT =
            "SELECT word, gloss, freq, raw FROM entries WHERE ";

    private final Store mStore;

    public PinyinEngine(Store store) {
        mStore = store;
    }

    /**
     * 查询候选。
     *
     * @param rawInput 用户输入的字母串（可为空）
     * @param limit    最多返回多少条
     */
    public List<Candidate> query(String rawInput, int limit) {
        List<Candidate> empty = new ArrayList<>();
        if (rawInput == null || rawInput.isEmpty()) {
            return empty;
        }
        SQLiteDatabase db = Dictionary.db();
        if (db == null) {
            return empty;
        }
        String r = rawInput.toLowerCase(Locale.US);
        Map<String, Candidate> merged = new LinkedHashMap<>();

        // 1) 整词全拼
        collect(db, merged, SELECT + "raw = ?", new String[]{r}, SCORE_EXACT, r.length(), false);

        // 2) 音节切分后的词组与首音节单字
        for (List<String> syl : SyllableSplitter.split(r)) {
            String pinyin = SyllableSplitter.join(syl);
            collect(db, merged, SELECT + "pinyin = ?", new String[]{pinyin},
                    SCORE_PHRASE, r.length(), false);
            if (!syl.isEmpty()) {
                collect(db, merged, SELECT + "pinyin = ?", new String[]{syl.get(0)},
                        SCORE_SINGLE, r.length(), false);
            }
        }

        // 3) 前缀（边打边出词）
        String upper = upperBound(r);
        if (upper != null) {
            collect(db, merged, SELECT + "raw >= ? AND raw < ?", new String[]{r, upper},
                    SCORE_PREFIX, r.length(), true);
        }

        // 4) 简拼
        if (r.length() > 1) {
            collect(db, merged, SELECT + "initials = ?", new String[]{r},
                    SCORE_INITIALS, r.length(), false);
        }

        // 5) 整句组合：多音节但没有现成词时，用每音节的高频单字拼成短语
        addComposed(db, merged, r);

        List<Candidate> list = new ArrayList<>(merged.values());
        applyUserFrequency(list);
        Collections.sort(list);
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    /**
     * 九宫格（T9）查询：数字串 → 拼音 → 候选。
     *
     * @param digits 只含 2–9 的数字串
     */
    public List<Candidate> queryT9(String digits, int limit) {
        List<Candidate> empty = new ArrayList<>();
        if (digits == null || digits.isEmpty()) {
            return empty;
        }
        SQLiteDatabase db = Dictionary.db();
        if (db == null) {
            return empty;
        }
        Map<String, Candidate> merged = new LinkedHashMap<>();
        collect(db, merged, SELECT + "t9 = ?", new String[]{digits},
                SCORE_T9_EXACT, digits.length(), false);
        String upper = upperBound(digits);
        if (upper != null) {
            collect(db, merged, SELECT + "t9 >= ? AND t9 < ?",
                    new String[]{digits, upper}, SCORE_T9_PREFIX, digits.length(), true);
        }
        List<Candidate> list = new ArrayList<>(merged.values());
        applyUserFrequency(list);
        Collections.sort(list);
        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    // ------------------------------------------------------------------

    /**
     * 整句组合：把输入切成音节，每个音节取词频最高的单字，拼成一个短语候选。
     * 让「woshi」这类没有词条的常见说法也能直接上屏。
     */
    private void addComposed(SQLiteDatabase db, Map<String, Candidate> merged, String raw) {
        List<List<String>> splits = SyllableSplitter.split(raw);
        if (splits.isEmpty()) {
            return;
        }
        List<String> syllables = splits.get(0); // 音节数最少的切分
        if (syllables.size() < 2) {
            return;
        }
        StringBuilder word = new StringBuilder();
        int freqSum = 0;
        for (String syllable : syllables) {
            SingleChar top = topChar(db, syllable);
            if (top == null) {
                return;
            }
            word.append(top.ch);
            freqSum += top.freq;
        }
        String text = word.toString();
        if (merged.containsKey(text)) {
            return;
        }
        int score = SCORE_COMPOSED + freqSum / (syllables.size() * 20);
        merged.put(text, new Candidate(text, "", score));
    }

    private SingleChar topChar(SQLiteDatabase db, String syllable) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT word, freq FROM entries WHERE pinyin = ? "
                    + "AND length(word) = 1 ORDER BY freq DESC LIMIT 1", new String[]{syllable});
            if (c.moveToNext()) {
                return new SingleChar(c.getString(0), c.getInt(1));
            }
        } catch (Throwable ignored) {
            // 查询失败按无结果处理
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    private static final class SingleChar {

        final String ch;
        final int freq;

        SingleChar(String ch, int freq) {
            this.ch = ch;
            this.freq = freq;
        }
    }

    private void collect(SQLiteDatabase db, Map<String, Candidate> merged, String sql,
                         String[] args, int base, int rawLen, boolean lengthPenalty) {
        Cursor c = null;
        try {
            c = db.rawQuery(sql, args);
            while (c.moveToNext()) {
                String word = c.getString(0);
                String gloss = c.getString(1);
                int freq = c.getInt(2);
                String rawCode = c.getString(3);
                int pinyinLen = rawCode == null || rawCode.isEmpty()
                        ? rawLen : rawCode.length();
                int score = base + boost(freq);
                if (lengthPenalty) {
                    score -= (pinyinLen - rawLen) * 12;
                }
                Candidate existing = merged.get(word);
                if (existing == null || score > existing.score) {
                    merged.put(word, new Candidate(word, gloss, score));
                }
            }
        } catch (Throwable t) {
            // 词典表结构异常时不影响输入
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** 词频（0–1000）换算成 0–100 的排序加分，保证同层级内按常用度排序 */
    private static int boost(int freq) {
        int v = Math.abs(freq);
        if (v > 1000) {
            v = 1000;
        }
        return v / 10;
    }

    private void applyUserFrequency(List<Candidate> list) {
        if (mStore == null || list.isEmpty()) {
            return;
        }
        List<String> words = new ArrayList<>(list.size());
        for (Candidate c : list) {
            words.add(c.word);
        }
        Map<String, Integer> freq;
        try {
            freq = mStore.getFrequencies(words);
        } catch (Throwable t) {
            return;
        }
        if (freq.isEmpty()) {
            return;
        }
        List<Candidate> boosted = new ArrayList<>(list.size());
        for (Candidate c : list) {
            Integer f = freq.get(c.word);
            if (f != null && f > 0) {
                int add = f * 3;
                if (add > 300) {
                    add = 300;
                }
                boosted.add(new Candidate(c.word, c.gloss, c.score + add));
            } else {
                boosted.add(c);
            }
        }
        list.clear();
        list.addAll(boosted);
    }

    /** 前缀上界：把末位字母 +1，用于走索引的范围查询 */
    private static String upperBound(String s) {
        if (s.isEmpty()) {
            return null;
        }
        int i = s.length() - 1;
        char c = s.charAt(i);
        char next = (char) (c + 1);
        return s.substring(0, i) + next;
    }
}
