package com.itemdroplookup.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.api.gameval.ItemID;
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

    @Test public void reviewedQuestRemainsUseExactNativeIdsOnlyWhenTheIndexHasNoExactEntry()
    {
        assertEquals(Integer.valueOf(ItemID.MA2_GUTHIX_HEART),
                ItemIconService.exactId("Ent's roots", Collections.emptyList()));
        assertEquals(Integer.valueOf(ItemID.MA2_ZAMORAK_HEART),
                ItemIconService.exactId("Demon's heart", Collections.emptyList()));
        assertEquals(Integer.valueOf(ItemID.MA2_SARADOMIN_HEART),
                ItemIconService.exactId("Justiciar's hand", Collections.emptyList()));
        assertEquals(Integer.valueOf(ItemID.MA2_GUTHIX_HEART),
                ItemIconService.exactId("ENT'S ROOTS", Collections.emptyList()));
        for (String missing : Arrays.asList("ent", "Ents roots", "Ent's roots (noted)", "Demon's heart (balance)",
                "Demon's heart (darkness)", "Demon's heart (light)", "Justiciar", "Ent's roots extra"))
            assertNull(missing, ItemIconService.exactId(missing, Collections.emptyList()));
    }

    @Test public void reviewedIdsNeverOverrideIndexedIdentityOrAmbiguity()
    {
        assertEquals(Integer.valueOf(100), ItemIconService.exactId("Ent's roots",
                Collections.singletonList(item(100, "Ent's roots"))));
        assertNull(ItemIconService.exactId("Ent's roots",
                Arrays.asList(item(ItemID.MA2_GUTHIX_HEART, "Ent's roots"), item(100, "Ent's roots"))));
        assertEquals(Integer.valueOf(ItemID.MA2_GUTHIX_HEART), ItemIconService.exactId("Ent's roots",
                Collections.singletonList(item(100, "Ent's roots (noted)"))));
        assertFalse(ItemIconService.acceptedDefinition("Ent's roots", "Demon's heart (balance)", -1, -1));
        assertFalse(ItemIconService.acceptedDefinition("Ent's roots", "Ent's roots", 799, -1));
        assertFalse(ItemIconService.acceptedDefinition("Ent's roots", "Ent's roots", -1, 14401));
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
