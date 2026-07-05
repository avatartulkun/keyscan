package com.secureqr.scanner.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

public final class LocaleHelper {
    private static final String PREFS = "secureqr_settings";
    private static final String KEY_LANGUAGE = "setting_language";

    private LocaleHelper() {
    }

    public static Context apply(Context context) {
        String language = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "zh");
        Locale locale = "en".equals(language) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        Locale.setDefault(locale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.setLocale(locale);
        }
        return context.createConfigurationContext(config);
    }

    public static void saveLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public static String currentLanguage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "zh");
    }
}
