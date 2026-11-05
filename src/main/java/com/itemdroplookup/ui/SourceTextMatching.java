package com.itemdroplookup.ui;

import java.util.Locale;

/** Finder text only. Callers decide which source facts and locations are eligible. */
final class SourceTextMatching
{
    private SourceTextMatching() { }

    static boolean contains(String value, String query)
    {
        if (query.trim().isEmpty()) return true;
        if (value == null) return false;
        String key = finderKey(query);
        return !key.isEmpty() && finderKey(value).contains(key);
    }

    private static String finderKey(String text)
    {
        return text.trim().toLowerCase(Locale.ROOT).replace("\u2019", "'").replace("'", "").replace(" ", "");
    }
}
