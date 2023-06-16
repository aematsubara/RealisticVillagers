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

            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();

            if (MINECRAFT_VERSION < 9) {
                container.getIntegers()
                        .write(1, (int) Math.floor(x * 32.0d))
                        .write(2, (int) Math.floor(y * 32.0d))
                        .write(3, (int) Math.floor(z * 32.0d));
            } else {
                container.getDoubles()
                        .write(0, x)
                        .write(1, y)
                        .write(2, z);
            }

            container.getBytes()
                    .write(0, yawAngle)
                    .write(1, pitchAngle);
            container.getBooleans().write(0, onGround);

            npc.setLocation(location);
            return container;
        });

        return this;
    }

    private byte getCompressedAngle(double angle) {
        return (byte) (angle * 256.0f / 360.0f);
    }
}