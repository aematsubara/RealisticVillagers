package me.matsubara.realisticvillagers.task;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.handler.npc.BasicNPCHandler;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PreviewTask extends BukkitRunnable {

    private final RealisticVillagers plugin;
    private final Player player;
    private final NPC npc;
    private final int seconds;

    private int tick;
    private int hue;
    private float yaw;

    private static final float ROTATION_SPEED = 5.0f;

    public PreviewTask(@NotNull RealisticVillagers plugin, Player player, WrappedSignedProperty textures) {
        this.plugin = plugin;
        this.player = player;
        this.seconds = Config.SKIN_PREVIEW_SECONDS.asInt();

        plugin.getTracker().checkNametagTeam();

        Location targetLocation = getPlayerTargetLocation(player);

        WrappedGameProfile profile = new WrappedGameProfile(UUID.randomUUID(), VillagerTracker.HIDE_NAMETAG_NAME);
        profile.getProperties().put("textures", textures);

        this.npc = new NPC(profile,
                targetLocation,
                new BasicNPCHandler(),
                ThreadLocalRandom.current().nextInt(10000, Integer.MAX_VALUE));

        this.yaw = targetLocation.getYaw();

        npc.show(player, plugin, 40L);

        plugin.getTracker().getPreviews().put(player.getUniqueId(), this);
    }

    @Override
    public void run() {
        if (tick == seconds * 20 || !player.isValid()) {
            npc.hide(player);
            cancel();
            return;
        }

        Location location = getPlayerTargetLocation(player);
        location.setYaw(yaw = yaw(yaw + ROTATION_SPEED));

        npc.teleport().queueTeleport(location, false).send(player);
        npc.rotation().queueRotate(location.getYaw(), location.getPitch()).send(player);

        boolean rainbow = Config.SKIN_PREVIEW_RAINBOW_MESSAGE.asBool();
        if (rainbow || tick % 20 == 0) {
            String message = Config.SKIN_PREVIEW_MESSAGE.asStringTranslated().replace("%remaining%", String.valueOf(seconds - tick / 20));

            @SuppressWarnings("deprecation") BaseComponent[] components = rainbow ?
                    new BaseComponent[]{new TextComponent(ChatColor.stripColor(message))} :
                    TextComponent.fromLegacyText(message);

            if (rainbow) components[0].setColor(ChatColor.of(Color.getHSBColor(hue / 360.0f, 1.0f, 1.0f)));

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
            hue += 6;
        }

        if (tick % 20 == 0) spawnParticles(location, player);

        tick++;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        npc.hide(player);
        plugin.getTracker().getPreviews().remove(player.getUniqueId());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR); // Clear message.
    }

    private void spawnParticles(@NotNull Location location, @NotNull Player player) {
        BoundingBox box = player.getBoundingBox();

        Location at = location.clone().add(0.0d, box.getHeight(), 0.0d);

        double y = box.getHeight() / 2;
        player.getWorld().spawnParticle(
                Particle.FIREWORKS_SPARK,
                at,
                1,
                box.getWidthX() / 4,
                y,
                box.getWidthZ() / 4,
                0.001d);
    }

    public static float yaw(float yaw) {
        float temp = yaw % 360.0f;
        return yaw < 0.0f ? temp + 360.0f : temp;
    }

    private @NotNull Location getPlayerTargetLocation(@NotNull Player player) {
        Location eyeLocation = player.getEyeLocation();

        Vector direction = eyeLocation.getDirection().multiply(3.75d);
        Location targetLocation = eyeLocation.clone().add(direction);

        BlockFace face = PluginUtils.yawToFace(targetLocation.getYaw(), 0x3);
        if (face != null) targetLocation.setDirection(PluginUtils.getDirection(face.getOppositeFace()));

        Block block = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (block != null) {
            targetLocation.setY(block.getY() + 1);
            return targetLocation;
        }

        return targetLocation.subtract(0.0d, 0.5d, 0.0d);
    }
}
