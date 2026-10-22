package com.pickword.ime.engine;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.pickword.ime.data.Dictionary;
import com.pickword.ime.data.Store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /** 模糊音匹配（低于 SCORE_SINGLE，保证精确匹配始终优先） */
    private static final int SCORE_FUZZY = 760;

    /** 含生僻汉字（CJK 区但不在 GB2312）的候选降权，避免怪字挤占前排 */
    private static final int RARE_PENALTY = 600;

    /** 上下文联想：上一词之后出现过该候选时，按共现次数加权（封顶） */
    private static final int CONTEXT_BOOST_PER = 40;
    private static final int CONTEXT_BOOST_CAP = 260;

    private static final String SELECT =
            "SELECT word, gloss, freq, raw FROM entries WHERE ";

    private final Store mStore;

    /** 上下文联想的「上一词」；由输入法在每次查询前设置 */
    private String mContextWord = null;
    /** 是否启用模糊音（由输入法按设置注入） */
    private boolean mFuzzy = true;

    public PinyinEngine(Store store) {
        mStore = store;
    }

    /** 设置上下文联想的上一词（null/空表示不联想） */
    public void setContext(String prevWord) {
        mContextWord = prevWord;
    }

    /** 设置是否启用模糊音 */
    public void setFuzzy(boolean enabled) {
        mFuzzy = enabled;
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

        // 6) 模糊音：把易混音节互相映射（zong→zhong 等），作为低优先级补充
        if (mFuzzy) {
            collectFuzzy(db, merged, r);
        }

        List<Candidate> list = new ArrayList<>(merged.values());
        applyUserFrequency(list);
        applyContextBoost(list);
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
        applyContextBoost(list);
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
        if (containsRareHanzi(text)) {
            score -= RARE_PENALTY;
        }
        merged.put(text, new Candidate(text, "", score));
    }

    /**
     * 模糊音补充：把输入串按音节做易混扩展，查对应的整词拼音。
     * 例如 "zong" → {"zong","zhong"}，从而 "中"(zhong) 也能作为低优先级候选出现。
     * 仅在扩展结果与原输入不同（确有模糊映射）时才查询，避免无谓开销。
     */
    private void collectFuzzy(SQLiteDatabase db, Map<String, Candidate> merged, String raw) {
        Set<String> variants = fuzzyRawVariants(raw);
        if (variants == null || variants.isEmpty()) {
            return;
        }
        String[] args = new String[variants.size()];
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String v : variants) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
            args[i++] = v;
        }
        Cursor c = null;
        try {
            c = db.rawQuery(SELECT + "raw IN (" + sb + ")", args);
            while (c.moveToNext()) {
                String word = c.getString(0);
                String gloss = c.getString(1);
                int freq = c.getInt(2);
                String rawCode = c.getString(3);
                int pinyinLen = rawCode == null || rawCode.isEmpty() ? raw.length() : rawCode.length();
                int score = SCORE_FUZZY + boost(freq);
                if (containsRareHanzi(word)) {
                    score -= RARE_PENALTY;
                }
                Candidate existing = merged.get(word);
                if (existing == null || score > existing.score) {
                    merged.put(word, new Candidate(word, gloss, score));
                }
            }
        } catch (Throwable t) {
            // 模糊查询失败不影响主流程
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /** 把整串按音节做模糊扩展，返回去重后的整词拼音候选（最多 16 个，不含原串） */
    private static Set<String> fuzzyRawVariants(String raw) {
        List<List<String>> splits = SyllableSplitter.split(raw);
        if (splits.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        List<String> syllables = splits.get(0); // 音节数最少的切分
        List<Set<String>> options = new ArrayList<>(syllables.size());
        for (String syl : syllables) {
            // 每个音节的模糊扩展只保留「合法音节」，杜绝 f/h 互混等产生的非法组合（如 fao）
            Set<String> expanded = Fuzzy.expand(syl);
            Set<String> valid = new LinkedHashSet<>();
            for (String v : expanded) {
                if (SyllableSplitter.isSyllable(v)) {
                    valid.add(v);
                }
            }
            // Fuzzy.expand 必含原音节，故 valid 必非空
            options.add(valid);
        }
        Set<String> result = new LinkedHashSet<>();
        combine(options, 0, new StringBuilder(), result);
        result.remove(raw); // 精确串已由主流程覆盖
        return result;
    }

    /** 笛卡尔积，封顶 16。各分量已在 fuzzyRawVariants 里过滤为合法音节，这里直接拼整词拼音 */
    private static void combine(List<Set<String>> options, int idx,
                                 StringBuilder cur, Set<String> out) {
        if (out.size() >= 16) {
            return;
        }
        if (idx == options.size()) {
            out.add(cur.toString());
            return;
        }
        for (String s : options.get(idx)) {
            int len = cur.length();
            cur.append(s);
            combine(options, idx + 1, cur, out);
            cur.setLength(len);
            if (out.size() >= 16) {
                return;
            }
        }
    }

    /**
     * 上下文联想：若当前候选词曾在「上一词」之后出现过，按其共现次数加权前移。
     * 这是仿搜狗/百度「上下文预测」的本地版（无需云端），数据全部来自用户自身输入。
     */
    private void applyContextBoost(List<Candidate> list) {
        if (mStore == null || list.isEmpty()
                || mContextWord == null || mContextWord.isEmpty()) {
            return;
        }
        List<String> words = new ArrayList<>(list.size());
        for (Candidate c : list) {
            words.add(c.word);
        }
        Map<String, Integer> cooc;
        try {
            cooc = mStore.getCooc(mContextWord, words);
        } catch (Throwable t) {
            return;
        }
        if (cooc.isEmpty()) {
            return;
        }
        List<Candidate> boosted = new ArrayList<>(list.size());
        for (Candidate c : list) {
            Integer n = cooc.get(c.word);
            if (n != null && n > 0) {
                int add = n * CONTEXT_BOOST_PER;
                if (add > CONTEXT_BOOST_CAP) {
                    add = CONTEXT_BOOST_CAP;
                }
                boosted.add(new Candidate(c.word, c.gloss, c.score + add));
            } else {
                boosted.add(c);
            }
        }
        list.clear();
        list.addAll(boosted);
    }

    /**
     * 是否含「生僻汉字」：落在 CJK 统一表意文字区（U+4E00–U+9FFF）但无法用 GB2312
     * 编码的字符。GB2312 覆盖 6763 个汉字（含一级 3755 最常用），可粗略代表「常用字」；
     * 扩展 A/B 等区的生僻字不在其中，应在候选里降权，避免「嚺 / 毜 / 兿」挤占前排。
     */
    private static boolean containsRareHanzi(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x4E00 || c > 0x9FFF) {
                continue;
            }
            try {
                if (String.valueOf(c).getBytes("GB2312").length != 2) {
                    return true;
                }
            } catch (Exception ignored) {
                return true;
            }
        }
        return false;
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
                if (containsRareHanzi(word)) {
                    score -= RARE_PENALTY;
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
