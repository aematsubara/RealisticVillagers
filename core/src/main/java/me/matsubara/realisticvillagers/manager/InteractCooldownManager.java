package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class InteractCooldownManager implements Listener {

    private final RealisticVillagers plugin;
    private final Set<InteractCooldown> cooldowns = new HashSet<>();

    public InteractCooldownManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        Iterator<InteractCooldown> iterator = cooldowns.iterator();

        while (iterator.hasNext()) {
            InteractCooldown cooldown = iterator.next();
            if (!cooldown.player().equals(player.getUniqueId())) continue;
            if (!cooldown.inCooldown()) iterator.remove();
        }
    }

    public boolean canInteract(Player player, Villager villager, String type) {
        InteractCooldown cooldown = getCooldown(player.getUniqueId(), villager.getUniqueId(), type);

        if (cooldown == null) {
            long finish = System.currentTimeMillis() + plugin.getConfig().getLong("interact-cooldown." + type, 1L) * 1000L;
            cooldowns.add(new InteractCooldown(
                    player.getUniqueId(),
                    villager.getUniqueId(),
                    type,
                    finish));
            return true;
        }

        boolean inCooldown = cooldown.inCooldown();
        if (!inCooldown) cooldowns.remove(cooldown);

        return !inCooldown;
    }

    private InteractCooldown getCooldown(UUID player, UUID villager, String type) {
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