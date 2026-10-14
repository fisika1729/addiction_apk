package com.screenoff.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Home-screen widget: status line + 1min / 2min / custom chips + START/STOP toggle.
 * The system performs the idle screen-off; this provider just renders state.
 */
public class TickerWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE = "com.screenoff.widget.TOGGLE";
    public static final String ACTION_SET1MIN = "com.screenoff.widget.SET1MIN";
    public static final String ACTION_SET2MIN = "com.screenoff.widget.SET2MIN";
    public static final String ACTION_SET_CUSTOM = "com.screenoff.widget.SET_CUSTOM";
    public static final String ACTION_DISARM = "com.screenoff.widget.DISARM";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        context = ScreenOffApp.wrap(context);
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        stopCountdown(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        context = ScreenOffApp.wrap(context);
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        switch (action) {
            case ACTION_TOGGLE: {
                if (AppState.isArmed(context)) {
                    stopCountdown(context);
                } else {
                    startCountdown(context, AppState.getDelaySeconds(context));
                }
                break;
            }
            case ACTION_SET1MIN:
            case ACTION_SET2MIN: {
                long d = ACTION_SET1MIN.equals(action)
                        ? AppState.DELAY_1MIN : AppState.DELAY_2MIN;
                applyChip(context, d);
                break;
            }
            case ACTION_SET_CUSTOM: {
                if (AppState.isArmed(context)) {
                    stopCountdown(context);
                } else {
                    Intent i = new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MainActivity.EXTRA_OPEN_CUSTOM, true);
                    context.startActivity(i);
                }
                break;
            }
            case ACTION_DISARM: {
                stopCountdown(context);
                break;
            }
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED: {
                stopCountdown(context);
                break;
            }
            default:
                break;
        }
        AppState.refresh(context);
    }

    static void applyChip(Context context, long d) {
        if (AppState.isArmed(context)) {
            stopCountdown(context);
            startCountdown(context, d);
        } else {
            AppState.setDelay(context, d);
        }
    }

    static void startCountdown(Context context, long delay) {
        boolean ok = AppState.arm(context, delay);  // sets system idle timeout
        if (ok) {
            Intent svc = new Intent(context, TimerService.class);
            context.startForegroundService(svc);
        }
        AppState.refresh(context);
    }

    static void stopCountdown(Context context) {
        AppState.disarm(context);
        context.stopService(new Intent(context, TimerService.class));
    }

    public static RemoteViews buildViews(Context context) {
        context = ScreenOffApp.wrap(context);
        boolean armed = AppState.isArmed(context);
        boolean repeating = AppState.isRepeat(context);
        long delay = AppState.getDelaySeconds(context);

        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        rv.setTextViewText(R.id.btn1min, context.getString(R.string.wchip_1min));
        rv.setTextViewText(R.id.btn2min, context.getString(R.string.wchip_2min));

        if (armed) {
            rv.setTextViewText(R.id.status_text, context.getString(
                    repeating ? R.string.widget_counting_repeat : R.string.widget_counting,
                    delay));
            rv.setTextColor(R.id.status_text, context.getColor(R.color.accent));
            rv.setTextViewText(R.id.btn_toggle, context.getText(R.string.widget_stop));
            rv.setTextColor(R.id.btn_toggle, context.getColor(R.color.danger));
        } else if (AppState.needsWriteAccess(context)) {
            rv.setTextViewText(R.id.status_text, context.getText(R.string.widget_needs_write));
            rv.setTextColor(R.id.status_text, context.getColor(R.color.danger));
            rv.setTextViewText(R.id.btn_toggle, context.getText(R.string.widget_start));
            rv.setTextColor(R.id.btn_toggle, context.getColor(R.color.text_bright));
        } else {
            rv.setTextViewText(R.id.status_text,
                    context.getString(R.string.widget_idle, delay));
            rv.setTextColor(R.id.status_text, context.getColor(R.color.text_dim));
            rv.setTextViewText(R.id.btn_toggle, context.getText(R.string.widget_start));
            rv.setTextColor(R.id.btn_toggle, context.getColor(R.color.text_bright));
        }

        int accent = context.getColor(R.color.accent);
        int dim = context.getColor(R.color.text_dim);
        long sel = delay;
        rv.setTextColor(R.id.btn1min, sel == AppState.DELAY_1MIN ? accent : dim);
        rv.setTextColor(R.id.btn2min, sel == AppState.DELAY_2MIN ? accent : dim);
        boolean customSel = sel != AppState.DELAY_1MIN && sel != AppState.DELAY_2MIN;
        rv.setTextColor(R.id.btnCustom, customSel ? accent : dim);
        rv.setTextViewText(R.id.btnCustom,
                customSel ? context.getString(R.string.widget_custom_sel, sel)
                        : context.getText(R.string.widget_custom));

        rv.setOnClickPendingIntent(R.id.btn_toggle, actionPending(context, ACTION_TOGGLE, 101));
        rv.setOnClickPendingIntent(R.id.btn1min, actionPending(context, ACTION_SET1MIN, 102));
        rv.setOnClickPendingIntent(R.id.btn2min, actionPending(context, ACTION_SET2MIN, 103));
        rv.setOnClickPendingIntent(R.id.btnCustom, actionPending(context, ACTION_SET_CUSTOM, 104));

        return rv;
    }

    private static PendingIntent actionPending(Context context, String action, int code) {
        Intent intent = new Intent(context, TickerWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
