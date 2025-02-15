package me.matsubara.realisticvillagers.listener;


import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarriedEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class MarriageListener implements Listener {
    private final RealisticVillagers plugin;

    public MarriageListener(RealisticVillagers plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @EventHandler
    private void ForceDivorcewhenmarried(MarriedEvent event) {
        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();
        INMSConverter converter = plugin.getConverter();


        Player player1 = event.getPlayer1();
        Player player2 = event.getPlayer2();
        UUID partnerUUID1 = null;
        UUID partnerUUID2 = null;


        String uuidString = player1.getPersistentDataContainer().get(plugin.getMarriedWith(), PersistentDataType.STRING);
        if (uuidString != null) partnerUUID1 = event.getPlayer1().getUUID();
        player1.getPersistentDataContainer().remove(plugin.getMarriedWith());

        String uuidString2 = player2.getPersistentDataContainer().get(plugin.getMarriedWith(), PersistentDataType.STRING);
        if (uuidString2 != null) partnerUUID2 = event.getPlayer2().getUUID();
        player2.getPersistentDataContainer().remove(plugin.getMarriedWith());



        for (IVillagerNPC offlineVillager : tracker.getOfflineVillagers()) {
            if (!offlineVillager.getUniqueId().equals(partnerUUID1)) continue;

            LivingEntity bukkit = offlineVillager.bukkit();
            if (bukkit == null) {
                bukkit = plugin.getUnloadedOffline(offlineVillager);
                if (bukkit == null) continue;
            }

            // In this case, we don't need to ignore invalid villagers.
            IVillagerNPC npc = converter.getNPC(bukkit).orElse(null);
            if (npc == null) continue;

            npc.divorceAndDropRing(player1);
            break;
        }
        for (IVillagerNPC offlineVillager : tracker.getOfflineVillagers()) {
            if (!offlineVillager.getUniqueId().equals(partnerUUID2)) continue;

            LivingEntity bukkit = offlineVillager.bukkit();
            if (bukkit == null) {
                bukkit = plugin.getUnloadedOffline(offlineVillager);
                if (bukkit == null) continue;
            }

            // In this case, we don't need to ignore invalid villagers.
            IVillagerNPC npc = converter.getNPC(bukkit).orElse(null);
            if (npc == null) continue;

            npc.divorceAndDropRing(player1);
            break;
        }

        // At this point, either the player or the villager (or both) should be divorced.

    }
}
