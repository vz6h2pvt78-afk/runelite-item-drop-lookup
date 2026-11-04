package com.itemdroplookup.ui;

import com.itemdroplookup.model.DropSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DropQuantity
{
    private static final Pattern RANGE = Pattern.compile("(\\d+)-(\\d+)( \\(noted\\))?");
    private DropQuantity() { }

    static String display(DropSource source)
    {
        String raw = source.getQuantity();
        if (raw == null || raw.isEmpty()) return "Not recorded";
        // These two reviewed Duke rows contain alternatives in the same Wiki table cell.
        if ("Duke Sucellus".equals(source.getMonsterName()) && "63,95 (noted)".equals(raw)
                && ("Bronze bar".equals(source.getItemName()) || "Iron ore".equals(source.getItemName())))
            return "63 or 95 (noted)";
        Matcher range = RANGE.matcher(raw);
        return range.matches() ? range.replaceFirst("$1–$2$3") : raw;
    }
}
