package de.kerim.o2nachbuchen;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Einstellungen, Zähler und Protokoll (alles lokal auf dem Handy). */
public final class Store {

    public static final String PREFS = "o2nachbuchen";

    static final String K_ENABLED = "enabled";
    static final String K_LAST_AUTO = "last_auto_ms";
    static final String K_LAST_ANY = "last_any_ms";
    static final String K_LAST_SUB = "last_sub_id";
    static final String K_DAY = "day_key";
    static final String K_DAY_COUNT = "day_count";
    static final String K_MONTH = "month_key";
    static final String K_MONTH_COUNT = "month_count";
    static final String K_LAST_OK = "last_ok_ms";
    static final String K_LOG = "log";

    private static final int MAX_LOG = 100;

    public static final class Entry {
        public final long time;
        public final String type;   // OK, INFO, WARN, ERR
        public final String text;
        Entry(long time, String type, String text) {
            this.time = time; this.type = type; this.text = text;
        }
    }

    private Store() {}

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) { return p(c).getBoolean(K_ENABLED, true); }
    public static void setEnabled(Context c, boolean on) { p(c).edit().putBoolean(K_ENABLED, on).apply(); }

    public static long lastAuto(Context c) { return p(c).getLong(K_LAST_AUTO, 0); }
    public static long lastAny(Context c) { return p(c).getLong(K_LAST_ANY, 0); }
    public static long lastOk(Context c) { return p(c).getLong(K_LAST_OK, 0); }

    public static int lastSubId(Context c) { return p(c).getInt(K_LAST_SUB, -1); }
    public static void setLastSubId(Context c, int sub) {
        if (sub >= 0) p(c).edit().putInt(K_LAST_SUB, sub).apply();
    }

    static String dayKey(long t) { return new SimpleDateFormat("yyyyMMdd", Locale.GERMANY).format(new Date(t)); }
    static String monthKey(long t) { return new SimpleDateFormat("yyyyMM", Locale.GERMANY).format(new Date(t)); }

    /** Anzahl automatischer + manueller Sendeversuche heute (für die Tagesgrenze). */
    public static synchronized int attemptsToday(Context c) {
        SharedPreferences sp = p(c);
        String today = dayKey(System.currentTimeMillis());
        if (!today.equals(sp.getString(K_DAY, ""))) return 0;
        return sp.getInt(K_DAY_COUNT, 0);
    }

    /** Merkt sich einen Sendeversuch (vor dem Senden aufgerufen). */
    public static synchronized void markAttempt(Context c, boolean auto) {
        SharedPreferences sp = p(c);
        long now = System.currentTimeMillis();
        String today = dayKey(now);
        int day = today.equals(sp.getString(K_DAY, "")) ? sp.getInt(K_DAY_COUNT, 0) : 0;
        SharedPreferences.Editor e = sp.edit()
                .putString(K_DAY, today)
                .putInt(K_DAY_COUNT, day + 1)
                .putLong(K_LAST_ANY, now);
        if (auto) e.putLong(K_LAST_AUTO, now);
        e.commit();
    }

    /** Erfolgreich verschickte WEITER-SMS zählen. */
    public static synchronized void markSent(Context c) {
        SharedPreferences sp = p(c);
        long now = System.currentTimeMillis();
        String m = monthKey(now);
        int month = m.equals(sp.getString(K_MONTH, "")) ? sp.getInt(K_MONTH_COUNT, 0) : 0;
        sp.edit().putString(K_MONTH, m).putInt(K_MONTH_COUNT, month + 1).putLong(K_LAST_OK, now).commit();
    }

    public static synchronized int sentThisMonth(Context c) {
        SharedPreferences sp = p(c);
        if (!monthKey(System.currentTimeMillis()).equals(sp.getString(K_MONTH, ""))) return 0;
        return sp.getInt(K_MONTH_COUNT, 0);
    }

    public static synchronized void log(Context c, String type, String text) {
        try {
            SharedPreferences sp = p(c);
            JSONArray old = new JSONArray(sp.getString(K_LOG, "[]"));
            JSONArray neu = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("y", type);
            o.put("x", text);
            neu.put(o);
            for (int i = 0; i < old.length() && neu.length() < MAX_LOG; i++) neu.put(old.get(i));
            sp.edit().putString(K_LOG, neu.toString()).commit();
        } catch (Exception ignored) {
            // Protokoll darf die eigentliche Funktion nie stören
        }
    }

    public static synchronized List<Entry> readLog(Context c) {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString(K_LOG, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Entry(o.optLong("t"), o.optString("y"), o.optString("x")));
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static synchronized void clearLog(Context c) {
        p(c).edit().putString(K_LOG, "[]").apply();
    }
}
