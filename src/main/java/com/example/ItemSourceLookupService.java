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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Singleton
public class ItemSourceLookupService
{
    private static final String SOURCES_RESOURCE = "/item_sources.json";

    private final Gson gson = new Gson();
    private List<ItemSourceRecord> itemSourceRecords;

    public List<ItemSource> searchByItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return Collections.emptyList();
        }

        String query = normalize(itemName);

        return getItemSourceRecords().stream()
                .filter(record -> record.itemName != null)
                .filter(record -> normalize(record.itemName).equals(query))
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

    private List<ItemSourceRecord> getItemSourceRecords()
    {
        if (itemSourceRecords != null)
        {
            return itemSourceRecords;
        }

        try (InputStream inputStream = ItemSourceLookupService.class.getResourceAsStream(SOURCES_RESOURCE))
        {
            if (inputStream == null)
            {
                itemSourceRecords = Collections.emptyList();
                return itemSourceRecords;
            }

            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ItemSourceRecord>>() {}.getType();

            itemSourceRecords = gson.fromJson(reader, listType);

            if (itemSourceRecords == null)
            {
                itemSourceRecords = Collections.emptyList();
            }

            return itemSourceRecords;
        }
        catch (Exception ex)
        {
            itemSourceRecords = Collections.emptyList();
            return itemSourceRecords;
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