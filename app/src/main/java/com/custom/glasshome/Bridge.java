package com.custom.glasshome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Bridge {
    private static final String LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";
    private final Context ctx;
    // appIcon() is called from the JavaBridge thread and blocks JS; cache the encoded result.
    private final Map<String, String> iconCache = new ConcurrentHashMap<>();

    public Bridge(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    private Intent leanbackIntent(String pkg) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(LEANBACK);
        if (pkg != null) i.setPackage(pkg);
        return i;
    }

    @JavascriptInterface
    public String launchUri() {
        String u = MainActivity.lastUri;
        MainActivity.lastUri = "";
        return u == null ? "" : u;
    }

    @JavascriptInterface
    public String listApps() {
        JSONArray arr = new JSONArray();
        try {
            PackageManager pm = ctx.getPackageManager();
            Map<String, String> found = new LinkedHashMap<>();
            java.util.Set<String> tvApps = new java.util.HashSet<>();
            Intent[] intents = new Intent[]{
                    new Intent(Intent.ACTION_MAIN).addCategory(LEANBACK),
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            };
            for (Intent in : intents) {
                List<ResolveInfo> list = pm.queryIntentActivities(in, 0);
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (in.hasCategory(LEANBACK)) tvApps.add(pkg);
                    if (found.containsKey(pkg)) continue;
                    CharSequence label = ri.loadLabel(pm);
                    found.put(pkg, label != null ? label.toString() : pkg);
                }
            }
            for (Map.Entry<String, String> e : found.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("id", e.getKey());
                o.put("title", e.getValue());
                o.put("tv", tvApps.contains(e.getKey()));
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 26) o.put("cat", pm.getApplicationInfo(e.getKey(), 0).category);
                } catch (Exception ignored2) {
                }
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String appLogo(String pkg) {
        try {
            Drawable d = ctx.getPackageManager().getApplicationIcon(pkg);
            int w = 160, h = 160;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        }
    }

    @JavascriptInterface
    public String appIcon(String pkg) {
        if (pkg == null) return "";
        String hit = iconCache.get(pkg);
        if (hit != null) return hit;
        String res = renderIcon(pkg);
        if (!res.isEmpty()) iconCache.put(pkg, res);
        return res;
    }

    private String renderIcon(String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            Drawable d = null;
            int w = 192, h = 192;
            try {
                d = pm.getActivityBanner(leanbackIntent(pkg));
            } catch (Exception ignored) {
            }
            if (d == null) {
                try {
                    d = pm.getApplicationBanner(pkg);
                } catch (Exception ignored) {
                }
            }
            if (d != null) {
                w = 320;
                h = 180;
            } else {
                d = pm.getApplicationIcon(pkg);
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        }
    }

    @JavascriptInterface
    public boolean launch(String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean openSearch() {
        String[] acts = {"android.search.action.GLOBAL_SEARCH", Intent.ACTION_ASSIST, Intent.ACTION_WEB_SEARCH};
        for (String act : acts) {
            try {
                Intent i = new Intent(act);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return true;
            } catch (Exception e) { }
        }
        return false;
    }

    @JavascriptInterface
    public boolean openUri(String uri, String pkg) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            // ook als de app al open staat: altijd naar de gekozen video/film gaan
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (pkg != null && !pkg.isEmpty()) {
                i.setPackage(pkg);
                try {
                    ctx.startActivity(i);
                    return true;
                } catch (ActivityNotFoundException e) {
                    i.setPackage(null);
                }
            }
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String lastPlayed() {
        return MediaWatch.all(ctx);
    }

    @JavascriptInterface
    public boolean mediaAccess() {
        try {
            String l = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
            return l != null && l.contains(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean resumeApp(final String pkg) {
        boolean ok = launch(pkg);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.media.session.MediaController c = MediaWatch.controllerFor(pkg);
                if (c != null) c.getTransportControls().play();
            } catch (Throwable ignored) {
            }
        }, 2500);
        return ok;
    }

    @JavascriptInterface
    public void openSettings(String which) {
        String w = which == null ? "" : which;
        String action;
        switch (w) {
            case "sound": action = Settings.ACTION_SOUND_SETTINGS; break;
            case "bluetooth": action = Settings.ACTION_BLUETOOTH_SETTINGS; break;
            case "display": action = Settings.ACTION_DISPLAY_SETTINGS; break;
            case "inputs": action = "com.android.tv.action.VIEW_INPUTS"; break;
            default: action = Settings.ACTION_SETTINGS;
        }
        if (!start(action)) {
            start(Settings.ACTION_SETTINGS);
        }
    }

    private boolean start(String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean isHomeOverrideEnabled() {
        try {
            Intent h = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = ctx.getPackageManager().resolveActivity(h, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && ctx.getPackageName().equals(ri.activityInfo.packageName)) return true;
        } catch (Exception ignored) {
        }
        try {
            String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            ComponentName cn = new ComponentName(ctx, HomeOverrideService.class);
            String full = cn.flattenToString();
            String shortName = cn.flattenToShortString();
            for (String s : enabled.split(":")) {
                if (s.equalsIgnoreCase(full) || s.equalsIgnoreCase(shortName)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        start(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    @JavascriptInterface
    public void openAppInfo(String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            start(Settings.ACTION_SETTINGS);
        }
    }

    @JavascriptInterface
    public void setHomeOverride(boolean on) {
        SharedPreferences sp = ctx.getSharedPreferences(HomeOverrideService.PREFS, Context.MODE_PRIVATE);
        sp.edit().putBoolean(HomeOverrideService.KEY_OVERRIDE, on).apply();
    }

    // ---------- volume / audio ----------

    private AudioManager audio() {
        return (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    @JavascriptInterface
    public String getVolume() {
        JSONObject o = new JSONObject();
        try {
            AudioManager am = audio();
            o.put("cur", am.getStreamVolume(AudioManager.STREAM_MUSIC));
            o.put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            o.put("muted", am.isStreamMute(AudioManager.STREAM_MUSIC));
            o.put("fixed", am.isVolumeFixed());
        } catch (Throwable t) {
            try {
                o.put("cur", 0).put("max", 0).put("muted", false).put("fixed", true);
            } catch (Throwable ignored) {
            }
        }
        return o.toString();
    }

    @JavascriptInterface
    public String setVolume(int level) {
        try {
            AudioManager am = audio();
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, level)),
                    AudioManager.FLAG_SHOW_UI);
        } catch (Throwable ignored) {
        }
        return getVolume();
    }

    @JavascriptInterface
    public String adjustVolume(int dir) {
        try {
            int d = dir > 0 ? AudioManager.ADJUST_RAISE : dir < 0 ? AudioManager.ADJUST_LOWER : AudioManager.ADJUST_SAME;
            // Also works with fixed volume: Android forwards it to the TV/AVR over HDMI-CEC.
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI);
        } catch (Throwable ignored) {
        }
        return getVolume();
    }

    @JavascriptInterface
    public String toggleMute() {
        try {
            audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI);
        } catch (Throwable ignored) {
        }
        return getVolume();
    }

    private static String outputLabel(int type) {
        switch (type) {
            case 9:  // TYPE_HDMI
                return "HDMI";
            case 10: // TYPE_HDMI_ARC
            case 29: // TYPE_HDMI_EARC (API 31; literal keeps API 28 safe)
                return "HDMI ARC/eARC";
            case 7:  // TYPE_BLUETOOTH_SCO
            case 8:  // TYPE_BLUETOOTH_A2DP
            case 26: // TYPE_BLE_HEADSET (API 31)
            case 27: // TYPE_BLE_SPEAKER (API 31)
            case 30: // TYPE_BLE_BROADCAST (API 33)
                return "Bluetooth";
            case 11: // TYPE_USB_DEVICE
            case 12: // TYPE_USB_ACCESSORY
            case 22: // TYPE_USB_HEADSET
                return "USB";
            case 1:  // TYPE_BUILTIN_EARPIECE
            case 2:  // TYPE_BUILTIN_SPEAKER
                return "Ingebouwd";
            default:
                return "Overig";
        }
    }

    @JavascriptInterface
    public String audioOutputs() {
        JSONArray arr = new JSONArray();
        try {
            AudioDeviceInfo[] devs = audio().getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo d : devs) {
                JSONObject o = new JSONObject();
                CharSequence n = d.getProductName();
                o.put("name", n != null ? n.toString() : "");
                o.put("type", outputLabel(d.getType()));
                arr.put(o);
            }
        } catch (Throwable ignored) {
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String bluetoothDevices() {
        JSONArray arr = new JSONArray();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return "[]";
            }
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
            if (ad == null) return "[]";
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            if (bonded == null) return "[]";
            for (BluetoothDevice d : bonded) {
                JSONObject o = new JSONObject();
                String n = d.getName();
                o.put("name", n != null ? n : d.getAddress());
                o.put("address", d.getAddress());
                o.put("connected", false);
                arr.put(o);
            }
        } catch (Throwable t) {
            return "[]";
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String networkInfo() {
        String type = "none";
        String ssid = "";
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities nc = n != null ? cm.getNetworkCapabilities(n) : null;
            if (nc != null) {
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    type = "ethernet";
                } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    type = "wifi";
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        TransportInfo ti = nc.getTransportInfo();
                        if (ti instanceof WifiInfo) {
                            String s = ((WifiInfo) ti).getSSID();
                            if (s != null && !s.contains("<unknown ssid>")) {
                                if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                                    s = s.substring(1, s.length() - 1);
                                }
                                ssid = s;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return new JSONObject().put("type", type).put("ssid", ssid).toString();
        } catch (Throwable t) {
            return "{\"type\":\"none\",\"ssid\":\"\"}";
        }
    }

    // ---------- launching ----------

    private boolean tryStart(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean launchActivity(String pkg, String cls) {
        try {
            if (pkg == null || pkg.isEmpty() || cls == null || cls.isEmpty()) return false;
            String c = cls.startsWith(".") ? pkg + cls : cls;
            return tryStart(new Intent().setComponent(new ComponentName(pkg, c)));
        } catch (Throwable t) {
            return false;
        }
    }

    @JavascriptInterface
    public void openHomePanel() {
        try {
            if (tryStart(new Intent("com.google.android.apps.tv.launcherx.action.HOME_CONTROL"))) return;
            Intent v = new Intent(Intent.ACTION_VIEW, Uri.parse("googletv://home-control"));
            v.setPackage("com.google.android.apps.tv.launcherx");
            if (tryStart(v)) return;
            PackageManager pm = ctx.getPackageManager();
            String home = "com.google.android.apps.chromecast.app";
            Intent l = pm.getLeanbackLaunchIntentForPackage(home);
            if (l == null) l = pm.getLaunchIntentForPackage(home);
            if (l != null && tryStart(l)) return;
        } catch (Throwable ignored) {
        }
        tryStart(new Intent(Settings.ACTION_SETTINGS));
    }

    @JavascriptInterface
    public String platform() {
        return "androidtv";
    }
}
