package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;
import com.google.gson.reflect.TypeToken;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.AnimatorReward;
import com.itemdroplookup.model.ConditionalDropRate;

import javax.inject.Inject;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class DropLookupService
{
    private static final String DROPS_RESOURCE = "/wiki_monster_drops.json";

    private final Gson gson;
    private List<DropRecord> dropRecords;
    private Map<String, List<DropRecord>> recordsByItemName;
    private Set<String> reviewedEchoSources = Collections.emptySet();
    private Set<String> reviewedSourceItems = Collections.emptySet();

    @Inject
    public DropLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public List<DropSource> searchByItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String resolvedQuery = ItemNameMatching.resolveAlias(itemName);
        Set<DropRecord> matches = new LinkedHashSet<>(ItemNameMatching.bestMatches(
                resolvedQuery, getDropRecords(), record -> record.itemName));

        // 3. Strict typo fallback.
        // This runs only if exact/partial/compact matching found nothing.
        // It avoids broad false matches like runepouch -> rune javelin.
        if (matches.isEmpty() && resolvedQuery.length() >= 4
                && !ItemNameMatching.hasExplicitQualifier(resolvedQuery))
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
                .filter(record -> AcquisitionSourceRules.isOrdinaryDrop(record.itemName, record.monsterName)
                        && !reviewedEchoSources.contains(record.monsterName))
                .map(record -> new DropSource(
                        record.itemName,
                        record.monsterName,
                        record.quantity,
                        record.dropRate,
                        record.notes,
                        record.membersOnly, record.animatorReward, record.conditionalRate, record.reviewedDropTable
                ))
                .sorted(Comparator.comparing(DropSource::getMonsterName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /** Retain a reviewed source-only identity even when its available sources are excluded. */
    public Optional<String> findReviewedSourceItemName(String query)
    {
        if (query == null || query.trim().isEmpty()) return Optional.empty();
        List<DropRecord> records = getDropRecords();
        for (String name : reviewedSourceItems)
            if (name.equalsIgnoreCase(query.trim())) return Optional.of(name);
        List<String> matches = ItemNameMatching.bestMatches(query, reviewedSourceItems, name -> name);
        if (matches.size() != 1 || ItemNameMatching.rank(query, matches.get(0)) > 1) return Optional.empty();
        String candidate = matches.get(0);
        List<DropRecord> recordedMatches = ItemNameMatching.bestMatches(query, records, record -> record.itemName);
        if (recordedMatches.isEmpty() || recordedMatches.stream().anyMatch(record -> !candidate.equalsIgnoreCase(record.itemName)))
            return Optional.empty();
        return Optional.of(candidate);
    }

    boolean hasUniqueRecordedDrop(DropSource key)
    {
        return getDropRecords().stream().filter(r -> key.getMonsterName().equals(r.monsterName)
                && key.getItemName().equals(r.itemName) && key.getQuantity().equals(r.quantity)
                && key.getDropRate().equals(r.dropRate) && key.getNotes().equals(r.notes)).limit(2).count() == 1;
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

    private synchronized Map<String, List<DropRecord>> getRecordsByItemName()
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

            String itemName = ItemNameMatching.normalize(record.itemName);
            recordsByItemName.computeIfAbsent(itemName, key -> new ArrayList<>()).add(record);
        }

        return recordsByItemName;
    }

    private synchronized List<DropRecord> getDropRecords()
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
            if (dropRecords != null)
            {
                Map<String, DropRecord> additions = ReviewedRows.read(gson, "/dragon_warhammer_source.json",
                        "sources", "itemName", this::readDragonWarhammer);
                for (DropRecord addition : additions.values())
                {
                    boolean recorded = dropRecords.stream().anyMatch(existing -> addition.itemName.equals(existing.itemName)
                            && addition.monsterName.equals(existing.monsterName));
                    if (!recorded) dropRecords.add(addition);
                }
                Map<String, AnimatorReward> reviewed = AnimatorRewards.read(gson);
                Map<String, ConditionalDropRate> rates = DropTaskRates.read(gson);
                Map<String, DropRecord> rateMatches = new HashMap<>();
                for (DropRecord record : dropRecords)
                {
                    ConditionalDropRate rate = rates.get(record.itemName);
                    if (rate != null && rate.matches(record.monsterName, record.itemName, record.quantity, record.dropRate, record.notes))
                    {
                        DropRecord first = rateMatches.putIfAbsent(record.itemName, record);
                        if (first == null) record.conditionalRate = rate;
                        else first.conditionalRate = null;
                    }
                    if (!"Warrior guild token".equals(record.itemName) || !"Always".equals(record.dropRate) || !"".equals(record.notes)) continue;
                    AnimatorReward reward = reviewed.get(record.monsterName);
                    if (reward != null && Integer.toString(reward.getBase()).equals(record.quantity)) record.animatorReward = reward;
                }
                Set<String> exclusions = AcquisitionSourceRules.reviewedEchoSources(gson);
                Set<String> identities = dropRecords.stream()
                        .filter(record -> record.itemName != null && exclusions.contains(record.monsterName))
                        .map(record -> record.itemName).collect(Collectors.toCollection(LinkedHashSet::new));
                reviewedSourceItems = Collections.unmodifiableSet(identities);
                reviewedEchoSources = exclusions;
            }

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

    private DropRecord readDragonWarhammer(JsonObject row, String item)
    {
        fields(row, "itemName,monsterName,quantity,dropRate,notes,membersOnly,dropTable,evidence", "");
        require(item.equals("Dragon warhammer") && string(row, "monsterName").equals("Lizardman shaman")
                && string(row, "quantity").equals("1") && string(row, "dropRate").equals("1/3000")
                && text(row, "notes").isEmpty() && string(row, "dropTable").equals("Pre-roll"),
                "Unreviewed Dragon warhammer source");
        require(row.get("membersOnly").isJsonPrimitive() && row.getAsJsonPrimitive("membersOnly").isBoolean()
                && row.get("membersOnly").getAsBoolean(), "Unreviewed source membership");
        require(array(row, "evidence").size() == 2, "Missing unique-table or parent evidence");
        String[] pages = {"Lizardman shaman", "Template:Uniques/Lizardman shaman"};
        long[] revisions = {15371945, 14669471};
        for (int i = 0; i < pages.length; i++)
        {
            JsonObject proof = object(array(row, "evidence").get(i));
            fields(proof, "page,revisionId,sourceUrl", "");
            require(string(proof, "page").equals(pages[i]) && integer(proof, "revisionId", 1, Integer.MAX_VALUE) == revisions[i]
                    && string(proof, "sourceUrl").equals("https://oldschool.runescape.wiki/w/"
                            + pages[i].replace(' ', '_') + "?oldid=" + revisions[i]), "Changed source primary");
        }
        DropRecord record = gson.fromJson(row, DropRecord.class);
        record.reviewedDropTable = "Pre-roll";
        return record;
    }

    private static class DropRecord
    {
        private String itemName;
        private String monsterName;
        private String quantity;
        private String dropRate;
        private String notes;
        private Boolean membersOnly;
        private transient AnimatorReward animatorReward;
        private transient ConditionalDropRate conditionalRate;
        private transient String reviewedDropTable;
    }
}
