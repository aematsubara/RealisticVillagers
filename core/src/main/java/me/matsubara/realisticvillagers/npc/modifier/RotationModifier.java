package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Location;

public class RotationModifier extends NPCModifier {

    public RotationModifier(NPC npc) {
        super(npc);
    }

    public RotationModifier queueRotate(float yaw, float pitch) {
        byte yawAngle = (byte) (yaw * 256.0f / 360.0f);
        byte pitchAngle = (byte) (pitch * 256.0f / 360.0f);

        // Head rotation.
        queueInstantly((npc, player) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_HEAD_ROTATION);
            container.getIntegers().write(0, npc.getEntityId());
            container.getBytes().write(0, yawAngle);
            return container;
        });

        // Entity position.
        queueInstantly((npc, player) -> {
            PacketContainer container;
            if (MINECRAFT_VERSION < 9) {
                container = new PacketContainer(Server.ENTITY_TELEPORT);
                container.getIntegers().write(0, npc.getEntityId());

                Location location = npc.getLocation();
                container.getIntegers()
                        .write(1, (int) Math.floor(location.getX() * 32.0d))
                        .write(2, (int) Math.floor(location.getY() * 32.0d))
                        .write(3, (int) Math.floor(location.getZ() * 32.0d));
            } else {
                container = new PacketContainer(Server.ENTITY_LOOK);
                container.getIntegers().write(0, npc.getEntityId());
            }

            container.getBytes()
                    .write(0, yawAngle)
                    .write(1, pitchAngle);
            container.getBooleans().write(0, true);
            return container;
        });

        return this;
    }
}