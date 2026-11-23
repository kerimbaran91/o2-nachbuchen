package de.kerim.o2nachbuchen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;

/** Verschickt die SMS "WEITER" an 80112 – mit Schutz gegen Doppel-Buchungen. */
public final class Booker {

    public static final String TEXT = "WEITER";

    /** Mindestabstand zwischen zwei automatischen Buchungen. */
    static final long AUTO_COOLDOWN_MS = 3 * 60 * 1000L;
    /** Mindestabstand bei Handbuchung (gegen Doppeltippen). */
    static final long MANUAL_COOLDOWN_MS = 15 * 1000L;
    /** Sicherheitsgrenze pro Tag (gegen Endlosschleifen). */
    static final int MAX_PER_DAY = 40;

    static final String CH_BOOK = "buchung";
    static final String CH_WARN = "warnung";

    private Booker() {}

    /** @return null bei Erfolg, sonst ein Text, warum nicht gesendet wurde. */
    public static synchronized String book(Context ctx, int subId, boolean auto, String reason) {
        Context c = ctx.getApplicationContext();
        long now = System.currentTimeMillis();

        if (auto) {
            long since = now - Store.lastAuto(c);
            if (since >= 0 && since < AUTO_COOLDOWN_MS) {
                String m = "Nicht erneut gebucht – letzte Buchung vor " + (since / 1000) + " s (Doppel-Schutz)";
                Store.log(c, "INFO", m);
                return m;
            }
        } else {
            long since = now - Store.lastAny(c);
            if (since >= 0 && since < MANUAL_COOLDOWN_MS) {
                return "Bitte kurz warten – gerade wurde schon gebucht.";
            }
        }

        if (Store.attemptsToday(c) >= MAX_PER_DAY) {
            String m = "Tagesgrenze erreicht (" + MAX_PER_DAY + " Buchungen) – heute keine weitere automatische Buchung";
            Store.log(c, "WARN", m);
            if (auto) notify(c, CH_WARN, 2, "Nachbuchen gestoppt", m);
            return m;
        }

        if (subId < 0) subId = Store.lastSubId(c);

        try {
            SmsManager sms = manager(c, subId);
            Intent sent = new Intent(c, SentReceiver.class);
            sent.putExtra("auto", auto);
            PendingIntent pi = PendingIntent.getBroadcast(c, (int) (now & 0xFFFF), sent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Store.markAttempt(c, auto);
            sms.sendTextMessage(Detector.NUMBER, null, TEXT, pi, null);
            Store.log(c, "INFO", (auto ? "Automatisch: " : "Von Hand: ") + "„" + TEXT + "“ an "
                    + Detector.NUMBER + " gesendet" + (reason != null ? " (" + reason + ")" : ""));
            return null;
        } catch (SecurityException e) {
            String m = "Keine SMS-Berechtigung – bitte in der App erlauben";
            Store.log(c, "ERR", m);
            notify(c, CH_WARN, 3, "Nachbuchen fehlgeschlagen", m);
            return m;
        } catch (Exception e) {
            String m = "SMS konnte nicht gesendet werden: " + e.getMessage();
            Store.log(c, "ERR", m);
            notify(c, CH_WARN, 3, "Nachbuchen fehlgeschlagen", m);
            return m;
        }
    }

    @SuppressWarnings("deprecation")
    static SmsManager manager(Context c, int subId) {
        boolean valid = subId >= 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager base = c.getSystemService(SmsManager.class);
            return valid ? base.createForSubscriptionId(subId) : base;
        }
        return valid ? SmsManager.getSmsManagerForSubscriptionId(subId) : SmsManager.getDefault();
    }

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CH_BOOK, "Nachbuchungen",
                NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_WARN, "Probleme",
                NotificationManager.IMPORTANCE_HIGH));
    }

    static void notify(Context c, String channel, int id, String title, String text) {
        try {
            channels(c);
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, channel)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(id, n);
        } catch (Exception ignored) {
            // ohne Benachrichtigungs-Erlaubnis einfach still weiterarbeiten
        }
    }
}
