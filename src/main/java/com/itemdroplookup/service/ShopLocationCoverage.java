package com.itemdroplookup.service;

import com.google.gson.*;
import com.itemdroplookup.model.ShopLocations;
import com.itemdroplookup.model.BulkPurchase;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

@Slf4j
final class ShopLocationCoverage
{
    static final class Offer
    {
        final ShopLocations locations;
        final BulkPurchase bulk;

        Offer(ShopLocations locations, BulkPurchase bulk)
        {
            this.locations = locations;
            this.bulk = bulk;
        }
    }

    static Map<List<String>, Offer> read(Gson gson)
    {
        Map<List<String>, Offer> result = new HashMap<>();
        try (InputStream stream = ShopLocationCoverage.class.getResourceAsStream("/shop_locations.json"))
        {
            if (stream == null) return result;
            JsonObject root = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            fields(root,"schemaVersion,shops","");
            integer(root,"schemaVersion",1,1);
            for (JsonElement group : array(root,"shops"))
            {
                JsonObject shop = object(group);
                fields(shop,"sourceName,sourceType,sites,offers,evidence","");
                String name = string(shop,"sourceName");
                boolean forestry = name.equals("Forestry Shop");
                require(forestry || name.equals("Slayer Rewards"),"Unsupported shop");
                require("Reward Shop".equals(string(shop,"sourceType")),"Wrong offer type");
                evidence(array(shop,"evidence"));
                List<String> labels = new ArrayList<>(), worldLabels = new ArrayList<>();
                for (JsonElement e : array(shop,"sites"))
                {
                    JsonObject site = object(e);
                    fields(site,"version,place,branch,membersShop","");
                    String place = string(site,"place"), branch = string(site,"branch");
                    String version = "(" + (labels.size()+1) + ")";
                    require(version.equals(string(site,"version")),"Invalid shop version order");
                    String label = branch.equals(place) ? place : place + " — " + branch;
                    boolean members = bool(site,"membersShop");
                    worldLabels.add(label + (members ? " (members worlds)" : " (also F2P)"));
                    require(!labels.contains(label),"Duplicate site");
                    labels.add(label);
                }
                require(labels.size() == (forestry ? 4 : 10),"Incomplete location map");
                for (JsonElement e : array(shop,"offers"))
                {
                    JsonObject offer = object(e);
                    fields(offer,"itemName,cost,notes","itemMembers,bulkPurchase");
                    Boolean members = !offer.has("itemMembers") || offer.get("itemMembers").isJsonNull() ? null : bool(offer,"itemMembers");
                    List<String> key = Arrays.asList(name,string(offer,"itemName"),text(offer,"cost"),text(offer,"notes"));
                    ShopLocations locations = new ShopLocations(forestry && Boolean.FALSE.equals(members) ? worldLabels : labels, members);
                    BulkPurchase bulk = offer.has("bulkPurchase") ? bulkPurchase(name, offer) : null;
                    require(result.put(key,new Offer(locations,bulk))==null,"Ambiguous offer");
                }
            }
        }
        catch(Exception ex)
        {
            log.debug("Reviewed shop location mapping unavailable",ex);
            result.clear();
        }
        return result;
    }

    private static BulkPurchase bulkPurchase(String shop, JsonObject offer)
    {
        try
        {
            JsonObject bulk = object(offer.get("bulkPurchase"));
            fields(bulk, "itemName,quantity", "");
            String item = string(bulk, "itemName");
            String label = string(offer, "itemName");
            int quantity = integer(bulk, "quantity", 250, 250);
            require(shop.equals("Slayer Rewards") && (item.equals("Broad bolts") || item.equals("Broad arrows"))
                    && label.equals(item + " (x250)") && string(offer, "cost").equals("35 Slayer points"),
                    "Unreviewed bulk offer");
            return new BulkPurchase(item, label, quantity);
        }
        catch (RuntimeException ex)
        {
            log.debug("Bulk purchase identity unavailable", ex);
            return null;
        }
    }
}
