package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;

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

    static String sourceRateLine(DropSource source)
    {
        String reviewed = reviewedRateLine(source);
        if (reviewed != null) return reviewed;
        if (isDreamCake(source)) return "Rate: Always inside the Nightmare Zone dream";
        if (hasUnresolvedAlternate(source)) return "Recorded rate: " + format(source.getDropRate()) + " (alternate listed)";
        return unresolvedEvent(source.getMonsterName()) ? "Listed rate: " + rawRate(source)
                : "Rate: " + format(source.getDropRate());
    }

    static String detailRateLine(DropSource source)
    {
        return sourceRateLine(source);
    }

    static boolean isDreamCake(DropSource source)
    {
        return "Kamil (Nightmare Zone)".equals(source.getMonsterName()) && "Chocolate cake".equals(source.getItemName())
                && "1".equals(source.getQuantity()) && "Always".equals(source.getDropRate()) && "".equals(source.getNotes());
    }

    static final String DREAM_CAKE_NOTE = "Keeping the cake after leaving the dream is unverified.";

    static boolean hasUnresolvedAlternate(DropSource source)
    {
        if (!"1".equals(source.getQuantity()) || !"".equals(source.getNotes())) return false;
        return ("Bull shark".equals(source.getMonsterName()) && "Bull shark jaw".equals(source.getItemName())
                    && "1/3".equals(source.getDropRate()))
                || ("Hammerhead shark".equals(source.getMonsterName()) && "Hammerhead shark jaw".equals(source.getItemName())
                    && "1/2".equals(source.getDropRate()));
    }

    private static String rawRate(DropSource source)
    {
        return source.getDropRate() == null || source.getDropRate().trim().isEmpty() ? "Not recorded" : source.getDropRate();
    }

    private static String reviewedRateLine(DropSource source)
    {
        // Exact reviewed source only. Do not infer multi-roll semantics from a
        // monster family, arbitrary notes, or similarly named gem/bolt items.
        if ("Zulrah".equals(source.getMonsterName()) && "Dragonstone".equals(source.getItemName())
                && "1".equals(source.getQuantity()) && "5/7968".equals(source.getDropRate())
                && "Rare drop table. Rate and quantity are per roll; two rolls per kill.".equals(source.getNotes()))
            return "Rate: " + format(source.getDropRate()) + " per roll; 2 rolls per kill";
        return null;
    }
}
