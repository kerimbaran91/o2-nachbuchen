package de.kerim.o2nachbuchen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** Wird vom System bei jeder eingehenden SMS aufgerufen – auch wenn die App geschlossen ist. */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        try {
            SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (parts == null || parts.length == 0) return;

            String from = parts[0].getDisplayOriginatingAddress();
            if (!Detector.isO2(from)) return;   // andere SMS interessieren uns nicht

            StringBuilder sb = new StringBuilder();
            for (SmsMessage m : parts) {
                if (m != null && m.getDisplayMessageBody() != null) sb.append(m.getDisplayMessageBody());
            }
            String body = sb.toString();

            int subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
                    intent.getIntExtra("subscription", -1));
            Store.setLastSubId(context, subId);

            Detector.Result r = Detector.classify(body);
            String shortText = body.length() > 160 ? body.substring(0, 160) + "…" : body;

            switch (r) {
                case BOOK:
                    Store.log(context, "INFO", "O2-SMS erkannt: " + shortText);
                    if (Store.isEnabled(context)) {
                        Booker.book(context, subId, true, "O2-Hinweis");
                    } else {
                        Store.log(context, "WARN", "Automatik ist ausgeschaltet – nicht nachgebucht");
                    }
                    break;
                case CONFIRM:
                    Store.log(context, "OK", "O2 bestätigt: " + shortText);
                    break;
                case FAIL:
                    Store.log(context, "ERR", "O2 meldet Problem: " + shortText);
                    Booker.notify(context, Booker.CH_WARN, 4, "O2 meldet ein Problem beim Nachbuchen", body);
                    break;
                default:
                    Store.log(context, "INFO", "O2-SMS (keine Aktion): " + shortText);
            }
        } catch (Exception e) {
            Store.log(context, "ERR", "Fehler beim Lesen der SMS: " + e.getMessage());
        }
    }
}
