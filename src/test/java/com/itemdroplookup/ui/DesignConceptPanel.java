package com.itemdroplookup.ui;

import com.google.gson.Gson;
import com.itemdroplookup.model.DropSource;
import com.itemdroplookup.model.ItemSource;
import com.itemdroplookup.model.MonsterLocation;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService;
import java.awt.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** C1 only: bounded offline prototypes. Production UI and source data are unchanged. */
public final class DesignConceptPanel extends JPanel
{
    static final Color BACK = new Color(29, 33, 38);
    static final Color CARD = new Color(41, 47, 54);
    static final Color TEXT = new Color(239, 240, 232);
    static final Color MUTED = new Color(184, 196, 203);
    static final Color ACCENT = new Color(222, 193, 126);
    static final Font BODY = loadFont();
    private final boolean overview;
    private final int fontSize;
    private final DropLookupService drops;
    private final ItemSourceLookupService sources;
    private final MonsterLocationLookupService locations;
    private final AccessRequirementLookupService requirements;
    private final JTextField search = new JTextField();
    private final JPanel content = column();
    private final JScrollPane scroll = new JScrollPane(content);
    private String query = "";
    private String selectedItem = "";
    private String method = "All sources";
    private List<DropSource> matchedDrops = new ArrayList<>();
    private List<ItemSource> matchedSources = new ArrayList<>();

    public DesignConceptPanel(boolean overview, int fontSize)
    {
        super(new BorderLayout(0, 12));
        this.overview = overview;
        this.fontSize = fontSize;
        Gson gson = new Gson();
        drops = new DropLookupService(gson);
        sources = new ItemSourceLookupService(gson);
        locations = new MonsterLocationLookupService(gson);
        requirements = new AccessRequirementLookupService(gson);
        setBackground(BACK);
        setBorder(new EmptyBorder(12, 10, 10, 10));
        JPanel header = column();
        header.add(text("Item Drop Lookup", 18, ACCENT));
        header.add(Box.createVerticalStrut(9));
        search.setFont(BODY.deriveFont((float) fontSize));
        search.setBackground(CARD);
        search.setForeground(TEXT);
        search.setCaretColor(TEXT);
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(MUTED), new EmptyBorder(6, 7, 6, 7)));
        search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 37));
        search.setPreferredSize(new Dimension(200, 37));
        search.setName("conceptSearch");
        search.setAlignmentX(LEFT_ALIGNMENT);
        search.getAccessibleContext().setAccessibleName("Item name");
        search.addActionListener(e -> search(search.getText()));
        header.add(search);
        header.add(Box.createVerticalStrut(7));
        header.add(button("Find item", () -> search(search.getText()), true));
        add(header, BorderLayout.NORTH);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        scroll.getViewport().setBackground(BACK);
        add(scroll, BorderLayout.CENTER);
        search("Spade");
    }

    public void search(String value)
    {
        query = value.trim();
        search.setText(query);
        matchedDrops = drops.searchByItemName(query);
        matchedSources = sources.searchByItemName(query);
        Map<String, String> items = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        matchedDrops.forEach(d -> items.put(d.getItemName(), d.getItemName()));
        matchedSources.forEach(s -> items.put(s.getItemName(), s.getItemName()));
        if (items.size() == 1) { selectItem(items.values().iterator().next()); return; }
        clear();
        content.add(text(items.isEmpty() ? "No matching items" : "Choose an item", 17, TEXT));
        content.add(text(items.isEmpty() ? "Try the item name or a shorter spelling." : items.size() + " items match “" + query + "”", fontSize, MUTED));
        for (String item : items.values())
        {
            gap();
            content.add(button(item, () -> selectItem(item), false));
        }
        refresh();
    }

    private void selectItem(String item)
    {
        selectedItem = item;
        if (overview) showOverview(); else showSources();
    }

    private List<DropSource> itemDrops()
    {
        return matchedDrops.stream().filter(d -> d.getItemName().equalsIgnoreCase(selectedItem)).collect(Collectors.toList());
    }

    private List<ItemSource> itemSources()
    {
        return matchedSources.stream().filter(s -> s.getItemName().equalsIgnoreCase(selectedItem)).collect(Collectors.toList());
    }

    private Map<String, Long> counts()
    {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (ItemSource source : itemSources()) counts.merge(methodName(source), 1L, Long::sum);
        if (!itemDrops().isEmpty()) counts.put("Monster drops", (long) itemDrops().size());
        return counts;
    }

    private String methodName(ItemSource source)
    {
        switch (source.getSourceType())
        {
            case "Shop": return "Buy from shops";
            case "Reward Shop": return "Exchange rewards";
            case "Ground pickup": return "Pick up";
            case "Gathering": return "Gather";
            default: return source.getSourceType();
        }
    }

    private void itemHeader()
    {
        content.add(text(selectedItem, 18, TEXT));
        gap();
    }

    private void showOverview()
    {
        clear();
        itemHeader();
        content.add(text("Ways to get it", fontSize, MUTED));
        counts().forEach((name, count) -> {
            gap();
            JPanel card = card();
            card.add(text(name, 15, ACCENT));
            card.add(text(count + (count == 1 ? " source" : " sources"), fontSize, MUTED));
            card.add(button("See sources →", () -> { method = name; showSources(); }, false));
            content.add(card);
        });
        gap();
        content.add(button("See all sources", () -> { method = "All sources"; showSources(); }, true));
        refresh();
    }

    private void showSources()
    {
        clear();
        if (overview) { content.add(button("← Item overview", this::showOverview, false)); gap(); }
        itemHeader();
        List<String> options = new ArrayList<>();
        options.add("All sources");
        options.addAll(counts().keySet());
        if (!options.contains(method)) method = "All sources";
        JComboBox<String> filter = new JComboBox<>(options.toArray(new String[0]));
        filter.setName("conceptMethodFilter");
        filter.setFont(BODY.deriveFont((float) fontSize));
        filter.setBackground(CARD);
        filter.setForeground(TEXT);
        filter.setSelectedItem(method);
        filter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        filter.setAlignmentX(LEFT_ALIGNMENT);
        filter.setMinimumSize(new Dimension(0, 35));
        filter.getAccessibleContext().setAccessibleName("Acquisition method");
        filter.addActionListener(e -> { method = (String) filter.getSelectedItem(); showSources(); });
        content.add(filter);
        gap();
        long shown = counts().entrySet().stream().filter(e -> method.equals("All sources") || method.equals(e.getKey())).mapToLong(Map.Entry::getValue).sum();
        content.add(text(shown + " sources · " + method, fontSize, MUTED));
        for (ItemSource source : itemSources())
        {
            if (!method.equals("All sources") && !method.equals(methodName(source))) continue;
            gap();
            JPanel card = card();
            card.add(text(methodName(source), fontSize, ACCENT));
            card.add(text(source.getSourceName(), 15, TEXT));
            if (hasText(source.getLocation())) card.add(text(source.getLocation(), fontSize, MUTED));
            if (hasText(source.getCost())) card.add(text(source.getCost(), fontSize, TEXT));
            card.add(button("Details →", () -> showSourceDetail(source), false));
            content.add(card);
        }
        if (method.equals("All sources") || method.equals("Monster drops")) for (DropSource drop : itemDrops())
        {
            gap();
            JPanel card = card();
            card.add(text(drop.getMonsterName(), 15, TEXT));
            MonsterLocationLookupService.LocationMatch match = locations.lookup(drop);
            if (match.isVerified() && !match.getLocations().isEmpty())
                card.add(text(match.getLocations().stream().map(MonsterLocation::getLocation).distinct().collect(Collectors.joining(" · ")), fontSize, MUTED));
            card.add(text("Chance: " + drop.getDropRate() + "\nQuantity: " + drop.getQuantity(), fontSize, TEXT));
            requirements.forSource(drop).stream().filter(r -> r.isSourceCardRequirement()).forEach(r -> card.add(text(r.getText(), fontSize, ACCENT)));
            card.add(button("Locations & details →", () -> showDropDetail(drop), false));
            content.add(card);
        }
        refresh();
    }

    public void showLensa()
    {
        search("Lyre");
        itemDrops().stream().filter(d -> d.getMonsterName().equals("Lensa")).findFirst().ifPresent(this::showDropDetail);
    }

    private void showDropDetail(DropSource drop)
    {
        clear();
        content.add(button("← " + selectedItem, this::showSources, false));
        gap();
        content.add(text(drop.getMonsterName(), 18, TEXT));
        content.add(text("Chance: " + drop.getDropRate() + "\nQuantity: " + drop.getQuantity(), fontSize, MUTED));
        if (hasText(drop.getNotes())) content.add(text(drop.getNotes(), fontSize, TEXT));
        requirements.forSource(drop).forEach(r -> { gap(); content.add(text(r.getLabel() + ": " + r.getText(), fontSize, ACCENT)); });
        MonsterLocationLookupService.LocationMatch match = locations.lookup(drop);
        gap();
        content.add(text(match.isVerified() ? "Locations for this drop" : "Monster locations", 15, ACCENT));
        if (!match.isVerified()) content.add(text("This drop is not yet linked to a specific location.", fontSize, MUTED));
        if (match.getLocations().isEmpty()) content.add(text("No location recorded.", fontSize, MUTED));
        for (MonsterLocation location : match.getLocations())
        {
            gap();
            JPanel card = card();
            card.add(text(location.getLocation(), 15, TEXT));
            if (hasText(location.getLevelsText())) card.add(text("Combat: " + location.getLevelsText(), fontSize, MUTED));
            if (hasText(location.getRequirements())) card.add(text(location.getRequirements(), fontSize, TEXT));
            content.add(card);
        }
        refresh();
    }

    private void showSourceDetail(ItemSource source)
    {
        clear();
        content.add(button("← " + selectedItem, this::showSources, false));
        gap();
        content.add(text(source.getSourceName(), 17, TEXT));
        content.add(text(source.getLocation(), fontSize, MUTED));
        content.add(text(source.getCost(), fontSize, TEXT));
        gap();
        content.add(text(source.getNotes(), fontSize, TEXT));
        refresh();
    }

    private JPanel card()
    {
        JPanel panel = column();
        panel.setBackground(CARD);
        panel.setBorder(new EmptyBorder(10, 9, 10, 9));
        return panel;
    }

    private JComponent text(String value, int size, Color color)
    {
        JTextArea label = new JTextArea(value == null ? "" : value);
        label.setFont(BODY.deriveFont((float) size));
        label.setForeground(color);
        label.setOpaque(false);
        label.setEditable(false);
        label.setFocusable(false);
        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        label.setSize(183, Short.MAX_VALUE);
        Dimension preferred = label.getPreferredSize();
        label.setPreferredSize(new Dimension(183, preferred.height));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JButton button(String title, Runnable action, boolean primary)
    {
        JButton button = new JButton("<html><div style='width:125px'>" + title.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
        button.getAccessibleContext().setAccessibleName(title);
        button.setFont(BODY.deriveFont((float) fontSize));
        button.setForeground(primary ? BACK : TEXT);
        button.setBackground(primary ? ACCENT : CARD);
        button.setFocusPainted(true);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? ACCENT : new Color(83, 96, 106)), new EmptyBorder(8, 5, 8, 5)));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(36, button.getPreferredSize().height)));
        button.setMinimumSize(new Dimension(0, button.getPreferredSize().height));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void clear() { content.removeAll(); }
    private void gap() { content.add(Box.createVerticalStrut(10)); }
    private void refresh()
    {
        content.revalidate(); content.repaint();
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }
    private static boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private static JPanel column()
    {
        JPanel panel = new WidthTrackingPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BACK);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }
    private static Font loadFont()
    {
        try (InputStream in = DesignConceptPanel.class.getResourceAsStream("/design/OpenDyslexic-Regular.otf"))
        {
            if (in == null) throw new IllegalStateException("Missing OpenDyslexic preview resource");
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        }
        catch (Exception ex) { throw new IllegalStateException("Cannot load intended preview font", ex); }
    }

    private static final class WidthTrackingPanel extends JPanel implements Scrollable
    {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 28; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return Math.max(28, r.height - 28); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
