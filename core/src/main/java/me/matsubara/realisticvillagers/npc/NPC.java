package me.matsubara.realisticvillagers.npc;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.base.Preconditions;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.NametagManager;
import me.matsubara.realisticvillagers.npc.modifier.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("deprecation")
@Getter
public class NPC {

    private final Collection<Player> seeingPlayers = new CopyOnWriteArraySet<>();
    private final int entityId;
    private final WrappedGameProfile profile;
    private final SpawnCustomizer spawnCustomizer;
    private final IVillagerNPC villager;

    public NPC(WrappedGameProfile profile, SpawnCustomizer spawnCustomizer, int entityId, IVillagerNPC villager) {
        this.entityId = entityId;
        this.spawnCustomizer = spawnCustomizer;
        this.villager = villager;

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

    public void show(Player player, RealisticVillagers plugin) {
        show(player, plugin, null);
    }

    public void show(Player player, RealisticVillagers plugin, @Nullable Location location) {
        seeingPlayers.add(player);

        VisibilityModifier modifier = visibility();
        modifier.queuePlayerListChange(PlayerInfoAction.ADD_PLAYER).send(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            modifier.queueSpawn(location).send(player);
            spawnCustomizer.handleSpawn(this, player);

            NametagManager nametagManager = plugin.getNametagManager();
            if (nametagManager != null) nametagManager.showNametag(villager, player);

            // Keeping the NPC longer in the player list, otherwise the skin might not be shown sometimes.
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> modifier.queuePlayerListChange(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER).send(player),
                    40);
        }, 10L);
    }

    public void hide(Player player, @NotNull RealisticVillagers plugin) {
        visibility()
                .queuePlayerListChange(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)
                .queueDestroy()
                .send(player);

        NametagManager nametagManager = plugin.getNametagManager();
        if (nametagManager != null) nametagManager.hideNametag(villager, player);

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

    public static class Builder {

        private WrappedGameProfile profile;
        private int entityId = -1;
        private IVillagerNPC villager;

        private SpawnCustomizer spawnCustomizer = (npc, player) -> {
        };

        private Builder() {
        }

        public Builder profile(WrappedGameProfile profile) {
            this.profile = profile;
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

        public Builder entity(IVillagerNPC villager) {
            this.villager = villager;
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

            NPC npc = new NPC(profile, spawnCustomizer, entityId, villager);
            pool.takeCareOf(npc);
            return npc;
        }
    }
}