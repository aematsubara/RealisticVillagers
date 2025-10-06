package me.matsubara.realisticvillagers.npc.modifier;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Location;

import java.util.Collections;
import java.util.EnumSet;

public class VisibilityModifier extends NPCModifier {

    // Static actions we need to send out for all player updates (since 1.19.3).
    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ACTIONS;

    static {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19_3)) {
            ADD_ACTIONS = EnumSet.of(
                    WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
                    WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME);
        } else ADD_ACTIONS = EnumSet.noneOf(WrapperPlayServerPlayerInfoUpdate.Action.class);
    }

    public VisibilityModifier(NPC npc) {
        super(npc);
    }

    public VisibilityModifier queuePlayerListChange(boolean remove) {
        if (remove && PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19_3)) {
            queueInstantly(((npc, player) -> new WrapperPlayServerPlayerInfoRemove(Collections.singletonList(npc.getProfile().getUUID()))));
            return this;
        }

        queuePacket((npc, player) -> {
            UserProfile profile = npc.getProfile();
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_19_3)) {
                WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, false, 20, GameMode.CREATIVE, null, null);
                return new WrapperPlayServerPlayerInfoUpdate(ADD_ACTIONS, info);
            } else {
                WrapperPlayServerPlayerInfo.PlayerData info = new WrapperPlayServerPlayerInfo.PlayerData(null, profile, GameMode.CREATIVE, 20);
                return new WrapperPlayServerPlayerInfo(remove ?
                        WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER :
                        WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, info);
            }
        });

        return this;
    }

    public VisibilityModifier queueSpawn(Location location) {
        queueInstantly((npc, player) -> {
            com.github.retrooper.packetevents.protocol.world.Location at = SpigotConversionUtil.fromBukkitLocation(location);
            if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_2)) {
                return new WrapperPlayServerSpawnEntity(npc.getEntityId(),
                        npc.getProfile().getUUID(),
                        EntityTypes.PLAYER,
                        at,
                        at.getYaw(),
                        0,
                        null);
            } else {
                return new WrapperPlayServerSpawnPlayer(
                        npc.getEntityId(),
                        npc.getProfile().getUUID(),
                        at);
            }
        });

        return this;
    }

    public VisibilityModifier queueDestroy() {
        queueInstantly((npc, player) -> new WrapperPlayServerDestroyEntities(npc.getEntityId()));
        return this;
    }
}