package de.kerim.o2nachbuchen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // Dark-Mode-Farben
    static final int BG = 0xFF0F1115;
    static final int CARD = 0xFF1A1D23;
    static final int LINE = 0xFF2A2E36;
    static final int TEXT = 0xFFFFFFFF;
    static final int SUB = 0xFF9AA0A6;
    static final int BLUE = 0xFF0066FF;
    static final int GREEN = 0xFF34C759;
    static final int ORANGE = 0xFFFF9F0A;
    static final int RED = 0xFFFF453A;

    private static final int REQ_SMS = 1;
    private static final int REQ_NOTIF = 2;

    private LinearLayout root;
    private TextView statusTitle, statusSub, statMonth, statLast;
    private Switch autoSwitch;
    private LinearLayout setupBox, logBox;
    private boolean smsAskedOnce = false;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sp, key) -> runOnUiThread(this::refresh);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Booker.channels(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.p(this).registerOnSharedPreferenceChangeListener(prefListener);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.p(this).unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    // ---------------------------------------------------------------- UI aufbauen

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(24));
        sv.addView(root);
        setContentView(sv);

        // Kopfzeile
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        head.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(12), 0, 0, 0);
        ht.addView(text("O2 Nachbuchen", 22, TEXT, true));
        ht.addView(text("Version " + version() + " · bucht bei 80 % automatisch 1 GB nach", 13, SUB, false));
        head.addView(ht);
        root.addView(head);

        // Status
        LinearLayout status = card();
        statusTitle = text("", 20, TEXT, true);
        statusSub = text("", 14, SUB, false);
        status.addView(statusTitle);
        status.addView(statusSub);
        LinearLayout swRow = new LinearLayout(this);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(12), 0, 0);
        TextView swLabel = text("Automatisch nachbuchen", 16, TEXT, false);
        swRow.addView(swLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        autoSwitch = new Switch(this);
        autoSwitch.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{BLUE, 0xFFB0B4BA}));
        autoSwitch.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}}, new int[]{0x880066FF, 0xFF3A3F47}));
        autoSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on != Store.isEnabled(this)) {
                Store.setEnabled(this, on);
                Store.log(this, on ? "OK" : "WARN", on ? "Automatik eingeschaltet" : "Automatik ausgeschaltet");
            }
        });
        swRow.addView(autoSwitch);
        status.addView(swRow);
        root.addView(status);

        // Einrichtung
        LinearLayout setup = card();
        setup.addView(text("Einrichtung", 16, TEXT, true));
        setupBox = new LinearLayout(this);
        setupBox.setOrientation(LinearLayout.VERTICAL);
        setup.addView(setupBox);
        root.addView(setup);

        // Statistik
        LinearLayout stats = card();
        stats.addView(text("Übersicht", 16, TEXT, true));
        statMonth = text("", 14, SUB, false);
        statLast = text("", 14, SUB, false);
        statMonth.setPadding(0, dp(8), 0, 0);
        stats.addView(statMonth);
        stats.addView(statLast);
        root.addView(stats);

        // Handbuchung
        Button manual = button("Jetzt 1 GB nachbuchen", BLUE, TEXT);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        mlp.topMargin = dp(14);
        manual.setOnClickListener(v -> askManual());
        root.addView(manual, mlp);

        // Protokoll
        LinearLayout log = card();
        LinearLayout lh = new LinearLayout(this);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        lh.addView(text("Protokoll", 16, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView clear = text("Löschen", 14, BLUE, true);
        clear.setPadding(dp(12), dp(6), 0, dp(6));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Protokoll wirklich löschen?")
                .setPositiveButton("Löschen", (d, w) -> Store.clearLog(this))
                .setNegativeButton("Abbrechen", null).show());
        lh.addView(clear);
        log.addView(lh);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        log.addView(logBox);
        root.addView(log);
    }

    // ---------------------------------------------------------------- Aktualisieren

    private void refresh() {
        if (root == null) return;
        boolean sms = hasSms();
        boolean notif = hasNotif();
        boolean battery = batteryFree();
        boolean noPause = notPaused();
        boolean on = Store.isEnabled(this);

        autoSwitch.setChecked(on);

        if (!sms) {
            statusTitle.setText("● Einrichtung nötig");
            statusTitle.setTextColor(ORANGE);
            statusSub.setText("Ohne SMS-Berechtigung kann die App nicht nachbuchen.");
        } else if (!on) {
            statusTitle.setText("● Pausiert");
            statusTitle.setTextColor(SUB);
            statusSub.setText("Automatik ist ausgeschaltet – es wird nichts nachgebucht.");
        } else if (!battery) {
            statusTitle.setText("● Aktiv (mit Einschränkung)");
            statusTitle.setTextColor(ORANGE);
            statusSub.setText("Bitte Akku auf „Nicht eingeschränkt“ stellen, damit Samsung die App nicht schlafen legt.");
        } else if (!noPause) {
            statusTitle.setText("● Aktiv (mit Einschränkung)");
            statusTitle.setTextColor(ORANGE);
            statusSub.setText("Bitte „App-Aktivität pausieren“ ausschalten – sonst schaltet Android die App nach einigen Monaten ohne Öffnen ab.");
        } else {
            statusTitle.setText("● Aktiv");
            statusTitle.setTextColor(GREEN);
            statusSub.setText("Sobald O2 die 80-%-SMS schickt, wird automatisch 1 GB nachgebucht. Du musst nichts tun.");
        }

        // Einrichtungs-Zeilen
        setupBox.removeAllViews();
        setupBox.addView(setupRow("SMS empfangen & senden", sms,
                sms ? "Erlaubt" : "Nötig, um die O2-SMS zu erkennen und „WEITER“ zu schicken",
                "Erlauben", v -> askSms()));
        setupBox.addView(setupRow("Benachrichtigungen", notif,
                notif ? "Erlaubt" : "Damit du siehst, wann nachgebucht wurde (optional)",
                "Erlauben", v -> askNotif()));
        setupBox.addView(setupRow("Akku: Nicht eingeschränkt", battery,
                battery ? "Eingestellt" : "Sonst kann Samsung die App im Hintergrund stoppen",
                "Einstellen", v -> askBattery()));
        setupBox.addView(setupRow("Nie automatisch pausieren", noPause,
                noPause ? "Eingestellt" : "Sonst entzieht Android nach Monaten ohne Öffnen die Berechtigungen",
                "Einstellen", v -> askNoPause()));

        // Statistik
        statMonth.setText("Diesen Monat nachgebucht: " + Store.sentThisMonth(this) + " × 1 GB");
        long last = Store.lastOk(this);
        statLast.setText("Letzte Buchung: " + (last > 0 ? fmt(last) : "noch keine"));

        // Protokoll
        logBox.removeAllViews();
        List<Store.Entry> list = Store.readLog(this);
        if (list.isEmpty()) {
            TextView t = text("Noch keine Einträge. Hier erscheint jede SMS von O2 (80112) und jede Buchung.", 13, SUB, false);
            t.setPadding(0, dp(8), 0, 0);
            logBox.addView(t);
        }
        int n = 0;
        for (Store.Entry e : list) {
            if (n++ >= 50) break;
            logBox.addView(logRow(e));
        }
    }

    // ---------------------------------------------------------------- Aktionen

    private void askManual() {
        if (!hasSms()) { askSms(); return; }
        new AlertDialog.Builder(this)
                .setTitle("1 GB nachbuchen?")
                .setMessage("Es wird eine kostenlose SMS „WEITER“ an 80112 geschickt.")
                .setPositiveButton("Nachbuchen", (d, w) -> {
                    String err = Booker.book(this, -1, false, "Knopf in der App");
                    Toast.makeText(this, err == null ? "SMS wird gesendet …" : err, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void askSms() {
        boolean canAsk = !smsAskedOnce
                || shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)
                || shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS);
        if (canAsk) {
            smsAskedOnce = true;
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS}, REQ_SMS);
        } else {
            showRestrictedHelp();
        }
    }

    private void showRestrictedHelp() {
        new AlertDialog.Builder(this)
                .setTitle("SMS-Berechtigung freischalten")
                .setMessage("Android sperrt SMS-Rechte bei Apps, die nicht aus dem Play Store kommen.\n\n"
                        + "So geht's:\n"
                        + "1. Auf „App-Info öffnen“ tippen\n"
                        + "2. Oben rechts auf ⋮ (drei Punkte) tippen\n"
                        + "3. „Eingeschränkte Einstellungen zulassen“ wählen\n"
                        + "4. Dann unter „Berechtigungen“ → „SMS“ → „Zulassen“\n\n"
                        + "Danach zurück in diese App.")
                .setPositiveButton("App-Info öffnen", (d, w) -> openAppInfo())
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void askNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            openAppInfo();
        }
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            openAppInfo();
        }
    }

    private void askNoPause() {
        new AlertDialog.Builder(this)
                .setTitle("Nie automatisch pausieren")
                .setMessage("Gleich öffnet sich die App-Info.\n\n"
                        + "Dort den Schalter „App-Aktivität pausieren, wenn nicht verwendet“ "
                        + "(oder „Berechtigungen entfernen, wenn App nicht verwendet wird“) AUSschalten.\n\n"
                        + "Danach zurück in diese App.")
                .setPositiveButton("Öffnen", (d, w) -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        openAppInfo();
                    }
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void openAppInfo() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_SMS) {
            if (hasSms()) {
                Store.log(this, "OK", "SMS-Berechtigung erteilt – App ist bereit");
                if (!batteryFree()) askBattery();
                else if (!hasNotif()) askNotif();
                else if (!notPaused()) askNoPause();
            } else {
                showRestrictedHelp();
            }
        }
        refresh();
    }

    // ---------------------------------------------------------------- Prüfungen

    private boolean hasSms() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotif() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    /** true = Android pausiert die App nie (Berechtigungen bleiben dauerhaft). */
    private boolean notPaused() {
        if (Build.VERSION.SDK_INT < 30) return true;
        try {
            return getPackageManager().isAutoRevokeWhitelisted();
        } catch (Exception e) {
            return true;
        }
    }

    private boolean batteryFree() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    // ---------------------------------------------------------------- Bausteine

    private View setupRow(String title, boolean ok, String sub, String action, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        TextView icon = text(ok ? "✓" : "!", 16, ok ? GREEN : ORANGE, true);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 15, TEXT, false));
        tx.addView(text(sub, 12, SUB, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!ok) {
            Button bt = button(action, LINE, TEXT);
            bt.setTextSize(13);
            bt.setOnClickListener(l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            lp.leftMargin = dp(8);
            row.addView(bt, lp);
        }
        return row;
    }

    private View logRow(Store.Entry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        int color;
        switch (e.type) {
            case "OK": color = GREEN; break;
            case "WARN": color = ORANGE; break;
            case "ERR": color = RED; break;
            default: color = SUB;
        }
        TextView time = text(fmt(e.time), 12, color, true);
        TextView msg = text(e.text, 14, TEXT, false);
        msg.setTextIsSelectable(true);
        row.addView(time);
        row.addView(msg);
        View sep = new View(this);
        sep.setBackgroundColor(LINE);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        wrap.addView(row);
        return wrap;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        c.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String fmt(long t) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(new Date(t));
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
