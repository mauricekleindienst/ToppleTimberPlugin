package com.elytraarmor;

import com.elytraarmor.keys.Keys;
import com.elytraarmor.listener.AnvilListener;
import com.elytraarmor.listener.GlideListener;
import com.elytraarmor.listener.PackJoinListener;
import com.elytraarmor.listener.StonecutterListener;
import com.elytraarmor.pack.ResourcePackServer;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElytraArmorPlugin extends JavaPlugin {

    private ResourcePackServer packServer;
    private String   packUrl;
    private byte[]   packSha1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        // Resource pack HTTP server
        int    port = getConfig().getInt("pack-port", 8085);
        String host = getConfig().getString("pack-host", "localhost");
        try {
            packServer = new ResourcePackServer(port);
            packUrl    = "http://" + host + ":" + port + "/pack.zip";
            packSha1   = packServer.getSha1();
            getLogger().info("Resource pack served at " + packUrl);
        } catch (Exception e) {
            getLogger().warning("Could not start resource pack server: " + e.getMessage()
                + " — equipment visuals will not load for players.");
        }

        getServer().getPluginManager().registerEvents(new AnvilListener(this), this);
        getServer().getPluginManager().registerEvents(new GlideListener(this), this);
        getServer().getPluginManager().registerEvents(new StonecutterListener(this), this);
        getServer().getPluginManager().registerEvents(new PackJoinListener(this), this);

        // Push pack to any players already online (e.g. after /reload)
        if (packUrl != null) {
            for (var player : getServer().getOnlinePlayers()) {
                player.setResourcePack(packUrl, packSha1, "ElytraArmor equipment models", false);
            }
        }

        getLogger().info("ElytraArmor enabled — combine Elytra + chestplate for flight & protection!");
    }

    @Override
    public void onDisable() {
        if (packServer != null) packServer.stop();
        getLogger().info("ElytraArmor disabled.");
    }

    public String getPackUrl()  { return packUrl; }
    public byte[] getPackSha1() { return packSha1; }
}
