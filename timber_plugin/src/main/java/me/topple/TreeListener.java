package me.topple;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Topple — realistic tree-felling for Paper 26.1+
 *
 * Chop the base of a natural tree with an axe and the whole tree falls:
 * logs arc in the direction you were facing, the canopy explodes outward,
 * and any clinging vines strip away. All loot drops at the stump.
 */
@SuppressWarnings("deprecation") // some Bukkit sound/enchantment APIs are in flux across Paper builds
public class TreeListener implements Listener {

    private final TopplePlugin plugin;

    /** Per-player last-fell timestamp (ms). Cleaned on quit to prevent map growth. */
    private final Map<UUID, Long> lastFellTime = new HashMap<>();

    /** The six axis-aligned faces — used for vine/lichen detection. */
    private static final BlockFace[] CARDINAL = {
        BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST
    };

    /**
     * Resolved once at class-load so we never call the deprecated
     * {@code Enchantment.UNBREAKING} constant at runtime.
     */
    private static final Enchantment UNBREAKING =
            Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));

    // Blocks that natural trees grow from; player structures rest on planks / stone-brick / etc.
    private static final Set<Material> NATURAL_GROUND = Set.of(
        Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL,
        Material.MYCELIUM, Material.MUD, Material.ROOTED_DIRT, Material.FARMLAND,
        Material.MOSS_BLOCK, Material.MUDDY_MANGROVE_ROOTS, Material.MANGROVE_ROOTS,
        Material.SAND, Material.RED_SAND, Material.GRAVEL,
        Material.STONE, Material.DEEPSLATE, Material.GRANITE, Material.DIORITE,
        Material.ANDESITE, Material.TUFF, Material.CALCITE,
        Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL,
        Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.BASALT, Material.BLACKSTONE,
        Material.SNOW_BLOCK, Material.ICE, Material.CLAY, Material.DIRT_PATH,
        Material.SUSPICIOUS_GRAVEL, Material.SUSPICIOUS_SAND
    );

    public TreeListener(TopplePlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Entry point
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        if (!Tag.LOGS.isTagged(broken.getType())) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("topple.use")) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (plugin.getConfig().getBoolean("require-axe", true) && !isAxe(held.getType())) return;
        if (plugin.getConfig().getBoolean("sneak-disables", true) && player.isSneaking()) return;

        if (plugin.getConfig().getStringList("disabled-worlds").contains(broken.getWorld().getName()))
            return;

        // Per-player cooldown
        long cooldownMs = plugin.getConfig().getLong("cooldown-ms", 100);
        if (cooldownMs > 0) {
            long now = System.currentTimeMillis();
            Long last = lastFellTime.get(player.getUniqueId());
            if (last != null && now - last < cooldownMs) return;
            lastFellTime.put(player.getUniqueId(), now);
        }

        int maxLogs = plugin.getConfig().getInt("max-logs", 0);
        Set<Block> logs = findConnectedLogs(broken, maxLogs);
        if (logs.size() <= 1) return;

        int minY = logs.stream().mapToInt(Block::getY).min().orElse(broken.getY());
        if (broken.getY() > minY + plugin.getConfig().getInt("max-break-height", 3)) return;

        // Gather leaves + vines before validation (we need the leaf count for the tree check)
        Set<Block> leaves = findConnectedLeaves(logs);
        Set<Block> vines  = plugin.getConfig().getBoolean("strip-vines", true)
                            ? findConnectedVines(logs) : Set.of();

        // ── Tree vs. structure guards ──────────────────────────────────────────
        if (leaves.size() < plugin.getConfig().getInt("min-leaves", 5)) return;
        if (plugin.getConfig().getBoolean("require-natural-root", true)
                && !isRootedNaturally(logs, minY)) return;

        event.setDropItems(false);
        logs.remove(broken); // broken block is handled separately in fellTree()

        boolean dropItems  = player.getGameMode() != GameMode.CREATIVE;
        ItemStack captured = held.clone(); // fortune/durability evaluated at chop time

        fellTree(logs, leaves, vines, broken, getFallDirection(player), dropItems, player, captured);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cooldown cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastFellTime.remove(event.getPlayer().getUniqueId());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FallingBlock landing — vanish visual entities without placing or dropping
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler(ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (!fb.getPersistentDataContainer().has(plugin.getToppleKey(), PersistentDataType.BOOLEAN))
            return;
        event.setCancelled(true);
        fb.remove();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Core tree-fall logic
    // ═══════════════════════════════════════════════════════════════════════════

    private void fellTree(Set<Block> logs, Set<Block> leaves, Set<Block> vines,
                          Block base, Vector fallDir, boolean dropItems,
                          Player player, ItemStack capturedTool) {

        World   world    = base.getWorld();
        int     baseY    = base.getY();
        double  fallMult = plugin.getConfig().getDouble("fall-multiplier", 0.07);
        boolean hurtEnt  = plugin.getConfig().getBoolean("hurt-entities",  false);
        boolean doDmgAxe = plugin.getConfig().getBoolean("damage-axe",     true);
        int     maxVisL  = plugin.getConfig().getInt("max-visual-leaves",   120);

        // ── Step 1: drop the base block's item NOW, while the block type is still valid
        //    (Minecraft sets it to AIR after this event handler returns)
        if (dropItems) {
            world.dropItemNaturally(
                    base.getLocation().clone().add(0.5, 0.5, 0.5),
                    new ItemStack(base.getType()));
        }

        // Single sharp crack at the stump — heard by everyone nearby
        world.playSound(base.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.5f, 0.8f);

        // ── Step 2: prepare animation data ────────────────────────────────────
        Vector perpDir = new Vector(-fallDir.getZ(), 0, fallDir.getX());

        // Limit visual leaf entities; all leaves still drop loot via breakNaturally()
        List<Block> shuffled = new ArrayList<>(leaves);
        Collections.shuffle(shuffled);
        Set<Block> visualLeaves = new HashSet<>(
                shuffled.subList(0, Math.min(maxVisL, shuffled.size())));

        Map<Integer, List<Block>> logsByH   = buildHeightMap(logs,   baseY);
        Map<Integer, List<Block>> leavesByH = buildHeightMap(leaves, baseY);
        Map<Integer, List<Block>> vinesByH  = buildHeightMap(vines,  baseY);

        int maxH = 0;
        if (!logsByH.isEmpty())   maxH = Math.max(maxH, Collections.max(logsByH.keySet()));
        if (!leavesByH.isEmpty()) maxH = Math.max(maxH, Collections.max(leavesByH.keySet()));
        final int finalMaxH = maxH;

        // ── Step 3: staggered LOG fall ─────────────────────────────────────────
        // delay = min(height/3, 5) ticks so even a 30-block tree is fully tipping
        // within 5 ticks (0.25 s), minimising the "floating trunk" window.
        logsByH.forEach((height, row) ->
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block log : row) {
                    if (!Tag.LOGS.isTagged(log.getType())) continue;
                    BlockData data   = log.getBlockData();
                    Location  center = log.getLocation().clone().add(0.5, 0.5, 0.5);

                    if (dropItems) world.dropItemNaturally(center, new ItemStack(log.getType()));
                    if (dropItems && doDmgAxe && player.isOnline()) damageAxe(player);

                    world.spawnParticle(Particle.BLOCK, center, 10, 0.3, 0.3, 0.3, data);
                    log.setType(Material.AIR, false);

                    spawnToppleEntity(world, center, data,
                            fallDir.clone().multiply(0.02 + height * fallMult)
                                   .add(perpDir.clone().multiply(jitter(0.025)))
                                   .setY(-0.02 + height * 0.004),
                            hurtEnt);
                }
            }, staggerDelay(height))
        );

        // ── Step 4: staggered LEAF fall ────────────────────────────────────────
        // breakNaturally(capturedTool) rolls the vanilla loot table with the
        // player's Fortune level → saplings and apples benefit.
        leavesByH.forEach((height, row) ->
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block leaf : row) {
                    if (!Tag.LEAVES.isTagged(leaf.getType())) continue;
                    BlockData data   = leaf.getBlockData();
                    Location  center = leaf.getLocation().clone().add(0.5, 0.5, 0.5);

                    if (dropItems) leaf.breakNaturally(capturedTool);
                    else           leaf.setType(Material.AIR, false);

                    world.spawnParticle(Particle.BLOCK, center, 5, 0.4, 0.4, 0.4, data);

                    if (visualLeaves.contains(leaf)) {
                        spawnToppleEntity(world, center, data,
                                fallDir.clone().multiply(0.01 + Math.max(0, height) * fallMult * 0.5)
                                       .add(perpDir.clone().multiply(jitter(0.12)))
                                       .setY(ThreadLocalRandom.current().nextDouble(-0.04, 0.10)),
                                false);
                    }
                }
            }, staggerDelay(height))
        );

        // ── Step 5: staggered VINE / LICHEN strip ─────────────────────────────
        // Vines and glow lichen cling to the bark and peel off as the tree falls.
        // They drop nothing without shears — visual only.
        vinesByH.forEach((height, row) ->
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Block vine : row) {
                    if (!isClingBlock(vine)) continue;
                    BlockData data   = vine.getBlockData();
                    Location  center = vine.getLocation().clone().add(0.5, 0.5, 0.5);

                    world.spawnParticle(Particle.BLOCK, center, 4, 0.3, 0.3, 0.3, data);
                    vine.setType(Material.AIR, false);

                    spawnToppleEntity(world, center, data,
                            fallDir.clone().multiply(0.01 + Math.max(0, height) * fallMult * 0.4)
                                   .add(perpDir.clone().multiply(jitter(0.08)))
                                   .setY(ThreadLocalRandom.current().nextDouble(-0.03, 0.07)),
                            false);
                }
            }, staggerDelay(height))
        );

        // ── Step 6: impact thud ────────────────────────────────────────────────
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> world.playSound(base.getLocation(), Sound.BLOCK_WOOD_STEP, 3.0f, 0.4f),
                20L + finalMaxH / 3L);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Tree vs. structure detection
    // ═══════════════════════════════════════════════════════════════════════════

    /** At least one of the lowest logs must rest on natural terrain. */
    private boolean isRootedNaturally(Set<Block> logs, int minY) {
        return logs.stream()
                .filter(b -> b.getY() == minY)
                .anyMatch(b -> NATURAL_GROUND.contains(b.getRelative(0, -1, 0).getType()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Log BFS
    // ═══════════════════════════════════════════════════════════════════════════

    private Set<Block> findConnectedLogs(Block start, int cap) {
        Set<Block> found = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        found.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            if (cap > 0 && found.size() >= cap) break;
            Block cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = cur.getRelative(dx, dy, dz);
                        if (Tag.LOGS.isTagged(n.getType()) && found.add(n)) queue.add(n);
                    }
        }
        return found;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Leaf BFS — honours Minecraft's leaf-distance model
    //
    //  Two rules stop the search from bleeding into neighbouring trees:
    //   1. DISTANCE BOUND  — a leaf stores the shortest path to its nearest log.
    //      If our BFS reaches it at depth D > storedDistance, a foreign log is
    //      closer, so we leave that leaf alone.
    //   2. FOREIGN-LOG FENCE — if a candidate leaf is adjacent (any of 26
    //      neighbours) to a log not in our set, it borders another tree.
    //   3. PERSISTENT LEAVES — player-placed leaves are never touched.
    // ═══════════════════════════════════════════════════════════════════════════

    private Set<Block> findConnectedLeaves(Set<Block> logs) {
        Set<Block> result = new HashSet<>();
        Map<Block, Integer> dist = new HashMap<>();
        Deque<Block> queue = new ArrayDeque<>();

        for (Block log : logs)
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = log.getRelative(dx, dy, dz);
                        if (!isNonPersistentLeaf(n)) continue;
                        if (isAdjacentToForeignLog(n, logs)) continue;
                        if (dist.put(n, 1) == null) { result.add(n); queue.add(n); }
                    }

        while (!queue.isEmpty()) {
            Block cur  = queue.poll();
            int   curD = dist.get(cur);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = cur.getRelative(dx, dy, dz);
                        if (!isNonPersistentLeaf(n) || dist.containsKey(n)) continue;
                        int newD = curD + 1;
                        if (newD > leafDistance(n)) continue;
                        if (isAdjacentToForeignLog(n, logs)) continue;
                        dist.put(n, newD); result.add(n); queue.add(n);
                    }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Vine / glow-lichen BFS
    //  Seeds from the 6 cardinal faces of every log, then follows vine chains
    //  downward (vines grow in vertical columns hanging from bark).
    // ═══════════════════════════════════════════════════════════════════════════

    private Set<Block> findConnectedVines(Set<Block> logs) {
        Set<Block> result = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();

        for (Block log : logs)
            for (BlockFace face : CARDINAL) {
                Block n = log.getRelative(face);
                if (isClingBlock(n) && result.add(n)) queue.add(n);
            }

        // Vines extend downward in chains; follow each one to the ground.
        while (!queue.isEmpty()) {
            Block cur = queue.poll();
            if (cur.getType() == Material.VINE) {
                Block below = cur.getRelative(BlockFace.DOWN);
                if (below.getType() == Material.VINE && result.add(below)) queue.add(below);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Tool durability — 1 damage per log, respects Unbreaking
    // ═══════════════════════════════════════════════════════════════════════════

    private void damageAxe(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isAxe(item.getType())) return;
        if (!(item.getItemMeta() instanceof Damageable meta)) return;
        if (item.getType().getMaxDurability() <= 0) return;

        int level = UNBREAKING != null ? item.getEnchantmentLevel(UNBREAKING) : 0;
        if (level > 0 && ThreadLocalRandom.current().nextInt(level + 1) != 0) return;

        int newDmg = meta.getDamage() + 1;
        if (newDmg >= item.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            meta.setDamage(newDmg);
            item.setItemMeta(meta);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Spawn a tagged FallingBlock entity that vanishes on landing (no placement, no drop). */
    private void spawnToppleEntity(World world, Location center,
                                   BlockData data, Vector velocity, boolean hurtEntities) {
        FallingBlock fb = world.spawnFallingBlock(center, data);
        fb.setVelocity(velocity);
        fb.setDropItem(false);
        fb.setHurtEntities(hurtEntities);
        fb.getPersistentDataContainer().set(plugin.getToppleKey(), PersistentDataType.BOOLEAN, true);
    }

    /** Groups a block collection by Y-height relative to a base Y value. */
    private Map<Integer, List<Block>> buildHeightMap(Set<Block> blocks, int baseY) {
        Map<Integer, List<Block>> map = new TreeMap<>();
        for (Block b : blocks)
            map.computeIfAbsent(b.getY() - baseY, k -> new ArrayList<>()).add(b);
        return map;
    }

    /**
     * Stagger delay in ticks for a given height above the base.
     * Capped at 5 ticks so even a 30-block tree fully tips within 0.25 s.
     */
    private long staggerDelay(int height) {
        return Math.min(Math.max(0L, height / 3L), 5L);
    }

    /** Random sideways offset for natural-looking scatter. */
    private double jitter(double max) {
        return ThreadLocalRandom.current().nextDouble(-max, max);
    }

    private boolean isNonPersistentLeaf(Block b) {
        if (!Tag.LEAVES.isTagged(b.getType())) return false;
        if (b.getBlockData() instanceof Leaves ld) return !ld.isPersistent();
        return true;
    }

    private int leafDistance(Block b) {
        if (b.getBlockData() instanceof Leaves ld) return ld.getDistance();
        return 7;
    }

    private boolean isAdjacentToForeignLog(Block b, Set<Block> ourLogs) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block n = b.getRelative(dx, dy, dz);
                    if (Tag.LOGS.isTagged(n.getType()) && !ourLogs.contains(n)) return true;
                }
        return false;
    }

    /** True for VINE and GLOW_LICHEN — the clinging plants that grow on bark. */
    private boolean isClingBlock(Block b) {
        return b.getType() == Material.VINE || b.getType() == Material.GLOW_LICHEN;
    }

    private Vector getFallDirection(Player player) {
        Vector dir = player.getLocation().getDirection().clone();
        dir.setY(0);
        if (dir.lengthSquared() < 0.001) dir.setX(1);
        return dir.normalize();
    }

    private boolean isAxe(Material mat) {
        return switch (mat) {
            case WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }
}
