package me.matsubara.realisticvillagers.manager.revive;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.util.PluginUtils;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public class MonumentAnimation extends BukkitRunnable {

    private final RealisticVillagers plugin;
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

    MonumentAnimation(RealisticVillagers plugin, String tag, @NotNull Block block) {
        this.plugin = plugin;
        this.tag = tag;
        this.block = block;
        this.display = initializeBossBar();

        BlockFace facing;
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            facing = directional.getFacing();
        } else if (data instanceof Rotatable rotatable) {
            facing = rotatable.getRotation();
        } else {
            facing = PluginUtils.yawToFace(block.getLocation().getYaw(), 0x3);
        }
        this.spawnYaw = PluginUtils.faceToYaw(facing);

        BlockFace face = PluginUtils.yawToFace(spawnYaw, 0x3);
        this.fixedMonument = getRotatedArray(ReviveManager.MONUMENT, face != null ? face.ordinal() : 0);

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
        plugin.getReviveManager().getRunningTasks().remove(block);
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