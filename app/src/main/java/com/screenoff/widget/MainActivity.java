package com.screenoff.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    public static final String EXTRA_OPEN_CUSTOM = "open_custom";

    private static final int REQ_NOTIF = 8;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(ScreenOffApp.wrap(base));
    }

    private TextView statusText;
    private View grantWrite;
    private Button sel1min;
    private Button sel2min;
    private Button selCustom;
    private android.widget.Switch repeatSwitch;

    private final View.OnClickListener actions = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.btn_grant_write) {
                requestWriteAccess();
            } else if (id == R.id.btn_off_now) {
                offNow();
            } else if (id == R.id.btn_sel_1min) {
                selectDelay(AppState.DELAY_1MIN);
            } else if (id == R.id.btn_sel_2min) {
                selectDelay(AppState.DELAY_2MIN);
            } else if (id == R.id.btn_sel_custom) {
                showCustomDialog(false);
            } else if (id == R.id.btn_start) {
                startSelected();
            } else if (id == R.id.btn_stop) {
                TickerWidgetProvider.stopCountdown(MainActivity.this);
            } else if (id == R.id.btn_lang_sys) {
                applyLang(ScreenOffApp.LANG_DEFAULT);
            } else if (id == R.id.btn_lang_en) {
                applyLang(ScreenOffApp.LANG_EN);
            } else if (id == R.id.btn_lang_hi) {
                applyLang(ScreenOffApp.LANG_HI);
            } else if (id == R.id.btn_lang_ta) {
                applyLang(ScreenOffApp.LANG_TA);
            }
            render();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.status_text);
        grantWrite = findViewById(R.id.btn_grant_write);
        sel1min = findViewById(R.id.btn_sel_1min);
        sel2min = findViewById(R.id.btn_sel_2min);
        selCustom = findViewById(R.id.btn_sel_custom);
        repeatSwitch = findViewById(R.id.repeat_switch);

        findViewById(R.id.btn_grant_write).setOnClickListener(actions);
        findViewById(R.id.btn_off_now).setOnClickListener(actions);
        findViewById(R.id.btn_sel_1min).setOnClickListener(actions);
        findViewById(R.id.btn_sel_2min).setOnClickListener(actions);
        findViewById(R.id.btn_sel_custom).setOnClickListener(actions);
        findViewById(R.id.btn_start).setOnClickListener(actions);
        findViewById(R.id.btn_stop).setOnClickListener(actions);
        findViewById(R.id.btn_lang_sys).setOnClickListener(actions);
        findViewById(R.id.btn_lang_en).setOnClickListener(actions);
        findViewById(R.id.btn_lang_hi).setOnClickListener(actions);
        findViewById(R.id.btn_lang_ta).setOnClickListener(actions);

        repeatSwitch.setChecked(AppState.isRepeat(this));
        repeatSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppState.setRepeat(MainActivity.this, isChecked);
                render();
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            }
        }

        boolean openCustom = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_OPEN_CUSTOM, false);
        if (openCustom) {
            selCustom.post(() -> showCustomDialog(true));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    /** Only SELECTION — picking a delay never starts the idle timer. */
    private void selectDelay(long delay) {
        AppState.setDelay(this, delay);
        Toast.makeText(this, getString(R.string.sel_toast_fmt, labelFor(delay)),
                Toast.LENGTH_SHORT).show();
    }

    private void startSelected() {
        if (!AppState.hasWriteAccess(this)) {
            requestWriteAccess();
            return;
        }
        AppState.setRepeat(this, repeatSwitch.isChecked());
        TickerWidgetProvider.startCountdown(this, AppState.getDelaySeconds(this));
    }

    /** No admin anywhere: brief idle timeout trick (Android minimum ~5 s). */
    private void offNow() {
        if (!AppState.hasWriteAccess(this)) {
            requestWriteAccess();
            return;
        }
        if (!AppState.isArmed(this)) {
            if (!AppState.arm(this, AppState.DELAY_1MIN)) {
                return;
            }
        }
        AppState.writeSystemTimeout(this, 5000L);
        AppState.refresh(this);
        Toast.makeText(this, R.string.off_now_toast, Toast.LENGTH_SHORT).show();
        Intent svc = new Intent(this, TimerService.class);
        startForegroundService(svc);
    }

    private void requestWriteAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, R.string.write_settings_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void applyLang(String code) {
        ScreenOffApp.persistLang(this, code);
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager lm = getSystemService(android.app.LocaleManager.class);
            if (lm != null) {
                if (ScreenOffApp.LANG_DEFAULT.equals(code)) {
                    lm.setApplicationLocales(android.os.LocaleList.getEmptyLocaleList());
                } else {
                    lm.setApplicationLocales(
                            new android.os.LocaleList(new java.util.Locale(code)));
                }
            }
        }
        AppState.refresh(this);
        Toast.makeText(this, R.string.lang_toast, Toast.LENGTH_SHORT).show();
        recreate();
    }

    private String labelFor(long delay) {
        if (delay == AppState.DELAY_1MIN) {
            return getString(R.string.app_sel_1min);
        }
        if (delay == AppState.DELAY_2MIN) {
            return getString(R.string.app_sel_2min);
        }
        return getString(R.string.sel_custom_secs_fmt, delay);
    }

    /**
     * Custom tab: an input dialog; the accepted value becomes the stored
     * selection and the tab label shows it.
     */
    private void showCustomDialog(final boolean fromWidget) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.custom_hint);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        long current = AppState.getDelaySeconds(this);
        if (current != AppState.DELAY_1MIN && current != AppState.DELAY_2MIN) {
            input.setText(String.valueOf(current));
            input.setSelection(input.getText().length());
        }
        final MainActivity self = this;
        new AlertDialog.Builder(this)
                .setTitle(R.string.custom_dialog_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    long value;
                    try {
                        value = Long.parseLong(input.getText().toString().trim());
                    } catch (Exception e) {
                        value = -1;
                    }
                    if (value < AppState.MIN_DELAY || value > AppState.MAX_DELAY) {
                        Toast.makeText(self, R.string.custom_range_toast, Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppState.setDelay(self, value);
                    Toast.makeText(self, getString(R.string.custom_applied_toast, value),
                            Toast.LENGTH_SHORT).show();
                    self.render();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
        input.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250L);
    }

    private void render() {
        boolean canWrite = AppState.hasWriteAccess(this);
        grantWrite.setVisibility(canWrite ? View.GONE : View.VISIBLE);

        renderLangChips();

        repeatSwitch.setOnCheckedChangeListener(null);
        repeatSwitch.setChecked(AppState.isRepeat(this));
        repeatSwitch.setOnCheckedChangeListener(repeatListener);

        // Tab highlight: green accent for the selected delay.
        int accent = getColor(R.color.accent);
        int dim = getColor(R.color.text_dim);
        sel1min.setTextColor(getColor(R.color.text_bright));
        sel2min.setTextColor(getColor(R.color.text_bright));
        sel1min.setTextColor(AppState.getDelaySeconds(this) == AppState.DELAY_1MIN ? accent : dim);
        sel2min.setTextColor(AppState.getDelaySeconds(this) == AppState.DELAY_2MIN ? accent : dim);
        long delay = AppState.getDelaySeconds(this);
        if (delay != AppState.DELAY_1MIN && delay != AppState.DELAY_2MIN) {
            selCustom.setText(getString(R.string.app_sel_custom_fmt, labelFor(delay)));
            selCustom.setTextColor(accent);
        } else {
            selCustom.setText(R.string.widget_custom);
            selCustom.setTextColor(dim);
        }

        if (!canWrite) {
            statusText.setText(R.string.needs_write_status);
            statusText.setTextColor(getColor(R.color.danger));
        } else if (AppState.isArmed(this)) {
            String s = getString(AppState.isRepeat(this)
                    ? R.string.widget_counting_repeat
                    : R.string.widget_counting, delay);
            statusText.setText(s);
            statusText.setTextColor(getColor(R.color.accent));
        } else {
            statusText.setText(getString(R.string.app_status_idle, labelFor(delay)));
            statusText.setTextColor(getColor(R.color.text_dim));
        }
    }

    private void renderLangChips() {
        String active = ScreenOffApp.activeLang(this);
        int accent = getColor(R.color.accent);
        int dim = getColor(R.color.text_dim);
        ((Button) findViewById(R.id.btn_lang_sys)).setTextColor(
                ScreenOffApp.LANG_DEFAULT.equals(active) ? accent : dim);
        ((Button) findViewById(R.id.btn_lang_en)).setTextColor(
                ScreenOffApp.LANG_EN.equals(active) ? accent : dim);
        ((Button) findViewById(R.id.btn_lang_hi)).setTextColor(
                ScreenOffApp.LANG_HI.equals(active) ? accent : dim);
        ((Button) findViewById(R.id.btn_lang_ta)).setTextColor(
                ScreenOffApp.LANG_TA.equals(active) ? accent : dim);
    }

    private final CompoundButton.OnCheckedChangeListener repeatListener =
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    AppState.setRepeat(MainActivity.this, isChecked);
                    render();
                }
            };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
