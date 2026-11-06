package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.AcquisitionMethod.RelatedItemAction;
import com.itemdroplookup.model.AcquisitionMethod.Section;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Two reviewed recipes only. No traversal or inferred ingredient acquisition. */
@Slf4j
final class ZenyteProductionAdapter
{
    static List<AcquisitionMethod> read(Gson gson, InputStream input)
    {
        List<AcquisitionMethod> methods = new ArrayList<>();
        try (InputStream stream = input)
        {
            if (stream == null) return methods;
            JsonObject root = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            fields(root, "schemaVersion,recipes", "");
            integer(root, "schemaVersion", 1, 1);
            require(array(root, "recipes").size() == 2, "Unreviewed zenyte recipe set");
            Set<String> seen = new HashSet<>();
            for (JsonElement value : array(root, "recipes"))
            {
                try
                {
                    AcquisitionMethod method = project(object(value));
                    require(seen.add(method.getId()), "Duplicate zenyte method");
                    methods.add(method);
                }
                catch (RuntimeException ex) { log.debug("Reviewed zenyte recipe unavailable", ex); }
            }
        }
        catch (RuntimeException | java.io.IOException ex) { log.debug("Zenyte recipes unavailable", ex); }
        return methods;
    }

    static AcquisitionMethod project(JsonObject recipe)
    {
        fields(recipe, "id,output,inputs,sourceName,locationText,requirements,evidence", "relatedItemActions");
        String id = string(recipe, "id");
        boolean fuse = id.equals("production:uncut-zenyte:wall-of-flame");
        require(fuse || id.equals("production:zenyte:chisel"), "Unknown zenyte recipe");
        String outputName = fuse ? "Uncut zenyte" : "Zenyte";
        String source = fuse ? "Fuse an uncut zenyte" : "Cut an uncut zenyte";
        String location = fuse ? "Wall of flame beneath the Temple of Marimbo, Ape Atoll." : "";
        require(string(recipe, "sourceName").equals(source) && text(recipe, "locationText").equals(location), "Unknown action or location");
        List<String> requirements = fuse
                ? List.of("Crafting 70 (cannot be boosted).", "Complete Monkey Madness II.",
                          "Wear a zombie monkey greegree (small or big).", "Members only.")
                : List.of("Crafting 89 (boosts allowed).", "Chisel required.", "Members only.");
        List<String> recorded = new ArrayList<>();
        for (JsonElement line : array(recipe, "requirements"))
        {
            require(line.isJsonPrimitive() && line.getAsJsonPrimitive().isString(), "Unknown requirement");
            recorded.add(line.getAsString());
        }
        require(recorded.equals(requirements), "Unreviewed zenyte requirements");
        JsonObject output = object(recipe.get("output"));
        fields(output, "itemName,quantity", "");
        require(string(output, "itemName").equals(outputName), "Wrong output identity");
        integer(output, "quantity", 1, 1);
        List<String> inputNames = new ArrayList<>(), inputLines = new ArrayList<>();
        for (JsonElement value : array(recipe, "inputs"))
        {
            JsonObject input = object(value);
            fields(input, "itemName,quantity", "");
            String name = string(input, "itemName");
            integer(input, "quantity", 1, 1);
            inputNames.add(name);
            inputLines.add("1 × " + name + (name.equals("Onyx") ? " (cut)" : ""));
        }
        require(inputNames.equals(fuse ? List.of("Zenyte shard", "Onyx") : List.of("Uncut zenyte")), "Unreviewed zenyte ingredients");
        evidence(array(recipe, "evidence"));
        List<Section> sections = new ArrayList<>();
        sections.add(Section.itemLines("Ingredients", inputLines, inputNames));
        sections.add(new Section("Requirements", requirements));
        if (fuse) sections.add(new Section("Where", List.of(location)));
        sections.add(new Section("Makes", List.of("1 × " + outputName)));
        String report = "Production: " + source + " | " + String.join(" + ", inputLines) + " → 1 × " + outputName
                + " | " + String.join(" ", requirements) + (fuse ? " | " + location : "");
        return new AcquisitionMethod(id, outputName, source, "Production", outputName, location,
                sections, sections, true, report, actions(recipe, fuse ? "Zenyte shard" : "Uncut zenyte"));
    }

    private static List<RelatedItemAction> actions(JsonObject recipe, String target)
    {
        // A missing/bad optional action never withholds a valid recipe.
        try
        {
            if (!recipe.has("relatedItemActions")) return Collections.emptyList();
            require(array(recipe, "relatedItemActions").size() == 1, "Unreviewed related actions");
            JsonObject action = object(array(recipe, "relatedItemActions").get(0));
            fields(action, "itemName,label", "");
            require(string(action, "itemName").equals(target)
                    && string(action, "label").equals("Find " + target + " sources"), "Unreviewed related target or label");
            return List.of(new RelatedItemAction(target, string(action, "label")));
        }
        catch (RuntimeException ex)
        {
            log.debug("Zenyte related action unavailable; recipe retained", ex);
            return Collections.emptyList();
        }
    }
}
