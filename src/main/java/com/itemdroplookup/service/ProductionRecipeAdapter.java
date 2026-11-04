package com.itemdroplookup.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itemdroplookup.model.AcquisitionMethod;
import com.itemdroplookup.model.AcquisitionMethod.Section;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import static com.itemdroplookup.service.AcquisitionEventLookupService.*;

/** Projects the reviewed normal-furnace recipe by its output. Inputs never enter the item search index. */
@Slf4j
final class ProductionRecipeAdapter
{
    static List<AcquisitionMethod> readBundled(Gson gson)
    {
        try (InputStream stream = ProductionRecipeAdapter.class.getResourceAsStream("/production_recipes.json"))
        {
            if (stream == null) return Collections.emptyList();
            return project(gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class));
        }
        catch (RuntimeException | java.io.IOException ex)
        {
            log.debug("Reviewed Production methods unavailable", ex);
            return Collections.emptyList();
        }
    }

    static List<AcquisitionMethod> project(JsonObject root)
    {
        fields(root, "schemaVersion,recipes", "");
        integer(root, "schemaVersion", 1, 1);
        require(array(root, "recipes").size() == 1, "Unexpected Production recipe set");
        JsonObject recipe = object(array(root, "recipes").get(0));
        fields(recipe, "id,output,inputs,action,facility,skill,level,tools,membersOnly,locationCoverage,evidence", "");
        String id = string(recipe, "id");
        require("production:bronze-bar:normal-furnace".equals(id), "Unknown production route");
        JsonObject output = object(recipe.get("output"));
        fields(output, "itemName,quantity", "");
        require("Bronze bar".equals(string(output, "itemName")), "Unexpected output identity");
        integer(output, "quantity", 1, 1);
        List<String> inputs = new ArrayList<>(), names = new ArrayList<>();
        for (JsonElement element : array(recipe, "inputs"))
        {
            JsonObject input = object(element);
            fields(input, "itemName,quantity", "");
            String name = string(input, "itemName");
            int quantity = integer(input, "quantity", 1, 1);
            inputs.add(quantity + " × " + name);
            names.add(name);
        }
        require(names.equals(Arrays.asList("Copper ore","Tin ore")), "Unexpected consumed inputs");
        String facility = string(recipe, "facility");
        require("Normal furnace".equals(facility) && "Smelt".equals(string(recipe, "action")), "Unknown facility/action");
        require("Smithing".equals(string(recipe, "skill")) && integer(recipe, "level", 1, 1) == 1, "Unknown skill");
        require(array(recipe, "tools").size() == 0 && !bool(recipe, "membersOnly")
                && "UNREVIEWED".equals(string(recipe,"locationCoverage")), "Unknown recipe scope");
        evidence(array(recipe, "evidence"));
        List<Section> sections = Arrays.asList(
                new Section("Smelt", Collections.singletonList("Smithing 1 at a normal furnace.")),
                Section.itemLines("Inputs", inputs, names),
                new Section("Makes", Collections.singletonList("1 Bronze bar")),
                new Section("Access", Arrays.asList("Free-to-play recipe. No tool required.", "Location-specific furnace access not reviewed.")));
        return Collections.singletonList(new AcquisitionMethod(id, "Bronze bar", facility, "Production", "Bronze bar", "", sections, true));
    }
}
