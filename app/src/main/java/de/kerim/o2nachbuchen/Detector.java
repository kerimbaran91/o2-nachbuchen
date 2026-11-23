package de.kerim.o2nachbuchen;

import java.util.Locale;

/** Entscheidet, ob eine SMS von O2 ein Nachbuchen auslösen soll. */
public final class Detector {

    public static final String NUMBER = "80112";

    public enum Result { BOOK, CONFIRM, FAIL, OTHER }

    private Detector() {}

    /** Absender ist die O2-Kurzwahl 80112 (auch mit Vorwahl o. ä.). */
    public static boolean isO2(String address) {
        if (address == null) return false;
        String digits = address.replaceAll("[^0-9]", "");
        return digits.endsWith(NUMBER);
    }

    public static Result classify(String body) {
        if (body == null) return Result.OTHER;
        String t = body.toLowerCase(Locale.GERMAN);

        // Fehlermeldungen von O2 -> nicht erneut senden (sonst Endlosschleife)
        if (has(t, "konnte nicht", "nicht durchgeführt", "nicht möglich", "fehlgeschlagen",
                "nicht erfolgreich", "fehler", "existiert nicht", "unbekannt")) {
            return Result.FAIL;
        }

        boolean volumeWord = has(t, "datenvolumen", "highspeed", "high-speed", "volumen", " gb", "gb ");
        boolean usedUp = has(t, "verbraucht", "aufgebraucht", "80 %", "80%", "80 prozent",
                "100 %", "100%", "gedrosselt", "drosselung", "reduziert");
        boolean replyHint = t.contains("weiter");
        boolean confirm = has(t, "erfolgreich", "wurde gebucht", "wurden gebucht", "ist gebucht",
                "hinzugebucht", "aufgebucht", "freigeschaltet");

        // 80 %- oder 100 %-Hinweis -> nachbuchen
        if (usedUp && (volumeWord || replyHint)) return Result.BOOK;

        // Bestätigung einer Buchung -> nur protokollieren
        if (confirm) return Result.CONFIRM;

        // Aufforderung "antworte mit WEITER" ohne Prozentangabe -> nachbuchen
        if (replyHint && volumeWord) return Result.BOOK;

        return Result.OTHER;
    }

    private static boolean has(String t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }
}
