package me.matsubara.realisticvillagers.listener;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.manager.InteractCooldownManager;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class PlayerListeners implements Listener {

    private final RealisticVillagers plugin;
    private final Multimap<UUID, Map.Entry<Long, Integer>> babyGrowCount = ArrayListMultimap.create();
    private final List<String> ignoredActivities = ImmutableList.of("hide", "panic", "fight");

    private static final String EMPTY = "";

    public PlayerListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        // Should add support for ItemsAdder?
        discoverRecipes(event.getPlayer(), plugin.getRing().getKey(), plugin.getWhistle().getKey());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        babyGrowCount.removeAll(event.getPlayer().getUniqueId());
    }

    private void discoverRecipes(Player player, @NotNull NamespacedKey... keys) {
        for (NamespacedKey key : keys) {
            player.discoverRecipe(key);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(@NotNull EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        if (!(event.getTarget() instanceof Player player)) return;

        Raid raid = plugin.getConverter().getRaidAt(player.getLocation());
        if (raid == null || !raid.getHeroes().contains(player.getUniqueId())) return;

        if (!Config.IRON_GOLEM_ATTACK_PLAYER_DURING_RAID.asBool()) {
            // Prevent iron golem attacking players (they might hit them by accident during a raid).
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Player player = event.getPlayer();
        if (!Config.GREET_MESSAGES_ENABLED.asBool()) return;

        InteractCooldownManager cooldown = plugin.getCooldownManager();
        if (!cooldown.canInteract(player, "welcome", Config.GREET_MESSAGES_COOLDOWN.asLong())) return;

        double range = Config.GREET_MESSAGES_RANGE.asDouble();
        int requiredReputation = Config.GREET_MESSAGES_REQUIRED_REPUTATION.asInt();

        for (Entity near : player.getNearbyEntities(range, range, range)) {
            if (!(near instanceof Villager villager)
                    || plugin.getTracker().isInvalid(villager, true)
                    || villager.isSleeping()
                    || !villager.hasLineOfSight(player)) continue;

            // Ignore non-custom, inside raid, lower reputation, ignored activities.
            Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(villager);
            if (npc.isEmpty() || npc.get().isInsideRaid()) continue;
            if (npc.get().getReputation(player.getUniqueId()) < requiredReputation) continue;
            if (ignoredActivities.contains(npc.get().getActivityName(EMPTY).toLowerCase(Locale.ROOT))) continue;

            InteractionTargetType relationship = InteractionTargetType.getInteractionTarget(npc.get(), player);
            if (!cooldown.canInteract(player, villager, relationship.getName(), Config.GREET_MESSAGES_PER_TYPE_COOLDOWN.asLong())) {
                continue;
            }

            GUIInteractType type = GUIInteractType.GREET;
            if (cooldown.canInteract(player, villager, type.getName())) {
                plugin.getInventoryListeners().handleChatInteraction(npc.get(), type, player);
                return;
            }
        }

        cooldown.removeCooldown(player, "welcome");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        handleWhistle(event);
        handleBabySpawn(event);

        if (plugin.isDisabledIn(event.getPlayer().getWorld())) return;

        if (!XReflection.supports(19)) return;
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
            if (!(entity instanceof Villager villager) || plugin.getTracker().isInvalid(villager, true)) continue;
            plugin.getConverter().getNPC(villager).ifPresent(npc -> npc.reactToSeekHorn(player));
        }
    }

    private void handleWhistle(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (!container.has(plugin.getIsWhistleKey(), PersistentDataType.INTEGER)) return;

        plugin.openWhistleGUI(player, null, null);
    }

    private void handleBabySpawn(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        long procreation = container.getOrDefault(plugin.getProcreationKey(), PersistentDataType.LONG, -1L);
        if (procreation == -1) return;

        event.setCancelled(true);

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        if (plugin.isDisabledIn(player.getWorld())) return;

        Messages messages = plugin.getMessages();

        long elapsed = System.currentTimeMillis() - procreation,
                cooldown = Config.BABY_GROW_COOLDOWN.asLong(),
                leftMillis = cooldown - elapsed,
                leftSeconds = (leftMillis / 1000L) % 60L;

        if (elapsed < cooldown && leftSeconds > 0) {
            String next = PluginUtils.formatMillis(leftMillis);
            messages.send(player, Messages.Message.BABY_GROW, string -> string.replace("%time%", next));
            return;
        }

        String childName = container.get(plugin.getChildNameKey(), PersistentDataType.STRING);
        String childSex = container.get(plugin.getChildSexKey(), PersistentDataType.STRING);

        String mother = container.get(plugin.getMotherUUIDKey(), PersistentDataType.STRING);
        UUID motherUUID = mother != null ? UUID.fromString(mother) : UUID.randomUUID();

        Block spawnAt = clicked.getRelative(BlockFace.UP);
        if (!spawnAt.isPassable() || !spawnAt.getRelative(BlockFace.UP).isPassable()) {
            messages.send(player, Messages.Message.BABY_CAN_NOT_SPAWN);
            return;
        }

        UUID playerUUID = player.getUniqueId();

        // If player isn't in map, add new entry with this baby.
        if (!babyGrowCount.containsKey(playerUUID)) {
            babyGrowCount.put(playerUUID, new AbstractMap.SimpleEntry<>(procreation, 3));
        }

        Collection<Map.Entry<Long, Integer>> entries = babyGrowCount.get(playerUUID);

        // If player is already in map, but this baby isn't in, add it.
        boolean existsInList = entries.stream().anyMatch(entry -> entry.getKey() == procreation);
        if (!existsInList) babyGrowCount.put(playerUUID, new AbstractMap.SimpleEntry<>(procreation, 3));

        // Check for counts and send messages.
        Map.Entry<Long, Integer> toRemove = null;
        for (Map.Entry<Long, Integer> entry : entries) {
            if (entry.getKey() != procreation) continue;

            int count = entry.getValue();
            if (count > 0) {
                messages.send(player, Messages.Message.BABY_COUNTDOWN, string -> string.replace("%countdown%", String.valueOf(count)));
                entry.setValue(count - 1);
                return;
            }

            toRemove = entry;
            break;
        }
        if (toRemove != null) {
            babyGrowCount.remove(playerUUID, toRemove);
        }

        messages.send(player, Messages.Message.BABY_SPAWNED, string -> string.replace("%baby-name%", Objects.requireNonNullElse(childName, "???")));

        plugin.getConverter().createBaby(
                spawnAt.getLocation(),
                childName,
                childSex,
                motherUUID,
                player);

        player.getInventory().setItem(hand, null);
    }
}