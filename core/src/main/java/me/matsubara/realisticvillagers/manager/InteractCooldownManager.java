package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InteractCooldownManager implements Listener {

    private final RealisticVillagers plugin;
    private final Set<InteractCooldown> cooldowns = ConcurrentHashMap.newKeySet();

    private static final UUID WELCOME_MESSAGE_UUID = UUID.randomUUID();

    public InteractCooldownManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        Iterator<InteractCooldown> iterator = cooldowns.iterator();

        while (iterator.hasNext()) {
            InteractCooldown cooldown = iterator.next();
            if (!cooldown.player().equals(player.getUniqueId())) continue;
            if (!cooldown.inCooldown()) iterator.remove();
        }
    }

    public boolean canInteract(Player player, Villager villager, String type) {
        return canInteract(player, villager, type, null);
    }

    public boolean canInteract(Player player, String type, Long finishTime) {
        return canInteract(player, null, type, finishTime);
    }

    public boolean canInteract(@NotNull Player player, @Nullable Villager villager, String type, @Nullable Long finishTime) {
        InteractCooldown cooldown = getCooldown(
                player.getUniqueId(),
                villager != null ? villager.getUniqueId() : WELCOME_MESSAGE_UUID,
                type);

        if (cooldown == null) {
            if (finishTime != null) addCooldown(
                    player.getUniqueId(),
                    villager != null ? villager.getUniqueId() : WELCOME_MESSAGE_UUID,
                    type,
                    finishTime);
            else if (villager != null) addCooldown(player, villager, type);
            else throw new IllegalArgumentException("Only one of the two nullable values can be null!");
            return true;
        }

        boolean inCooldown = cooldown.inCooldown();
        if (!inCooldown) cooldowns.remove(cooldown);

        return !inCooldown;
    }

    public void addCooldown(@NotNull Player player, @NotNull LivingEntity living, String type) {
        long finishType = plugin.getConfig().getLong("interact-cooldown." + type, 1L) * 1000L;
        addCooldown(player.getUniqueId(), living.getUniqueId(), type, finishType);
    }

    private void addCooldown(UUID playerUUID, UUID villagerUUID, String type, long finishTime) {
        cooldowns.add(new InteractCooldown(playerUUID, villagerUUID, type, System.currentTimeMillis() + finishTime));
    }

    public void removeCooldown(Player player, String type) {
        Iterator<InteractCooldown> iterator = cooldowns.iterator();

        while (iterator.hasNext()) {
            InteractCooldown cooldown = iterator.next();
            if (!cooldown.player().equals(player.getUniqueId())) continue;
            if (!cooldown.type().equalsIgnoreCase(type)) continue;
            iterator.remove();
        }
    }

    private @Nullable InteractCooldown getCooldown(UUID player, UUID villager, String type) {
        for (InteractCooldown cooldown : cooldowns) {
            if (!cooldown.player().equals(player)) continue;
            if (!cooldown.villager().equals(villager)) continue;

            if (cooldown.type().equalsIgnoreCase(type)) return cooldown;
        }
        return null;
    }

    public record InteractCooldown(UUID player, UUID villager, String type, long finish) {

        public boolean inCooldown() {
            return finish > System.currentTimeMillis();
        }
    }
}