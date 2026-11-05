package com.itemdroplookup.service;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemNameMatchingTest
{
    @Test public void explicitQualifiersCannotChangePoisonDoseChargeOrContainer()
    {
        for (String[] pair : new String[][]{
                {"Dragon dagger(p)", "Dragon dagger(p++)"}, {"Dragon dagger p+", "Dragon dagger(p++)"},
                {"Prayer potion(5)", "Prayer potion(4)"}, {"Super restore9", "Super restore(4)"},
                {"Amulet of glory(5)", "Amulet of glory(4)"}, {"Antidote+", "Antidote++(4)"},
                {"Rune pouch note", "Rune pouch"}, {"Air rune pack", "Air rune"},
                {"Rune platebody(noted)", "Rune platebody"}})
        {
            assertTrue(pair[0], ItemNameMatching.hasExplicitQualifier(pair[0]));
            assertEquals(pair[0], Integer.MAX_VALUE, ItemNameMatching.rank(pair[0],pair[1]));
        }
        assertEquals(1, ItemNameMatching.rank("dragon dagger p++", "Dragon dagger(p++)"));
        assertEquals(1, ItemNameMatching.rank("prayerpotion4", "Prayer potion(4)"));
        assertEquals(0, ItemNameMatching.rank("Zulrah’s scales", "Zulrah's scales"));
    }

    @Test public void catalogIdentityAndPricesUseTheSameConservativeCollisionRule() throws Exception
    {
        ItemPriceLookupService service = new ItemPriceLookupService(new com.google.gson.Gson());
        java.lang.reflect.Field records = ItemPriceLookupService.class.getDeclaredField("itemPriceRecords");
        records.setAccessible(true);
        records.set(service, new com.google.gson.Gson().fromJson(
                "[{\"itemName\":\"A bc\"},{\"itemName\":\"Ab c\"},{\"itemName\":\"Rune dagger(p+)\"},{\"itemName\":\"Rune dagger(p++)\"}]",
                records.getGenericType()));
        assertFalse(service.findExactItemName("abc").isPresent());
        assertFalse(service.searchByItemName("abc").isPresent());
        assertEquals("Ab c",service.findExactItemName("Ab c").get());
        assertEquals("Ab c",service.searchByItemName("Ab c").get().getItemName());
        assertEquals("Rune dagger(p+)",service.findExactItemName("rune dagger p+").get());
        assertFalse(service.findExactItemName("rune dagger p+++").isPresent());
        assertFalse(service.findExactItemName("rune dagger").isPresent());
    }

    @Test public void compactCollisionsKeepAllCanonicalChoicesUntilExactNameIsGiven()
    {
        java.util.List<String> items=Arrays.asList("A bc", "Ab c", "Abc extra");
        assertEquals(Arrays.asList("A bc","Ab c"), ItemNameMatching.bestMatches("abc",items,s->s));
        assertEquals(Collections.singletonList("Ab c"), ItemNameMatching.bestMatches("Ab c",items,s->s));
        assertEquals(Collections.singletonList("Dragon dagger(p+)"), ItemNameMatching.bestMatches(
                "Dragon dagger p+",Arrays.asList("Dragon dagger(p)","Dragon dagger(p+)","Dragon dagger(p++)"),s->s));
    }
}
