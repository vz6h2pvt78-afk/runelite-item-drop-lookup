package com.itemdroplookup.service;

import com.google.gson.*;
import com.itemdroplookup.model.ShopLocations;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

@Slf4j
final class ShopLocationCoverage
{
    static Map<List<String>, ShopLocations> read(Gson gson)
    {
        Map<List<String>, ShopLocations> result = new HashMap<>();
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
                    fields(offer,"itemName,cost,notes","itemMembers");
                    Boolean members = !offer.has("itemMembers") || offer.get("itemMembers").isJsonNull() ? null : bool(offer,"itemMembers");
                    List<String> key = Arrays.asList(name,string(offer,"itemName"),text(offer,"cost"),text(offer,"notes"));
                    require(result.put(key,new ShopLocations(forestry && Boolean.FALSE.equals(members) ? worldLabels : labels,members))==null,"Ambiguous offer");
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
}
