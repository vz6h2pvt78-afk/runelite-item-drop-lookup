package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.service.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BrowseSelectionTest
{
    private final Gson gson = new Gson();
    private final DropLookupService drops = new DropLookupService(gson);
    private final ItemSourceLookupService sources = new ItemSourceLookupService(gson);
    private final ItemPriceLookupService prices = new ItemPriceLookupService(gson);

    private ItemSearchSelection search(String query)
    {
        return new ItemSearchSelection(query, drops, sources, prices);
    }

    @Test public void registeredTermsAreBeginnerChoicesAndPreciseNamesStayDirect()
    {
        for (String query : List.of("log", "logs", "potion", "potions", "fish", "uncut", "uncut gem", "uncut gems", " LOGS ", "Uncut   Gems"))
            assertTrue(query, search(query).isBrowse());
        assertEquals(search("log").choices(), search("logs").choices());
        assertTrue(search("logs").choices().containsAll(List.of("Logs", "Magic logs")));
        assertFalse(search("logs").choices().contains("Log basket"));
        assertTrue(search("fish").choices().containsAll(List.of("Shark", "Raw shark", "Lobster")));
        assertFalse(search("fish").choices().contains("Lobster pot"));
        assertFalse(search("potions").choices().contains("Prayer enhance (+)"));
        assertFalse(search("potions").choices().contains("Absorption"));
        for (String query : List.of("Magic logs", "Prayer potion", "Raw shark", "Uncut ruby",
                "Prayer enhance (+)", "Prayer potion(3)", "ppot", "dwh", "Staff", "Air rune pack"))
        {
            assertFalse(query, search(query).isBrowse());
            assertEquals(query, 1, search(query).choices().size());
        }
        for (String query : List.of("logs(9)", "potion(9)", "fish(9)", "uncut(9)", "potion(", "fish pack"))
            assertFalse(query, search(query).isBrowse());
    }

    @Test public void everyBrowseMemberKeepsItsExactSourcesAndStableObjects()
    {
        for (String query : List.of("logs", "potions", "fish", "uncut"))
        {
            ItemSearchSelection browse = search(query);
            for (String item : browse.choices())
            {
                ItemSearchSelection direct = search(item);
                assertEquals(query + "/" + item, gson.toJson(direct.dropsFor(item)), gson.toJson(browse.dropsFor(item)));
                assertEquals(query + "/" + item, gson.toJson(direct.sourcesFor(item)), gson.toJson(browse.sourcesFor(item)));
                if (!browse.dropsFor(item).isEmpty()) assertSame(browse.dropsFor(item).get(0), browse.dropsFor(item).get(0));
                if (!browse.sourcesFor(item).isEmpty()) assertSame(browse.sourcesFor(item).get(0), browse.sourcesFor(item).get(0));
            }
        }
        // Ordinary Logs must remain its exact identity despite the deliberate browse collision.
        ItemSearchSelection logs = search("logs");
        assertFalse(logs.sourcesFor("Logs").isEmpty());
        assertTrue(logs.sourcesFor("Logs").stream().allMatch(s -> s.getItemName().equals("Logs")));
        assertTrue(logs.sourcesFor("Logs").stream().anyMatch(s -> s.getSourceName().equals("Chop ordinary trees")));
        assertTrue(logs.sourcesFor("Logs").stream().anyMatch(s -> s.getSourceName().equals("Ent trunk")));
        assertEquals(1, search("uncut").sourcesFor("Uncut zenyte").size());
        assertTrue(search("uncut").dropsFor("Uncut zenyte").isEmpty());
    }

    @Test public void qualifiedPotionFamiliesKeepAllExactDosesThroughEitherPath()
    {
        for (String[] scenario : new String[][]{{"prayer", "Prayer enhance (+)"}, {"sai", "Xeric's aid (+)"}})
        {
            String family = scenario[1];
            ItemSearchSelection direct = search(family);
            ItemSearchSelection broad = search(scenario[0]);
            assertEquals(Collections.singletonList(family), direct.choices());
            assertEquals(4, direct.dropsFor(family).size());
            assertTrue(direct.dropsFor(family).stream().allMatch(d -> d.getItemName().equals(family + "(4)")));
            assertEquals(gson.toJson(direct.dropsFor(family)), gson.toJson(broad.dropsFor(family)));
            for (int dose = 1; dose <= 4; dose++)
            {
                String item = family + "(" + dose + ")";
                ItemSearchSelection exact = search(item);
                if (dose == 4) assertEquals(Collections.singletonList(item), exact.choices());
                assertTrue(exact.dropsFor(item).stream().allMatch(d -> d.getItemName().equals(item)));
            }
            assertTrue(search(family + "(5)").choices().isEmpty());
        }
    }
}
