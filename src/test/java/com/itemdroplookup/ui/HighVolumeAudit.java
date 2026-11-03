package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.AccessRequirementLookupService;
import java.awt.Component;
import java.awt.Container;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Offline diagnostic audit. Results supplement, and do not replace, live sidebar review. */
public final class HighVolumeAudit
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 2)
        {
            throw new IllegalArgumentException("Expected item-name text file and output JSON path");
        }
        List<String> queries = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        List<Map<String, Object>> report = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            Gson gson = new Gson();
            DropLookupService drops = new DropLookupService(gson);
            ItemSourceLookupService sources = new ItemSourceLookupService(gson);
            ItemPriceLookupService prices = new ItemPriceLookupService(gson);
            MonsterLocationLookupService locations = new MonsterLocationLookupService(gson);
            ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, sources, prices, locations, new AccessRequirementLookupService(gson));
            for (String query : queries)
            {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("query", query);
                List<DropSource> npcs = drops.searchByItemName(query);
                List<ItemSource> other = sources.searchByItemName(query);
                row.put("npcCount", npcs.size());
                row.put("npcItemNames", npcs.stream().map(DropSource::getItemName).distinct().collect(Collectors.toList()));
                row.put("sourceTypes", other.stream().collect(Collectors.groupingBy(ItemSource::getSourceType, LinkedHashMap::new, Collectors.counting())));
                row.put("otherItemNames", other.stream().map(ItemSource::getItemName).distinct().collect(Collectors.toList()));
                prices.searchByItemName(query).ifPresent(price -> {
                    row.put("priceItem", price.getItemName());
                    row.put("price", price.getPrice());
                    row.put("priceNotes", price.getNotes());
                });
                JTextField search = (JTextField) named(panel, "itemSearch");
                search.setText(query);
                search.postActionEvent();
                row.put("allView", labels(panel));
                row.put("visibleOtherCount", descendants(panel).stream().filter(c -> "itemSourceCard".equals(c.getName())).count());
                Map<String, Object> filters = new LinkedHashMap<>();
                JComboBox<?> filter = (JComboBox<?>) named(panel, "sourceTypeFilter");
                for (String type : new String[] {"NPC Drops", "Non-NPC Sources", "Shops", "Reward Shops", "All"})
                {
                    filter.setSelectedItem(type);
                    filters.put(type, labels(panel));
                }
                row.put("filterViews", filters);
                JTextField finder = (JTextField) named(panel, "sourceSearch");
                String needle = !npcs.isEmpty() ? npcs.get(0).getMonsterName() : !other.isEmpty() ? other.get(0).getSourceName() : "missing source";
                finder.setText(needle);
                finder.postActionEvent();
                row.put("findSource", needle);
                row.put("findView", labels(panel));
                Component locationButton = named(panel, "openLocations");
                if (locationButton == null)
                {
                    Component group = descendants(panel).stream().filter(c -> c.getName() != null && c.getName().startsWith("sourceGroup:")).findFirst().orElse(null);
                    if (group instanceof JButton) { ((JButton) group).doClick(0); }
                    locationButton = named(panel, "openLocations");
                }
                if (locationButton instanceof JButton)
                {
                    ((JButton) locationButton).doClick(0);
                    row.put("detailView", labels(panel));
                    ((JButton) named(panel, "backToResults")).doClick(0);
                    row.put("backRetained", search == named(panel, "itemSearch") && needle.equals(((JTextField) named(panel, "sourceSearch")).getText()));
                }
                finder.setText("no-source-qa-zz");
                finder.postActionEvent();
                Component reset = named(panel, "resetSourceFilters");
                row.put("emptyFilterResetAvailable", reset != null);
                if (reset instanceof JButton) { ((JButton) reset).doClick(0); }
                finder.setText("");
                finder.postActionEvent();
                report.add(row);
                System.out.println(query + ": NPC=" + npcs.size() + ", other=" + other.size() + ", names=" + row.get("npcItemNames") + "/" + row.get("otherItemNames"));
            }
        });
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
    }

    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents())
        {
            result.add(child);
            if (child instanceof Container) { result.addAll(descendants((Container) child)); }
        }
        return result;
    }

    private static Component named(Container root, String name)
    {
        return descendants(root).stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    }

    private static List<String> labels(Container root)
    {
        return descendants(root).stream().filter(c -> c instanceof JLabel && c.isVisible())
                .map(c -> ((JLabel) c).getText()).filter(t -> t != null && !t.isBlank()).collect(Collectors.toList());
    }
}
