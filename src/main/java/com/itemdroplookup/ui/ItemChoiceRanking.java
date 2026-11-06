package com.itemdroplookup.ui;

import com.itemdroplookup.service.ItemNameMatching;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Presentation only; candidates have already passed identity compatibility. */
final class ItemChoiceRanking
{
    private ItemChoiceRanking() { }

    static Comparator<String> forQuery(String query)
    {
        String normalized = ItemNameMatching.resolveAlias(query);
        String[] words = words(normalized);
        Map<String, Integer> scores = new HashMap<>();
        return Comparator.<String>comparingInt(name ->
                scores.computeIfAbsent(name, key -> score(normalized, words, key)))
                .thenComparing(String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder());
    }

    private static String[] words(String value)
    {
        return ItemNameMatching.normalize(value).split("[^a-z0-9+]+");
    }

    private static int score(String query, String[] wanted, String name)
    {
        String[] candidate = words(name);
        if (wanted.length == 1)
        {
            for (int i = 0; i < candidate.length; i++)
            {
                if (wanted[0].equals(candidate[i])) return i == candidate.length - 1 ? 0 : 1;
            }
        }
        if (orderedWords(wanted, candidate, false)) return 2;
        // A word beginning later in the name is more useful than a substring
        // inside another word. Name-prefix matches follow this same tier.
        if (orderedWords(wanted, candidate, true)) return 3;
        return ItemNameMatching.normalize(name).startsWith(query) ? 4 : 5;
    }

    private static boolean orderedWords(String[] wanted, String[] candidate, boolean prefixes)
    {
        int next = 0;
        for (String word : candidate)
        {
            if (prefixes ? word.startsWith(wanted[next]) : word.equals(wanted[next]))
            {
                if (++next == wanted.length) return true;
            }
        }
        return false;
    }
}
