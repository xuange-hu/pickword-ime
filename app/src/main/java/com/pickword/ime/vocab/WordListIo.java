package com.pickword.ime.vocab;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;

import com.pickword.ime.data.Store;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Executor;

/** 词表的导入（CSV）与导出（JSON），全部走本地文件，方便做备考词表与备份。 */
public final class WordListIo {

    private WordListIo() {
    }

    public interface Callback {

        void onDone(int count);

        void onError(String message);
    }

    /** 导入 CSV：每行「词,释义」或「词<TAB>释义」 */
    public static void importCsv(final Context context, final Store store, final Uri uri,
                                Executor executor, final Handler mainHandler,
                                final Callback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                BufferedReader reader = null;
                try {
                    InputStream in = context.getContentResolver().openInputStream(uri);
                    if (in == null) {
                        throw new java.io.IOException("cannot open uri");
                    }
                    reader = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        String[] parts;
                        if (line.contains("\t")) {
                            parts = line.split("\t");
                        } else {
                            parts = line.split(",");
                        }
                        if (parts.length == 0 || parts[0].trim().isEmpty()) {
                            continue;
                        }
                        String word = parts[0].trim();
                        String gloss = parts.length > 1 ? parts[1].trim() : "";
                        if (store.addWord(word, gloss) > 0) {
                            count++;
                        }
                    }
                    final int result = count;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDone(result);
                        }
                    });
                } catch (Throwable t) {
                    postError(mainHandler, callback, t);
                } finally {
                    close(reader);
                }
            }
        });
    }

    /** 导出 JSON，可直接作为备份再导入其它设备（字段与 CSV 保持一致） */
    public static void exportJson(final Context context, final Store store, final Uri uri,
                                  Executor executor, final Handler mainHandler,
                                  final Callback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final List<Word> words = store.listWords();
                BufferedWriter writer = null;
                try {
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os == null) {
                        throw new java.io.IOException("cannot open uri");
                    }
                    writer = new BufferedWriter(new OutputStreamWriter(os,
                            Charset.forName("UTF-8")));
                    writer.write("{\"version\":1,\"words\":[");
                    for (int i = 0; i < words.size(); i++) {
                        Word w = words.get(i);
                        if (i > 0) {
                            writer.write(",");
                        }
                        writer.write("{\"word\":" + quote(w.word)
                                + ",\"gloss\":" + quote(w.gloss == null ? "" : w.gloss)
                                + ",\"interval\":" + w.intervalDays
                                + ",\"due\":" + w.dueTime
                                + ",\"ease\":" + w.ease
                                + ",\"reps\":" + w.reps
                                + ",\"lapses\":" + w.lapses + "}");
                    }
                    writer.write("]}");
                    writer.flush();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onDone(words.size());
                        }
                    });
                } catch (Throwable t) {
                    postError(mainHandler, callback, t);
                } finally {
                    close(writer);
                }
            }
        });
    }

    private static void postError(Handler mainHandler, final Callback callback, final Throwable t) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(t == null ? "" : String.valueOf(t.getMessage()));
            }
        });
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
                // 忽略
            }
        }
    }
}
