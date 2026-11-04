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

/** C2c review only. The font comparison never enters the production JAR. */
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
        JComboBox<String> treatment = new JComboBox<>(new String[] {"OpenDyslexic", "Atkinson"});
        treatment.setName("reviewFont");
        treatment.setFont(new LookupStyle(13).font());
        treatment.getAccessibleContext().setAccessibleName("Development font comparison");
        treatment.setToolTipText("Atkinson Hyperlegible: approximate lowercase height match; same layout and colours");
        JPanel body = new JPanel(new BorderLayout());
        ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, sources, prices, locations, requirements);
        JComponent size = (JComponent) PanelTestSupport.find(panel, "textSize");
        size.setEnabled(false);
        size.setToolTipText("Text size is held fixed during this font comparison");
        body.add(panel.getWrappedPanel(), BorderLayout.CENTER);
        installComparison(body, treatment);
        JPanel controls = LookupStyle.column();
        JLabel identifier = new JLabel("Review C2c");
        identifier.setFont(new LookupStyle(13).font());
        identifier.setForeground(LookupStyle.ACCENT);
        identifier.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(identifier);
        controls.add(treatment);
        review.add(controls, BorderLayout.NORTH);
        review.add(body, BorderLayout.CENTER);
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).setText("Spade");
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).postActionEvent();
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setColor(LookupStyle.ACCENT); graphics.fillRoundRect(1, 1, 14, 14, 4, 4);
        graphics.setColor(Color.BLACK); graphics.drawString("B", 4, 12); graphics.dispose();
        navigation = NavigationButton.builder().tooltip("C2c B review — layout and font")
                .icon(icon).priority(4).panel(review).build();
        toolbar.addNavigation(navigation);
    }

    static void installComparison(Container body, JComboBox<String> treatment)
    {
        Runnable update = () -> {
            ReviewFontComparison.apply(body, treatment.getSelectedIndex() == 1);
            body.revalidate();
            body.repaint();
        };
        treatment.addActionListener(e -> update.run());
        boolean[] queued = {false};
        watchContent(body, () -> {
            if (queued[0]) return;
            queued[0] = true;
            SwingUtilities.invokeLater(() -> {
                queued[0] = false;
                update.run();
            });
        });
        update.run();
    }

    private static void watchContent(Container parent, Runnable changed)
    {
        if (parent instanceof JComponent)
        {
            JComponent component = (JComponent) parent;
            if (Boolean.TRUE.equals(component.getClientProperty("reviewWatched"))) return;
            component.putClientProperty("reviewWatched", true);
        }
        parent.addContainerListener(new java.awt.event.ContainerAdapter()
        {
            @Override public void componentAdded(java.awt.event.ContainerEvent event)
            {
                if (event.getChild() instanceof Container) watchContent((Container) event.getChild(), changed);
                changed.run();
            }
        });
        for (Component child : parent.getComponents()) if (child instanceof Container) watchContent((Container) child, changed);
    }

    @Override protected void shutDown() { if (navigation != null) toolbar.removeNavigation(navigation); }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ImplementedDesignReview.class);
        RuneLite.main(args);
    }
}
