package me.statsplugin;

import java.util.Map;
import java.util.UUID;

public record PlayerStat(
    UUID uuid,
    String name,
    long totalMined,
    String topBlock,
    long playtimeSeconds,
    long deaths,
    long kills
) {}
