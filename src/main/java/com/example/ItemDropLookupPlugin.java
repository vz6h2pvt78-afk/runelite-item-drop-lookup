package com.example;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
        name = "Item Drop Lookup"
)
public class ItemDropLookupPlugin extends Plugin
{
    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private DropLookupService dropLookupService;

    @Inject
    private ItemSourceLookupService itemSourceLookupService;

    @Inject
    private ItemPriceLookupService itemPriceLookupService;

    private ItemDropLookupPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        panel = new ItemDropLookupPanel(dropLookupService, itemSourceLookupService, itemPriceLookupService);

        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = icon.createGraphics();
        graphics.fillOval(2, 2, 12, 12);
        graphics.dispose();

        navButton = NavigationButton.builder()
                .tooltip("Item Drop Lookup")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        log.debug("Item Drop Lookup started!");
    }

    @Override
    protected void shutDown() throws Exception
    {
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }

        log.debug("Item Drop Lookup stopped!");
    }

    @Provides
    ItemDropLookupConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ItemDropLookupConfig.class);
    }
}