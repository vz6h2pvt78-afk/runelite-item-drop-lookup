package com.itemdroplookup.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reviewed alternative sites for a single offer. Site and offer membership have distinct scopes. */
public final class ShopLocations
{
    private final List<String> sites;
    private final Boolean membersOffer;

    public ShopLocations(List<String> sites, Boolean membersOffer)
    {
        this.sites = Collections.unmodifiableList(new ArrayList<>(sites));
        this.membersOffer = membersOffer;
    }

    public List<String> getSites() { return sites; }
    public Boolean getMembersOffer() { return membersOffer; }
}
