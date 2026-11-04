package com.itemdroplookup.model;

public class ItemSource
{
    private final String itemName;
    private final String sourceType;
    private final String sourceName;
    private final String location;
    private final String cost;
    private final String notes;
    private final String packName;
    private final String packItemName;
    private final int packQuantity;
    private final boolean packNoted;
    private final AcquisitionMethod acquisitionMethod;

    public ItemSource(String itemName, String sourceType, String sourceName, String location, String cost, String notes)
    {
        this(itemName, sourceType, sourceName, location, cost, notes, "", "", 0, false);
    }

    public ItemSource(String itemName, String sourceType, String sourceName, String location, String cost, String notes,
            String packName, String packItemName, int packQuantity, boolean packNoted)
    {
        this(itemName, sourceType, sourceName, location, cost, notes, packName, packItemName, packQuantity, packNoted, null);
    }

    public ItemSource(String itemName, String sourceType, String sourceName, String location, String cost, String notes,
            String packName, String packItemName, int packQuantity, boolean packNoted, AcquisitionMethod acquisitionMethod)
    {
        this.itemName = itemName;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.location = location;
        this.cost = cost;
        this.notes = notes;
        this.packName = packName;
        this.packItemName = packItemName;
        this.packQuantity = packQuantity;
        this.packNoted = packNoted;
        this.acquisitionMethod = acquisitionMethod;
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

    public String getPackName() { return packName; }
    public String getPackItemName() { return packItemName; }
    public int getPackQuantity() { return packQuantity; }
    public boolean isPackNoted() { return packNoted; }
    public AcquisitionMethod getAcquisitionMethod() { return acquisitionMethod; }
}
