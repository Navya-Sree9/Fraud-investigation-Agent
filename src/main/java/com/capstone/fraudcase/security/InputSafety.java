package com.capstone.fraudcase.security;

import com.capstone.fraudcase.observability.Redactor;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Intake and question safety checks: rejects markup/scripts and unmasked card/account numbers, normalises text,
 * and flags likely prompt-injection phrases. Flagged text is still treated purely as data, never as instructions.
 */
public final class InputSafety {

    private static final Pattern MARKUP = Pattern.compile("<\\s*/?\\s*[a-zA-Z!?][^>]*>|javascript:|on[a-z]+\\s*=|&#", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final List<Pattern> INJECTION = List.of(
            Pattern.compile("ignore (all |any )?(the )?(previous|prior|above|earlier) (instructions|rules|prompts?)"),
            Pattern.compile("disregard (all |any |the )?(previous|prior|above|system) "),
            Pattern.compile("(you are now|act as|pretend to be) "),
            Pattern.compile("system prompt"),
            Pattern.compile("(issue|process|approve) (a |the )?(refund|credit|chargeback)"),
            Pattern.compile("(block|unblock|cancel|freeze) (the |my )?(card|account)"),
            Pattern.compile("(classify|mark) (this|it|the case) as "),
            Pattern.compile("set humanreviewrequired"),
            Pattern.compile("(transfer|move|send) (the )?(funds|money)"));

    private InputSafety() {
    }

    /** Returns violation messages for free text (empty when acceptable). */
    public static List<String> violations(String field, String text) {
        List<String> errors = new ArrayList<>();
        if (text == null) {
            return errors;
        }
        if (MARKUP.matcher(text).find()) {
            errors.add(field + " must not contain markup or scripts");
        }
        if (Redactor.containsSensitiveNumber(text)) {
            errors.add(field + " appears to contain an unmasked card or account number");
        }
        return errors;
    }

    public static String normalise(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = CONTROL.matcher(Normalizer.normalize(text, Normalizer.Form.NFKC)).replaceAll(" ");
        return cleaned.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    /** Detected prompt-injection indicators (lower-cased matches), used for a visible SECURITY_FLAG evidence item. */
    public static List<String> injectionIndicators(String text) {
        if (text == null) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return INJECTION.stream().map(p -> p.matcher(lower)).filter(java.util.regex.Matcher::find).map(java.util.regex.Matcher::group).toList();
    }
}
