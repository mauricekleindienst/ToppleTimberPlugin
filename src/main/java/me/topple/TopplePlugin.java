package me.topple;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class TopplePlugin extends JavaPlugin implements CommandExecutor {

    private NamespacedKey toppleKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        toppleKey = new NamespacedKey(this, "topple_falling");
        getServer().getPluginManager().registerEvents(new TreeListener(this), this);

        var cmd = getCommand("topple");
        if (cmd != null) cmd.setExecutor(this);

        getLogger().info("Topple enabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("topple.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§aTopple: config reloaded.");
            return true;
        }
        sender.sendMessage("§eUsage: /topple reload");
        return true;
    }

    public NamespacedKey getToppleKey() {
        return toppleKey;
    }
}
