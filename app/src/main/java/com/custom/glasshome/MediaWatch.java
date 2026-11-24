package com.custom.glasshome;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Onthoudt per app wat er als laatste werd afgespeeld (titel, plaatje, positie),
 * zodat Glass Home een echte "Verder kijken"-kaart kan tonen voor elke app,
 * ook voor apps die je later installeert.
 */
public class MediaWatch extends NotificationListenerService {
    static final String PREFS = "gh_media";
    private static MediaWatch inst;
    private MediaSessionManager msm;
    private final Map<String, MediaController.Callback> cbs = new HashMap<>();
    private final Map<String, MediaController> ctrls = new HashMap<>();
    private MediaSessionManager.OnActiveSessionsChangedListener lst;

    static MediaController controllerFor(String pkg) {
        return inst == null ? null : inst.ctrls.get(pkg);
    }

    /** De app die nu muziek/video afspeelt (of als laatste gepauzeerd is). */
    static MediaController active() {
        if (inst == null) return null;
        MediaController best = null;
        long bestT = -1;
        try {
            for (MediaController c : inst.msm.getActiveSessions(new ComponentName(inst, MediaWatch.class))) {
                if (c.getPackageName().equals(inst.getPackageName())) continue;
                PlaybackState st = c.getPlaybackState();
                if (st == null || c.getMetadata() == null) continue;
                int s2 = st.getState();
                if (s2 == PlaybackState.STATE_PLAYING) return c;
                if (s2 == PlaybackState.STATE_PAUSED || s2 == PlaybackState.STATE_BUFFERING) {
                    long t = st.getLastPositionUpdateTime();
                    if (t > bestT) { bestT = t; best = c; }
                }
            }
        } catch (Throwable ignored) { }
        return best;
    }

    @Override
    public void onListenerConnected() {
        inst = this;
        try {
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName me = new ComponentName(this, MediaWatch.class);
            lst = this::track;
            msm.addOnActiveSessionsChangedListener(lst, me);
            track(msm.getActiveSessions(me));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        try {
            if (msm != null && lst != null) msm.removeOnActiveSessionsChangedListener(lst);
        } catch (Throwable ignored) {
        }
        inst = null;
    }

    private void track(List<MediaController> list) {
        if (list == null) return;
        for (MediaController c : list) {
            final String pkg = c.getPackageName();
            if (pkg == null || pkg.equals(getPackageName())) continue;
            MediaController old = ctrls.get(pkg);
            if (old != null && cbs.containsKey(pkg)) {
                try { old.unregisterCallback(cbs.get(pkg)); } catch (Throwable ignored) { }
            }
            ctrls.put(pkg, c);
            MediaController.Callback cb = new MediaController.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadata m) { save(c); }

                @Override
                public void onPlaybackStateChanged(PlaybackState s) { save(c); }
            };
            cbs.put(pkg, cb);
            try { c.registerCallback(cb); } catch (Throwable ignored) { }
            save(c);
        }
    }

    private void save(MediaController c) {
        try {
            PlaybackState st = c.getPlaybackState();
            MediaMetadata m = c.getMetadata();
            if (m == null) return;
            String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null || title.trim().isEmpty()) title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null || title.trim().isEmpty()) return;
            boolean playing = st != null && (st.getState() == PlaybackState.STATE_PLAYING || st.getState() == PlaybackState.STATE_PAUSED);
            if (!playing) return;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONObject o = new JSONObject();
            o.put("pkg", c.getPackageName());
            o.put("t", title);
            String sub = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            if (sub == null) sub = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (sub == null) sub = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
            if (sub != null) o.put("s", sub);
            long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (dur > 0) o.put("dur", dur);
            if (st != null && st.getPosition() > 0) o.put("pos", st.getPosition());
            String mid = m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            if (mid != null) o.put("id", mid);
            String uri = m.getString(MediaMetadata.METADATA_KEY_MEDIA_URI);
            if (uri != null) o.put("uri", uri);
            String art = m.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if (art == null) art = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
            if (art != null && art.startsWith("http")) o.put("img", art);
            else {
                Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b != null) {
                    int w = 480, h = Math.max(1, Math.round(b.getHeight() * 480f / Math.max(1, b.getWidth())));
                    Bitmap s = Bitmap.createScaledBitmap(b, w, h, true);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    s.compress(Bitmap.CompressFormat.JPEG, 78, bos);
                    o.put("img", "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP));
                }
            }
            o.put("ts", System.currentTimeMillis());
            sp.edit().putString(c.getPackageName(), o.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    static String all(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                try { o.put(e.getKey(), new JSONObject(String.valueOf(e.getValue()))); } catch (Throwable ignored) { }
            }
            return o.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }
}
