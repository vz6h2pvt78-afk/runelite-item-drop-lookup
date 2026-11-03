package com.itemdroplookup.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display only. The original rate remains the sorting and binding key. */
final class DropRatePresentation
{
    private static final Pattern FRACTION = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*/\\s*([0-9]+(?:\\.[0-9]+)?)");

    static String format(String raw)
    {
        if (raw == null || raw.trim().isEmpty()) return "Not recorded";
        String value = raw.trim();
        Matcher match = FRACTION.matcher(value);
        if (!match.matches()) return value;
        try
        {
            BigDecimal numerator = new BigDecimal(match.group(1));
            BigDecimal denominator = new BigDecimal(match.group(2));
            if (numerator.signum() <= 0 || denominator.signum() <= 0 || numerator.compareTo(denominator) > 0) return value;
            BigDecimal rounded = denominator.divide(numerator, 1, RoundingMode.HALF_UP).stripTrailingZeros();
            if (rounded.compareTo(BigDecimal.ONE) == 0 && numerator.compareTo(denominator) != 0) return value;
            boolean exact = rounded.multiply(numerator).compareTo(denominator) == 0;
            return (exact ? "" : "About ") + "1 in " + rounded.toPlainString();
        }
        catch (ArithmeticException | NumberFormatException ex) { return value; }
    }

    static boolean unresolvedEvent(String monster)
    {
        // These named cases are under the separately reviewed C3 mechanics work.
        return "Ent".equalsIgnoreCase(monster) || "Night beast".equalsIgnoreCase(monster) || "Zulrah".equalsIgnoreCase(monster);
    }
}
