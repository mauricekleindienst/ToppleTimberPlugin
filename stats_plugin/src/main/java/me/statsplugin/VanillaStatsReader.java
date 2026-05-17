package me.statsplugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

public class VanillaStatsReader {

    private final File statsDir;
    private final Logger log;

    private volatile List<PlayerStat> cache = List.of();

    public VanillaStatsReader(File worldFolder, Logger log) {
        this.statsDir = new File(worldFolder, "stats");
        this.log = log;
    }

    public List<PlayerStat> getCached() {
        return cache;
    }

    public void refresh() {
        if (!statsDir.exists()) return;

        File[] files = statsDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;

        List<PlayerStat> fresh = new ArrayList<>();

        for (File file : files) {
            String uuidStr = file.getName().replace(".json", "");
            try {
                UUID uuid = UUID.fromString(uuidStr);

                JsonObject root;
                try (FileReader fr = new FileReader(file)) {
                    root = JsonParser.parseReader(fr).getAsJsonObject();
                }

                JsonObject stats = root.getAsJsonObject("stats");
                if (stats == null) continue;

                // ── Blocks mined ──────────────────────────────────────────
                long totalMined = 0;
                String topBlock = "—";
                long topCount = 0;
                JsonObject minedObj = stats.getAsJsonObject("minecraft:mined");
                if (minedObj != null) {
                    for (Map.Entry<String, JsonElement> e : minedObj.entrySet()) {
                        long v = e.getValue().getAsLong();
                        totalMined += v;
                        if (v > topCount) {
                            topCount = v;
                            topBlock = fmtBlock(e.getKey().replace("minecraft:", ""));
                        }
                    }
                }

                // ── Custom stats ──────────────────────────────────────────
                JsonObject custom = stats.getAsJsonObject("minecraft:custom");
                long playtimeTicks = getCustom(custom, "minecraft:play_one_minute");
                long deaths        = getCustom(custom, "minecraft:deaths");

                // ── Mob kills ─────────────────────────────────────────────
                long kills = 0;
                JsonObject killed = stats.getAsJsonObject("minecraft:killed");
                if (killed != null) {
                    for (JsonElement e : killed.asMap().values()) kills += e.getAsLong();
                }

                // ── Player name ───────────────────────────────────────────
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = uuidStr.substring(0, 8) + "…";

                fresh.add(new PlayerStat(uuid, name, totalMined, topBlock,
                                         playtimeTicks / 20, deaths, kills));

            } catch (Exception ex) {
                log.warning("Could not read stats for " + uuidStr + ": " + ex.getMessage());
            }
        }

        fresh.sort(Comparator.comparingLong(PlayerStat::totalMined).reversed());
        cache = List.copyOf(fresh);
    }

    private static long getCustom(JsonObject custom, String key) {
        if (custom == null) return 0;
        JsonElement el = custom.get(key);
        return el != null ? el.getAsLong() : 0;
    }

    private static String fmtBlock(String raw) {
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }
}
