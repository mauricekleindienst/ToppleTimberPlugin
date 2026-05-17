package com.elytraarmor.listener;

import com.elytraarmor.ElytraArmorPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PackJoinListener implements Listener {

    private final ElytraArmorPlugin plugin;

    public PackJoinListener(ElytraArmorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String url  = plugin.getPackUrl();
        byte[] sha1 = plugin.getPackSha1();
        if (url == null || sha1 == null) return;
        player.setResourcePack(url, sha1, "ElytraArmor equipment models", false);
    }
}
