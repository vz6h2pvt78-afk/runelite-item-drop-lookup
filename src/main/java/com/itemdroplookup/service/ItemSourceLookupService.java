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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Singleton
public class ItemSourceLookupService
{
    // Curated, hand-maintained non-NPC sources.
    private static final String CURATED_RESOURCE = "/item_sources.json";
    // Generated shop sources (tools/build_shop_sources.py). Optional: absent until generated.
    private static final String SHOP_RESOURCE = "/wiki_shop_sources.json";

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
        List<ItemSourceRecord> merged = new ArrayList<>();
        merged.addAll(loadRecords(CURATED_RESOURCE));
        merged.addAll(loadRecords(SHOP_RESOURCE));

        itemSourceRecords = merged;
        return itemSourceRecords;
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