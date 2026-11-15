package com.capstone.fraudcase.observability;

import java.util.regex.Pattern;

/** Removes anything resembling a card/account number, email or phone number before text is stored or logged. */
public final class Redactor {

    private static final Pattern LONG_DIGITS = Pattern.compile("(?<![A-Za-z0-9-])\\d(?:[ -]?\\d){11,18}(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\+\\d[\\d ()-]{8,}\\d");
    private static final Pattern SECRET = Pattern.compile("(?i)(sk-[A-Za-z0-9_-]{8,}|bearer\\s+[A-Za-z0-9._-]+)");

    private Redactor() {
    }

    public static String redact(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        String out = SECRET.matcher(text).replaceAll("[REDACTED-SECRET]");
        out = LONG_DIGITS.matcher(out).replaceAll("[REDACTED-NUMBER]");
        out = EMAIL.matcher(out).replaceAll("[REDACTED-EMAIL]");
        out = PHONE.matcher(out).replaceAll("[REDACTED-PHONE]");
        return out.length() > maxLength ? out.substring(0, maxLength - 3) + "..." : out;
    }

    public static boolean containsSensitiveNumber(String text) {
        return text != null && LONG_DIGITS.matcher(text).find();
    }
}
