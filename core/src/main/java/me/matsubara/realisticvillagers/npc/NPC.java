package me.matsubara.realisticvillagers.npc;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.base.Preconditions;
import me.matsubara.realisticvillagers.npc.modifier.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("deprecation")
public class NPC {

    private final Collection<Player> seeingPlayers = new CopyOnWriteArraySet<>();
    private final int entityId;
    private final WrappedGameProfile profile;
    private final SpawnCustomizer spawnCustomizer;
    private Location location;

    private NPC(WrappedGameProfile profile, Location location, SpawnCustomizer spawnCustomizer, int entityId) {
        this.entityId = entityId;
        this.location = location;
        this.spawnCustomizer = spawnCustomizer;

        // No profile -> create a random one.
        if (profile == null) {
            this.profile = new WrappedGameProfile(UUID.randomUUID(), randomName());
        } else if (profile.getName() == null || profile.getUUID() == null) {
            this.profile = new WrappedGameProfile(
                    profile.getUUID() == null ? UUID.randomUUID() : profile.getUUID(),
                    profile.getName() == null ? randomName() : profile.getName());
        } else {
            this.profile = profile;
        }
    }

    private static @NotNull String randomName() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Contract(" -> new")
    public static NPC.@NotNull Builder builder() {
        return new Builder();
    }

    protected void show(Player player, Plugin plugin, long tabListRemoveTicks) {
        seeingPlayers.add(player);

        VisibilityModifier modifier = visibility();
        modifier.queuePlayerListChange(PlayerInfoAction.ADD_PLAYER).send(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            modifier.queueSpawn().send(player);
            spawnCustomizer.handleSpawn(this, player);

            if (tabListRemoveTicks >= 0) {
                // Keeping the NPC longer in the player list, otherwise the skin might not be shown sometimes.
                Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> modifier.queuePlayerListChange(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER).send(player),
                        tabListRemoveTicks);
            }
        }, 10L);
    }

    protected void hide(Player player) {
        visibility()
                .queuePlayerListChange(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)
                .queueDestroy()
                .send(player);
        removeSeeingPlayer(player);
    }

    protected void removeSeeingPlayer(Player player) {
        seeingPlayers.remove(player);
    }

    public Collection<Player> getSeeingPlayers() {
        return Collections.unmodifiableCollection(seeingPlayers);
    }

    public boolean isShownFor(Player player) {
        return seeingPlayers.contains(player);
    }

    public RotationModifier rotation() {
        return new RotationModifier(this);
    }

    public EquipmentModifier equipment() {
        return new EquipmentModifier(this);
    }

    public MetadataModifier metadata() {
        return new MetadataModifier(this);
    }

    public VisibilityModifier visibility() {
        return new VisibilityModifier(this);
    }

    public TeleportModifier teleport() {
        return new TeleportModifier(this);
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public WrappedGameProfile getProfile() {
        return profile;
    }

    public int getEntityId() {
        return entityId;
    }

    public Location getLocation() {
        return location;
    }

    public static class Builder {

        private WrappedGameProfile profile;
        private int entityId = -1;

        private Location location = new Location(Bukkit.getWorlds().get(0), 0D, 0D, 0D);
        private SpawnCustomizer spawnCustomizer = (npc, player) -> {
        };

        private Builder() {
        }

        public Builder profile(WrappedGameProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder location(Location location) {
            this.location = Preconditions.checkNotNull(location, "location");
            return this;
        }

        public Builder spawnCustomizer(SpawnCustomizer spawnCustomizer) {
            this.spawnCustomizer = Preconditions.checkNotNull(spawnCustomizer, "spawnCustomizer");
            return this;
        }

        public Builder entityId(int entityId) {
            this.entityId = entityId;
            return this;
        }

        @NotNull
        public NPC build(NPCPool pool) {
            if (entityId == -1) {
                throw new IllegalArgumentException("No entity id given!");
            }

            if (profile == null) {
                throw new IllegalArgumentException("No profile given!");
            }

            NPC npc = new NPC(profile, location, spawnCustomizer, entityId);
            pool.takeCareOf(npc);
            return npc;
        }
    }
}