package com.itemdroplookup.model;

public class ItemSource
{
    private final String itemName;
    private final String sourceType;
    private final String sourceName;
    private final String location;
    private final String cost;
    private final String notes;

    public ItemSource(String itemName, String sourceType, String sourceName, String location, String cost, String notes)
    {
        this.itemName = itemName;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.location = location;
        this.cost = cost;
        this.notes = notes;
    }

    public String getItemName()
    {
        return itemName;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public String getLocation()
    {
        return location;
    }

    public String getCost()
    {
        return cost;
    }

    public String getNotes()
    {
        return notes;
    }
}