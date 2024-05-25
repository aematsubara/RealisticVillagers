package me.matsubara.realisticvillagers.npc.modifier;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import me.matsubara.realisticvillagers.npc.NPC;

public class RotationModifier extends NPCModifier {

    public RotationModifier(NPC npc) {
        super(npc);
    }

    public RotationModifier queueHeadRotation(float yaw) {
        queueInstantly((npc, player) -> new WrapperPlayServerEntityHeadLook(npc.getEntityId(), yaw));
        return this;
    }
}