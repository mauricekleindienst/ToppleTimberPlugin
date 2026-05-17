package com.elytraarmor.listener;

import com.elytraarmor.ElytraArmorPlugin;
import com.elytraarmor.keys.Keys;
import com.elytraarmor.util.ItemUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GlideListener implements Listener {

    private final ElytraArmorPlugin plugin;

    public GlideListener(ElytraArmorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding()) return;
        if (!plugin.getConfig().getBoolean("glow-on-glide", true)) return;
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack chest = player.getInventory().getChestplate();
        if (!ItemUtil.isCombined(chest)) return;

        ItemMeta meta = chest.getItemMeta();
        if (meta == null) return;
        String matName = meta.getPersistentDataContainer()
            .getOrDefault(Keys.CHEST_MATERIAL, PersistentDataType.STRING, "IRON_CHESTPLATE");
        Material chestMat = Material.matchMaterial(matName);
        if (chestMat == null) chestMat = Material.IRON_CHESTPLATE;

        spawnLaunchEffect(player, chestMat);
    }

    private void spawnLaunchEffect(Player player, Material chestMat) {
        Location base = player.getLocation().add(0, 0.5, 0);

        // Ring of ENCHANT sparkles
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            Location ring = base.clone().add(
                Math.cos(angle) * 0.8, 0, Math.sin(angle) * 0.8);
            player.getWorld().spawnParticle(Particle.ENCHANT, ring, 4, 0.05, 0.15, 0.05, 0.04);
        }

        // Center burst — tier-dependent
        Particle center = switch (chestMat) {
            case NETHERITE_CHESTPLATE -> Particle.SOUL_FIRE_FLAME;
            case DIAMOND_CHESTPLATE   -> Particle.END_ROD;
            case GOLDEN_CHESTPLATE    -> Particle.ENCHANT;
            default                    -> Particle.CRIT;
        };
        int count = chestMat == Material.NETHERITE_CHESTPLATE ? 12 : 6;
        player.getWorld().spawnParticle(center, base, count, 0.25, 0.2, 0.25, 0.04);
    }
}
