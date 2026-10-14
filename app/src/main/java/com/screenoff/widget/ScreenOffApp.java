package com.screenoff.widget;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

/**
 * Locale wrapper: every component (activity, service, widget provider) wraps
 * its base context with the user's selected UI language ("sys"/"en"/"hi"/"ta")
 * so all components resolve the same localized strings.
 */
public class ScreenOffApp extends Application {

    public static final String LANG_KEY = "ui_lang";
    public static final String LANG_DEFAULT = "sys";
    public static final String LANG_EN = "en";
    public static final String LANG_HI = "hi";
    public static final String LANG_TA = "ta";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(wrap(base));
    }

    /** Resolve the effective language and return a configured context (or base). */
    public static Context wrap(Context base) {
        Context into = null;
        // Android 13+: the system per-app language choice wins.
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager lm =
                    base.getSystemService(android.app.LocaleManager.class);
            if (lm != null && !lm.getApplicationLocales().isEmpty()) {
                Locale chosen = lm.getApplicationLocales().get(0);
                into = base.createConfigurationContext(configFor(chosen));
            }
        }
        if (into == null) {
            String code = base.getApplicationContext()
                    .getSharedPreferences("screenoff", Context.MODE_PRIVATE)
                    .getString(LANG_KEY, LANG_DEFAULT);
            if (!LANG_DEFAULT.equals(code)) {
                into = base.createConfigurationContext(configFor(new Locale(code)));
            }
        }
        return into != null ? into : base;
    }

    private static Configuration configFor(Locale locale) {
        Configuration cfg = new Configuration();
        cfg.setLocale(locale);
        return cfg;
    }

    /** Persist the manual choice (pre-Android-13 mechanism). */
    public static void persistLang(Context ctx, String code) {
        ctx.getApplicationContext()
                .getSharedPreferences("screenoff", Context.MODE_PRIVATE)
                .edit()
                .putString(LANG_KEY, code)
                .apply();
    }

    /** Active language code for highlight rendering ("sys", "en"…). */
    public static String activeLang(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager lm = ctx.getSystemService(android.app.LocaleManager.class);
            if (lm != null && !lm.getApplicationLocales().isEmpty()) {
                return toCode(lm.getApplicationLocales().get(0));
            }
        }
        return ctx.getApplicationContext()
                .getSharedPreferences("screenoff", Context.MODE_PRIVATE)
                .getString(LANG_KEY, LANG_DEFAULT);
    }

    private static String toCode(Locale l) {
        String tag = l.toLanguageTag();
        if (tag.startsWith("hi")) {
            return LANG_HI;
        }
        if (tag.startsWith("ta")) {
            return LANG_TA;
        }
        if (tag.startsWith("en")) {
            return LANG_EN;
        }
        return LANG_DEFAULT;
    }
}
