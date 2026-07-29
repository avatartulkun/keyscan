package com.secureqr.scanner.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

public final class LocaleHelper {
    public static final String SYSTEM_DEFAULT = "SYSTEM_DEFAULT";
    private static final String PREFS = "secureqr_settings";
    private static final String KEY_LANGUAGE = "setting_language";

    private LocaleHelper() {
    }

    public static Context apply(Context context) {
        String language = currentLanguage(context);
        if (SYSTEM_DEFAULT.equals(language)) {
            Configuration systemConfig = context.getResources().getConfiguration();
            Locale systemLocale = android.os.Build.VERSION.SDK_INT >= 24
                    ? systemConfig.getLocales().get(0) : systemConfig.locale;
            Locale.setDefault(systemLocale);
            return context;
        }
        Locale locale;
        if ("en-US".equals(language)) locale = Locale.forLanguageTag("en-US");
        else if ("ja-JP".equals(language)) locale = Locale.JAPANESE;
        else if ("ko-KR".equals(language)) locale = Locale.KOREAN;
        else if ("de-DE".equals(language)) locale = Locale.GERMAN;
        else if ("fr-FR".equals(language)) locale = Locale.FRENCH;
        else if ("es-ES".equals(language)) locale = Locale.forLanguageTag("es-ES");
        else if ("it-IT".equals(language)) locale = Locale.ITALIAN;
        else if ("nl-NL".equals(language)) locale = Locale.forLanguageTag("nl-NL");
        else if ("pt-BR".equals(language)) locale = Locale.forLanguageTag("pt-BR");
        else if ("ru-RU".equals(language)) locale = Locale.forLanguageTag("ru-RU");
        else if ("zh-TW".equals(language)) locale = Locale.forLanguageTag("zh-TW");
        else locale = Locale.SIMPLIFIED_CHINESE;
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
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, SYSTEM_DEFAULT);
        if (saved == null || saved.isEmpty()) return SYSTEM_DEFAULT;
        if ("zh".equals(saved)) return "zh-CN";
        if ("en".equals(saved)) return "en-US";
        if ("ja".equals(saved)) return "ja-JP";
        if ("ko".equals(saved)) return "ko-KR";
        if ("de".equals(saved)) return "de-DE";
        if ("fr".equals(saved)) return "fr-FR";
        if ("es".equals(saved)) return "es-ES";
        if ("it".equals(saved)) return "it-IT";
        if ("nl".equals(saved)) return "nl-NL";
        if ("ru".equals(saved)) return "ru-RU";
        return saved;
    }
}
