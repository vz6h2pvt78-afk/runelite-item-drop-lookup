package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itemdroplookup.model.ItemSource;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ItemSourceLookupService
{
    // Curated, hand-maintained non-NPC sources.
    private static final String CURATED_RESOURCE = "/item_sources.json";
    // Generated shop sources (tools/build_shop_sources.py). Optional: absent until generated.
    private static final String SHOP_RESOURCE = "/wiki_shop_sources.json";

    // Generated (wiki_shop_sources.json) records hidden at lookup time because a
    // higher-quality curated record supersedes them, or the generated cost/label is
    // misleading (e.g. a coin price that ignores a reward-point unlock).
    //
    // This is applied ONLY to generated records, never to curated ones, so a curated
    // record that happens to share an (itemName, sourceName) pair is unaffected.
    // Matching is exact on (itemName, sourceName), case-insensitive, and deliberately
    // narrow: it hides only the listed pairs, not every generated record for an item.
    // The generated JSON is left untouched.
    private static final Set<String> SUPPRESSED_GENERATED_SOURCES = buildSuppressedGeneratedSources();

    private static Set<String> buildSuppressedGeneratedSources()
    {
        Set<String> suppressed = new HashSet<>();
        suppressed.add(suppressionKey("Graceful hood", "Grace's Graceful Clothing"));
        suppressed.add(suppressionKey("Rune pouch", "Slayer Rewards"));
        suppressed.add(suppressionKey("Rune pouch", "Justine's stuff for the Last Shopper Standing"));
        return suppressed;
    }

    private static String suppressionKey(String itemName, String sourceName)
    {
        return itemName.trim().toLowerCase(Locale.ROOT)
                + "|"
                + sourceName.trim().toLowerCase(Locale.ROOT);
    }

    private final Gson gson;
    private List<ItemSourceRecord> itemSourceRecords;

    @Inject
    public ItemSourceLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public List<ItemSource> searchByItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String query = normalize(itemName);
        String compactQuery = compactNormalize(itemName);

        return getItemSourceRecords().stream()
                .filter(record -> record.itemName != null)
                .filter(record ->
                {
                    String normalizedItem = normalize(record.itemName);
                    String compactItem = compactNormalize(record.itemName);

                    return normalizedItem.equals(query)
                            || compactItem.equals(compactQuery)
                            || normalizedItem.contains(query);
                })
                .map(record -> new ItemSource(
                        record.itemName,
                        record.sourceType,
                        record.sourceName,
                        record.location,
                        record.cost,
                        record.notes
                ))
                .collect(Collectors.toList());
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

    private List<ItemSourceRecord> getItemSourceRecords()
    {
        if (itemSourceRecords != null)
        {
            return itemSourceRecords;
        }

        // Curated sources first, then generated shop sources (if present). Both feed
        // the same searchable list; a missing generated file is treated as empty.
        // Suppression is applied only to the generated set so curated records — even
        // ones sharing an (itemName, sourceName) pair — are always kept.
        List<ItemSourceRecord> merged = new ArrayList<>();
        merged.addAll(loadRecords(CURATED_RESOURCE));
        merged.addAll(filterSuppressedGenerated(loadRecords(SHOP_RESOURCE)));

        itemSourceRecords = merged;
        return itemSourceRecords;
    }

    private List<ItemSourceRecord> filterSuppressedGenerated(List<ItemSourceRecord> records)
    {
        if (SUPPRESSED_GENERATED_SOURCES.isEmpty())
        {
            return records;
        }

        List<ItemSourceRecord> kept = new ArrayList<>();
        for (ItemSourceRecord record : records)
        {
            if (record.itemName == null || record.sourceName == null
                    || !SUPPRESSED_GENERATED_SOURCES.contains(suppressionKey(record.itemName, record.sourceName)))
            {
                kept.add(record);
            }
        }
        return kept;
    }

    private List<ItemSourceRecord> loadRecords(String resource)
    {
        try (InputStream inputStream = ItemSourceLookupService.class.getResourceAsStream(resource))
        {
            if (inputStream == null)
            {
                return Collections.emptyList();
            }

            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ItemSourceRecord>>() {}.getType();

            List<ItemSourceRecord> records = gson.fromJson(reader, listType);

            return records != null ? records : Collections.emptyList();
        }
        catch (Exception ex)
        {
            return Collections.emptyList();
        }
    }

    private static class ItemSourceRecord
    {
        private String itemName;
        private String sourceType;
        private String sourceName;
        private String location;
        private String cost;
        private String notes;
    }
}