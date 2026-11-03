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

/** C2 review only. The styling switch and this class never enter the production JAR. */
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
        JComboBox<String> treatment = new JComboBox<>(new String[] {"Current styling", "Warmer styling"});
        treatment.setFont(new LookupStyle(13).font());
        treatment.setToolTipText("C2 review only: compare the same screen without changing text size");
        JPanel body = new JPanel(new BorderLayout())
        {
            @Override protected void paintComponent(Graphics graphics)
            {
                applyTreatment(this, treatment.getSelectedIndex() == 1);
                super.paintComponent(graphics);
            }
        };
        ItemDropLookupPanel panel = new ItemDropLookupPanel(drops, sources, prices, locations, requirements);
        body.add(panel.getWrappedPanel(), BorderLayout.CENTER);
        treatment.addActionListener(e -> body.repaint());
        review.add(treatment, BorderLayout.NORTH);
        review.add(body, BorderLayout.CENTER);
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).setText("Spade");
        ((JTextField) PanelTestSupport.find(panel, "itemSearch")).postActionEvent();
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setColor(new Color(222, 193, 126)); graphics.fillRoundRect(1, 1, 14, 14, 4, 4);
        graphics.setColor(Color.BLACK); graphics.drawString("B", 4, 12); graphics.dispose();
        navigation = NavigationButton.builder().tooltip("C2 implemented B — Apollo review")
                .icon(icon).priority(4).panel(review).build();
        toolbar.addNavigation(navigation);
    }

    static void applyTreatment(Container parent, boolean warm)
    {
        Color[] current = {LookupStyle.BACK, LookupStyle.CARD, LookupStyle.TEXT, LookupStyle.MUTED, LookupStyle.ACCENT, new Color(83, 96, 106)};
        Color[] alternative = {new Color(35, 31, 26), new Color(53, 46, 37), new Color(238, 224, 191), new Color(201, 188, 161), new Color(202, 165, 91), new Color(120, 98, 67)};
        for (Component component : parent.getComponents())
        {
            for (int i = 0; i < current.length; i++)
            {
                Color from = warm ? current[i] : alternative[i];
                Color to = warm ? alternative[i] : current[i];
                if (from.equals(component.getBackground())) component.setBackground(to);
                if (from.equals(component.getForeground())) component.setForeground(to);
            }
            if (component instanceof Container) applyTreatment((Container) component, warm);
        }
    }

    @Override protected void shutDown() { if (navigation != null) toolbar.removeNavigation(navigation); }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ImplementedDesignReview.class);
        RuneLite.main(args);
    }
}
