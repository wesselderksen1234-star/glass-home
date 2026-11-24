package com.custom.glasshome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

public class HomeOverrideService extends AccessibilityService {
    public static volatile HomeOverrideService inst;
    @Override protected void onServiceConnected() { super.onServiceConnected(); inst = this; }
    @Override public boolean onUnbind(Intent intent) { inst = null; return super.onUnbind(intent); }
    @Override public void onDestroy() { inst = null; super.onDestroy(); }
    public static final String PREFS = "glasshome";
    public static final String KEY_OVERRIDE = "override";
    private static final long DEBOUNCE_MS = 1500;
    private static final String[] STOCK = {
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"
    };
    private static final String[] EXCLUDE = {
            "settings", "dashboard", "panel", "notification", "account", "profile"
    };
    private long lastLaunch = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_OVERRIDE, true)) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        boolean stock = false;
        for (String s : STOCK) {
            if (s.contentEquals(pkg)) { stock = true; break; }
        }
        if (!stock) return;
        String c = cls.toString().toLowerCase(Locale.ROOT);
        for (String x : EXCLUDE) {
            if (c.contains(x)) return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLaunch < DEBOUNCE_MS) return;
        lastLaunch = now;
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }
}
