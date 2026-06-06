package com.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Singleton
public class ItemPriceLookupService
{
    private static final String PRICES_RESOURCE = "/ge_prices.json";

    private final Gson gson;
    private List<ItemPriceRecord> itemPriceRecords;

    @Inject
    public ItemPriceLookupService(Gson gson)
    {
        this.gson = gson;
    }

    public Optional<ItemPrice> searchByItemName(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return Optional.empty();
        }

        String query = normalize(itemName);
        String compactQuery = compactNormalize(itemName);

        return getItemPriceRecords().stream()
                .filter(record -> record.itemName != null)
                .filter(record ->
                {
                    String normalizedItem = normalize(record.itemName);
                    String compactItem = compactNormalize(record.itemName);

                    return normalizedItem.equals(query)
                            || compactItem.equals(compactQuery);
                })
                .findFirst()
                .map(record -> new ItemPrice(
                        record.itemName,
                        record.price,
                        record.highAlch,
                        record.tradeable,
                        record.notes
                ));
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

    private List<ItemPriceRecord> getItemPriceRecords()
    {
        if (itemPriceRecords != null)
        {
            return itemPriceRecords;
        }

        try (InputStream inputStream = ItemPriceLookupService.class.getResourceAsStream(PRICES_RESOURCE))
        {
            if (inputStream == null)
            {
                itemPriceRecords = Collections.emptyList();
                return itemPriceRecords;
            }

            InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<ItemPriceRecord>>() {}.getType();

            itemPriceRecords = gson.fromJson(reader, listType);

            if (itemPriceRecords == null)
            {
                itemPriceRecords = Collections.emptyList();
            }

            return itemPriceRecords;
        }
        catch (Exception ex)
        {
            itemPriceRecords = Collections.emptyList();
            return itemPriceRecords;
        }
    }

    private static class ItemPriceRecord
    {
        private String itemName;
        private String price;
        private String highAlch;
        private boolean tradeable;
        private String notes;
    }
}