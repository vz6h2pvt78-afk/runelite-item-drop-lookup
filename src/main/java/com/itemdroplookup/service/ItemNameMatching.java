package com.itemdroplookup.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared search convenience rules; canonical names retain their identity. */
public final class ItemNameMatching
{
    private static final Pattern PARENTHESES = Pattern.compile("\\(([^)]*)\\)");
    private static final Pattern NUMBERS = Pattern.compile("[0-9]+");
    private static final Pattern PLUSES = Pattern.compile("\\++");
    private static final Pattern CONTAINERS = Pattern.compile("\\b(?:note|noted|pack)\\b");
    private static final Pattern POISON_SUFFIX = Pattern.compile("(?:^|\\s)p(?:\\+{0,2})$");
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("dboots", "dragon boots"), Map.entry("d boots", "dragon boots"),
            Map.entry("dragon boot", "dragon boots"), Map.entry("tassets", "bandos tassets"),
            Map.entry("tassies", "bandos tassets"), Map.entry("bandos tass", "bandos tassets"),
            Map.entry("bcp", "bandos chestplate"), Map.entry("dchain", "dragon chainbody"),
            Map.entry("d chain", "dragon chainbody"), Map.entry("d med", "dragon med helm"),
            Map.entry("dmed", "dragon med helm"), Map.entry("rune scim", "rune scimitar"),
            Map.entry("r scim", "rune scimitar"),
            Map.entry("amulet of glory (uncharged)", "amulet of glory"));

    private ItemNameMatching() { }

    public static String normalize(String value)
    {
        return value.trim().toLowerCase(Locale.ROOT).replace('\u2019', '\'')
                .replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
    }

    public static String resolveAlias(String value)
    {
        String query = normalize(value);
        return ALIASES.getOrDefault(query, query);
    }

    public static Map<String, String> reviewedAliases() { return ALIASES; }

    public static String compact(String value)
    {
        // Plus signs distinguish poison and potion variants.
        return normalize(value).replaceAll("[^a-z0-9+]", "");
    }

    public static boolean hasExplicitQualifier(String query)
    {
        String value = normalize(query);
        return value.contains("(") || value.contains(")") || value.contains("+")
                || NUMBERS.matcher(value).find() || CONTAINERS.matcher(value).find()
                || POISON_SUFFIX.matcher(value).find();
    }

    public static boolean compatible(String query, String canonical)
    {
        String q = normalize(query), item = normalize(canonical);
        String remainder = PARENTHESES.matcher(q).replaceAll("");
        if (remainder.contains("(") || remainder.contains(")")) return false;
        List<String> qualifiers = tokens(PARENTHESES, q);
        Matcher poison = POISON_SUFFIX.matcher(q);
        if (qualifiers.isEmpty() && poison.find()) qualifiers = Collections.singletonList("(" + poison.group().trim() + ")");
        return (qualifiers.isEmpty() || qualifiers.equals(tokens(PARENTHESES, item)))
                && compatibleTokens(NUMBERS, q, item)
                && compatibleTokens(PLUSES, q, item)
                && compatibleTokens(CONTAINERS, q, item);
    }

    private static boolean compatibleTokens(Pattern pattern, String query, String item)
    {
        List<String> explicit = tokens(pattern, query);
        return explicit.isEmpty() || explicit.equals(tokens(pattern, item));
    }

    private static List<String> tokens(Pattern pattern, String value)
    {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) result.add(matcher.group().replaceAll("\\s+", ""));
        return result;
    }

    /** 0 canonical, 1 compact, 2 prefix, 3 contains, otherwise no match. */
    public static int rank(String query, String canonical)
    {
        String q = normalize(query), item = normalize(canonical);
        if (!compatible(q, item)) return Integer.MAX_VALUE;
        if (item.equals(q)) return 0;
        String key = compact(q), candidate = compact(item);
        String possessive = compact(item.replaceAll("'s\\b", ""));
        if (key.isEmpty()) return Integer.MAX_VALUE;
        if (candidate.equals(key) || possessive.equals(key)) return 1;
        if (candidate.startsWith(key) || possessive.startsWith(key)) return 2;
        if (candidate.contains(key) || possessive.contains(key)) return 3;
        return Integer.MAX_VALUE;
    }

    /** Retains every canonical identity in a tied tier, including compact collisions. */
    public static <T> List<T> bestMatches(String query, Iterable<T> records, Function<T, String> name)
    {
        List<T> result = new ArrayList<>();
        // Many rows share an item. Calculate its rank once per lookup, without
        // retaining arbitrary player queries or a growing process-wide cache.
        Map<String, Integer> ranks = new HashMap<>();
        int best = Integer.MAX_VALUE;
        for (T record : records)
        {
            String canonical = name.apply(record);
            if (canonical == null) continue;
            int rank = ranks.computeIfAbsent(canonical, item -> rank(query, item));
            if (rank == Integer.MAX_VALUE) continue;
            if (rank < best) { result.clear(); best = rank; }
            if (rank == best) result.add(record);
        }
        return result;
    }
}
