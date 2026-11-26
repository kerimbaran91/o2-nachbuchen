package de.kerim.o2nachbuchen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Führt einen geplanten neuen Sendeversuch aus (nach einem Sendefehler). */
public class RetryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int attempt = intent.getIntExtra("attempt", 1);
        int sub = intent.getIntExtra("sub", -1);
        long since = intent.getLongExtra("since", 0);

        // Wurde inzwischen schon erfolgreich gebucht (z. B. von Hand)? Dann nichts tun.
        if (Store.lastOk(context) >= since && since > 0) {
            Store.log(context, "INFO", "Wiederholung nicht nötig – inzwischen erfolgreich gebucht");
            return;
        }
        if (!Store.isEnabled(context)) {
            Store.log(context, "WARN", "Wiederholung abgebrochen – Automatik ist ausgeschaltet");
            return;
        }
        Booker.book(context, sub, true, "Wiederholung nach Sendefehler", attempt);
    }
}
