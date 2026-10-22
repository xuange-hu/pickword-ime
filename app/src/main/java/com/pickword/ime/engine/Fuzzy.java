package com.pickword.ime.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模糊音（fuzzy pinyin）等价类。
 *
 * <p>模仿搜狗/百度输入法的「智能纠错」：用户分不清卷舌音（z/zh）、前后鼻音（an/ang）
 * 时，把易混的音节互相映射，使 "zong" 也能出 "中"（zhong）、"si" 也能出 "是"（shi）。
 *
 * <p>做法：把音节拆成「声母 + 韵母」，各自按等价类扩展，再笛卡尔积重组。例如
 * "zong" → 声母 {z,zh} × 韵母 {ong} → {"zong","zhong"}。扩展结果包含原音节，
 * 因此不会丢失精确匹配。
 */
public final class Fuzzy {

    /** 声母等价类（含双向映射） */
    private static final List<Set<String>> INITIAL_CLASSES = new ArrayList<>();
    /** 韵母等价类（含双向映射） */
    private static final List<Set<String>> FINAL_CLASSES = new ArrayList<>();

    static {
        INITIAL_CLASSES.add(set("z", "zh"));
        INITIAL_CLASSES.add(set("c", "ch"));
        INITIAL_CLASSES.add(set("s", "sh"));
        INITIAL_CLASSES.add(set("n", "l", "r"));
        INITIAL_CLASSES.add(set("f", "h"));
    }

    static {
        FINAL_CLASSES.add(set("an", "ang"));
        FINAL_CLASSES.add(set("en", "eng"));
        FINAL_CLASSES.add(set("in", "ing"));
    }

    private static Set<String> set(String... items) {
        return new LinkedHashSet<>(Arrays.asList(items));
    }

    private Fuzzy() {
    }

    /** 返回该音节的模糊等价集合（含自身），至少含原音节 */
    public static Set<String> expand(String syllable) {
        Set<String> result = new LinkedHashSet<>();
        if (syllable == null || syllable.isEmpty()) {
            return result;
        }
        String initial = splitInitial(syllable);
        String fin = syllable.substring(initial.length());
        Set<String> initials = expandIn(initial, INITIAL_CLASSES);
        Set<String> finals = expandFinal(fin);
        for (String ini : initials) {
            for (String f : finals) {
                result.add(ini + f);
            }
        }
        result.add(syllable); // 保底保留精确音节
        return result;
    }

    private static Set<String> expandIn(String part, List<Set<String>> classes) {
        for (Set<String> cls : classes) {
            if (cls.contains(part)) {
                return new LinkedHashSet<>(cls);
            }
        }
        Set<String> single = new LinkedHashSet<>();
        single.add(part);
        return single;
    }

    /**
     * 韵母等价：处理 y/w 零声母的介音（yin↔ying、wang↔wan 等）。
     * yin 的核心韵母是 in，wang 的核心是 ang，需把介音 y→i / w→u 还原后再查等价类，
     * 输出时还原介音，避免生成无效音节。
     */
    private static Set<String> expandFinal(String fin) {
        for (Set<String> cls : FINAL_CLASSES) {
            if (cls.contains(fin)) {
                return new LinkedHashSet<>(cls);
            }
        }
        if (fin.length() > 1) {
            if (fin.startsWith("y") || fin.startsWith("w")) {
                // y/w 为零声母介音：yin= y+in、wang= w+ang，去掉介音查等价类后还原
                String glide = fin.substring(0, 1);
                String core = fin.substring(1);
                for (Set<String> cls : FINAL_CLASSES) {
                    if (cls.contains(core)) {
                        Set<String> out = new LinkedHashSet<>();
                        for (String v : cls) {
                            out.add(glide + v);
                        }
                        return out;
                    }
                }
            }
        }
        Set<String> single = new LinkedHashSet<>();
        single.add(fin);
        return single;
    }

    /** 取声母：优先两字母 zh/ch/sh，其次单字母声母，否则空串（零声母） */
    private static String splitInitial(String syllable) {
        if (syllable.startsWith("zh") || syllable.startsWith("ch") || syllable.startsWith("sh")) {
            return syllable.substring(0, 2);
        }
        char c = syllable.charAt(0);
        if ("bpmfdtngkhjqxrlzcs".indexOf(c) >= 0) {
            return String.valueOf(c);
        }
        // y/w 为零声母标记，不参与模糊；返回空串，整体作为韵母
        return "";
    }
}
