package com.itemdroplookup.service;

import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Optional presentation only: canonical identity has already been selected. */
@FunctionalInterface
public interface ItemIconProvider
{
    ItemIconProvider NONE = (name, current, ready) -> { };

    void request(String canonicalName, BooleanSupplier current, Consumer<BufferedImage> ready);
}
