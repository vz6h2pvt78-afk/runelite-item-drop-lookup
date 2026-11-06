package com.itemdroplookup.service;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;
import lombok.extern.slf4j.Slf4j;

/** Uses RuneLite's in-memory name index, reviewed native IDs and native image cache. */
@Slf4j
public final class ItemIconService implements ItemIconProvider
{
    // Reviewed native identities omitted by RuneLite's tradeable price index.
    // Every live definition must still pass the exact name, note and placeholder guards.
    private static final Map<String, Integer> REVIEWED_ICON_IDS = Map.ofEntries(
            Map.entry("ent's roots", ItemID.MA2_GUTHIX_HEART),
            Map.entry("demon's heart", ItemID.MA2_ZAMORAK_HEART),
            Map.entry("justiciar's hand", ItemID.MA2_SARADOMIN_HEART),
            Map.entry("herb box", ItemID.NZONE_HERBBOX),
            Map.entry("herb sack", ItemID.SLAYER_HERB_SACK),
            Map.entry("red egg", ItemID.BARBASSAULT_EGG_02),
            Map.entry("red goblin mail", ItemID.GOBLIN_ARMOUR_RED),
            Map.entry("red naval shirt", ItemID.BREW_UNIFORM_RED),
            Map.entry("red navy slacks", ItemID.BREW_NAVY_SLACKS_RED),
            Map.entry("red satchel", ItemID.TOL_RED_SACK),
            Map.entry("prayer enhance (+)(1)", ItemID.RAIDS_VIAL_PRAYER_STRONG_1),
            Map.entry("prayer enhance (+)(2)", ItemID.RAIDS_VIAL_PRAYER_STRONG_2),
            Map.entry("prayer enhance (+)(3)", ItemID.RAIDS_VIAL_PRAYER_STRONG_3),
            Map.entry("prayer enhance (+)(4)", ItemID.RAIDS_VIAL_PRAYER_STRONG_4));

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
        return result != null ? result : REVIEWED_ICON_IDS.get(canonicalName.toLowerCase(Locale.ROOT));
    }

    static boolean acceptedDefinition(String canonicalName, String name, int noteTemplate, int placeholderTemplate)
    {
        return canonicalName.equalsIgnoreCase(name) && noteTemplate == -1 && placeholderTemplate == -1;
    }
}
