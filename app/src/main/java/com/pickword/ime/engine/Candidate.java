package com.pickword.ime.engine;

/** 一条候选：中文词 + 英文译词 + 排序分 */
public class Candidate implements Comparable<Candidate> {

    public final String word;
    public final String gloss;
    public final int score;

    public Candidate(String word, String gloss, int score) {
        this.word = word;
        this.gloss = gloss;
        this.score = score;
    }

    @Override
    public int compareTo(Candidate other) {
        if (this.score != other.score) {
            return other.score - this.score; // 高分在前
        }
        // 同分时短词优先，保证「我」排在「我国」前面
        return this.word.length() - other.word.length();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Candidate)) {
            return false;
        }
        return word.equals(((Candidate) o).word);
    }

    @Override
    public int hashCode() {
        return word.hashCode();
    }
}
