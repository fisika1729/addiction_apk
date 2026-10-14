package com.screenoff.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * Central state shared by the widget, the service and the activity.
 *
 * No device admin, no accessibility: the app only manages the SYSTEM idle
 * screen-off timeout (Settings.System.SCREEN_OFF_TIMEOUT), the same knob the
 * user sets under Display → Sleep. The OS performs the actual screen-off, so
 * from the outside (and to banking apps) everything looks like plain idle.
 */
public final class AppState {

    public static final long DELAY_15S = 15L;
    public static final long DELAY_30S = 30L;
    public static final long DELAY_1MIN = 60L;
    public static final long DELAY_2MIN = 120L;
    public static final long MIN_DELAY = 3L;
    public static final long MAX_DELAY = 3600L;

    private static final String PREFS = "screenoff";
    private static final String KEY_ARMED = "armed";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_ORIG_TIMEOUT = "orig_timeout";
    private static final String KEY_NEEDS_WRITE = "needs_write";

    private AppState() {
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isArmed(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ARMED, false);
    }

    public static boolean isRepeat(Context ctx) {
        return prefs(ctx).getBoolean(KEY_REPEAT, false);
    }

    public static void setRepeat(Context ctx, boolean repeat) {
        prefs(ctx).edit().putBoolean(KEY_REPEAT, repeat).apply();
        refresh(ctx);
    }

    public static long getDelaySeconds(Context ctx) {
        SharedPreferences p = prefs(ctx);
        try {
            return clampDelay(p.getLong(KEY_DELAY, DELAY_1MIN));
        } catch (ClassCastException e) {
            try {
                return clampDelay(p.getInt(KEY_DELAY, -1) > 0
                        ? p.getInt(KEY_DELAY, -1) : DELAY_1MIN);
            } catch (Exception ignore) {
                return DELAY_1MIN;
            }
        }
    }

    private static long clampDelay(long v) {
        if (v < MIN_DELAY || v > MAX_DELAY) {
            return DELAY_1MIN;
        }
        return v;
    }

    /** Persist the selected delay without arming anything. */
    public static void setDelay(Context ctx, long delaySeconds) {
        prefs(ctx).edit().putLong(KEY_DELAY, clampDelay(delaySeconds)).apply();
        refresh(ctx);
    }

    // ---- WRITE_SETTINGS / system idle-timeout handling -------------------

    public static boolean hasWriteAccess(Context ctx) {
        return Settings.System.canWrite(ctx);
    }

    public static long readSystemTimeout(Context ctx) {
        try {
            return Settings.System.getInt(ctx.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 30000);
        } catch (Exception e) {
            return 30000;
        }
    }

    /** Arm: remember the user's own timeout once, then override it with our delay. */
    public static boolean arm(Context ctx, long delaySeconds) {
        long delay = clampDelay(delaySeconds);
        if (!hasWriteAccess(ctx)) {
            prefs(ctx).edit().putBoolean(KEY_NEEDS_WRITE, true).putBoolean(KEY_ARMED, false).apply();
            refresh(ctx);
            return false;
        }
        SharedPreferences p = prefs(ctx);
        if (p.getLong(KEY_ORIG_TIMEOUT, -1L) < 0L) {
            p.edit().putLong(KEY_ORIG_TIMEOUT, readSystemTimeout(ctx)).apply();
        }
        p.edit()
                .putBoolean(KEY_ARMED, true)
                .putLong(KEY_DELAY, delay)
                .putBoolean(KEY_NEEDS_WRITE, false)
                .apply();
        writeSystemTimeout(ctx, delay * 1000L);
        refresh(ctx);
        return true;
    }

    public static void writeSystemTimeout(Context ctx, long millis) {
        try {
            Settings.System.putLong(ctx.getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, millis);
        } catch (Exception ignore) {
        }
    }

    /** Restore the timeout the user had before we started managing it. */
    public static void restoreSystemTimeout(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long orig = p.getLong(KEY_ORIG_TIMEOUT, -1L);
        if (orig > 0L) {
            writeSystemTimeout(ctx, orig);
            p.edit().putLong(KEY_ORIG_TIMEOUT, -1L).apply();
        }
    }

    public static void disarm(Context ctx) {
        restoreSystemTimeout(ctx);
        prefs(ctx).edit().putBoolean(KEY_ARMED, false).apply();
        refresh(ctx);
    }

    public static boolean needsWriteAccess(Context ctx) {
        return prefs(ctx).getBoolean(KEY_NEEDS_WRITE, false);
    }

    /** Re-post widget update on the main thread. */
    public static void refresh(Context ctx) {
        Runnable r = () -> renderWidget(ctx.getApplicationContext());
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }

    public static void renderWidget(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        mgr.updateAppWidget(
                new ComponentName(ctx, TickerWidgetProvider.class),
                TickerWidgetProvider.buildViews(ctx));
    }
}
