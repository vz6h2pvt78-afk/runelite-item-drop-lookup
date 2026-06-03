package com.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class DropLookupService
{
    private static final String DROPS_RESOURCE = "/wiki_monster_drops.json";

    private final Gson gson = new Gson();
    private List<DropRecord> dropRecords;
    private Map<String, List<DropRecord>> recordsByItemName;

    public List<DropSource> searchByItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String query = normalize(itemName);
        String resolvedQuery = resolveAlias(query);

        Set<DropRecord> matches = new LinkedHashSet<>();

        // 1. Exact item-name match.
        matches.addAll(getRecordsByItemName().getOrDefault(resolvedQuery, Collections.emptyList()));

        // 2. Partial and compact matching.
        // This catches:
        // dragonboots -> dragon boots
        // gracefulhood -> graceful hood
        // runepouch -> rune pouch
        if (matches.isEmpty())
        {
            String compactQuery = compactNormalize(resolvedQuery);

            for (Map.Entry<String, List<DropRecord>> entry : getRecordsByItemName().entrySet())
            {
                String item = entry.getKey();
                String compactItem = compactNormalize(item);

                if (item.contains(resolvedQuery)
                        || compactItem.equals(compactQuery)
                        || allWordsMatch(item, resolvedQuery))
                {
                    matches.addAll(entry.getValue());
                }
            }
        }

        // 3. Strict typo fallback.
        // This runs only if exact/partial/compact matching found nothing.
        // It avoids broad false matches like runepouch -> rune javelin.
        if (matches.isEmpty() && resolvedQuery.length() >= 4)
        {
            for (Map.Entry<String, List<DropRecord>> entry : getRecordsByItemName().entrySet())
            {
                String item = entry.getKey();

                if (roughlyMatches(item, resolvedQuery))
                {
                    matches.addAll(entry.getValue());
                }
            }
        }

        return matches.stream()
                .map(record -> new DropSource(
                        record.itemName,
                        record.monsterName,
                        record.quantity,
                        record.dropRate,
                        record.notes
                ))
                .sorted(Comparator.comparing(DropSource::getMonsterName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private String resolveAlias(String query)
    {
        Map<String, String> aliases = new HashMap<>();

        // Keep this list for narrow/common slang only.
        // Avoid broad aliases like "whip" because they can mean multiple items.
        aliases.put("dboots", "dragon boots");
        aliases.put("d boots", "dragon boots");
        aliases.put("dragon boot", "dragon boots");

        aliases.put("tassets", "bandos tassets");
        aliases.put("tassies", "bandos tassets");
        aliases.put("bandos tass", "bandos tassets");
        aliases.put("bcp", "bandos chestplate");

        aliases.put("dchain", "dragon chainbody");
        aliases.put("d chain", "dragon chainbody");

        aliases.put("d med", "dragon med helm");
        aliases.put("dmed", "dragon med helm");

        aliases.put("rune scim", "rune scimitar");
        aliases.put("r scim", "rune scimitar");

        return aliases.getOrDefault(query, query);
    }

    private boolean allWordsMatch(String itemName, String query)
    {
        String[] words = query.split("\\s+");

        for (String word : words)
        {
            if (!itemName.contains(word))
            {
                return false;
            }
        }

        return true;
    }

    private boolean roughlyMatches(String itemName, String query)
    {
        if (itemName.contains(query))
        {
            return true;
        }

        String[] queryWords = query.split("\\s+");
        String[] itemWords = itemName.split("\\s+");

        int meaningfulQueryWords = 0;
        int matchedWords = 0;

        for (String queryWord : queryWords)
        {
            if (queryWord.length() < 3)
            {
                continue;
            }

            meaningfulQueryWords++;

            for (String itemWord : itemWords)
            {
                if (itemWord.length() < 3)
                {
                    continue;
                }

                if (itemWord.startsWith(queryWord)
                        || isCloseTypo(itemWord, queryWord))
                {
                    matchedWords++;
                    break;
                }
            }
        }

        if (meaningfulQueryWords == 0)
        {
            return false;
        }

        return matchedWords == meaningfulQueryWords;
    }

    private boolean isCloseTypo(String itemWord, String queryWord)
    {
        if (Math.abs(itemWord.length() - queryWord.length()) > 2)
        {
            return false;
        }

        return levenshteinDistance(itemWord, queryWord) <= 1;
    }

    private int levenshteinDistance(String first, String second)
    {
        int[][] dp = new int[first.length() + 1][second.length() + 1];

        for (int i = 0; i <= first.length(); i++)
        {
            dp[i][0] = i;
        }

        for (int j = 0; j <= second.length(); j++)
        {
            dp[0][j] = j;
        }

        for (int i = 1; i <= first.length(); i++)
        {
            for (int j = 1; j <= second.length(); j++)
            {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[first.length()][second.length()];
    }

    private String normalize(String value)
    {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", " ")
                .replace("_", " ")
                .replaceAll("\\s+", " ");
    }

    private String compactNormalize(String value)
    {
        return normalize(value)
                .replaceAll("[^a-z0-9]", "");
    }

    private Map<String, List<DropRecord>> getRecordsByItemName()
    {
        if (recordsByItemName != null)
        {
            return recordsByItemName;
        }

        recordsByItemName = new HashMap<>();

        for (DropRecord record : getDropRecords())
        {
            if (record.itemName == null)
            {
                continue;
            }

            String itemName = normalize(record.itemName);
            recordsByItemName.computeIfAbsent(itemName, key -> new ArrayList<>()).add(record);
        }

        return recordsByItemName;
    }

    private List<DropRecord> getDropRecords()
    {
        if (dropRecords != null)
        {
            return dropRecords;
        }

        try (InputStream inputStream = DropLookupService.class.getResourceAsStream(DROPS_RESOURCE))
        {
            if (inputStream == null)
            {
                dropRecords = Collections.emptyList();
                return dropRecords;
            }

            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<DropRecord>>() {}.getType();

            dropRecords = gson.fromJson(reader, listType);

            if (dropRecords == null)
            {
                dropRecords = Collections.emptyList();
            }

            return dropRecords;
        }
        catch (Exception ex)
        {
            dropRecords = Collections.emptyList();
            return dropRecords;
        }
    }

    private static class DropRecord
    {
        private String itemName;
        private String monsterName;
        private String quantity;
        private String dropRate;
        private String notes;
    }
}