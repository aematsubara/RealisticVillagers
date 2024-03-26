package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class TeleportModifier extends NPCModifier {

    public TeleportModifier(NPC npc) {
        super(npc);
    }

    public TeleportModifier queueTeleport(@NotNull Location location, boolean onGround) {
        byte yawAngle = getCompressedAngle(location.getYaw());
        byte pitchAngle = getCompressedAngle(location.getPitch());

        queueInstantly((npc, player) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_TELEPORT);
            container.getIntegers().write(0, npc.getEntityId());

            container.getDoubles()
                    .write(0, location.getX())
                    .write(1, location.getY())
                    .write(2, location.getZ());

            container.getBytes()
                    .write(0, yawAngle)
                    .write(1, pitchAngle);
            container.getBooleans().write(0, onGround);

            return container;
        });

        return this;
    }

    private byte getCompressedAngle(double angle) {
        return (byte) (angle * 256.0f / 360.0f);
    }
}