package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AnnoyingMeterManager implements Listener {

    private final RealisticVillagers plugin;
    private final Map<UUID, Map<UUID, Deque<Long>>> interactions = new HashMap<>();

    public AnnoyingMeterManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        interactions.remove(event.getPlayer().getUniqueId());
    }

    public void stopBeingAnnoyed(@NotNull Player player, IVillagerNPC npc) {
        Map<UUID, Deque<Long>> interactions = this.interactions.get(player.getUniqueId());
        if (interactions != null) interactions.remove(npc.getUniqueId());
    }

    public boolean isVillagerAnnoyed(@NotNull Player player, IVillagerNPC npc) {
        if (!Config.ANNOYING_METER_ENABLED.asBool()) return false;

        // Ignore family members.
        if (Config.ANNOYING_METER_IGNORE_FAMILY_MEMBERS.asBool()
                && npc.isFamily(player, true)) return false;

        Deque<Long> deque = interactions
                .computeIfAbsent(player.getUniqueId(), temp -> new HashMap<>())
                .computeIfAbsent(npc.getUniqueId(), temp -> new ArrayDeque<>());

        long now = System.currentTimeMillis();
        deque.addLast(now);

        long frame = Config.ANNOYING_METER_TIMEFRAME.asLong() * 1000L;
        while (!deque.isEmpty() && (now - deque.peekFirst()) > frame) {
            deque.pollFirst();
        }

        if (deque.size() <= Config.ANNOYING_METER_MAX_CLICKS.asInt()) return false;

        if (Config.ANNOYING_METER_ANGRY_PARTICLES.asBool()) {
            LivingEntity bukkit = npc.bukkit();
            bukkit.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_ANGRY,
                    bukkit.getLocation().add(0.0d, 1.5d, 0.0d),
                    10,
                    0.3d, 0.5d, 0.3d);
        }

        npc.addMinorNegative(player, Config.ANNOYING_METER_REPUTATION_LOSS.asInt());
        plugin.getMessages().send(player, npc, Messages.Message.ANNOYED);

        deque.clear();
        return true;
    }
}