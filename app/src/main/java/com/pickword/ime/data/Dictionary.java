package com.pickword.ime.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 内置词库：随 APK 打包的 {@code pickword.db}，首次使用时从 assets 复制到应用私有目录，
 * 之后以只读方式打开。词库包含拼音词典（词 / 拼音 / 简拼 / 词频）与英文译词。
 */
public final class Dictionary {

    public static final String DB_NAME = "pickword.db";

    private static volatile Dictionary sInstance;
    private static volatile boolean sReady;

    private SQLiteDatabase mDb;

    private Dictionary(SQLiteDatabase db) {
        mDb = db;
    }

    /** 在后台线程调用：完成复制并打开，标记就绪 */
    public static void prepare(Context context) {
        if (sReady) {
            return;
        }
        synchronized (Dictionary.class) {
            if (sReady) {
                return;
            }
            try {
                File file = new File(context.getFilesDir(), DB_NAME);
                if (!file.exists() || file.length() == 0) {
                    copyFromAssets(context, file);
                }
                SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null,
                        SQLiteDatabase.OPEN_READONLY);
                sInstance = new Dictionary(db);
                sReady = true;
            } catch (Throwable t) {
                sReady = false;
            }
        }
    }

    public static boolean isReady() {
        return sReady;
    }

    /** 词典未就绪时返回 null，调用方按空结果处理 */
    public static SQLiteDatabase db() {
        Dictionary d = sInstance;
        return d != null ? d.mDb : null;
    }

    private static void copyFromAssets(Context context, File out) throws IOException {
        InputStream in = null;
        OutputStream os = null;
        File tmp = new File(out.getAbsolutePath() + ".tmp");
        try {
            in = context.getAssets().open(DB_NAME);
            os = new FileOutputStream(tmp);
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
            os.flush();
            if (!tmp.renameTo(out)) {
                throw new IOException("rename failed: " + tmp.getName());
            }
        } finally {
            closeQuietly(os);
            closeQuietly(in);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // 忽略
            }
        }
    }
}
