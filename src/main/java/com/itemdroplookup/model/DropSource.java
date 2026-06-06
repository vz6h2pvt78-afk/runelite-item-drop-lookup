package com.itemdroplookup.model;

public class DropSource
{
    private final String itemName;
    private final String monsterName;
    private final String quantity;
    private final String dropRate;
    private final String notes;

    public DropSource(String itemName, String monsterName, String quantity, String dropRate, String notes)
    {
        this.itemName = itemName;
        this.monsterName = monsterName;
        this.quantity = quantity;
        this.dropRate = dropRate;
        this.notes = notes;
    }

    public String getItemName()
    {
        return itemName;
    }

    public String getMonsterName()
    {
        return monsterName;
    }

    public String getQuantity()
    {
        return quantity;
    }

    public String getDropRate()
    {
        return dropRate;
    }

    public String getNotes()
    {
        return notes;
    }
}