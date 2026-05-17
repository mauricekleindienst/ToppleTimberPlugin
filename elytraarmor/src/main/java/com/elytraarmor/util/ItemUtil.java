package com.elytraarmor.util;

import com.elytraarmor.keys.Keys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class ItemUtil {

    private static final Map<Material, int[]> ARMOR_STATS = Map.of(
        Material.LEATHER_CHESTPLATE,   new int[]{3, 0},
        Material.CHAINMAIL_CHESTPLATE, new int[]{5, 0},
        Material.GOLDEN_CHESTPLATE,    new int[]{5, 0},
        Material.IRON_CHESTPLATE,      new int[]{6, 0},
        Material.DIAMOND_CHESTPLATE,   new int[]{8, 2},
        Material.NETHERITE_CHESTPLATE, new int[]{8, 3}
    );

    public static boolean isChestplate(Material m) {
        return ARMOR_STATS.containsKey(m);
    }

    public static int[] getArmorStats(Material m) {
        return ARMOR_STATS.getOrDefault(m, new int[]{0, 0});
    }

    // ── Tier appearance ───────────────────────────────────────────────────

    public static NamedTextColor tierColor(Material m) {
        return switch (m) {
            case LEATHER_CHESTPLATE   -> NamedTextColor.GOLD;
            case CHAINMAIL_CHESTPLATE -> NamedTextColor.GRAY;
            case GOLDEN_CHESTPLATE    -> NamedTextColor.YELLOW;
            case IRON_CHESTPLATE      -> NamedTextColor.WHITE;
            case DIAMOND_CHESTPLATE   -> NamedTextColor.AQUA;
            case NETHERITE_CHESTPLATE -> NamedTextColor.DARK_PURPLE;
            default                    -> NamedTextColor.WHITE;
        };
    }

    private static String tierIcon(Material m) {
        return switch (m) {
            case LEATHER_CHESTPLATE   -> "◉";
            case CHAINMAIL_CHESTPLATE -> "⬡";
            case GOLDEN_CHESTPLATE    -> "★";
            case IRON_CHESTPLATE      -> "◆";
            case DIAMOND_CHESTPLATE   -> "❖";
            case NETHERITE_CHESTPLATE -> "✸";
            default                    -> "◈";
        };
    }

    // ── Combined item detection ───────────────────────────────────────────

    public static boolean isCombined(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(Keys.COMBINED, PersistentDataType.STRING);
    }

    // ── Enchantment serialisation ─────────────────────────────────────────

    public static String serialiseEnchants(Map<Enchantment, Integer> map) {
        if (map.isEmpty()) return "";
        StringJoiner joiner = new StringJoiner(",");
        map.forEach((e, level) -> joiner.add(e.getKey().getKey().toUpperCase() + ":" + level));
        return joiner.toString();
    }

    public static Map<Enchantment, Integer> deserialiseEnchants(String raw) {
        Map<Enchantment, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String part : raw.split(",")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            Enchantment e = Registry.ENCHANTMENT.get(
                org.bukkit.NamespacedKey.minecraft(kv[0].toLowerCase()));
            if (e == null) continue;
            try { map.put(e, Integer.parseInt(kv[1])); } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    public static Map<Enchantment, Integer> mergeEnchants(
            Map<Enchantment, Integer> a,
            Map<Enchantment, Integer> b,
            boolean excludeCurseOfBinding) {

        Map<Enchantment, Integer> result = new LinkedHashMap<>(a);
        b.forEach((ench, level) -> {
            if (excludeCurseOfBinding && ench.equals(Enchantment.BINDING_CURSE)) return;
            result.merge(ench, level, Math::max);
        });
        return result;
    }

    // ── Damage protection calculation ─────────────────────────────────────

    public static double protectionMultiplier(
            int armorPoints, int toughness,
            Map<Enchantment, Integer> enchants,
            double incomingDmg,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {

        double effectiveDefence = Math.min(20.0,
            Math.max(armorPoints, armorPoints - incomingDmg * 4.0 / (toughness + 8.0)));
        double armorReduction = effectiveDefence / 25.0;

        int epf = 0;
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            epf += enchantEpf(e.getKey(), e.getValue(), cause);
        }
        epf = Math.min(25, epf);
        double enchantReduction = epf * 0.04;

        double total = 1.0 - (1.0 - armorReduction) * (1.0 - enchantReduction);
        return Math.max(0.0, 1.0 - total);
    }

    private static int enchantEpf(Enchantment ench, int level,
                                   org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (ench.equals(Enchantment.PROTECTION)) return level;
        if (ench.equals(Enchantment.FIRE_PROTECTION)) {
            boolean isFire = cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE
                || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA;
            return isFire ? level * 2 : 0;
        }
        if (ench.equals(Enchantment.BLAST_PROTECTION)) {
            boolean isBlast = cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
            return isBlast ? level * 2 : 0;
        }
        if (ench.equals(Enchantment.PROJECTILE_PROTECTION)) {
            boolean isProjectile = cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE;
            return isProjectile ? level * 2 : 0;
        }
        return 0;
    }

    // ── Combined item factory ─────────────────────────────────────────────

    public static ItemStack createCombined(
            ItemStack elytra,
            ItemStack chestplate,
            boolean excludeBinding,
            boolean averageDurability) {

        Material chestMat = chestplate.getType();
        int[] stats = getArmorStats(chestMat);

        Map<Enchantment, Integer> elytraEnchants = new HashMap<>(elytra.getEnchantments());
        Map<Enchantment, Integer> chestEnchants  = new HashMap<>(chestplate.getEnchantments());
        Map<Enchantment, Integer> merged         = mergeEnchants(elytraEnchants, chestEnchants, excludeBinding);

        ItemMeta chestMeta = chestplate.getItemMeta();
        String chestDisplayName = (chestMeta != null && chestMeta.hasDisplayName())
            ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                  .legacySection().serialize(chestMeta.displayName())
            : prettyMaterialName(chestMat);

        ItemStack result = new ItemStack(Material.ELYTRA, 1);
        ItemMeta meta = Objects.requireNonNull(result.getItemMeta());

        // ── Display name ──────────────────────────────────────────────────
        NamedTextColor color = tierColor(chestMat);
        meta.displayName(
            Component.text(tierIcon(chestMat) + " ", color)
                .append(Component.text(chestDisplayName, color))
                .append(Component.text(" ✦ ", NamedTextColor.GOLD))
                .append(Component.text("Elytra", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false)
        );

        // ── Lore ──────────────────────────────────────────────────────────
        Component bar = Component.text("━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(bar);

        // Armor stats with visual bar
        Component statsLine = Component.text("  ⚔ Armor  ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(armorBar(stats[0]))
            .append(Component.text("  +" + stats[0], NamedTextColor.WHITE));
        if (stats[1] > 0) {
            statsLine = statsLine
                .append(Component.text("   ❖ Toughness  ", NamedTextColor.GRAY))
                .append(Component.text("+" + stats[1], NamedTextColor.WHITE));
        }
        lore.add(statsLine);

        lore.add(Component.text("  ◎ Elytra Flight  ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text("Active", NamedTextColor.GREEN)));

        lore.add(bar);

        if (!merged.isEmpty()) {
            lore.add(Component.text("  ✨ Enchantments", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            merged.forEach((ench, level) -> lore.add(
                Component.text("   • ", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(prettyEnchantName(ench) + " " + toRoman(level),
                        NamedTextColor.GRAY))));
            lore.add(bar);
        }

        lore.add(Component.text("  " + prettyMaterialName(chestMat) + " ✦ Elytra",
                NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));

        meta.lore(lore);

        // ── Enchantments on the item itself ───────────────────────────────
        merged.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        // ── Armor attributes — let Minecraft apply damage reduction natively ──
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
            new NamespacedKey("elytraarmor", "armor_points"),
            stats[0],
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.CHEST
        ));
        if (stats[1] > 0) {
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                new NamespacedKey("elytraarmor", "armor_toughness"),
                stats[1],
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.CHEST
            ));
        }

        // ── Always show enchantment glint so the item visually stands out ──
        meta.setEnchantmentGlintOverride(true);

        // ── PDC storage (only what split() needs to reconstruct both items) ──
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.COMBINED,           PersistentDataType.STRING, "true");
        pdc.set(Keys.CHEST_MATERIAL,     PersistentDataType.STRING, chestMat.name());
        pdc.set(Keys.CHEST_ENCHANTS,     PersistentDataType.STRING, serialiseEnchants(chestEnchants));
        pdc.set(Keys.CHEST_DISPLAY_NAME, PersistentDataType.STRING, chestDisplayName);

        result.setItemMeta(meta);

        if (averageDurability) applyAverageDurability(result, elytra, chestplate);

        // ── Equippable component — custom model that shows chestplate + wings ──
        result.setData(DataComponentTypes.EQUIPPABLE,
            Equippable.equippable(EquipmentSlot.CHEST)
                .assetId(Key.key("elytraarmor", tierModelName(chestMat)))
                .equipSound(Key.key("minecraft", "item.armor.equip_elytra"))
                .damageOnHurt(true)
                .build()
        );

        return result;
    }

    private static String tierModelName(Material m) {
        return switch (m) {
            case LEATHER_CHESTPLATE   -> "leather_elytra";
            case CHAINMAIL_CHESTPLATE -> "chainmail_elytra";
            case GOLDEN_CHESTPLATE    -> "golden_elytra";
            case IRON_CHESTPLATE      -> "iron_elytra";
            case DIAMOND_CHESTPLATE   -> "diamond_elytra";
            case NETHERITE_CHESTPLATE -> "netherite_elytra";
            default                    -> "iron_elytra";
        };
    }

    // ── Split back ────────────────────────────────────────────────────────

    public static ItemStack[] split(ItemStack combined) {
        if (!isCombined(combined)) return null;
        ItemMeta combinedMeta = combined.getItemMeta();
        if (combinedMeta == null) return null;

        PersistentDataContainer pdc = combinedMeta.getPersistentDataContainer();
        String matName  = pdc.getOrDefault(Keys.CHEST_MATERIAL,     PersistentDataType.STRING, "DIAMOND_CHESTPLATE");
        String enchRaw  = pdc.getOrDefault(Keys.CHEST_ENCHANTS,     PersistentDataType.STRING, "");
        String dispName = pdc.getOrDefault(Keys.CHEST_DISPLAY_NAME, PersistentDataType.STRING, "");

        Material chestMat = Material.matchMaterial(matName);
        if (chestMat == null) chestMat = Material.DIAMOND_CHESTPLATE;

        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta em = Objects.requireNonNull(elytra.getItemMeta());
        combined.getEnchantments().forEach((ench, level) -> {
            if (ench.equals(Enchantment.UNBREAKING) || ench.equals(Enchantment.MENDING)
                || ench.equals(Enchantment.VANISHING_CURSE)) {
                em.addEnchant(ench, level, true);
            }
        });
        elytra.setItemMeta(em);

        ItemStack chest = new ItemStack(chestMat);
        ItemMeta cm = Objects.requireNonNull(chest.getItemMeta());
        if (!dispName.isEmpty()) {
            cm.displayName(Component.text(dispName, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        }
        Map<Enchantment, Integer> chestEnchants = deserialiseEnchants(enchRaw);
        chestEnchants.forEach((ench, level) -> cm.addEnchant(ench, level, true));
        chest.setItemMeta(cm);

        return new ItemStack[]{elytra, chest};
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Component armorBar(int points) {
        int max = 8;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < max; i++) bar.append(i < points ? "█" : "░");
        TextColor barColor = points >= 8 ? NamedTextColor.GREEN
            : points >= 6 ? NamedTextColor.AQUA
            : points >= 4 ? NamedTextColor.YELLOW
            : NamedTextColor.GRAY;
        return Component.text(bar.toString(), barColor).decoration(TextDecoration.ITALIC, false);
    }

    private static void applyAverageDurability(ItemStack result, ItemStack a, ItemStack b) {
        if (!(result.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm)) return;
        short maxDurability = result.getType().getMaxDurability();
        if (maxDurability == 0) return;

        double pctA = 1.0 - (double) getDamage(a) / Math.max(1, a.getType().getMaxDurability());
        double pctB = 1.0 - (double) getDamage(b) / Math.max(1, b.getType().getMaxDurability());
        int damage = (int) ((1.0 - (pctA + pctB) / 2.0) * maxDurability);
        dm.setDamage(Math.max(0, damage));
        result.setItemMeta((ItemMeta) dm);
    }

    private static int getDamage(ItemStack item) {
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) return d.getDamage();
        return 0;
    }

    private static String prettyMaterialName(Material m) {
        return Arrays.stream(m.name().split("_"))
            .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b)
            .orElse(m.name());
    }

    private static String prettyEnchantName(Enchantment e) {
        return Arrays.stream(e.getKey().getKey().split("_"))
            .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
            .reduce((a, b) -> a + " " + b)
            .orElse(e.getKey().getKey());
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }
}
