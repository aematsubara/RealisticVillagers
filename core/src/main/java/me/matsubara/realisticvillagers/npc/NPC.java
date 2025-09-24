package me.matsubara.realisticvillagers.npc;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.google.common.base.Preconditions;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Nameable;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.types.SkinGUI;
import me.matsubara.realisticvillagers.npc.modifier.*;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

@Getter
public class NPC {

    private final RealisticVillagers plugin;
    private final Collection<Player> seeingPlayers = new CopyOnWriteArraySet<>();
    private final int entityId;
    private final UserProfile profile;
    private final SpawnCustomizer spawnCustomizer;
    private final IVillagerNPC npc;

    private static final Set<Villager.Profession> UNEMPLOYED = Set.of(Villager.Profession.NONE, Villager.Profession.NITWIT);
    private static final int IGNORE = -1;
    private static final int NO_BLOCK = -2;
    private static final boolean ENABLED = XReflection.supports(20, 2);
    private static final Color NAMETAG_BACKGROUND_COLOR = ENABLED ? Color.fromARGB((int) (0.35 * 255), 0, 0, 0) : null;

    public NPC(RealisticVillagers plugin, UserProfile profile, SpawnCustomizer spawnCustomizer, int entityId, IVillagerNPC npc) {
        this.plugin = plugin;
        this.entityId = entityId;
        this.spawnCustomizer = spawnCustomizer;
        this.npc = npc;
        this.profile = profile;
    }

    public void refreshNametags(Player player) {
        hideNametags(player);
        spawnNametags(player, true);
    }

    public void spawnNametags(Player player, boolean shouldSpawn) {
        if (!ENABLED) return;

        int itemId = spawnDisplayEntity(player, false, shouldSpawn);
        if (itemId == IGNORE) return;

        ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
        Object channel = SpigotReflectionUtil.getChannel(player);

        WrapperPlayServerSetPassengers itemPassengers = new WrapperPlayServerSetPassengers(npc.bukkit().getEntityId(), new int[]{itemId});
        manager.sendPacket(channel, itemPassengers);

        int blockId = spawnDisplayEntity(player, true, shouldSpawn);
        if (blockId == IGNORE) return;

        // @show-job-block is set to false, we need to hide it.
        if (blockId == NO_BLOCK) {
            hideBlockItem(player);
            return;
        }

        WrapperPlayServerSetPassengers blockPassengers = new WrapperPlayServerSetPassengers(itemId, new int[]{blockId});
        manager.sendPacket(channel, blockPassengers);
    }

    public void sendPassengers(Player player) {
        if (!ENABLED) return;

        if (!(npc instanceof Nameable nameable)) return;
        if (Config.DISABLE_NAMETAGS.asBool()) return;

        int itemId = nameable.getNametagEntity();
        if (itemId == IGNORE) return;

        ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
        Object channel = SpigotReflectionUtil.getChannel(player);

        WrapperPlayServerSetPassengers itemPassengers = new WrapperPlayServerSetPassengers(npc.bukkit().getEntityId(), new int[]{itemId});
        manager.sendPacket(channel, itemPassengers);

        if (!Config.CUSTOM_NAME_SHOW_JOB_BLOCK.asBool()) return;

        int blockId = nameable.getNametagItemEntity();
        if (blockId == IGNORE || blockId == NO_BLOCK) return;

        WrapperPlayServerSetPassengers blockPassengers = new WrapperPlayServerSetPassengers(itemId, new int[]{blockId});
        manager.sendPacket(channel, blockPassengers);
    }

    private void hideBlockItem(Player player) {
        if (!(npc instanceof Nameable nameable)) return;

        int nametagItemEntity = nameable.getNametagItemEntity();
        if (nametagItemEntity == -1) return;

        sendDestroyPacket(player, new int[]{nametagItemEntity});
    }

    public void hideNametags(Player player) {
        if (!ENABLED) return;
        if (!(npc instanceof Nameable nameable)) return;

        int nametagEntity = nameable.getNametagEntity();
        int nametagItemEntity = nameable.getNametagItemEntity();

        int[] ids = Stream.of(nametagEntity, nametagItemEntity)
                .filter(id -> id != IGNORE)
                .mapToInt(Integer::intValue)
                .toArray();

        if (ids.length == 0) return;

        sendDestroyPacket(player, ids);
    }

    private void sendDestroyPacket(Player player, int[] ids) {
        ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
        Object channel = SpigotReflectionUtil.getChannel(player);

        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(ids);
        manager.sendPacket(channel, destroy);
    }

    public List<String> getLines(@NotNull LivingEntity entity) {
        EntityType type = entity.getType();
        if (type == EntityType.VILLAGER) return Config.CUSTOM_NAME_VILLAGER_LINES.asStringList();
        if (type == EntityType.WANDERING_TRADER) return Config.CUSTOM_NAME_TRADER_LINES.asStringList();
        return Collections.emptyList();
    }

    private int spawnDisplayEntity(Player player, boolean block, boolean shouldSpawn) {
        if (!(npc instanceof Nameable nameable)) return IGNORE;
        if (Config.DISABLE_NAMETAGS.asBool()) return IGNORE;
        if (block && !Config.CUSTOM_NAME_SHOW_JOB_BLOCK.asBool()) return NO_BLOCK;

        LivingEntity bukkit = npc.bukkit();
        if (bukkit == null) return IGNORE;
        if (bukkit.hasPotionEffect(PotionEffectType.INVISIBILITY)) return IGNORE;

        int temp = block ? nameable.getNametagItemEntity() : nameable.getNametagEntity();
        int id = temp == -1 ? SpigotReflectionUtil.generateEntityId() : temp;
        if (block) nameable.setNametagItemEntity(id);
        else nameable.setNametagEntity(id);

        List<EntityData<?>> data = new ArrayList<>();
        fillGlobalData(data, block);

        if (block) {
            BlockData blockData;
            if (bukkit instanceof Villager villager && !npc.is(Villager.Profession.NONE, Villager.Profession.NITWIT)) {
                Material material = SkinGUI.PROFESSION_ICON.get(villager.getProfession().name());
                blockData = createBlockData(villager, material);
            } else {
                blockData = Material.AIR.createBlockData();
            }
            WrappedBlockState state = SpigotConversionUtil.fromBukkitBlockData(blockData);
            data.add(new EntityData<>(23, EntityDataTypes.BLOCK_STATE, state.getGlobalId())); // Displayed block state = WrappedBlockState#getGlobalId()
        } else {
            data.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, Component.text(getNameText(bukkit)))); // Text
            data.add(new EntityData<>(24, EntityDataTypes.INT, 200)); // Line width
            data.add(new EntityData<>(25, EntityDataTypes.INT, NAMETAG_BACKGROUND_COLOR.asARGB())); // Background color = Color#asARGB() / 1073741824
            data.add(new EntityData<>(26, EntityDataTypes.BYTE, (byte) -1)); // Text opacity
            // Flags (Has shadow = 0x01 / See through = 0x02 / Use default background color = 0x04 / Alignment = ?) / 0
            data.add(new EntityData<>(27, EntityDataTypes.BYTE, (byte) 0x02));
        }

        ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
        Object channel = SpigotReflectionUtil.getChannel(player);

        if (shouldSpawn) {
            Location at = bukkit.getLocation();
            at.setPitch(0.0f);

            manager.sendPacket(channel, new WrapperPlayServerSpawnEntity(
                    id,
                    UUID.randomUUID(),
                    block ? EntityTypes.BLOCK_DISPLAY : EntityTypes.TEXT_DISPLAY,
                    SpigotConversionUtil.fromBukkitLocation(at),
                    at.getYaw(),
                    0,
                    null));
        }

        manager.sendPacket(channel, new WrapperPlayServerEntityMetadata(id, data));
        return id;
    }

    private @NotNull String getNameText(LivingEntity bukkit) {
        StringBuilder builder = new StringBuilder();
        List<String> lines = getLines(bukkit);

        for (int i = 0; i < lines.size(); i++) {
            // For some reason, the name is null?
            String villagerName = Objects.requireNonNullElse(npc.getVillagerName(), Config.UNKNOWN.asStringTranslated());
            String line = PluginUtils.translate(lines.get(i).replace("%villager-name%", villagerName));

            builder.append(bukkit instanceof Villager villager ? line
                    .replace("%villager-name%", villagerName)
                    .replace("%level%", String.valueOf(villager.getVillagerLevel()))
                    .replace("%profession%", plugin.getProfessionFormatted(villager.getProfession().name()
                            .toLowerCase(Locale.ROOT), npc.isMale())) : line);

            if (i != lines.size() - 1) builder.append("\n");
        }

        return builder.toString();
    }

    private @NotNull BlockData createBlockData(Villager villager, Material material) {
        BlockData data = getJobBlockData(villager);
        return data != null ? data : material.createBlockData();
    }

    private @Nullable BlockData getJobBlockData(@NotNull Villager villager) {
        if (UNEMPLOYED.contains(villager.getProfession())) return null;

        Location pos = getJobSiteLocation(villager);
        if (pos == null) return null;

        World world = pos.getWorld();
        if (world == null) return null;

        return world.getBlockData(pos);
    }

    private @Nullable Location getJobSiteLocation(LivingEntity living) {
        try {
            return living.getMemory(MemoryKey.JOB_SITE);
        } catch (Exception exception) {
            return null;
        }
    }

    private void fillGlobalData(List<EntityData<?>> data, boolean block) {
        Vector3f translation, scale;
        if (block) {
            int amountOfLines = getLines(npc.bukkit()).size();
            float y = amountOfLines * 0.275f + 0.275f;
            translation = new Vector3f(-0.1f, y, -0.1f);
            scale = new Vector3f(0.25f, 0.25f, 0.25f);
        } else {
            translation = new Vector3f(0.0f, 0.25f, 0.0f);
            scale = new Vector3f(1.0f, 1.0f, 1.0f);
        }

        data.add(new EntityData<>(8, EntityDataTypes.INT, 0)); // Interpolation delay
        data.add(new EntityData<>(9, EntityDataTypes.INT, 0)); // Transformation interpolation duration
        data.add(new EntityData<>(10, EntityDataTypes.INT, 0)); // Position/Rotation interpolation duration
        data.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, translation)); // Translation / 0.0, 0.0, 0.0
        data.add(new EntityData<>(12, EntityDataTypes.VECTOR3F, scale)); // Scale
        data.add(new EntityData<>(13, EntityDataTypes.QUATERNION, new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f))); // Rotation left
        data.add(new EntityData<>(14, EntityDataTypes.QUATERNION, new Quaternion4f(0.0f, 0.0f, 0.0f, 1.0f))); // Rotation right
        // Billboard Constraints (0 = FIXED, 1 = VERTICAL, 2 = HORIZONTAL, 3 = CENTER) / 0
        data.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) (block ? 1 : 3)));
        data.add(new EntityData<>(16, EntityDataTypes.INT, -1)); // Brightness override (blockLight << 4 | skyLight << 20)
        data.add(new EntityData<>(17, EntityDataTypes.FLOAT, 1.0f)); // View range
        data.add(new EntityData<>(18, EntityDataTypes.FLOAT, 0.0f)); // Shadow radius
        data.add(new EntityData<>(19, EntityDataTypes.FLOAT, 1.0f)); // Shadow strength
        data.add(new EntityData<>(20, EntityDataTypes.FLOAT, 0.0f)); // Width
        data.add(new EntityData<>(21, EntityDataTypes.FLOAT, 0.0f)); // Height
        data.add(new EntityData<>(22, EntityDataTypes.INT, -1)); // Glow color override
    }

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public void show(Player player) {
        show(player, null);
    }

    public void show(Player player, @Nullable Location location) {
        seeingPlayers.add(player);

        VisibilityModifier modifier = visibility();
        modifier.queuePlayerListChange(false).send(player);
        modifier.queueSpawn(location).send(player);
        spawnCustomizer.handleSpawn(this, player);

        BukkitScheduler scheduler = Bukkit.getScheduler();

        // Spawn the nametags a few ticks after the NPC spawns.
        scheduler.runTaskLater(plugin,
                () -> {
                    if (isShownFor(player)) {
                        spawnNametags(player, true);
                    }
                },
                10L);

        // Keeping the NPC longer in the player list, otherwise the skin might not be shown sometimes.
        scheduler.runTaskLater(
                plugin,
                () -> modifier.queuePlayerListChange(true).send(player),
                40L);
    }

    public void hide(Player player) {
        hideNametags(player);
        visibility()
                .queuePlayerListChange(true)
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

    public static class Builder {

        private UserProfile profile;
        private int entityId = -1;
        private IVillagerNPC villager;

        private SpawnCustomizer spawnCustomizer = (npc, player) -> {
        };

        private Builder() {
        }

        public Builder profile(UserProfile profile) {
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

            NPC npc = new NPC(pool.getPlugin(), profile, spawnCustomizer, entityId, villager);
            pool.takeCareOf(npc);
            return npc;
        }
    }
}