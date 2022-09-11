package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.util.ReflectionUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public final class PlayerListeners implements Listener {

    private final RealisticVillagers plugin;
    private final SimpleDateFormat simpleFormat = new SimpleDateFormat("mm:ss");

    public PlayerListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;

        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null) return;
        if (item.getType() == Material.NAME_TAG) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        handleBabySpawn(event);

        if (!ReflectionUtils.supports(19)) return;
        if (!Config.ATTACK_PLAYER_PLAYING_GOAT_HORN_SEEK.asBool()) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();

        Material goatHorn = Material.valueOf("GOAT_HORN");
        if (item.getType() != goatHorn || player.hasCooldown(goatHorn)) return;

        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) return;

        if (!plugin.getConverter().isSeekGoatHorn(item)) return;

        int range = Config.GOAT_HORN_SEEK_RANGE.asInt();
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof Villager villager)) continue;
            IVillagerNPC npc = plugin.getConverter().getNPC(villager);
            npc.reactToSeekHorn(player);
        }
    }

    private void handleBabySpawn(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        long procreation = container.getOrDefault(plugin.getProcreationKey(), PersistentDataType.LONG, -1L);
        if (procreation == -1) return;

        long elapsedTime = System.currentTimeMillis() - procreation;

        event.setCancelled(true);

        int growCooldown = Config.BABY_GROW_COOLDOWN.asInt();
        if (elapsedTime <= growCooldown) {
            String next = simpleFormat.format(new Date(growCooldown - elapsedTime));
            player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.BABY_GROW).replace("%time%", next));
            return;
        }

        String childName = container.get(plugin.getChildNameKey(), PersistentDataType.STRING);
        String childSex = container.get(plugin.getChildSexKey(), PersistentDataType.STRING);

        String mother = container.get(plugin.getMotherUUIDKey(), PersistentDataType.STRING);
        UUID motherUUID = mother != null ? UUID.fromString(mother) : UUID.randomUUID();

        Block spawnAt = clicked.getRelative(BlockFace.UP);
        if (!spawnAt.isPassable() || !spawnAt.getRelative(BlockFace.UP).isPassable()) {
            player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.CAN_NOT_SPAWN_BABY));
            return;
        }

        plugin.getConverter().createBaby(
                spawnAt.getLocation(),
                childName,
                childSex,
                motherUUID,
                player);

        event.getPlayer().getInventory().setItem(hand, null);
    }
}