package com.pickword.ime.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.pickword.ime.vocab.SrsScheduler;
import com.pickword.ime.vocab.Word;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户数据：用户词频 + 生词本。全部保存在设备本地的 user.db，不出设备。
 */
public final class Store extends SQLiteOpenHelper {

    private static final String DB_NAME = "user.db";
    private static final int DB_VERSION = 1;

    private static final String T_USERDICT = "userdict";
    private static final String T_WORDBOOK = "wordbook";

    private static volatile Store sInstance;

    private Store(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    public static Store get(Context context) {
        if (sInstance == null) {
            synchronized (Store.class) {
                if (sInstance == null) {
                    sInstance = new Store(context);
                }
            }
        }
        return sInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_USERDICT + " ("
                + "word TEXT PRIMARY KEY, freq INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_WORDBOOK + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "word TEXT UNIQUE NOT NULL, "
                + "gloss TEXT, "
                + "ease REAL NOT NULL DEFAULT 2.5, "
                + "interval_days INTEGER NOT NULL DEFAULT 0, "
                + "due INTEGER NOT NULL DEFAULT 0, "
                + "reps INTEGER NOT NULL DEFAULT 0, "
                + "lapses INTEGER NOT NULL DEFAULT 0, "
                + "added INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_wordbook_due ON " + T_WORDBOOK + " (due)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 是首个版本，暂无迁移逻辑
    }

    // ---------------- 用户词频 ----------------

    /** 上屏一次，词频 +1（用于候选排序） */
    public void bumpFrequency(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        // 不用 UPSERT 语法：Android 5.0 自带的 SQLite 版本过低，会直接抛语法错误
        db.execSQL("INSERT OR IGNORE INTO " + T_USERDICT + " (word, freq) VALUES (?, 0)",
                new Object[]{word});
        db.execSQL("UPDATE " + T_USERDICT + " SET freq = freq + 1 WHERE word = ?",
                new Object[]{word});
    }

    public void clearFrequency() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(T_USERDICT, null, null);
    }

    /** 批量取词频，用于候选项排序加权 */
    public Map<String, Integer> getFrequencies(Collection<String> words) {
        Map<String, Integer> map = new HashMap<>();
        if (words == null || words.isEmpty()) {
            return map;
        }
        StringBuilder sb = new StringBuilder();
        String[] args = new String[words.size()];
        int i = 0;
        for (String w : words) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
            args[i++] = w;
        }
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT word, freq FROM " + T_USERDICT
                    + " WHERE word IN (" + sb + ")", args);
            while (c.moveToNext()) {
                map.put(c.getString(0), c.getInt(1));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return map;
    }

    // ---------------- 生词本 ----------------

    /** 收藏一个词；已存在则返回 -1 */
    public long addWord(String word, String gloss) {
        if (word == null || word.isEmpty()) {
            return -1;
        }
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = null;
        try {
            c = db.query(T_WORDBOOK, new String[]{"id"}, "word = ?",
                    new String[]{word}, null, null, null);
            if (c.moveToFirst()) {
                return -1;
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("word", word);
        cv.put("gloss", gloss == null ? "" : gloss);
        cv.put("ease", SrsScheduler.defaultEase());
        cv.put("interval_days", 0);
        cv.put("due", SrsScheduler.defaultDue(now));
        cv.put("reps", 0);
        cv.put("lapses", 0);
        cv.put("added", now);
        return db.insert(T_WORDBOOK, null, cv);
    }

    public boolean removeWord(long id) {
        return getWritableDatabase().delete(T_WORDBOOK, "id = ?",
                new String[]{String.valueOf(id)}) > 0;
    }

    public List<Word> listWords() {
        List<Word> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(T_WORDBOOK, null, null, null, null, null,
                    "id DESC");
            while (c.moveToNext()) {
                list.add(read(c));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return list;
    }

    /** 待复习的词：按到期时间升序，最多 limit 个 */
    public List<Word> listDueWords(long now, int limit) {
        List<Word> list = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(T_WORDBOOK, null, "due <= ?",
                    new String[]{String.valueOf(now)}, null, null, "due ASC",
                    String.valueOf(limit));
            while (c.moveToNext()) {
                list.add(read(c));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return list;
    }

    /** 按 SM-2 更新复习结果 */
    public void applyReview(String word, int quality) {
        Word w = findWord(word);
        if (w == null) {
            return;
        }
        long now = System.currentTimeMillis();
        SrsScheduler.Result r = SrsScheduler.schedule(w, quality, now);
        ContentValues cv = new ContentValues();
        cv.put("ease", r.ease);
        cv.put("interval_days", r.intervalDays);
        cv.put("due", r.dueTime);
        cv.put("reps", r.reps);
        cv.put("lapses", r.lapses);
        getWritableDatabase().update(T_WORDBOOK, cv, "word = ?", new String[]{word});
    }

    public Word findWord(String word) {
        Cursor c = null;
        try {
            c = getReadableDatabase().query(T_WORDBOOK, null, "word = ?",
                    new String[]{word}, null, null, null);
            if (c.moveToFirst()) {
                return read(c);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    public int count() {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM " + T_WORDBOOK, null);
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return 0;
    }

    public int dueCount(long now) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM " + T_WORDBOOK + " WHERE due <= ?",
                    new String[]{String.valueOf(now)});
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return 0;
    }

    private static Word read(Cursor c) {
        return new Word(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("word")),
                c.getString(c.getColumnIndexOrThrow("gloss")),
                c.getDouble(c.getColumnIndexOrThrow("ease")),
                c.getInt(c.getColumnIndexOrThrow("interval_days")),
                c.getLong(c.getColumnIndexOrThrow("due")),
                c.getInt(c.getColumnIndexOrThrow("reps")),
                c.getInt(c.getColumnIndexOrThrow("lapses")));
    }
}
