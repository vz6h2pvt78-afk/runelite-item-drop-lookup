package com.itemdroplookup.service;

import java.util.Locale;
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
