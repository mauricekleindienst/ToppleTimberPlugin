package com.elytraarmor.listener;

import com.elytraarmor.ElytraArmorPlugin;
import com.elytraarmor.util.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StonecutterListener implements Listener {

    private final ElytraArmorPlugin plugin;
    // track active tasks so we can cancel them if the player logs off
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public StonecutterListener(ElytraArmorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (!ItemUtil.isCombined(dropped.getItemStack())) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Cancel any existing tracking task for this player
        BukkitTask existing = activeTasks.remove(playerId);
        if (existing != null) existing.cancel();

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;

                // Give up: timeout, item gone, or player offline
                if (ticks > 120 || dropped.isDead() || !player.isOnline()) {
                    activeTasks.remove(playerId);
                    cancel();
                    return;
                }

                if (!dropped.isOnGround()) return;

                // Check stonecutter at item position or one block below
                Block at = dropped.getLocation().getBlock();
                Block stonecutter = null;
                if (at.getType() == Material.STONECUTTER) {
                    stonecutter = at;
                } else {
                    Block below = at.getRelative(BlockFace.DOWN);
                    if (below.getType() == Material.STONECUTTER) stonecutter = below;
                }

                if (stonecutter == null) return;

                activeTasks.remove(playerId);
                cancel();

                if (!plugin.getConfig().getBoolean("allow-split", true)) {
                    player.sendMessage(Component.text("✗ Splitting is disabled on this server.", NamedTextColor.RED));
                    return;
                }

                ItemStack[] parts = ItemUtil.split(dropped.getItemStack());
                if (parts == null) {
                    player.sendMessage(Component.text("✗ Split failed — item data may be corrupted.", NamedTextColor.RED));
                    return;
                }

                dropped.remove();

                var spawnLoc = stonecutter.getLocation().add(0.5, 1.1, 0.5);
                dropped.getWorld().dropItemNaturally(spawnLoc, parts[0]);
                dropped.getWorld().dropItemNaturally(spawnLoc, parts[1]);

                stonecutter.getWorld().playSound(
                    stonecutter.getLocation(), Sound.UI_STONECUTTER_TAKE_RESULT, 1.0f, 1.2f);

                player.sendMessage(Component.text("✦ Elytra and chestplate separated!", NamedTextColor.GREEN));
            }
        }.runTaskTimer(plugin, 3L, 3L);

        activeTasks.put(playerId, task);
    }
}
