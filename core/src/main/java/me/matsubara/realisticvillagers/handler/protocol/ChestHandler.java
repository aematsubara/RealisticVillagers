package me.matsubara.realisticvillagers.handler.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;
import com.google.common.collect.ImmutableSet;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.manager.ChestManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChestHandler extends SimplePacketListenerAbstract {

    private final RealisticVillagers plugin;
    private final Set<PacketType.Play.Server> listenTo = ImmutableSet.of(
            PacketType.Play.Server.NAMED_SOUND_EFFECT,
            PacketType.Play.Server.BLOCK_ACTION);

    public ChestHandler(RealisticVillagers plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlaySend(@NotNull PacketPlaySendEvent event) {
        if (event.isCancelled() || !listenTo.contains(event.getPacketType())) return;

        Player player = (Player) event.getPlayer();

        World world;
        try {
            world = player.getWorld();
        } catch (UnsupportedOperationException exception) {
            // Should "fix" -> UnsupportedOperationException: The method getWorld is not supported for temporary players.
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.SOUND_EFFECT) {
            WrapperPlayServerSoundEffect soundWrapper = new WrapperPlayServerSoundEffect(event);

            Sound sound;
            try {
                ResourceLocation name = soundWrapper.getSound().getName();
                sound = Registry.SOUNDS.get(NamespacedKey.minecraft(name.getKey()));
            } catch (IllegalStateException | NullPointerException exception) {
                // Should "fix" IllegalStateException: Unable to invoke method public static org.bukkit.Sound org.bukkit.craftbukkit.v1_19_R2.CraftSound.getBukkit(net.minecraft.sounds.SoundEffect);
                return;
            }
            if (sound != Sound.BLOCK_CHEST_OPEN && sound != Sound.BLOCK_CHEST_CLOSE) return;

            Vector3i integers = soundWrapper.getEffectPosition();
            double x = (float) integers.getX() / 8.0f;
            double y = (float) integers.getY() / 8.0f;
            double z = (float) integers.getZ() / 8.0f;
            Location location = new Location(world, x, y, z);

            Block block = location.getBlock();
            if (block.getType() != Material.CHEST) return;

            if (shouldCancel(block)) {
                event.setCancelled(true);
            }
            return;
        }

        WrapperPlayServerBlockAction blockAction = new WrapperPlayServerBlockAction(event);

        Material material = SpigotConversionUtil.toBukkitBlockData(blockAction.getBlockType()).getMaterial();
        if (material != Material.CHEST) return;

        // We only want to cancel the close animation if a villager has the inventory open.
        boolean open = /*packet.getIntegers().readSafely(1) == 1*/ blockAction.getBlockType().isOpen();
        if (open) return;

        Vector3i pos = blockAction.getBlockPosition();
        Location location = new Location(world, pos.x, pos.y, pos.z);

        Block block = world.getBlockAt(location);
        if (block.getType() != Material.CHEST) return;

        Vector vector = location.toVector();

        ChestManager chestManager = plugin.getChestManager();
        if (chestManager.getVillagerChests().containsKey(vector)) {
            event.setCancelled(true);
        }

        Map<Vector, Set<UUID>> playerChests = plugin.getChestManager().getPlayerChests();

        Set<UUID> viewers = playerChests.get(vector);
        if (viewers == null) return;

        Chest chest = (Chest) block.getState();
        viewers.removeIf(uuid -> !isViewer(chest, uuid));

        if (viewers.isEmpty()) {
            playerChests.remove(vector);
        }
    }

    private boolean isViewer(@NotNull Chest chest, UUID uuid) {
        for (HumanEntity viewer : chest.getBlockInventory().getViewers()) {
            if (viewer.getUniqueId().equals(uuid)) return true;
        }
        return false;
    }

    private boolean shouldCancel(@NotNull Block block) {
        if (block.getType() != Material.CHEST) return false;

        Chest chest = (Chest) block.getState();
        if (!(chest.getInventory() instanceof DoubleChestInventory doubleChest)) return test(block);

        return (test(doubleChest.getLeftSide())) || test(doubleChest.getRightSide());
    }

    private boolean test(@NotNull Inventory inventory) {
        Location location = inventory.getLocation();
        return location != null && test(location.getBlock());
    }

    private boolean test(@NotNull Block block) {
        ChestManager chestManager = plugin.getChestManager();
        Vector vector = block.getLocation().toVector();

        boolean isBeingLooted = chestManager.getVillagerChests().containsKey(vector);
        boolean isOpen = !((Chest) block.getState()).getInventory().getViewers().isEmpty();
        boolean isAboutToBeOpenOrClosed = chestManager.getPlayerChests().containsKey(vector);

        return isBeingLooted && (isOpen || isAboutToBeOpenOrClosed);
    }
}
