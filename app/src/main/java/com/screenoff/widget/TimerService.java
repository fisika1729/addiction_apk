package com.screenoff.widget;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

/**
 * Keeps the system idle screen-off knob armed while the user wants it and
 * re-arms it (repeat mode) or restores the original timeout (single mode)
 * when the display goes off — the OS itself performs the screen-off, so the
 * device never enters a device-admin/lockdown state that banking apps flag.
 */
public class TimerService extends Service {

    public static final String CHANNEL_ID = "countdown";
    public static final String CHANNEL_ID_ALERT = "alerts";
    public static final String ACTION_CANCEL = "com.screenoff.widget.CANCEL";
    public static final int NOTIF_ID = 42;
    public static final int NOTIF_ID_ALERT = 43;

    private BroadcastReceiver screenReceiver;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ScreenOffApp.wrap(base));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(NOTIF_ID, buildNotification(getText(R.string.notif_waiting).toString()));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            finishCountdown();
            return START_NOT_STICKY;
        }
        if (!AppState.isArmed(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIF_ID, buildNotification(waitingText()));
        listenForScreen();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
            screenReceiver = null;
        }
        super.onDestroy();
    }

    private void finishCountdown() {
        AppState.disarm(this);
        stopForeground(true);
        stopSelf();
    }

    private void listenForScreen() {
        if (screenReceiver == null) {
            screenReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!AppState.isArmed(TimerService.this)) {
                        finishCountdown();
                        return;
                    }
                    String action = intent.getAction();
                    long delay = AppState.getDelaySeconds(TimerService.this);
                    boolean repeat = AppState.isRepeat(TimerService.this);
                    if (Intent.ACTION_SCREEN_ON.equals(action)) {
                        if (repeat) {
                            // Wake-up: re-arm the same idle timer until STOP.
                            AppState.writeSystemTimeout(TimerService.this, delay * 1000L);
                        }
                        AppState.refresh(TimerService.this);
                    } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        if (!repeat) {
                            // Single mode: the idle-off happened — put the
                            // user's original timeout back and stop.
                            finishCountdown();
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenReceiver, filter);
        }
    }

    private String waitingText() {
        return getString(AppState.isRepeat(this)
                ? R.string.notif_countdown_repeat
                : R.string.notif_countdown, AppState.getDelaySeconds(this));
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, TimerService.class).setAction(ACTION_CANCEL);
        PendingIntent pCancel = PendingIntent.getService(
                this, 2, cancel, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stop = new Notification.Action.Builder(
                null, getText(R.string.notif_action_stop), pCancel).build();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_screenoff)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(stop)
                .build();
    }

    private void createChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getText(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID_ALERT,
                getText(R.string.alert_channel), NotificationManager.IMPORTANCE_DEFAULT));
    }
}
