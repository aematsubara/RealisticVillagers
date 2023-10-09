package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.google.common.collect.Lists;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class VisibilityModifier extends NPCModifier {

    // Static actions we need to send out for all player updates (since 1.19.3).
    private static final EnumSet<EnumWrappers.PlayerInfoAction> ADD_ACTIONS;

    static {
        if (PluginUtils.IS_1_19_3_OR_NEW) {
            ADD_ACTIONS = EnumSet.of(
                    EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                    EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
                    EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
                    EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
                    EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);
        } else ADD_ACTIONS = EnumSet.noneOf(EnumWrappers.PlayerInfoAction.class);
    }

    public VisibilityModifier(NPC npc) {
        super(npc);
    }

    public VisibilityModifier queuePlayerListChange(EnumWrappers.PlayerInfoAction action) {
        if (action == EnumWrappers.PlayerInfoAction.REMOVE_PLAYER && PluginUtils.IS_1_19_3_OR_NEW) {
            queueInstantly(((npc, player) -> {
                List<UUID> uuids = Collections.singletonList(npc.getProfile().getUUID());
                PacketContainer container = new PacketContainer(Server.PLAYER_INFO_REMOVE);
                container.getUUIDLists().write(0, uuids);
                return container;
            }));
            return this;
        }

        queuePacket((npc, player) -> {
            PacketContainer container = new PacketContainer(Server.PLAYER_INFO);

            if (PluginUtils.IS_1_19_3_OR_NEW) {
                try {
                    container.getPlayerInfoActions().write(0, ADD_ACTIONS);
                } catch (NullPointerException ignored) {

                }
            } else {
                container.getPlayerInfoAction().write(0, action);
            }

            WrappedGameProfile profile = npc.getProfile();
            PlayerInfoData data;
            if (PluginUtils.IS_1_19_3_OR_NEW) {
                data = new PlayerInfoData(
                        profile.getUUID(),
                        20,
                        false,
                        NativeGameMode.CREATIVE,
                        profile,
                        null,
                        (WrappedProfilePublicKey.WrappedProfileKeyData) null);
            } else {
                data = new PlayerInfoData(profile, 20, NativeGameMode.CREATIVE, null);
            }

            container.getPlayerInfoDataLists().write(PluginUtils.IS_1_19_3_OR_NEW ? 1 : 0, Lists.newArrayList(data));
            return container;
        });

        return this;
    }

    public VisibilityModifier queueSpawn() {
        queueInstantly((npc, player) -> {
            PacketContainer container = new PacketContainer(PluginUtils.IS_1_20_2_OR_NEW ? Server.SPAWN_ENTITY : Server.NAMED_ENTITY_SPAWN);
            container.getIntegers().write(0, npc.getEntityId());
            container.getUUIDs().write(0, npc.getProfile().getUUID());

            if (PluginUtils.IS_1_20_2_OR_NEW) {
                container.getEntityTypeModifier().write(0, EntityType.PLAYER);
            }

            double x = npc.getLocation().getX();
            double y = npc.getLocation().getY();
            double z = npc.getLocation().getZ();

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
                    .write(0, (byte) (npc.getLocation().getYaw() * 256.0f / 360.0f))
                    .write(1, (byte) (npc.getLocation().getPitch() * 256.0f / 360.0f));
            if (MINECRAFT_VERSION < 15) {
                container.getDataWatcherModifier().write(0, new WrappedDataWatcher());
            }

            return container;
        });

        return this;
    }

    public VisibilityModifier queueDestroy() {
        queueInstantly((npc, player) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_DESTROY);
            if (MINECRAFT_VERSION >= 17) {
                container.getIntLists().write(0, Collections.singletonList(npc.getEntityId()));
            } else {
                container.getIntegerArrays().write(0, new int[]{npc.getEntityId()});
            }
            return container;
        });

        return this;
    }
}