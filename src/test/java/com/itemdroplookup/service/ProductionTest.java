package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProductionTest
{
    private final Gson gson = new Gson();

    @Test public void exactOutputOwnsTheRecipeAndItsIngredients()
    {
        ItemSourceLookupService sources = new ItemSourceLookupService(gson);
        assertEquals(1, sources.searchByItemName("Bronze bar").stream().filter(s -> s.getSourceType().equals("Production")).count());
        for (String input : List.of("Copper ore", "Tin ore"))
            assertFalse(sources.searchByItemName(input).stream().anyMatch(s -> s.getSourceType().equals("Production")));
        List<AcquisitionMethod> methods = ProductionRecipeAdapter.readBundled(gson);
        assertEquals(1, methods.size());
        AcquisitionMethod method = methods.get(0);
        assertEquals("production:bronze-bar:normal-furnace", method.getId());
        assertEquals("", method.getLocationText());
        assertTrue(method.getSearchText().contains("Smithing 1"));
        assertTrue(method.getSearchText().contains("1 × Copper ore"));
        assertTrue(method.getSearchText().contains("1 × Tin ore"));
    }

    @Test public void malformedRecipeCannotSilentlyCreateAProductionSource() throws Exception
    {
        try(InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/production_recipes.json"), StandardCharsets.UTF_8))
        {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            root.getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonArray("inputs").remove(1);
            try {
                ProductionRecipeAdapter.project(root);
                fail("Missing consumed input");
            } catch(IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("inputs"));
            }
        }
    }
}
