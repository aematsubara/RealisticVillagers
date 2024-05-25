package me.matsubara.realisticvillagers.npc.modifier;

import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.inventory.ItemStack;

import java.util.List;


public class EquipmentModifier extends NPCModifier {

    public EquipmentModifier(NPC npc) {
        super(npc);
    }

    public EquipmentModifier queue(EquipmentSlot itemSlot, ItemStack equipment) {
        queueInstantly((npc, player) -> new WrapperPlayServerEntityEquipment(npc.getEntityId(),
                List.of(new Equipment(itemSlot, SpigotConversionUtil.fromBukkitItemStack(equipment)))));
        return this;
    }
}