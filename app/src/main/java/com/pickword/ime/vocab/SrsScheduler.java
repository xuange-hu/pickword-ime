package com.pickword.ime.vocab;

/**
 * SM-2 间隔重复调度器。
 *
 * <p>输入为复习质量 q（0–5，本项目只用 5=想起来了、2=忘记了），
 * 输出新的熟练系数（ease）、下次间隔天数与下次到期时间。
 */
public final class SrsScheduler {

    public static final int QUALITY_GOOD = 5;
    public static final int QUALITY_AGAIN = 2;

    private static final double MIN_EASE = 1.3;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private SrsScheduler() {
    }

    /** 默认新词的初始状态 */
    public static double defaultEase() {
        return 2.5;
    }

    public static long defaultDue(long now) {
        return now; // 新词立即可复习
    }

    public static Result schedule(Word word, int quality, long now) {
        double ease = word.ease;
        int interval = word.intervalDays;
        int reps = word.reps;
        int lapses = word.lapses;

        if (quality < 3) {
            // 忘记了：重新开始
            reps = 0;
            interval = 1;
            lapses = lapses + 1;
        } else {
            ease = ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
            if (ease < MIN_EASE) {
                ease = MIN_EASE;
            }
            if (reps == 0) {
                interval = 1;
            } else if (reps == 1) {
                interval = 6;
            } else {
                interval = (int) Math.round(interval * ease);
            }
            if (interval < 1) {
                interval = 1;
            }
            if (interval > 365) {
                interval = 365;
            }
            reps = reps + 1;
        }
        return new Result(ease, interval, now + interval * DAY_MILLIS, reps, lapses);
    }

    public static final class Result {

        public final double ease;
        public final int intervalDays;
        public final long dueTime;
        public final int reps;
        public final int lapses;

        Result(double ease, int intervalDays, long dueTime, int reps, int lapses) {
            this.ease = ease;
            this.intervalDays = intervalDays;
            this.dueTime = dueTime;
            this.reps = reps;
            this.lapses = lapses;
        }
    }
}
