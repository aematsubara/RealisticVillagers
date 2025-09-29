package me.matsubara.realisticvillagers.listener;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.KissEvent;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.MarriagePlayer;
import me.matsubara.realisticvillagers.entity.PlayerProcreationTracker;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.task.BabyTaskPlayer;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.entity.Player;
import at.pcgamingfreaks.MarriageMaster.Bukkit.API.Events.MarriedEvent;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.Objects;
import java.util.UUID;


public class MarriageListener implements Listener {
    private final RealisticVillagers plugin;

    public MarriageListener(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void ForceDivorcewhenmarried(MarriedEvent event) {
        UUID partnerUUID1 = null;
        UUID partnerUUID2 = null;
        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();
        INMSConverter converter = plugin.getConverter();

        MarriagePlayer marriedplayer1 = event.getPlayer1();
        MarriagePlayer marriedplayer2 = event.getPlayer2();

        Player player1 = marriedplayer1.getPlayerOnline();
        Player player2 = marriedplayer2.getPlayerOnline();

        Player offlinePlayer1 = marriedplayer1.getPlayer().getPlayer();
        Player offlinePlayer2 = marriedplayer2.getPlayer().getPlayer();

        if (player1 != null) {
            String uuidString1 = player1.getPersistentDataContainer().get(plugin.getMarriedWith(), PersistentDataType.STRING);
            if (uuidString1 != null) {
                partnerUUID1 = UUID.fromString(uuidString1);
                player1.getPersistentDataContainer().remove(plugin.getMarriedWith());
            }
        } else if (offlinePlayer1 != null) {
            File playerFile1 = tracker.getPlayerNBTFile(offlinePlayer1.getUniqueId());
            if (playerFile1 != null && (partnerUUID1 = converter.getPartnerUUIDFromPlayerNBT(playerFile1)) != null) {
                converter.removePartnerFromPlayerNBT(playerFile1);
            }
        }

        if (player2 != null) {
            String uuidString2 = player2.getPersistentDataContainer().get(plugin.getMarriedWith(), PersistentDataType.STRING);
            if (uuidString2 != null) {
                partnerUUID2 = UUID.fromString(uuidString2);
                player2.getPersistentDataContainer().remove(plugin.getMarriedWith());
            }
        } else if (offlinePlayer2 != null) {
            File playerFile2 = tracker.getPlayerNBTFile(offlinePlayer2.getUniqueId());
            if (playerFile2 != null && (partnerUUID2 = converter.getPartnerUUIDFromPlayerNBT(playerFile2)) != null) {
                converter.removePartnerFromPlayerNBT(playerFile2);
            }
        }

        for (IVillagerNPC offlineVillager : tracker.getOfflineVillagers()) {
            if (!offlineVillager.getUniqueId().equals(partnerUUID1)) continue;

            LivingEntity bukkit = offlineVillager.bukkit();
            if (bukkit == null) {
                bukkit = plugin.getUnloadedOffline(offlineVillager);
                if (bukkit == null) continue;
            }

            IVillagerNPC npc = converter.getNPC(bukkit).orElse(null);
            if (npc == null) continue;

            npc.divorceAndDropRing(player1);
            messages.send(
                    player1,
                    Messages.Message.DIVORCED,
                    string -> string.replace("%player-name%", Objects.requireNonNullElse(offlinePlayer1.getName(), "???")));
            break;
        }
        for (IVillagerNPC offlineVillager : tracker.getOfflineVillagers()) {
            if (!offlineVillager.getUniqueId().equals(partnerUUID2)) continue;

            LivingEntity bukkit = offlineVillager.bukkit();
            if (bukkit == null) {
                bukkit = plugin.getUnloadedOffline(offlineVillager);
                if (bukkit == null) continue;
            }

            IVillagerNPC npc = converter.getNPC(bukkit).orElse(null);
            if (npc == null) continue;

            npc.divorceAndDropRing(player2);
            messages.send(
                    player2,
                    Messages.Message.DIVORCED,
                    string -> string.replace("%player-name%", Objects.requireNonNullElse(offlinePlayer1.getName(), "???")));
            break;
        }
    }

    @EventHandler
    private void KidWhenKissing(KissEvent event) {
        Messages messages = plugin.getMessages();
        MarriagePlayer kisser = event.getPlayer();
        MarriagePlayer partner = event.getPlayer().getPartner();

        Player kisserplayer = kisser.getPlayerOnline();
        Player partnerplayer = partner.getPlayerOnline();

        UUID player1UUID = kisserplayer.getUniqueId();
        UUID player2UUID = partnerplayer.getUniqueId();

        PlayerProcreationTracker procreationTracker = plugin.getProcreationTracker();
        long playerlastProcreation = procreationTracker.getLastProcreation(player1UUID, player2UUID);
        long playerelapsedTime = System.currentTimeMillis() - playerlastProcreation;

        int playerprocreationCooldown = Config.PROCREATION_COOLDOWN.asInt();

        if (playerelapsedTime <= playerprocreationCooldown) {
            String next = PluginUtils.getTimeString(playerprocreationCooldown - playerelapsedTime);
            messages.send(kisserplayer, Messages.Message.PROCREATE_FAIL_HAS_BABY);
            messages.send(kisserplayer, Messages.Message.PROCREATE_COOLDOWN, string -> string.replace("%time%", next));
            return;
        }

        new BabyTaskPlayer(plugin, partnerplayer, kisserplayer).runTaskTimer(plugin, 0L, 20L);

    }
}
