package com.itemdroplookup.model;

import java.util.List;
import java.util.Locale;

/** Preparation and amounts for one existing Warrior guild token drop. */
public final class AnimatorReward
{
    private final String metal;
    private final List<String> equipment;
    private final int base;
    private final int easy;
    private final int medium;

    public AnimatorReward(String metal, List<String> equipment, int base, int easy, int medium)
    {
        this.metal = metal;
        this.equipment = List.copyOf(equipment);
        this.base = base;
        this.easy = easy;
        this.medium = medium;
    }

    public int getBase() { return base; }
    public int getEasy() { return easy; }
    public int getMedium() { return medium; }
    public List<String> getEquipment() { return equipment; }

    public String getSetupText()
    {
        return "Use the " + metal.toLowerCase(Locale.ROOT) + " platebody, platelegs and full helm on the magical animator.";
    }

    public String getRewardText()
    {
        return "Combat Achievement rewards: " + easy + " tokens after Easy, " + medium + " after Medium.";
    }
}
