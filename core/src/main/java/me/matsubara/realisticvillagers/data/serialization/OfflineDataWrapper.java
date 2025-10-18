package me.matsubara.realisticvillagers.data.serialization;

import lombok.Getter;
import me.matsubara.realisticvillagers.data.LastKnownPosition;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// NOTE: This class is only used for data serialization.
// Previous implementations were version-dependent, meaning that the data will be lost when updating to a new version.
@Getter
public class OfflineDataWrapper implements ConfigurationSerializable {

    private final UUID uuid;
    private final String villagerName;
    private final String sex;
    private final OfflineDataWrapper partner;
    private final List<OfflineDataWrapper> partners = new ArrayList<>();
    private final boolean isPartnerVillager;
    private final long lastProcreation;
    private final int skinTextureId;
    private final int kidSkinTextureId;
    private final OfflineDataWrapper father;
    private final OfflineDataWrapper mother;
    private final boolean isFatherVillager;
    private final List<OfflineDataWrapper> childrens = new ArrayList<>();
    private final List<String> targetEntities = new ArrayList<>();
    private final List<GossipEntryWrapper> entries = new ArrayList<>();
    private final List<UUID> players = new ArrayList<>();
    private final LastKnownPosition lastKnownPosition;
    private final String shoulderEntityLeft;
    private final String shoulderEntityRight;
    private final UUID bedHomeWorld;
    private final Vector bedHome;
    private final boolean wasInfected;
    private final boolean equipped;
    private final int foodLevel;
    private final int tickTimer;
    private final float saturationLevel;
    private final float exhaustionLevel;

    public static final String UUID = "UUID";
    public static final String NAME = "Name";
    public static final String SEX = "Sex";
    public static final String PARTNER = "Partner";
    public static final String PARTNERS = "Partners";
    public static final String IS_PARTNER_VILLAGER = "IsPartnerVillager";
    public static final String LAST_PROCREATION = "LastProcreation";
    public static final String SKIN_TEXTURE_ID = "SkinTextureId";
    public static final String KID_SKIN_TEXTURE_ID = "KidSkinTextureId";
    public static final String FATHER = "Father";
    public static final String MOTHER = "Mother";
    public static final String IS_FATHER_VILLAGER = "IsFatherVillager";
    public static final String WAS_INFECTED = "WasInfected";
    public static final String CHILDRENS = "Childrens";
    public static final String TARGET_ENTITIES = "TargetEntities";
    public static final String BED_HOME_WORLD = "BedHomeWorld";
    public static final String BED_HOME_POS = "BedHomePos";
    public static final String EQUIPPED = "Equipped";
    public static final String SHOULDER_ENTITY_LEFT = "ShoulderEntityLeft";
    public static final String SHOULDER_ENTITY_RIGHT = "ShoulderEntityRight";
    public static final String GOSSIP_ENTRIES = "GossipsEntries";
    public static final String PLAYERS = "Players";
    public static final String LAST_WORLD = "LastWorld";
    public static final String LAST_POS = "LastPos";
    public static final String FOOD_LEVEL = "FoodLevel";
    public static final String FOOD_TICK_TIMER = "FoodTickTimer";
    public static final String FOOD_SATURATION_LEVEL = "FoodSaturationLevel";
    public static final String FOOD_EXHAUSTION_LEVEL = "FoodExhaustionLevel";

    public OfflineDataWrapper(UUID uuid,
                              String villagerName,
                              String sex,
                              OfflineDataWrapper partner,
                              boolean isPartnerVillager,
                              long lastProcreation,
                              int skinTextureId,
                              int kidSkinTextureId,
                              OfflineDataWrapper father,
                              OfflineDataWrapper mother,
                              boolean isFatherVillager,
                              LastKnownPosition lastKnownPosition,
                              Collection<OfflineDataWrapper> partners,
                              Collection<OfflineDataWrapper> childrens,
                              Collection<String> targetEntities,
                              Collection<UUID> players,
                              Collection<GossipEntryWrapper> entries,
                              String shoulderEntityLeft,
                              String shoulderEntityRight,
                              UUID bedHomeWorld,
                              Vector bedHome,
                              boolean wasInfected,
                              boolean equipped,
                              int foodLevel,
                              int tickTimer,
                              float saturationLevel,
                              float exhaustionLevel) {
        this.uuid = uuid;
        this.villagerName = villagerName;
        this.sex = sex;
        this.partner = partner;
        this.isPartnerVillager = isPartnerVillager;
        this.lastProcreation = lastProcreation;
        this.skinTextureId = skinTextureId;
        this.kidSkinTextureId = kidSkinTextureId;
        this.father = father;
        this.mother = mother;
        this.isFatherVillager = isFatherVillager;
        this.lastKnownPosition = lastKnownPosition;
        this.shoulderEntityLeft = shoulderEntityLeft;
        this.shoulderEntityRight = shoulderEntityRight;
        this.bedHomeWorld = bedHomeWorld;
        this.bedHome = bedHome;
        this.wasInfected = wasInfected;
        this.equipped = equipped;
        this.foodLevel = foodLevel;
        this.tickTimer = tickTimer;
        this.saturationLevel = saturationLevel;
        this.exhaustionLevel = exhaustionLevel;
        this.partners.addAll(partners);
        this.childrens.addAll(childrens);
        this.targetEntities.addAll(targetEntities);
        this.players.addAll(players);
        this.entries.addAll(entries);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(UUID, uuid.toString());
        if (lastKnownPosition != null) {
            result.put(LAST_WORLD, lastKnownPosition.world());
            result.put(LAST_POS, new Vector(lastKnownPosition.x(), lastKnownPosition.y(), lastKnownPosition.z()));
        }
        if (villagerName != null) result.put(NAME, villagerName);
        if (sex != null) result.put(SEX, sex);
        if (partner != null) result.put(PARTNER, partner);
        result.put(PARTNERS, partners);
        result.put(IS_PARTNER_VILLAGER, isPartnerVillager);
        result.put(LAST_PROCREATION, lastProcreation);
        result.put(SKIN_TEXTURE_ID, skinTextureId);
        result.put(KID_SKIN_TEXTURE_ID, kidSkinTextureId);
        if (father != null) result.put(FATHER, father);
        if (mother != null) result.put(MOTHER, mother);
        result.put(IS_FATHER_VILLAGER, isFatherVillager);
        result.put(CHILDRENS, childrens);
        result.put(TARGET_ENTITIES, targetEntities.stream().toList());
        result.put(GOSSIP_ENTRIES, entries);
        result.put(PLAYERS, players.stream().map(java.util.UUID::toString).toList());
        if (shoulderEntityLeft != null) result.put(SHOULDER_ENTITY_LEFT, shoulderEntityLeft);
        if (shoulderEntityRight != null) result.put(SHOULDER_ENTITY_RIGHT, shoulderEntityRight);
        if (bedHomeWorld != null) result.put(BED_HOME_WORLD, bedHomeWorld.toString());
        if (bedHome != null) result.put(BED_HOME_POS, bedHome);
        result.put(WAS_INFECTED, wasInfected);
        result.put(EQUIPPED, equipped);
        result.put(FOOD_LEVEL, foodLevel);
        result.put(FOOD_TICK_TIMER, tickTimer);
        result.put(FOOD_SATURATION_LEVEL, saturationLevel);
        result.put(FOOD_EXHAUSTION_LEVEL, exhaustionLevel);
        return result;
    }

    @SuppressWarnings({"unused", "unchecked"})
    public static @NotNull OfflineDataWrapper deserialize(@NotNull Map<String, Object> args) {
        UUID uuid = PluginUtils.getOrDefault(args, UUID, String.class, java.util.UUID::fromString, null);
        String world = PluginUtils.getOrDefault(args, LAST_WORLD, String.class);
        Vector vector = PluginUtils.getOrDefault(args, LAST_POS, Vector.class, new Vector(0, 0, 0));
        LastKnownPosition lastKnownPosition = new LastKnownPosition(world, vector.getX(), vector.getY(), vector.getZ());
        String villagerName = PluginUtils.getOrDefault(args, NAME, String.class);
        String sex = PluginUtils.getOrDefault(args, SEX, String.class);
        OfflineDataWrapper partner = PluginUtils.getOrDefault(args, PARTNER, OfflineDataWrapper.class);
        List<OfflineDataWrapper> partners = PluginUtils.getOrDefault(args, PARTNERS, List.class, Collections.emptyList());
        boolean isPartnerVillager = PluginUtils.getOrDefault(args, IS_PARTNER_VILLAGER, Boolean.class);
        long lastProcreation = PluginUtils.getOrDefault(args, LAST_PROCREATION, Long.class);
        int skinTextureId = PluginUtils.getOrDefault(args, SKIN_TEXTURE_ID, Integer.class, -1);
        int kidSkinTextureId = PluginUtils.getOrDefault(args, KID_SKIN_TEXTURE_ID, Integer.class, -1);
        OfflineDataWrapper father = PluginUtils.getOrDefault(args, FATHER, OfflineDataWrapper.class);
        OfflineDataWrapper mother = PluginUtils.getOrDefault(args, MOTHER, OfflineDataWrapper.class);
        boolean isFatherVillager = PluginUtils.getOrDefault(args, IS_FATHER_VILLAGER, Boolean.class);
        List<OfflineDataWrapper> childrens = PluginUtils.getOrDefault(args, CHILDRENS, List.class, Collections.emptyList());
        List<String> targetEntities = PluginUtils.getOrDefault(args, TARGET_ENTITIES, List.class, Collections.emptyList());
        List<UUID> players = PluginUtils.getOrDefault(args, PLAYERS, List.class, Collections.emptyList());
        List<GossipEntryWrapper> entries = PluginUtils.getOrDefault(args, GOSSIP_ENTRIES, List.class, Collections.emptyList());
        String shoulderEntityLeft = PluginUtils.getOrDefault(args, SHOULDER_ENTITY_LEFT, String.class);
        String shoulderEntityRight = PluginUtils.getOrDefault(args, SHOULDER_ENTITY_RIGHT, String.class);
        UUID bedHomeWorld = PluginUtils.getOrDefault(args, BED_HOME_WORLD, String.class, java.util.UUID::fromString, null);
        Vector bedHome = PluginUtils.getOrDefault(args, BED_HOME_POS, Vector.class, new Vector(0, 0, 0));
        boolean wasInfected = PluginUtils.getOrDefault(args, WAS_INFECTED, Boolean.class);
        boolean equipped = PluginUtils.getOrDefault(args, EQUIPPED, Boolean.class);
        int foodLevel = PluginUtils.getOrDefault(args, FOOD_LEVEL, Integer.class, 20);
        int tickTimer = PluginUtils.getOrDefault(args, FOOD_TICK_TIMER, Integer.class);
        float saturationLevel = PluginUtils.getOrDefault(args, FOOD_SATURATION_LEVEL, Float.class, 5.0f);
        float exhaustionLevel = PluginUtils.getOrDefault(args, FOOD_EXHAUSTION_LEVEL, Float.class);

        return new OfflineDataWrapper(
                uuid,
                villagerName,
                sex,
                partner,
                isPartnerVillager,
                lastProcreation,
                skinTextureId,
                kidSkinTextureId,
                father,
                mother,
                isFatherVillager,
                lastKnownPosition,
                partners,
                childrens,
                targetEntities,
                players,
                entries,
                shoulderEntityLeft,
                shoulderEntityRight,
                bedHomeWorld,
                bedHome,
                wasInfected,
                equipped,
                foodLevel,
                tickTimer,
                saturationLevel,
                exhaustionLevel);
    }
}