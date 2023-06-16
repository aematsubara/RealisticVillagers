package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;


public class EquipmentModifier extends NPCModifier {

    public EquipmentModifier(NPC npc) {
        super(npc);
    }

    public EquipmentModifier queue(EnumWrappers.ItemSlot itemSlot, ItemStack equipment) {
        queueInstantly((npc, player) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_EQUIPMENT);
            container.getIntegers().write(0, npc.getEntityId());

            if (MINECRAFT_VERSION >= 16) {
                container.getSlotStackPairLists().write(0, Collections.singletonList(new Pair<>(itemSlot, equipment)));
                return container;
            }

            if (MINECRAFT_VERSION < 9) {
                int slotId = itemSlot.ordinal();
                container.getIntegers().write(1, slotId > 0 ? slotId - 1 : slotId);
            } else {
                container.getItemSlots().write(0, itemSlot);
            }

            container.getItemModifier().write(0, equipment);
            return container;
        });
        return this;
    }
}