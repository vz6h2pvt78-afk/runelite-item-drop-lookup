package com.itemdroplookup.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.http.api.item.ItemPrice;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemIconServiceTest
{
    private static ItemPrice item(int id, String name)
    {
        ItemPrice item = new ItemPrice(); item.setId(id); item.setName(name); return item;
    }

    @Test public void exactNamesNeverBorrowAnotherQualifierOrFuzzyMatch()
    {
        String[] names = {"Dragon dagger", "Dragon dagger(p)", "Dragon dagger(p+)", "Dragon dagger(p++)",
                "Amulet of glory", "Amulet of glory(4)", "Amulet of glory(6)", "Prayer potion(1)",
                "Prayer potion(2)", "Prayer potion(3)", "Prayer potion(4)", "Rune pouch note", "Raw shrimps", "Shrimps"};
        List<ItemPrice> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < names.length; i++) candidates.add(item(100 + i, names[i]));
        for (int i = 0; i < names.length; i++) assertEquals(Integer.valueOf(100 + i), ItemIconService.exactId(names[i], candidates));
        for (String missing : Arrays.asList("Rune pouch", "Amulet of glory(1)", "Prayer potion", "Air rune pack",
                "Dragon dagger p++", "Dragon", "Amulet of glory (uncharged)"))
            assertNull(missing, ItemIconService.exactId(missing, candidates));
        assertEquals(Integer.valueOf(100), ItemIconService.exactId("DRAGON DAGGER", candidates));
    }

    @Test public void MissingOrAmbiguousNamesStayEmptyRegardlessOfOrder()
    {
        assertNull(ItemIconService.exactId("Uncut ruby", Collections.emptyList()));
        List<ItemPrice> ambiguous = Arrays.asList(item(1,"Uncut ruby"), item(2,"Uncut ruby"));
        assertNull(ItemIconService.exactId("Uncut ruby", ambiguous));
        Collections.reverse(ambiguous);
        assertNull(ItemIconService.exactId("Uncut ruby", ambiguous));
        assertEquals(Integer.valueOf(1), ItemIconService.exactId("Uncut ruby",
                Arrays.asList(item(1,"Uncut ruby"), item(1,"Uncut ruby"), item(3,"Uncut ruby extra"))));
    }

    @Test public void ActualDefinitionMustMatchNameAndNormalForm()
    {
        assertTrue(ItemIconService.acceptedDefinition("Rune pouch note", "Rune pouch note", -1, -1));
        assertFalse(ItemIconService.acceptedDefinition("Rune pouch", "Rune pouch note", -1, -1));
        assertFalse(ItemIconService.acceptedDefinition("Uncut ruby", "Uncut ruby", 799, -1));
        assertFalse(ItemIconService.acceptedDefinition("Uncut ruby", "Uncut ruby", -1, 14401));
        assertFalse(ItemIconService.acceptedDefinition("Prayer potion(1)", "Prayer potion(4)", -1, -1));
    }
}
