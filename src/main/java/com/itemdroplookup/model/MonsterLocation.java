package com.itemdroplookup.model;

import java.util.Collections;
import java.util.List;

/** One wiki location row. Missing classifications remain unknown. */
public final class MonsterLocation
{
    public enum Wilderness
    {
        YES, NO, UNKNOWN
    }

    private String id;
    private String monsterName;
    private String sourcePage;
    private long revisionId;
    private String npcName;
    private String location;
    private List<Integer> levels;
    private String levelsText;
    private Boolean members;
    private List<String> leagueRegions;
    private Wilderness wilderness;
    private String wildernessEvidence;
    private Boolean available;
    private Integer spawns;
    private String spawnCountBasis;
    private String notes;
    private String requirements;
    private List<String> dropVersions;
    private String version;
    private String sourceUrl;
    // Set only by the reviewed primary-page encounter reader, never by legacy bindings.
    private transient int encounterWildernessLevel;

    public static MonsterLocation reviewedLair(String monster, String lair, int wildernessLevel, int combatLevel, int revision)
    {
        MonsterLocation place = new MonsterLocation();
        place.id = "encounter:" + monster;
        place.monsterName = monster;
        place.npcName = monster;
        place.sourcePage = monster;
        place.revisionId = revision;
        place.sourceUrl = "https://oldschool.runescape.wiki/w/" + monster + "?oldid=" + revision;
        place.location = lair;
        place.wilderness = Wilderness.YES;
        place.encounterWildernessLevel = wildernessLevel;
        place.wildernessEvidence = "Normal fighting lair in the monster's primary Wiki page";
        place.members = true;
        place.available = true;
        place.levels = Collections.singletonList(combatLevel);
        place.levelsText = String.valueOf(combatLevel);
        return place;
    }

    public int getEncounterWildernessLevel() { return encounterWildernessLevel; }

    public String getId() { return id; }
    public String getMonsterName() { return monsterName; }
    public String getSourcePage() { return sourcePage; }
    public long getRevisionId() { return revisionId; }
    public String getNpcName() { return npcName; }
    public String getLocation() { return location; }
    public List<Integer> getLevels() { return readOnly(levels); }
    public String getLevelsText() { return levelsText; }
    public Boolean getMembers() { return members; }
    public List<String> getLeagueRegions() { return readOnly(leagueRegions); }
    public Wilderness getWilderness() { return wilderness == null ? Wilderness.UNKNOWN : wilderness; }
    public String getWildernessEvidence() { return wildernessEvidence; }
    public boolean isAvailable() { return Boolean.TRUE.equals(available); }
    public Integer getSpawns() { return spawns; }
    public String getSpawnCountBasis() { return spawnCountBasis; }
    public String getNotes() { return notes; }
    public String getRequirements() { return requirements; }
    public List<String> getDropVersions() { return readOnly(dropVersions); }
    public String getVersion() { return version; }
    public String getSourceUrl() { return sourceUrl; }

    private static <T> List<T> readOnly(List<T> values)
    {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
    }
}
