package com.itemdroplookup.ui;

import com.itemdroplookup.service.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.*;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/** C2e review only. The selected production font is used without comparison controls. */
@PluginDescriptor(name = "Item Lookup C2 Review", enabledByDefault = true)
public class ImplementedDesignReview extends Plugin
{
    @Inject private ClientToolbar toolbar;
    @Inject private DropLookupService drops;
    @Inject private ItemSourceLookupService sources;
    @Inject private ItemPriceLookupService prices;
    @Inject private MonsterLocationLookupService locations;
    @Inject private AccessRequirementLookupService requirements;
    private NavigationButton navigation;

    @Override protected void startUp()
    {
        PluginPanel review = new PluginPanel(false) { };
        review.setLayout(new BorderLayout());
        ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, sources, prices, locations, requirements);
        JLabel identifier = new JLabel("Review C2e");
        identifier.setFont(new LookupStyle(LookupStyle.STANDARD_SIZE).font());
        identifier.setForeground(LookupStyle.ACCENT);
        identifier.setAlignmentX(Component.LEFT_ALIGNMENT);
        review.add(identifier, BorderLayout.NORTH);
        review.add(panel.getWrappedPanel(), BorderLayout.CENTER);
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).setText("Spade");
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).postActionEvent();
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setColor(LookupStyle.ACCENT); graphics.fillRoundRect(1, 1, 14, 14, 4, 4);
        graphics.setColor(Color.BLACK); graphics.drawString("B", 4, 12); graphics.dispose();
        navigation = NavigationButton.builder().tooltip("C2e B review — direct filters and Back arrows")
                .icon(icon).priority(4).panel(review).build();
        toolbar.addNavigation(navigation);
    }

    @Override protected void shutDown() { if (navigation != null) toolbar.removeNavigation(navigation); }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ImplementedDesignReview.class);
        RuneLite.main(args);
    }
}
