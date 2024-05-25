package me.matsubara.realisticvillagers.npc.modifier;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class TeleportModifier extends NPCModifier {

    public TeleportModifier(NPC npc) {
        super(npc);
    }

    public TeleportModifier queueTeleport(@NotNull Location location, boolean onGround) {
        queueInstantly((npc, player) -> {
            return new WrapperPlayServerEntityTeleport(npc.getEntityId(), SpigotConversionUtil.fromBukkitLocation(location), onGround);
        });
        return this;
    }
}