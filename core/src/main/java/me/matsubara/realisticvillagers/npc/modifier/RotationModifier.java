package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import me.matsubara.realisticvillagers.npc.NPC;

public class RotationModifier extends NPCModifier {

    public RotationModifier(NPC npc) {
        super(npc);
    }

    public RotationModifier queueHeadRotation(float yaw) {
        queueInstantly((npc, player) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_HEAD_ROTATION);
            container.getIntegers().write(0, npc.getEntityId());
            container.getBytes().write(0, (byte) (yaw * 256.0f / 360.0f));
            return container;
        });
        return this;
    }
}