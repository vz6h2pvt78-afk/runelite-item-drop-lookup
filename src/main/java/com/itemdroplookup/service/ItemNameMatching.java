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
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_COMPACT = Pattern.compile("[^a-z0-9+]");
    private static final Pattern POSSESSIVE = Pattern.compile("'s\\b");
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
            Map.entry("amulet of glory (uncharged)", "amulet of glory"),
            Map.entry("ppot", "prayer potion"), Map.entry("range pot", "ranging potion"),
            Map.entry("super restore potion", "super restore"), Map.entry("super energy potion", "super energy"),
            Map.entry("stam", "stamina potion"),
            Map.entry("d pick", "dragon pickaxe"), Map.entry("d pickaxe", "dragon pickaxe"), Map.entry("dpick", "dragon pickaxe"),
            Map.entry("dwh", "dragon warhammer"), Map.entry("tbow", "twisted bow"),
            Map.entry("d scim", "dragon scimitar"), Map.entry("d defender", "dragon defender"),
            Map.entry("d axe", "dragon axe"), Map.entry("d spear", "dragon spear"),
            Map.entry("acb", "armadyl crossbow"), Map.entry("ags", "armadyl godsword"),
            Map.entry("bgs", "bandos godsword"), Map.entry("sgs", "saradomin godsword"),
            Map.entry("zgs", "zamorak godsword"));

    private ItemNameMatching() { }

    public static String normalize(String value)
    {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\u2019', '\'')
                .replace('-', ' ').replace('_', ' ');
        return WHITESPACE.matcher(normalized).replaceAll(" ");
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
        return NON_COMPACT.matcher(normalize(value)).replaceAll("");
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
        return new Qualifiers(normalize(query)).accept(normalize(canonical));
    }

    private static final class Qualifiers
    {
        private final boolean valid;
        private final List<String> parentheses, numbers, pluses, containers;

        Qualifiers(String query)
        {
            String remainder = PARENTHESES.matcher(query).replaceAll("");
            valid = !remainder.contains("(") && !remainder.contains(")");
            List<String> explicit = tokens(PARENTHESES, query);
            Matcher poison = POISON_SUFFIX.matcher(query);
            parentheses = explicit.isEmpty() && poison.find()
                    ? Collections.singletonList("(" + poison.group().trim() + ")") : explicit;
            numbers = tokens(NUMBERS, query);
            pluses = tokens(PLUSES, query);
            containers = tokens(CONTAINERS, query);
        }

        boolean accept(String item)
        {
            return valid && match(parentheses, PARENTHESES, item) && match(numbers, NUMBERS, item)
                    && match(pluses, PLUSES, item) && match(containers, CONTAINERS, item);
        }

        private static boolean match(List<String> required, Pattern pattern, String item)
        {
            return required.isEmpty() || required.equals(tokens(pattern, item));
        }
    }

    private static List<String> tokens(Pattern pattern, String value)
    {
        List<String> result = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) result.add(WHITESPACE.matcher(matcher.group()).replaceAll(""));
        return result;
    }

    /** 0 canonical, 1 compact, 2 prefix, 3 contains, otherwise no match. */
    public static int rank(String query, String canonical)
    {
        return new Query(query).rank(canonical);
    }

    private static final class Query
    {
        private final String normalized, key;
        private final Qualifiers qualifiers;

        Query(String query)
        {
            normalized = normalize(query);
            key = NON_COMPACT.matcher(normalized).replaceAll("");
            qualifiers = new Qualifiers(normalized.trim());
        }

        int rank(String canonical)
        {
            String item = normalize(canonical);
            if (!qualifiers.accept(item.trim())) return Integer.MAX_VALUE;
            if (item.equals(normalized)) return 0;
            if (key.isEmpty()) return Integer.MAX_VALUE;
            String candidate = NON_COMPACT.matcher(item).replaceAll("");
            String possessive = item.contains("'s")
                    ? NON_COMPACT.matcher(POSSESSIVE.matcher(item).replaceAll("")).replaceAll("") : candidate;
            if (candidate.equals(key) || possessive.equals(key)) return 1;
            if (candidate.startsWith(key) || possessive.startsWith(key)) return 2;
            if (candidate.contains(key) || possessive.contains(key)) return 3;
            return Integer.MAX_VALUE;
        }
    }

    /** Retains every canonical identity in a tied tier, including compact collisions. */
    public static <T> List<T> bestMatches(String query, Iterable<T> records, Function<T, String> name)
    {
        List<T> result = new ArrayList<>();
        // Many rows share an item. Calculate its rank once per lookup, without
        // retaining arbitrary player queries or a growing process-wide cache.
        Map<String, Integer> ranks = new HashMap<>();
        Function<String, Integer> ranker = new Query(query)::rank;
        int best = Integer.MAX_VALUE;
        for (T record : records)
        {
            String canonical = name.apply(record);
            if (canonical == null) continue;
            int rank = ranks.computeIfAbsent(canonical, ranker);
            if (rank == Integer.MAX_VALUE) continue;
            if (rank < best) { result.clear(); best = rank; }
            if (rank == best) result.add(record);
        }
        return result;
    }
}
