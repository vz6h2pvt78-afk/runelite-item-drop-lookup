package com.itemdroplookup.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.AcquisitionMethod.Section;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Capture creates the jar. The clue tiers describe using that jar. */
final class LuckyImplingCapture
{
    static AcquisitionMethod project(JsonObject row)
    {
        fields(row, "id,sourceName,output,quantity,netHunter,bareHunter,emptyJar,areas,opening,evidence", "");
        String jar = string(row, "output");
        require("lucky-impling-jar-capture".equals(string(row, "id")) && "Lucky impling jar".equals(jar)
                && "Lucky impling".equals(string(row, "sourceName")), "Wrong lucky capture identity");
        integer(row, "quantity", 1, 1);
        int net = integer(row, "netHunter", 89, 89), bare = integer(row, "bareHunter", 99, 99);
        require(bool(row,"emptyJar") && strings(row,"areas").equals(Arrays.asList("Gielinor","Puro-Puro")), "Unknown lucky capture scope");
        JsonObject opening = object(row.get("opening"));
        fields(opening,"rolls,tiers,chancePerTier,excluded","");
        integer(opening,"rolls",1,1);
        List<String> tiers = strings(opening, "tiers");
        require(tiers.equals(Arrays.asList("easy","medium","hard","elite","master"))
                && string(opening,"chancePerTier").equals("1/5")
                && strings(opening,"excluded").equals(Arrays.asList("Bloodhound","Clue scroll (master)")), "Unknown lucky reward table");
        evidence(array(row, "evidence"));
        List<Section> sections = Arrays.asList(
                new Section("Catch into a jar", Arrays.asList("Take an empty impling jar.",
                        net+" Hunter with a butterfly or magic butterfly net, or "+bare+" bare-handed.", "Gielinor or Puro-Puro.")),
                new Section("If you open the jar", Arrays.asList("One roll from an "+String.join(", ", tiers.subList(0,4))
                        +" or master clue reward table. Each tier has a 1 in 5 chance.", "Bloodhound and master clue scrolls are excluded.")));
        return new AcquisitionMethod(string(row,"id"),jar,string(row,"sourceName"),"Gathering","Lucky impling","Gielinor; Puro-Puro",sections,true);
    }

    private static List<String> strings(JsonObject row, String key)
    {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array(row,key))
        {
            require(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString(),"Expected text in "+key);
            values.add(element.getAsString());
        }
        return values;
    }
}
