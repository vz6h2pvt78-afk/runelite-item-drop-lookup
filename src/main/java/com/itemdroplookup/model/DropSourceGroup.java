package com.itemdroplookup.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DropSourceGroup
{
    private final String parentName;
    private final List<DropSource> members;

    public DropSourceGroup(String parentName, List<DropSource> members)
    {
        this.parentName = parentName;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
    }

    public String getParentName()
    {
        return parentName;
    }

    public List<DropSource> getMembers()
    {
        return members;
    }
}
