package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.google.common.collect.Lists;
import me.matsubara.realisticvillagers.npc.NPC;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A modifier for modifying the visibility of a player.
 */
@SuppressWarnings("deprecation")
public class VisibilityModifier extends NPCModifier {

    // Static actions we need to send out for all player updates (since 1.19.3).
    private static final EnumSet<EnumWrappers.PlayerInfoAction> ADD_ACTIONS = EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
            EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
            EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME);

    // Converter for UUID lists.
    private static final EquivalentConverter<List<UUID>> UUID_LIST_CONVERTER = BukkitConverters.getListConverter(Converters.passthrough(UUID.class));

    /**
     * Creates a new modifier.
     *
     * @param npc The npc this modifier is for.
     * @see NPC#visibility()
     */
    @ApiStatus.Internal
    public VisibilityModifier(@NotNull NPC npc) {
        super(npc);
    }

    /**
     * Enqueues the change of the player list for the wrapped npc.
     *
     * @param action The action of the player list change as a protocol lib wrapper.
     * @return The same instance of this class, for chaining.
     */
    @NotNull
    public VisibilityModifier queuePlayerListChange(@NotNull EnumWrappers.PlayerInfoAction action) {
        if (action == EnumWrappers.PlayerInfoAction.REMOVE_PLAYER && NPC.IS_1_19_3) {
            super.queueInstantly(((targetNpc, target) -> {
                List<UUID> uuids = Collections.singletonList(targetNpc.getProfile().getUUID());
                PacketContainer container = new PacketContainer(Server.PLAYER_INFO_REMOVE);
                container.getModifier().withType(List.class, UUID_LIST_CONVERTER).write(0, uuids);
                return container;
            }));
            return this;
        }

        super.queuePacket((targetNpc, target) -> {
            PacketContainer container = new PacketContainer(Server.PLAYER_INFO);

            if (NPC.IS_1_19_3) {
                container.getPlayerInfoActions().writeSafely(0, ADD_ACTIONS);
            } else {
                container.getPlayerInfoAction().write(0, action);
            }

            WrappedGameProfile profile = targetNpc.getProfile();
            if (action == EnumWrappers.PlayerInfoAction.ADD_PLAYER && targetNpc.isUsePlayerProfiles()) {
                WrappedGameProfile playerProfile = WrappedGameProfile.fromPlayer(target);
                profile = new WrappedGameProfile(profile.getUUID(), profile.getName());
                profile.getProperties().putAll(playerProfile.getProperties());
            }

            PlayerInfoData data = new PlayerInfoData(
                    profile.getUUID(),
                    20,
                    false,
                    NativeGameMode.CREATIVE,
                    profile,
                    null,
                    null);

            container.getPlayerInfoDataLists().write(NPC.IS_1_19_3 ? 1 : 0, Lists.newArrayList(data));
            return container;
        });

        return this;
    }

    /**
     * Enqueues the spawn of the wrapped npc.
     *
     * @return The same instance of this class, for chaining.
     */
    @NotNull
    public VisibilityModifier queueSpawn() {
        super.queueInstantly((targetNpc, target) -> {
            PacketContainer container = new PacketContainer(Server.NAMED_ENTITY_SPAWN);
            container.getIntegers().write(0, targetNpc.getEntityId());
            container.getUUIDs().write(0, targetNpc.getProfile().getUUID());

            double x = targetNpc.getLocation().getX();
            double y = targetNpc.getLocation().getY();
            double z = targetNpc.getLocation().getZ();

            if (MINECRAFT_VERSION < 9) {
                container.getIntegers()
                        .write(1, (int) Math.floor(x * 32.0D))
                        .write(2, (int) Math.floor(y * 32.0D))
                        .write(3, (int) Math.floor(z * 32.0D));
            } else {
                container.getDoubles()
                        .write(0, x)
                        .write(1, y)
                        .write(2, z);
            }

            container.getBytes()
                    .write(0, (byte) (super.npc.getLocation().getYaw() * 256F / 360F))
                    .write(1, (byte) (super.npc.getLocation().getPitch() * 256F / 360F));
            if (MINECRAFT_VERSION < 15) {
                container.getDataWatcherModifier().write(0, new WrappedDataWatcher());
            }

            return container;
        });

        return this;
    }

    /**
     * Enqueues the de-spawn of the wrapped npc.
     *
     * @return The same instance of this class, for chaining.
     */
    @NotNull
    public VisibilityModifier queueDestroy() {
        super.queueInstantly((targetNpc, target) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_DESTROY);
            if (MINECRAFT_VERSION >= 17) {
                container.getIntLists().write(0, Collections.singletonList(targetNpc.getEntityId()));
            } else {
                container.getIntegerArrays().write(0, new int[]{super.npc.getEntityId()});
            }
            return container;
        });

        return this;
    }
}