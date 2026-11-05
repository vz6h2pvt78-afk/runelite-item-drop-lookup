package com.itemdroplookup.service;

import com.google.gson.JsonObject;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Five own-page shard qualifiers linked to existing, same-revision Iorwerth records. */
enum ReviewedShardLocation
{
    DARK_BEAST("Dark beast", "5f9a03c81abe69928796", 15350345, 182, ""),
    GREATER_NECHRYAEL("Greater Nechryael", "51b7c39b8d085595856b", 15341801, 200, "Regular"),
    KURASK("Kurask", "7d37e4898b37137e6b86", 15386817, 106, ""),
    MUTATED_BLOODVELD("Mutated Bloodveld", "5d02c785be1ea0f0d404", 15338397, 123, ""),
    WATERFIEND("Waterfiend", "6c6ac675635c5903fbfe", 15385712, 115, "");

    private final String monster, id, dropVersion;
    private final int revision, level;

    ReviewedShardLocation(String monster, String id, int revision, int level, String dropVersion)
    {
        this.monster = monster;
        this.id = id;
        this.revision = revision;
        this.level = level;
        this.dropVersion = dropVersion;
    }

    static ReviewedShardLocation forMonster(String name)
    {
        for (ReviewedShardLocation reviewed : values())
            if (reviewed.monster.equals(name)) return reviewed;
        throw new IllegalArgumentException("Unreviewed item-location association");
    }

    MonsterLocation read(JsonObject row, Map<String, MonsterLocation> byId, Set<String> duplicateIds,
                         DropLookupService drops)
    {
        fields(row, "monsterName,itemName,quantity,dropRate,notes,locationId,locationName,dropVersion,revisionId,sourceUrl", "");
        String url = "https://oldschool.runescape.wiki/w/" + monster.replace(' ', '_');
        integer(row, "revisionId", revision, revision);
        DropSource source = new DropSource("Crystal shard", monster, "3-5", "1/24", "");
        if (!source.getItemName().equals(string(row, "itemName")) || !source.getQuantity().equals(string(row, "quantity"))
                || !source.getDropRate().equals(string(row, "dropRate")) || !source.getNotes().equals(text(row, "notes"))
                || !id.equals(string(row, "locationId")) || !"Iorwerth Dungeon".equals(string(row, "locationName"))
                || !dropVersion.equals(text(row, "dropVersion")) || !(url + "?oldid=" + revision).equals(string(row, "sourceUrl"))
                || !drops.hasUniqueRecordedDrop(source))
            throw new IllegalArgumentException("Changed reviewed shard signature or evidence");
        MonsterLocation place = byId.get(id);
        List<String> expectedVersions = dropVersion.isEmpty() ? Collections.emptyList() : Collections.singletonList(dropVersion);
        if (place == null || duplicateIds.contains(id) || !monster.equals(place.getMonsterName())
                || !monster.equals(place.getSourcePage()) || !monster.equals(place.getNpcName())
                || revision != place.getRevisionId() || !"Iorwerth Dungeon".equals(place.getLocation())
                || !Boolean.TRUE.equals(place.getMembers()) || !place.isAvailable()
                || place.getWilderness() != MonsterLocation.Wilderness.NO
                || !"https://oldschool.runescape.wiki/w/Moss_Giant_(Iorwerth_Dungeon)".equals(place.getWildernessEvidence())
                || !Collections.singletonList(level).equals(place.getLevels()) || !String.valueOf(level).equals(place.getLevelsText())
                || !expectedVersions.equals(place.getDropVersions()) || !"".equals(place.getVersion())
                || !url.equals(place.getSourceUrl()))
            throw new IllegalArgumentException("Changed reviewed Iorwerth location");
        return place;
    }
}
