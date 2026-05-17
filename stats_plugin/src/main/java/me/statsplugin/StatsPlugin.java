package me.statsplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class StatsPlugin extends JavaPlugin {

    private VanillaStatsReader reader;
    private WebServer webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        var worldFolder = getServer().getWorlds().get(0).getWorldFolder();
        reader = new VanillaStatsReader(worldFolder, getLogger());

        // Initial load async so startup isn't blocked
        getServer().getScheduler().runTaskAsynchronously(this, reader::refresh);

        // Refresh stats every 60 seconds
        getServer().getScheduler().runTaskTimerAsynchronously(this, reader::refresh, 1200L, 1200L);

        int port = getConfig().getInt("web-port", 8080);
        String bind = getConfig().getString("bind-address", "0.0.0.0");
        webServer = new WebServer(this, reader, bind, port);
        try {
            webServer.start();
            getLogger().info("Stats dashboard → http://" + bind + ":" + port);
        } catch (IOException e) {
            getLogger().severe("Failed to start web server: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) webServer.stop();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            getServer().getScheduler().runTaskAsynchronously(this, reader::refresh);
            sender.sendMessage("§aStats reloaded.");
            return true;
        }
        int port = getConfig().getInt("web-port", 8080);
        sender.sendMessage("§6Stats Dashboard: §bhttp://localhost:" + port);
        return true;
    }
}
