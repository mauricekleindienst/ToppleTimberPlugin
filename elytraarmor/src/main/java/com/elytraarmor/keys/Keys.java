package com.elytraarmor.keys;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {

    /** Marks the item as a combined Elytra-Armor. Value: "true". */
    public static NamespacedKey COMBINED;

    /** Original chestplate Material name, e.g. "DIAMOND_CHESTPLATE". */
    public static NamespacedKey CHEST_MATERIAL;

    /** Serialised enchantment map from the chestplate ("NAME:LEVEL,..."). Used to restore enchants on split. */
    public static NamespacedKey CHEST_ENCHANTS;

    /** Custom display-name of the original chestplate (empty string if none). */
    public static NamespacedKey CHEST_DISPLAY_NAME;

    private Keys() {}

    public static void init(Plugin plugin) {
        COMBINED           = new NamespacedKey(plugin, "combined");
        CHEST_MATERIAL     = new NamespacedKey(plugin, "chest_material");
        CHEST_ENCHANTS     = new NamespacedKey(plugin, "chest_enchants");
        CHEST_DISPLAY_NAME = new NamespacedKey(plugin, "chest_display_name");
    }
}
