package com.itemdroplookup.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.JTabbedPane;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

/** Development-only sidebar. Never included in the normal Plugin Hub JAR. */
@PluginDescriptor(name = "Item Lookup Design Preview", enabledByDefault = true)
public class DesignConceptPlugin extends Plugin
{
    @Inject private ClientToolbar toolbar;
    private NavigationButton navigation;

    @Override protected void startUp()
    {
        PluginPanel panel = new PluginPanel(false) { };
        panel.setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(DesignConceptPanel.BODY.deriveFont(12f));
        tabs.addTab("A · Search", new DesignConceptPanel(false, 13));
        tabs.addTab("B · Overview", new DesignConceptPanel(true, 13));
        panel.add(tabs, BorderLayout.CENTER);
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(222, 193, 126)); g.fillRoundRect(1, 1, 14, 14, 4, 4);
        g.setColor(Color.BLACK); g.drawString("A", 4, 12); g.dispose();
        navigation = NavigationButton.builder().tooltip("Design concepts — Apollo review").icon(icon).priority(4).panel(panel).build();
        toolbar.addNavigation(navigation);
    }

    @Override protected void shutDown()
    {
        if (navigation != null) toolbar.removeNavigation(navigation);
    }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(DesignConceptPlugin.class);
        RuneLite.main(args);
    }
}
