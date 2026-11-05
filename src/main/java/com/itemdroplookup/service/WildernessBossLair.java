package com.itemdroplookup.service;

import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.MonsterLocation;
import java.util.List;

/** Approved primary revisions for four existing lairs and the named Voidwaker reward on each page. */
enum WildernessBossLair
{
    ARTIO("Artio", "Hunter's End", "661c22fc10edd1c47b2c", 15358758, 21, "Voidwaker hilt", "1/912"),
    CALLISTO("Callisto", "Callisto's Den", "7dc3d2578ee79c0d635b", 15360463, 40, "Voidwaker hilt", "1/360"),
    VETION("Vet'ion", "Vet'ion's Rest", "443dd543a3e6bc6c8d3b", 15360817, 35, "Voidwaker blade", "1/360"),
    VENENATIS("Venenatis", "Silk Chasm", "f867bf8c06f5f237885a", 15361982, 35, "Voidwaker gem", "1/360");

    final String monster, location, id;
    final int revision, level;
    final String item, rate;

    WildernessBossLair(String monster, String location, String id, int revision, int level, String item, String rate)
    {
        this.monster = monster;
        this.location = location;
        this.id = id;
        this.revision = revision;
        this.level = level;
        this.item = item;
        this.rate = rate;
    }

    static WildernessBossLair forMonster(String name)
    {
        for (WildernessBossLair lair : values())
            if (lair.monster.equals(name)) return lair;
        throw new IllegalArgumentException("Unreviewed boss encounter");
    }

    boolean matchesLocation(List<MonsterLocation> places)
    {
        if (places == null || places.size() != 1) return false;
        MonsterLocation place = places.get(0);
        return monster.equals(place.getMonsterName()) && monster.equals(place.getSourcePage())
                && id.equals(place.getId()) && location.equals(place.getLocation()) && revision == place.getRevisionId()
                && place.isAvailable() && place.getWilderness() == MonsterLocation.Wilderness.YES;
    }

    DropSource pickaxe()
    {
        return new DropSource("Dragon pickaxe", monster, "1", this == ARTIO ? "1/358" : "1/256", "");
    }

    DropSource component()
    {
        return new DropSource(item, monster, "1", rate, "");
    }
}
