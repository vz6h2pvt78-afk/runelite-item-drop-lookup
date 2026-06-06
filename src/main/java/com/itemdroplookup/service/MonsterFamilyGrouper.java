package com.itemdroplookup.service;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.DropSourceGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MonsterFamilyGrouper
{
    // Matches a trailing parenthetical at end-of-string.
    // Greedy (.+) ensures the split is on the LAST paren group:
    //   "Skeleton (Barrows)"          -> parent "Skeleton"
    //   "Skeleton Hellhound (Vet'ion)" -> parent "Skeleton Hellhound"
    private static final Pattern TRAILING_PAREN = Pattern.compile("^(.+)\\s+\\(([^)]+)\\)$");

    private MonsterFamilyGrouper() {}

    /**
     * Groups a list of drop sources by monster family using trailing-parenthetical
     * parsing only. Member order within each group reflects the order of {@code sources}.
     * Bare base names ("Skeleton") automatically join the same family as parenthetical
     * variants ("Skeleton (Barrows)") because both produce the same parent string.
     */
    public static List<DropSourceGroup> group(List<DropSource> sources)
    {
        Map<String, List<DropSource>> byParent = new LinkedHashMap<>();

        for (DropSource source : sources)
        {
            String parent = extractParent(source.getMonsterName());
            byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(source);
        }

        List<DropSourceGroup> groups = new ArrayList<>(byParent.size());

        for (Map.Entry<String, List<DropSource>> entry : byParent.entrySet())
        {
            groups.add(new DropSourceGroup(entry.getKey(), entry.getValue()));
        }

        return groups;
    }

    static String extractParent(String monsterName)
    {
        if (monsterName == null)
        {
            return "";
        }

        Matcher m = TRAILING_PAREN.matcher(monsterName);

        if (m.matches())
        {
            return m.group(1);
        }

        return monsterName;
    }
}
