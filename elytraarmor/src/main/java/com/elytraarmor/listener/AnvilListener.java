package com.elytraarmor.listener;

import com.elytraarmor.ElytraArmorPlugin;
import com.elytraarmor.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public class AnvilListener implements Listener {

    private final ElytraArmorPlugin plugin;

    public AnvilListener(ElytraArmorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil-combine", true)) return;

        AnvilInventory inv = event.getInventory();
        ItemStack left  = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || right == null) return;

        ItemStack elytra     = null;
        ItemStack chestplate = null;

        if (left.getType() == Material.ELYTRA && ItemUtil.isChestplate(right.getType())) {
            elytra = left;  chestplate = right;
        } else if (right.getType() == Material.ELYTRA && ItemUtil.isChestplate(left.getType())) {
            elytra = right; chestplate = left;
        } else {
            return;
        }

        AnvilView view = event.getView();

        if (ItemUtil.isCombined(elytra)) {
            event.setResult(null);
            view.setRepairCost(0);
            return;
        }

        boolean excludeBinding = plugin.getConfig().getBoolean("exclude-curse-of-binding", true);
        boolean avgDur         = plugin.getConfig().getBoolean("average-durability", true);

        ItemStack combined = ItemUtil.createCombined(elytra, chestplate, excludeBinding, avgDur);
        event.setResult(combined);

        view.setRepairCost(0);
    }
}
