package de.kerim.o2nachbuchen;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Meldet, ob die WEITER-SMS wirklich das Handy verlassen hat – und plant bei Fehler neue Versuche. */
public class SentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int code = getResultCode();
        boolean auto = intent.getBooleanExtra("auto", false);
        int attempt = intent.getIntExtra("attempt", 0);
        int sub = intent.getIntExtra("sub", -1);

        if (code == Activity.RESULT_OK) {
            Store.markSent(context);
            Store.log(context, "OK", "1 GB angefordert – SMS erfolgreich verschickt");
            Booker.notify(context, Booker.CH_BOOK, 1, "1 GB nachgebucht",
                    "„WEITER“ wurde an 80112 geschickt. Diesen Monat: "
                            + Store.sentThisMonth(context) + "× nachgebucht.");
            return;
        }

        String m = "SMS wurde nicht verschickt (Fehlercode " + code + ") – kein Netz?";
        if (auto && Booker.scheduleRetry(context, sub, attempt + 1)) {
            Store.log(context, "WARN", m + " Neuer Versuch in 2 Minuten ("
                    + (attempt + 1) + "/" + Booker.MAX_RETRIES + ")");
        } else {
            Store.log(context, "ERR", m);
            Booker.notify(context, Booker.CH_WARN, 3, "Nachbuchen fehlgeschlagen",
                    m + " Bitte in der App auf „Jetzt 1 GB nachbuchen“ tippen.");
        }
    }
}
