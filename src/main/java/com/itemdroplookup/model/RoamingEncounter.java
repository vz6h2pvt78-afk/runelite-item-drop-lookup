package com.itemdroplookup.model;

/** Reviewed context for one exact drop row, independent of legacy location bindings. */
public final class RoamingEncounter
{
    private final DropSource source;
    private final int monsterRevision;

    public RoamingEncounter(DropSource source, int monsterRevision)
    {
        this.source = source;
        this.monsterRevision = monsterRevision;
    }

    public DropSource getSource() { return source; }
    public String getEncounter() { return "Mage Arena II"; }
    public String getArea() { return "Deep Wilderness"; }
    public String getTracking() { return "Enchanted symbol"; }
    public boolean isMembersOnly() { return true; }
    public MonsterLocation.Wilderness getWilderness() { return MonsterLocation.Wilderness.YES; }
    public String getSourceUrl() { return "https://oldschool.runescape.wiki/w/Mage_Arena_II?oldid=15368856"; }
    public String getMonsterSourceUrl()
    {
        return "https://oldschool.runescape.wiki/w/" + source.getMonsterName().replace(' ', '_') + "?oldid=" + monsterRevision;
    }
}
