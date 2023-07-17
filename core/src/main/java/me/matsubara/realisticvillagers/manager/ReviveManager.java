package me.matsubara.realisticvillagers.manager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.customblockdata.CustomBlockData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ReviveManager implements Listener {

    private final RealisticVillagers plugin;
    private final @Getter Map<Block, MonumentAnimation> runningTasks = new HashMap<>();
    private final Set<UUID> ignoreDead = new HashSet<>();

    private static final Set<EntityDamageEvent.DamageCause> CAN_NOT_REVIVE = Sets.immutableEnumSet(
            EntityDamageEvent.DamageCause.SUFFOCATION,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.WORLD_BORDER);
    private static final BlockFace[] MONUMENT = {
            BlockFace.NORTH_EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_WEST,
            BlockFace.NORTH_WEST};

    public ReviveManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        if (Config.REVIVE_ENABLED.asBool()) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    public boolean isDay(@NotNull World world) {
        long time = world.getTime();
        return time < 13000 || time > 23000;
    }

    private boolean handleMonument(Player player, Block block) {
        // Already running.
        if (runningTasks.containsKey(block)) return false;

        CustomBlockData data = new CustomBlockData(block, plugin);

        // Shouldn't be null at this point, just checking to remove warning.
        String tag = data.get(plugin.getNpcValuesKey(), PersistentDataType.STRING);
        if (tag == null) return false;

        IVillagerNPC npc = plugin.getConverter().getNPCFromTag(tag);
        if (npc == null) return false;

        // Under head, should be an emerald block.
        Block downBlock = block.getRelative(BlockFace.DOWN);
        if (downBlock.getType() != Material.EMERALD_BLOCK) return false;

        // Sides of monument, should be 2 emerald blocks with fire at the top.
        for (BlockFace face : MONUMENT) {
            Block upBlock = block.getRelative(BlockFace.UP);
            if (upBlock.getRelative(face, 2).getType() != Material.FIRE) return false;
            if (block.getRelative(face, 2).getType() != Material.EMERALD_BLOCK) return false;
            if (downBlock.getRelative(face, 2).getType() != Material.EMERALD_BLOCK) return false;
        }

        // Villager already exists, cancel to prevent duplicated entity.
        for (IVillagerNPC offline : plugin.getTracker().getOfflineVillagers()) {
            if (offline.getUniqueId().equals(npc.getUniqueId())) {
                plugin.getMessages().send(player, Messages.Message.INTERACT_FAIL_ALREADY_ALIVE);
                return false;
            }
        }

        runningTasks.put(block, new MonumentAnimation(tag, block));
        return true;
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (MonumentAnimation animation : runningTasks.values()) {
            BossBar display = animation.getDisplay();
            if (display != null && display.getPlayers().contains(player)) {
                display.removePlayer(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(@NotNull EntityTransformEvent event) {
        EntityTransformEvent.TransformReason reason = event.getTransformReason();
        if (reason != EntityTransformEvent.TransformReason.INFECTION
                && reason != EntityTransformEvent.TransformReason.LIGHTNING) return;

        if (!(event.getEntity() instanceof Villager villager)) return;

        Entity transformed = event.getTransformedEntity();
        if (!(transformed instanceof Witch)
                && !(transformed instanceof ZombieVillager)) return;

        // Ignore villagers converted to ZOMBIE_VILLAGER or WITCH.
        if (!plugin.getTracker().isInvalid(villager, true)) {
            ignoreDead.add(villager.getUniqueId());
        }
    }

    @EventHandler
    public void onVillagerRemove(@NotNull VillagerRemoveEvent event) {
        VillagerRemoveEvent.RemovalReason reason = event.getReason();
        if (reason != VillagerRemoveEvent.RemovalReason.KILLED
                && reason != VillagerRemoveEvent.RemovalReason.DISCARDED) return;

        Villager bukkit = event.getNPC().bukkit();
        if (bukkit == null || !bukkit.isDead()) return;

        handleTombstone(bukkit);
    }

    private void handleTombstone(Villager villager) {
        Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(villager);
        if (npc.isEmpty()) return;

        EntityDamageEvent damage = villager.getLastDamageCause();
        if (damage != null && CAN_NOT_REVIVE.contains(damage.getCause())) {
            return;
        }

        if (ignoreDead.remove(villager.getUniqueId())) return;

        if (Config.REVIVE_ONLY_WITH_CROSS.asBool()
                && !PluginUtils.hasAnyOf(villager, plugin.getIsCrossKey())) return;

        ItemStack head = createHeadItem(npc.get(), plugin.getConverter().getNPCTag(villager, false));

        // Drop head at villager death location.
        villager.getWorld().dropItemNaturally(
                villager.getLocation(),
                head);
    }

    public ItemStack createHeadItem(IVillagerNPC npc, String tag) {
        return plugin.getItem("revive.head-item", npc)
                .replace("%villager-name%", npc.getVillagerName())
                .setData(plugin.getNpcValuesKey(), PersistentDataType.STRING, tag)
                .build();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(@NotNull BlockIgniteEvent event) {
        // We need to cancel block ignite in the last 2 strikes to prevent the villager burning.
        Entity source;
        if (event.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING
                && (source = event.getIgnitingEntity()) != null
                && source.hasMetadata("LastStages")) {
            event.setCancelled(true);
        }

        Block block = event.getBlock().getRelative(BlockFace.DOWN);
        if (block.getType() != Material.EMERALD_BLOCK
                || (Config.REVIVE_ONLY_AT_NIGHT.asBool()) && isDay(block.getWorld())) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> checkForMonument(event.getPlayer(), block));
    }

    private void checkForMonument(Player player, @NotNull Block block) {
        if (block.getRelative(BlockFace.UP).getType() != Material.FIRE) return;

        // Look for center first.
        for (BlockFace face : MONUMENT) {
            Block relative = block.getRelative(face, 2);
            if (relative.getType() != Material.PLAYER_HEAD) continue;

            CustomBlockData data = new CustomBlockData(relative, plugin);
            if (!data.has(plugin.getNpcValuesKey(), PersistentDataType.STRING)) continue;

            // Found the center (probably).
            if (handleMonument(player, relative)) break;
        }
    }

    @Getter
    public class MonumentAnimation extends BukkitRunnable {

        private final String tag;
        private final Block block;
        private final @Nullable BossBar display;
        private final float spawnYaw;
        private final BlockFace[] fixedMonument;

        private int stage = 0;
        private int count = 0;
        private int total = 0;
        private boolean isIncrement;

        private static final double BAR_RENDER_DISTANCE = 30.0d;
        private static final int[] STAGES = {20, 20, 20, 20, 20, 6, 6, 6, 6};
        private static final int LAST_STAGE = STAGES.length - 1;
        private static final int HEAD_STAGE = 4;

        private MonumentAnimation(String tag, @NotNull Block block) {
            this.tag = tag;
            this.block = block;
            this.display = initializeBossBar();

            BlockFace facing;
            BlockData data = block.getBlockData();
            if (data instanceof Directional directional) {
                facing = directional.getFacing();
            } else if (data instanceof Rotatable rotatable) {
                facing = rotatable.getRotation();
            } else throw new NullPointerException("Invalid monument block!");
            this.spawnYaw = PluginUtils.faceToYaw(facing);

            BlockFace face = PluginUtils.yawToFace(spawnYaw, 0x3);
            this.fixedMonument = getRotatedArray(MONUMENT, face != null ? face.ordinal() : 0);

            // Refresh display and initialize runnable.
            refreshDisplay(block);
            runTaskTimer(plugin, 0L, 1L);
        }

        private @Nullable BossBar initializeBossBar() {
            if (!Config.REVIVE_BOSSBAR_ENABLED.asBool()) return null;

            String name = plugin.getConverter().getNPCFromTag(tag).getVillagerName();
            String title = Config.REVIVE_BOSSBAR_TITLE.asStringTranslated(name).replace("%villager-name%", name);

            BossBar display = Bukkit.createBossBar(
                    title,
                    PluginUtils.getOrDefault(BarColor.class, Config.REVIVE_BOSSBAR_COLOR.asString(), BarColor.RED),
                    PluginUtils.getOrDefault(BarStyle.class, Config.REVIVE_BOSSBAR_STYLE.asString(), BarStyle.SOLID),
                    Config.REVIVE_BOSSBAR_FLAGS.asStringList()
                            .stream()
                            .map(string -> PluginUtils.getOrNull(BarFlag.class, string))
                            .filter(Objects::nonNull)
                            .toArray(BarFlag[]::new));

            String progressType = Config.REVIVE_BOSSBAR_PROGRESS_TYPE.asString("INCREASE");
            if (progressType.equalsIgnoreCase("DECREASE")) {
                display.setProgress(1.0d);
                isIncrement = false;
            } else {
                if (!progressType.equalsIgnoreCase("INCREASE")) {
                    plugin.getLogger().warning("Invalid @progress-type! Using INCREASE.");
                }
                display.setProgress(0.0d);
                isIncrement = true;
            }

            return display;
        }

        @Override
        public void run() {
            // Not in a stage, update counter and return.
            if (count != STAGES[stage]) {
                count++;
                total++;
                return;
            }

            // Refresh display in every stage.
            refreshDisplay(block);

            // Get location to spawn a lightning or summon particles.
            Location target = (stage >= HEAD_STAGE ? block : block.getRelative(fixedMonument[stage], 2)).getLocation();

            // Spawn ligtning at the middle of the target (Y+1 offset for emerald blocks).
            World world = block.getWorld();
            world.spawn(
                    target.clone().add(0.5d, stage >= HEAD_STAGE ? 0.0d : 1.0d, 0.5d),
                    LightningStrike.class,
                    lightning -> {
                        FixedMetadataValue value = new FixedMetadataValue(plugin, true);
                        lightning.setMetadata("FromMonument", value);
                        if (stage > HEAD_STAGE) lightning.setMetadata("LastStages", value);
                    });

            // Spawn angry particles above the lightning flame.
            ThreadLocalRandom random = ThreadLocalRandom.current();
            world.spawnParticle(
                    Particle.VILLAGER_ANGRY,
                    target.clone().add(0.5d, 1.5d, 0.5d),
                    1,
                    random.nextGaussian() * 0.02d,
                    random.nextGaussian() * 0.02d,
                    random.nextGaussian() * 0.02d);

            // Break (top) emerald blocks and head.
            if (stage == HEAD_STAGE || (stage < HEAD_STAGE && random.nextFloat() < Config.REVIVE_BREAK_EMERALD_CHANCE.asFloat())) {
                world.setType(target, Material.AIR);
            }

            if (display != null) {
                // Update progress bar.
                float progress;
                if (isIncrement) {
                    progress = Math.min(HEAD_STAGE, (stage + 1)) / (float) HEAD_STAGE;
                } else {
                    progress = Math.max(0, (HEAD_STAGE - stage - 1)) / (float) HEAD_STAGE;
                }
                display.setProgress(progress);
            }

            // Not the latest stage, increment/reset counters.
            if (stage != LAST_STAGE) {
                stage++;
                count = 1;
                total++;
                return;
            }

            // Remove health bar and spawn villager.
            handleSpawning();

            // Cancel task and remove from cache.
            cancel();
            runningTasks.remove(block);
        }

        private void handleSpawning() {
            if (display != null) {
                display.setVisible(false);
                display.removeAll();
            }

            // Remove fire from monument before spawning villager.
            if (Tag.FIRE.isTagged(block.getType())) block.setType(Material.AIR);
            for (BlockFace face : fixedMonument) {
                Block upBlock = block.getRelative(BlockFace.UP);
                Block relative = upBlock.getRelative(face, 2);
                if (Tag.FIRE.isTagged(relative.getType())) {
                    relative.setType(Material.AIR);
                }
            }

            Location spawnLocation = block.getLocation().add(0.5d, 0.0d, 0.5d);
            spawnLocation.setYaw(spawnYaw);

            // Spawn from tag at the middle of the top block.
            plugin.getConverter().spawnFromTag(spawnLocation, tag);
        }

        private void refreshDisplay(@NotNull Block block) {
            if (display == null) return;

            for (Entity entity : ImmutableList.copyOf(block.getWorld().getEntities())) {
                if (!(entity instanceof Player player)) continue;

                Location blockLocation = block.getLocation();
                Location playerLocation = player.getLocation();

                World blockWorld = block.getWorld();

                if (!blockWorld.equals(playerLocation.getWorld())
                        || !blockWorld.isChunkLoaded(blockLocation.getBlockX() >> 4, blockLocation.getBlockZ() >> 4)) {
                    if (display.getPlayers().contains(player)) display.removePlayer(player);
                    continue;
                }

                boolean inRange = blockLocation.distance(playerLocation) < BAR_RENDER_DISTANCE;

                if (!inRange && display.getPlayers().contains(player)) {
                    display.removePlayer(player);
                } else if (inRange && !display.getPlayers().contains(player)) {
                    display.addPlayer(player);
                }
            }
        }

        @Contract(pure = true)
        public static BlockFace @NotNull [] getRotatedArray(BlockFace @NotNull [] array, int startElement) {
            int startIndex = 0;
            for (int i = 0; i < array.length; i++) {
                if (i == startElement) {
                    startIndex = i;
                    break;
                }
            }

            BlockFace[] rotatedArray = new BlockFace[array.length];
            for (int i = 0; i < array.length; i++) {
                rotatedArray[i] = array[(startIndex + i) % array.length];
            }

            return rotatedArray;
        }
    }
}