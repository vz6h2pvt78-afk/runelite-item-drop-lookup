package com.itemdroplookup.service;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;
import lombok.extern.slf4j.Slf4j;

/** Uses RuneLite's existing in-memory name index and native image cache. */
@Slf4j
public final class ItemIconService implements ItemIconProvider
{
    private final ItemManager items;
    private final ClientThread clientThread;

    @Inject
    public ItemIconService(ItemManager items, ClientThread clientThread)
    {
        this.items = items;
        this.clientThread = clientThread;
    }

    @Override
    public void request(String canonicalName, BooleanSupplier current, Consumer<BufferedImage> ready)
    {
        clientThread.invokeLater(() -> {
            if (!current.getAsBoolean()) return;
            try
            {
                Integer id = exactId(canonicalName, items.search(canonicalName));
                if (id == null) return;
                ItemComposition definition = items.getItemComposition(id);
                if (!acceptedDefinition(canonicalName, definition.getName(), definition.getNote(),
                        definition.getPlaceholderTemplateId())) return;
                AsyncBufferedImage image = items.getImage(id);
                image.onLoaded(() -> { if (current.getAsBoolean()) ready.accept(image); });
            }
            catch (RuntimeException ex)
            {
                // Missing optional artwork must never prevent a lookup or item selection.
                log.debug("Item chooser icon unavailable for {}", canonicalName, ex);
            }
        });
    }

    static Integer exactId(String canonicalName, List<ItemPrice> candidates)
    {
        Integer result = null;
        for (ItemPrice candidate : candidates)
        {
            if (!canonicalName.equalsIgnoreCase(candidate.getName())) continue;
            if (result != null && result != candidate.getId()) return null;
            result = candidate.getId();
        }
        return result;
    }

    static boolean acceptedDefinition(String canonicalName, String name, int noteTemplate, int placeholderTemplate)
    {
        return canonicalName.equalsIgnoreCase(name) && noteTemplate == -1 && placeholderTemplate == -1;
    }
}
