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

/** Each reviewed recipe is indexed by its output and validated independently. */
@Slf4j
final class ProductionRecipeAdapter
{
    static List<AcquisitionMethod> readBundled(Gson gson)
    {
        return read(gson, ProductionRecipeAdapter.class.getResourceAsStream("/production_recipes.json"),
                ProductionRecipeAdapter.class.getResourceAsStream("/cooking_recipes.json"));
    }

    static List<AcquisitionMethod> read(Gson gson, InputStream bronze, InputStream cooking)
    {
        List<AcquisitionMethod> methods = new ArrayList<>();
        methods.addAll(readResource(gson, bronze, ProductionRecipeAdapter::project));
        methods.addAll(readResource(gson, cooking, ProductionRecipeAdapter::projectCooking));
        return methods;
    }

    private static List<AcquisitionMethod> readResource(Gson gson, InputStream input,
            java.util.function.Function<JsonObject, List<AcquisitionMethod>> adapter)
    {
        try (InputStream stream = input)
        {
            if (stream == null) return Collections.emptyList();
            return adapter.apply(gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class));
        }
        catch (RuntimeException | java.io.IOException ex)
        {
            log.debug("Reviewed Production methods unavailable", ex);
            return Collections.emptyList();
        }
    }

    private static List<AcquisitionMethod> projectCooking(JsonObject root)
    {
        fields(root, "schemaVersion,recipes", "");
        integer(root, "schemaVersion", 1, 1);
        require(array(root, "recipes").size() == 1, "Unreviewed cooking routes");
        JsonObject recipe = object(array(root, "recipes").get(0));
        fields(recipe, "id,action,facility,inputs,output,outputBasis,failure,skill,level,membersOnly,tools,alternateFacilityNote,evidence", "");
        String id = string(recipe, "id");
        String facility = string(recipe, "facility");
        require(id.equals("production:shrimps:cooking-range") && string(recipe, "action").equals("Cook")
                && facility.equals("Cooking range"), "Unreviewed cooking facility");
        require(string(recipe, "outputBasis").equals("ON_SUCCESS") && string(recipe, "failure").equals("BURN"),
                "Missing cooking success or failure condition");
        require(string(recipe, "skill").equals("Cooking") && integer(recipe, "level", 1, 1) == 1
                && !bool(recipe, "membersOnly") && array(recipe, "tools").size() == 0, "Unknown cooking requirements");
        require(array(recipe, "inputs").size() == 1, "Unreviewed cooking ingredients");
        JsonObject input = object(array(recipe, "inputs").get(0));
        JsonObject output = object(recipe.get("output"));
        fields(input, "itemName,quantity", "");
        fields(output, "itemName,quantity", "");
        String raw = string(input, "itemName"), cooked = string(output, "itemName");
        require(raw.equals("Raw shrimps") && cooked.equals("Shrimps"), "Unknown cooking input or output");
        int used = integer(input, "quantity", 1, 1), made = integer(output, "quantity", 1, 1);
        String alternate = string(recipe, "alternateFacilityNote");
        require(alternate.equals("Raw shrimps can also be cooked on a fire."), "Unknown alternate facility");
        evidence(array(recipe, "evidence"));
        String inputLine = used + " × " + raw;
        String outputLine = made + " × " + cooked;
        List<Section> sections = Arrays.asList(
                new Section("Cook", List.of("Cooking 1 at a cooking range.")),
                Section.itemLines("Input", List.of(inputLine), List.of(raw)),
                Section.itemLines("Makes on success", List.of(outputLine, "Can burn while cooking."), Arrays.asList(cooked, null)),
                new Section("Access", List.of("Free-to-play recipe. No tool required.", alternate)));
        String report = "Production: " + facility + " | " + inputLine + " → " + outputLine + " on success | Can burn";
        return List.of(new AcquisitionMethod(id, cooked, facility, "Production", cooked, "", sections, sections, true, report));
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
