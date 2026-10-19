package com.pickword.ime.vocab;

/** 生词本条目 */
public class Word {

    public final long id;
    public final String word;
    public final String gloss;
    public final double ease;
    public final int intervalDays;
    public final long dueTime;
    public final int reps;
    public final int lapses;

    public Word(long id, String word, String gloss, double ease, int intervalDays,
                long dueTime, int reps, int lapses) {
        this.id = id;
        this.word = word;
        this.gloss = gloss;
        this.ease = ease;
        this.intervalDays = intervalDays;
        this.dueTime = dueTime;
        this.reps = reps;
        this.lapses = lapses;
    }

    /** 熟练度（0–100），由间隔天数换算，用于列表展示 */
    public int level() {
        int level = (int) Math.round(100.0 * Math.min(intervalDays, 21) / 21.0);
        return Math.max(0, Math.min(100, level));
    }
}
