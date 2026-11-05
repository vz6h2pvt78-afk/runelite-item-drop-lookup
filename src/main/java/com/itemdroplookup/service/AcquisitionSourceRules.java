package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;
import java.util.Set;

/** Narrow exclusions for records that cannot provide ordinary-game acquisition. */
final class AcquisitionSourceRules
{
    // Each exact Wiki title was checked on 2026-11-02. All belong to the removed
    // Trailblazer Reloaded League relic family, including cache-only variants.
    // https://oldschool.runescape.wiki/w/Bloodthirsty
    // Keep the raw drop records unchanged; do not exclude by a name prefix.
    private static final Set<String> HISTORICAL_LEAGUE_NPCS = Set.of(
            "bloodthirsty abomination", "bloodthirsty abyssal demon", "bloodthirsty banshee",
            "bloodthirsty basilisk", "bloodthirsty basilisk sentinel", "bloodthirsty bloodveld",
            "bloodthirsty choke devil", "bloodthirsty cockathrice", "bloodthirsty crawler",
            "bloodthirsty drake", "bloodthirsty gargoyle", "bloodthirsty hand", "bloodthirsty hydra",
            "bloodthirsty infernal pyrelord", "bloodthirsty jelly", "bloodthirsty kurask",
            "bloodthirsty mage", "bloodthirsty mutated bloodveld", "bloodthirsty nechryarch",
            "bloodthirsty night beast", "bloodthirsty pyrelord", "bloodthirsty repugnant spectre",
            "bloodthirsty shadow wyrm", "bloodthirsty smoke devil", "bloodthirsty spectre",
            "bloodthirsty turoth", "bloodthirsty twisted banshee", "bloodthirsty warped jelly");
    private static final Map<String, Integer> ECHO_PRIMARY_REVISIONS = Map.ofEntries(
            Map.entry("Amoxliatl (Echo)", 15210490),
            Map.entry("Cerberus (Echo)", 15286453),
            Map.entry("Corrupted Hunllef (Echo)", 15371262),
            Map.entry("Dagannoth Kings (Echo)", 15289940),
            Map.entry("Dagannoth Prime (Echo)", 15289941),
            Map.entry("Dagannoth Rex (Echo)", 15289943),
            Map.entry("Dagannoth Supreme (Echo)", 15289942),
            Map.entry("Dusk (Echo)", 15289933),
            Map.entry("Grotesque Guardians (Echo)", 15356130),
            Map.entry("Hespori (Echo)", 15365104),
            Map.entry("Kalphite Queen (Echo)", 15286450),
            Map.entry("King Black Dragon (Echo)", 15286460),
            Map.entry("Sol Heredit (Echo)", 15349115),
            Map.entry("Thermonuclear smoke devil (Echo)", 15286455));

    static Set<String> reviewedEchoSources(Gson gson)
    {
        return Collections.unmodifiableSet(new HashSet<>(ReviewedRows.read(gson, "/league_echo_exclusions.json",
                "sources", "monsterName", AcquisitionSourceRules::readEchoExclusion).values()));
    }

    private static String readEchoExclusion(JsonObject row, String name)
    {
        fields(row, "monsterName,revisionId,league,sourceUrl", "");
        Integer revision = ECHO_PRIMARY_REVISIONS.get(name);
        require(revision != null, "Unreviewed League encounter");
        integer(row, "revisionId", revision, revision);
        String league = name.equals("Sol Heredit (Echo)") ? "Raging Echoes League" : "Demonic Pacts League";
        require(string(row, "league").equals(league)
                && string(row, "sourceUrl").equals("https://oldschool.runescape.wiki/w/"
                        + name.replace(' ', '_') + "?oldid=" + revision), "Changed League encounter evidence");
        return name;
    }

    private static final Set<String> ANIMATOR_METALS = Set.of("bronze", "iron", "steel", "black", "mithril", "adamant", "rune");

    private AcquisitionSourceRules() { }

    static boolean isOrdinaryDrop(String itemName, String monsterName)
    {
        String monster = monsterName.trim().toLowerCase(Locale.ROOT);
        if (HISTORICAL_LEAGUE_NPCS.contains(monster))
        {
            return false;
        }
        // Quest-encounter supplies are not an ordinary farming recommendation.
        // https://oldschool.runescape.wiki/w/Kamil (verified 2026-11-02)
        if ("kamil".equals(monster))
        {
            return false;
        }
        // The animator returns the player's supplied equipment. Preserve tokens.
        // https://oldschool.runescape.wiki/w/Animated_Adamant_Armour
        if (monster.startsWith("animated ") && monster.endsWith(" armour"))
        {
            String metal = monster.substring("animated ".length(), monster.length() - " armour".length());
            if (ANIMATOR_METALS.contains(metal))
            {
                String item = itemName.trim().toLowerCase(Locale.ROOT);
                return !item.equals(metal + " platebody") && !item.equals(metal + " platelegs")
                        && !item.equals(metal + " full helm");
            }
        }
        return true;
    }
}
