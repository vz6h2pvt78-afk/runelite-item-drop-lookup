package com.itemdroplookup;

import com.google.inject.Provides;
import com.itemdroplookup.service.DropLookupService;
import com.itemdroplookup.service.ItemPriceLookupService;
import com.itemdroplookup.service.ItemSourceLookupService;
import com.itemdroplookup.service.MonsterLocationLookupService;
import com.itemdroplookup.service.AccessRequirementLookupService;
import com.itemdroplookup.service.ItemIconService;
import com.itemdroplookup.ui.ItemDropLookupPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
        name = "Obtainable"
)
public class ItemDropLookupPlugin extends Plugin
{
    private static final BufferedImage ICON = ImageUtil.loadImageResource(ItemDropLookupPlugin.class, "/obtainable-icon.png");

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private DropLookupService dropLookupService;

    @Inject
    private ItemSourceLookupService itemSourceLookupService;

    @Inject
    private ItemPriceLookupService itemPriceLookupService;

    @Inject
    private MonsterLocationLookupService monsterLocationLookupService;

    @Inject
    private AccessRequirementLookupService accessRequirementLookupService;

    @Inject
    private ItemIconService itemIconService;

    private ItemDropLookupPanel panel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        panel = new ItemDropLookupPanel(dropLookupService, itemSourceLookupService, itemPriceLookupService,
                monsterLocationLookupService, accessRequirementLookupService, itemIconService);

        navButton = NavigationButton.builder()
                .tooltip("Obtainable")
                .icon(ICON)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        log.debug("Obtainable started!");
    }

    @Override
    protected void shutDown() throws Exception
    {
        if (panel != null)
        {
            panel.dispose();
            panel = null;
        }
        NavigationButton removedButton = navButton;
        navButton = null;
        if (removedButton != null)
        {
            clientToolbar.removeNavigation(removedButton);
        }

        log.debug("Obtainable stopped!");
    }

    @Provides
    ItemDropLookupConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ItemDropLookupConfig.class);
    }
}
