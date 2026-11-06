package com.itemdroplookup.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;

/** Decorative group artwork only; never used to identify or match a drop source. */
@Slf4j
final class GroupArtwork
{
    private GroupArtwork() { }

    static Icon forGroup(String groupName)
    {
        return "Goblin".equals(groupName) ? GoblinArtwork.ICON : null;
    }

    private static final class GoblinArtwork
    {
        private static final Icon ICON = load();

        private static Icon load()
        {
            try (InputStream stream = GroupArtwork.class.getResourceAsStream("/artwork/goblin.png"))
            {
                if (stream == null) return null;
                BufferedImage image = ImageIO.read(stream);
                if (image == null || image.getWidth() != 27 || image.getHeight() != 39) return null;
                return new ImageIcon(image, "Representative Goblin artwork");
            }
            catch (IOException | RuntimeException ex)
            {
                log.debug("Optional Goblin group artwork unavailable", ex);
                return null;
            }
        }
    }
}
