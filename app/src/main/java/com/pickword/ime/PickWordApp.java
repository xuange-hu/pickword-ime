package com.pickword.ime;

import android.app.Application;

import com.pickword.ime.data.Prefs;

/** 应用入口：只做一件事——按用户选择应用主题（跟随系统 / 浅色 / 深色）。 */
public class PickWordApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.applyTheme(Prefs.get(this).getTheme());
    }
}
