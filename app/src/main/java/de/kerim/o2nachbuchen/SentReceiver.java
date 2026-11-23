package de.kerim.o2nachbuchen;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Meldet, ob die WEITER-SMS wirklich das Handy verlassen hat. */
public class SentReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int code = getResultCode();
        if (code == Activity.RESULT_OK) {
            Store.markSent(context);
            Store.log(context, "OK", "1 GB angefordert – SMS erfolgreich verschickt");
            Booker.notify(context, Booker.CH_BOOK, 1, "1 GB nachgebucht",
                    "„WEITER“ wurde an 80112 geschickt. Diesen Monat: "
                            + Store.sentThisMonth(context) + "× nachgebucht.");
        } else {
            String m = "SMS wurde nicht verschickt (Fehlercode " + code + ") – kein Netz?";
            Store.log(context, "ERR", m);
            Booker.notify(context, Booker.CH_WARN, 3, "Nachbuchen fehlgeschlagen",
                    m + " Bitte in der App auf „Jetzt 1 GB nachbuchen“ tippen.");
        }
    }
}
