package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum Config {
    // ── Main config.yml ──────────────────────────────────────────────────────
    RENDER_DISTANCE("render-distance"),
    MINESKIN_API_KEY("mineskin-api-key"),
    DISABLE_INTERACTIONS("disable-interactions"),
    MAX_GOSSIP_TOPICS("max-gossip-topics"),
    DISABLE_SPECIAL_PRICES("disable-special-prices"),
    ATTACK_PLAYER_WEARING_MONSTER_SKULL("attack-player-wearing-monster-skull"),
    ATTACK_PLAYER_PLAYING_GOAT_HORN_SEEK("attack-player-playing-goat-horn-seek"),
    GOAT_HORN_SEEK_RANGE("goat-horn-seek-range"),
    MELEE_ATTACK_COOLDOWN("melee-attack-cooldown"),
    VILLAGER_FIX_IRON_GOLEM_WITH_IRON("villager-fix-iron-golem-with-iron"),
    VILLAGER_FIX_IRON_GOLEM_COOLDOWN("villager-fix-iron-golem-cooldown"),
    VILLAGER_HELP_FAMILY("villager-help-family"),
    VILLAGER_HELP_FAMILY_COOLDOWN("villager-help-family-cooldown"),
    AUTO_ASSIGN_FAMILY("auto-assign-family"),
    PARTNER_JOKE_ALWAYS_SUCCESS("partner-joke-always-success"),
    CHANCE_OF_CHAT_INTERACTION_SUCCESS("chance-of-chat-interaction-success"),

    ZOMBIE_INFECTION("zombie-infection"),
    WITCH_CONVERTION("witch-convertion"),
    WITCH_CONVERTION_FROM_VILLAGER_TRIDENT("witch-convertion-from-villager-trident"),

    BABY_GROW_COOLDOWN("baby-grow-cooldown"),
    PROCREATION_COOLDOWN("procreation-cooldown"),

    ARROWS_PASS_THROUGH_OTHER_VILLAGERS("arrows-pass-through-other-villagers"),
    ARROW_STATUS("arrow-status"),

    VILLAGER_MAX_HEALTH("villager-max-health"),

    TELEPORT_WHEN_FOLLOWING_IF_FAR_AWAY("teleport-when-following-if-far-away"),
    TELEPORT_WHEN_FOLLOWING_DISTANCE("teleport-when-following-distance"),

    VILLAGER_SPAWN_IRON_GOLEM("villager-spawn-iron-golem"),

    BAD_GIFT_REPUTATION("bad-gift-reputation"),
    GIFT_MODE("gift-mode"),
    WEDDING_RING_REPUTATION("wedding-ring-reputation"),
    CROSS_REPUTATION("cross-reputation"),
    BABY_REPUTATION("baby-reputation"),
    CHAT_INTERACT_REPUTATION("chat-interact-reputation"),

    HOSTILE_DETECTION_RANGE("hostile-detection-range"),

    DROP_WHOLE_INVENTORY("drop-whole-inventory"),

    ATTACK_DAMAGE("attack-damage"),
    CUSTOM_MELEE_WEAPONS("custom-melee-weapons"),
    CHANCE_OF_WEARING_HALLOWEEN_MASK("chance-of-wearing-halloween-mask"),
    RANGE_WEAPON_POWER("range-weapon-power"),
    VILLAGER_INVENTORY_SIZE("villager-inventory-size"),

    DIVORCE_IF_REPUTATION_IS_LESS_THAN("divorce-if-reputation-is-less-than"),

    VILLAGER_TITLE_ARTICLE_MALE("villager-title-article.male"),
    VILLAGER_TITLE_ARTICLE_FEMALE("villager-title-article.female"),
    SHOW_TITLE_IN_VILLAGER_CHAT_MESSAGE("show-title-in-villager-chat-message"),

    VILLAGER_ATTACK_PLAYER_DURING_RAID("villager-attack-player-during-raid"),
    IRON_GOLEM_ATTACK_PLAYER_DURING_RAID("iron-golem-attack-player-during-raid"),

    VILLAGER_DEFEND_FAMILY_MEMBER("villager-defend-family-member"),
    VILLAGER_DEFEND_HERO_OF_THE_VILLAGE("villager-defend-hero-of-the-village"),
    VILLAGER_DEFEND_FOLLOWING_PLAYER("villager-defend-following-player"),
    VILLAGER_DEFEND_ATTACK_PLAYERS("villager-defend-attack-players"),

    REPUTATION_REQUIRED_TO_MARRY("reputation-required-to-marry"),
    REPUTATION_REQUIRED_TO_PROCREATE("reputation-required-to-procreate"),

    APPEARANCE_BODY_SHAPES("appearance.body-shapes"),
    APPEARANCE_BUST("appearance.bust"),
    APPEARANCE_BUILD("appearance.build"),
    APPEARANCE_PLAYERS("appearance.players"),
    APPEARANCE_SERVE_PACK("appearance.serve-pack"),
    APPEARANCE_PACK_PORT("appearance.pack-port"),
    APPEARANCE_PACK_HOST("appearance.pack-host"),
    NAMETAGS_ONLY_WHEN_LOOKING("nametags.only-when-looking"),
    NAMETAGS_LOOK_RANGE("nametags.look-range"),

    ANNOYING_METER_ENABLED("annoying-meter.enabled"),
    ANNOYING_METER_IGNORE_FAMILY_MEMBERS("annoying-meter.ignore-family-members"),
    ANNOYING_METER_CLEAR_AFTER_SUCCESS_INTERACTION("annoying-meter.clear-after-success-interaction"),
    ANNOYING_METER_MAX_CLICKS("annoying-meter.max-clicks"),
    ANNOYING_METER_TIMEFRAME("annoying-meter.timeframe"),
    ANNOYING_METER_REPUTATION_LOSS("annoying-meter.reputation-loss"),
    ANNOYING_METER_ANGRY_PARTICLES("annoying-meter.angry-particles"),

    VILLAGE_ENABLED("village.enabled"),
    VILLAGE_DEFAULT_RADIUS("village.default-radius"),
    VILLAGE_MAYOR_REPUTATION_DIVISOR("village.mayor-reputation-divisor"),
    VILLAGE_SAVE_INTERVAL("village.save-interval"),
    VILLAGE_VERTICAL_RANGE("village.vertical-range"),
    VILLAGE_RESIDENT_CACHE_MILLIS("village.resident-cache-millis"),
    VILLAGE_MAYOR_REPUTATION_TO_COMMISSION("village.mayor.reputation-required-to-commission"),
    VILLAGE_MAYOR_REQUIRE_FAMILY("village.mayor.require-family"),
    VILLAGE_MAYOR_HEAD_BLOCK("village.mayor.head-block"),

    VILLAGE_ELECTION_ENABLED("village.election.enabled"),
    VILLAGE_ELECTION_DURATION("village.election.duration"),
    VILLAGE_ELECTION_CANDIDATES("village.election.candidates"),
    VILLAGE_ELECTION_PLAYER_VOTE_WEIGHT("village.election.player-vote-weight"),
    VILLAGE_ELECTION_VILLAGER_VOTE_WEIGHT("village.election.villager-vote-weight"),
    VILLAGE_ELECTION_CHECK_INTERVAL("village.election.check-interval"),

    VILLAGE_ECONOMY_ENABLED("village.economy.enabled"),
    VILLAGE_ECONOMY_INTERVAL("village.economy.interval"),
    VILLAGE_ECONOMY_CHANCE("village.economy.chance"),
    VILLAGE_ECONOMY_MIN_AMOUNT("village.economy.min-amount"),
    VILLAGE_ECONOMY_MAX_AMOUNT("village.economy.max-amount"),

    VILLAGE_BUILD_COLLISION("village.build.collision"),
    VILLAGE_BUILD_CLEARANCE("village.build.clearance"),
    VILLAGE_BUILD_EMPTY_CONTAINERS("village.build.empty-containers"),
    VILLAGE_BUILD_INSIDE_ONLY("village.build.inside-village-only"),
    VILLAGE_BUILD_PERSONAL_DISCOVERY("village.build.personal-discovery"),
    VILLAGE_BUILD_DISCOVERY_LIMIT("village.build.discovery-limit"),
    VILLAGE_BUILD_BLOCKS_PER_SECOND("village.build.blocks-per-second"),

    VILLAGE_GRAVES_ENABLED("village.graves.enabled"),

    VILLAGE_HARVEST_ENABLED("village.harvest.enabled"),
    VILLAGE_HARVEST_INTERVAL("village.harvest.interval"),
    VILLAGE_HARVEST_LIMIT_TO_VILLAGE("village.harvest.limit-to-village"),
    VILLAGE_HARVEST_WORKSTATION_RADIUS("village.harvest.workstation-radius"),

    IGNORE_SEX_WHEN_PROCREATING("villager-farm.ignore-sex-when-procreating"),
    ALLOW_PARTNER_CHEATING("villager-farm.allow-partner-cheating"),
    ALLOW_PARTNER_CHEATING_FOR_ALL("villager-farm.allow-partner-cheating-for-all"),
    ALLOW_PROCREATION_BETWEEN_FAMILY_MEMBERS("villager-farm.allow-procreation-between-family-members"),

    DIVORCE_REPUTATION_LOSS("divorce-reputation-loss"),
    DIVORCE_REPUTATION_LOSS_PAPERS("divorce-reputation-loss-papers"),

    TIME_TO_EXPECT("time-to-expect"),

    FISHING_COOLDOWN("fishing-cooldown"),

    USE_VILLAGER_SOUNDS("use-villager-sounds"),
    VILLAGER_MESSAGE_FORMAT("villager-message-format"),
    DISABLE_SKINS("disable-skins"),
    WORLDS_FILTER_TYPE("worlds-filter.type"),
    WORLDS_FILTER_WORLDS("worlds-filter.worlds"),
    DISABLE_NAMETAGS("disable-nametags"),
    TAME_COOLDOWN("tame-cooldown"),
    MELEE_ATTACK_JUMP_CHANCE("melee-attack-jump-chance"),
    BACK_UP_JUMP_CHANCE("back-up-jump-chance"),
    WHO_CAN_MODIFY_VILLAGER_INVENTORY("who-can-modify-villager-inventory"),
    WHO_CAN_MODIFY_VILLAGER_COMBAT("who-can-modify-villager-combat"),
    WHO_CAN_MODIFY_VILLAGER_HOME("who-can-modify-villager-home"),
    WHO_CAN_MODIFY_VILLAGER_NAME("who-can-modify-villager-name"),
    DISABLE_SPECIAL_PRICES_IF_ALLOWED_TO_MODIFY_INVENTORY("disable-special-prices-if-allowed-to-modify-inventory"),
    MELEE_ATTACK_RANGE("melee-attack-range"),
    DISABLE_VILLAGER_RIDING_NEARBY_BOAT("disable-villager-riding-nearby-boat"),
    REPUTATION_REQUIRED_TO_ASK_TO_FOLLOW("reputation-required-to-ask-to-follow"),
    FAMILY_BYPASS_ASK_TO_FOLLOW("family-bypass-ask-to-follow"),
    REPUTATION_REQUIRED_TO_ASK_TO_STAY("reputation-required-to-ask-to-stay"),
    FAMILY_BYPASS_ASK_TO_STAY("family-bypass-ask-to-stay"),
    STAY_STROLL_AROUND("stay-stroll-around"),
    INITIAL_REPUTATION_AT_BIRTH("initial-reputation-at-birth"),
    VILLAGER_ADOPTS_ABANDONED_PET("villager-adopts-abandoned-pet"),
    VILLAGER_CROSSBOW_FIREWORK_DAMAGES_OTHER_VILLAGERS("villager-crossbow-firework-damages-other-villagers"),
    RIPTIDE_ONLY_IN_WATER_OR_RAIN("riptide-only-in-water-or-rain"),
    SPEED_MODIFIER_EAT("speed-modifier.eat"),
    SPEED_MODIFIER_WALK("speed-modifier.walk"),
    SPEED_MODIFIER_SPRINT("speed-modifier.sprint"),
    SPEED_MODIFIER_SWIM("speed-modifier.swim"),
    BABY_LOOK_HEIGHT_OFFSET("baby-look-height-offset"),
    REVIVE_ENABLED("revive.enabled"),
    REVIVE_ONLY_AT_NIGHT("revive.only-at-night"),
    REVIVE_ONLY_WITH_CROSS("revive.only-with-cross"),
    REVIVE_BREAK_EMERALD_CHANCE("revive.break-emerald-chance"),
    REVIVE_INCLUDE_WANDERING_TRADER("revive.include-wandering-trader"),
    REVIVE_SPAWN_VALUES_HEALTH("revive.spawn-values.health"),
    REVIVE_SPAWN_VALUES_FOOD_LEVEL("revive.spawn-values.food-level"),
    REVIVE_SPAWN_VALUES_POTION_EFFECTS("revive.spawn-values.potion-effects"),
    REVIVE_BOSSBAR_ENABLED("revive.boss-bar.enabled"),
    REVIVE_BOSSBAR_TITLE("revive.boss-bar.title"),
    REVIVE_BOSSBAR_PROGRESS_TYPE("revive.boss-bar.progress-type"),
    REVIVE_BOSSBAR_COLOR("revive.boss-bar.color"),
    REVIVE_BOSSBAR_STYLE("revive.boss-bar.style"),
    REVIVE_BOSSBAR_FLAGS("revive.boss-bar.flags"),
    TAME_HORSES("tame-horses"),
    INCREASE_BABY_SCALE("increase-baby-scale"),
    GENDER_SELECTION_ENABLED("gender-selection.enabled"),
    GENDER_SELECTION_DEFAULT("gender-selection.default"),
    GENDER_SELECTION_ALLOW_CHANGE("gender-selection.allow-change"),

    // ── gui.yml ──────────────────────────────────────────────────────────────
    CUSTOM_NAME_SHOW_JOB_BLOCK("custom-nametags.show-job-block", ConfigFile.GUI),
    CUSTOM_NAME_SHADOW("custom-nametags.shadow", ConfigFile.GUI),
    CUSTOM_NAME_SEE_THROUGH("custom-nametags.see-through", ConfigFile.GUI),
    CUSTOM_NAME_TEXT_OPACITY("custom-nametags.text-opacity", ConfigFile.GUI),
    CUSTOM_NAME_BACKGROUND_COLOR("custom-nametags.background-color", ConfigFile.GUI),
    CUSTOM_NAME_BACKGROUND_OPACITY("custom-nametags.background-opacity", ConfigFile.GUI),
    CUSTOM_NAME_VILLAGER_LINES("custom-nametags.lines.villager", ConfigFile.GUI),
    CUSTOM_NAME_TRADER_LINES("custom-nametags.lines.wandering-trader", ConfigFile.GUI),
    CUSTOM_NAME_PARTNER_COLOR("custom-nametags.relationship-color.partner", ConfigFile.GUI),
    CUSTOM_NAME_CHILDREN_COLOR("custom-nametags.relationship-color.children", ConfigFile.GUI),

    SKIN_PREVIEW_SECONDS("skin-preview.seconds", ConfigFile.GUI),
    SKIN_PREVIEW_MESSAGE("skin-preview.message", ConfigFile.GUI),
    SKIN_PREVIEW_RAINBOW_MESSAGE("skin-preview.rainbow-message", ConfigFile.GUI),

    COMBAT_SEARCH_TITLE("input-gui.combat-search.title", ConfigFile.GUI),
    COMBAT_SEARCH_TEXT("input-gui.combat-search.text", ConfigFile.GUI),

    BABY_TITLE("input-gui.baby.title", ConfigFile.GUI),
    BABY_TEXT("input-gui.baby.text", ConfigFile.GUI),
    BABY_INVALID_NAME("input-gui.baby.invalid-name", ConfigFile.GUI),

    WHISTLE_SEARCH_TITLE("input-gui.whistle-search.title", ConfigFile.GUI),
    WHISTLE_SEARCH_TEXT("input-gui.whistle-search.text", ConfigFile.GUI),

    SKIN_SEARCH_TITLE("input-gui.skin-search.title", ConfigFile.GUI),
    SKIN_SEARCH_TEXT("input-gui.skin-search.text", ConfigFile.GUI),

    NEW_SKIN_TITLE("input-gui.new-skin.title", ConfigFile.GUI),
    NEW_SKIN_TEXT("input-gui.new-skin.text", ConfigFile.GUI),

    PLAYERS_TITLE("input-gui.players.title", ConfigFile.GUI),
    PLAYERS_TEXT("input-gui.players.text", ConfigFile.GUI),

    // ── loot.yml ─────────────────────────────────────────────────────────────
    LOOT_CHEST_ENABLED("loot-chest.enabled", ConfigFile.LOOT),
    LOOT_CHEST_REQUIRED_SIGN_LINE("loot-chest.required-sign-line", ConfigFile.LOOT),
    LOOT_CHEST_NITWIT_SHUFFLE_INVENTORY("loot-chest.nitwit-shuffle-inventory", ConfigFile.LOOT),
    LOOT_CHEST_COOLDOWN("loot-chest.cooldown", ConfigFile.LOOT),
    LOOT_CHEST_PER_CHEST_COOLDOWN("loot-chest.per-chest-cooldown", ConfigFile.LOOT),
    LOOT_CHEST_ALLOW_BABIES("loot-chest.allow-babies", ConfigFile.LOOT),
    LOOT_CHEST_CONTAINER_CHEST("loot-chest.containers.chest", ConfigFile.LOOT),
    LOOT_CHEST_CONTAINER_BARREL("loot-chest.containers.barrel", ConfigFile.LOOT),
    LOOT_CHEST_DEPOSIT_ENABLED("loot-chest.deposit-enabled", ConfigFile.LOOT),
    LOOT_CHEST_ITEMS("loot-chest.chest-items", ConfigFile.LOOT),
    SPAWN_LOOT_FORCE_EQUIP("spawn-loot.force-equip", ConfigFile.LOOT),

    // ── variable-text.yml ────────────────────────────────────────────────────
    DEAD("variable-text.dead", ConfigFile.VARIABLE_TEXT),
    INFINITE("variable-text.infinite", ConfigFile.VARIABLE_TEXT),
    NONE("variable-text.none", ConfigFile.VARIABLE_TEXT),
    UNKNOWN("variable-text.unknown", ConfigFile.VARIABLE_TEXT),
    VILLAGER("variable-text.villager", ConfigFile.VARIABLE_TEXT),
    PLAYER("variable-text.player", ConfigFile.VARIABLE_TEXT),
    CONSOLE("variable-text.console", ConfigFile.VARIABLE_TEXT),
    NO_PARTNERS("variable-text.no-partners", ConfigFile.VARIABLE_TEXT),
    NO_PARTNER_CURRENTLY("variable-text.no-partner-currently", ConfigFile.VARIABLE_TEXT),
    NO_CHILDRENS("variable-text.no-childrens", ConfigFile.VARIABLE_TEXT),
    NO_EFFECTS("variable-text.no-effects", ConfigFile.VARIABLE_TEXT),
    KID("variable-text.kid", ConfigFile.VARIABLE_TEXT),
    ADULT("variable-text.adult", ConfigFile.VARIABLE_TEXT),
    BOY("variable-text.boy", ConfigFile.VARIABLE_TEXT),
    GIRL("variable-text.girl", ConfigFile.VARIABLE_TEXT),
    MALE("variable-text.sex.male", ConfigFile.VARIABLE_TEXT),
    FEMALE("variable-text.sex.female", ConfigFile.VARIABLE_TEXT),
    ACRONYM_SECOND("variable-text.acronym.second", ConfigFile.VARIABLE_TEXT),
    ACRONYM_MINUTE("variable-text.acronym.minute", ConfigFile.VARIABLE_TEXT),
    ACRONYM_HOUR("variable-text.acronym.hour", ConfigFile.VARIABLE_TEXT),
    ACRONYM_DAY("variable-text.acronym.day", ConfigFile.VARIABLE_TEXT),
    GREET_MESSAGES_ENABLED("greet-messages.enabled", ConfigFile.VARIABLE_TEXT),
    GREET_MESSAGES_RANGE("greet-messages.range", ConfigFile.VARIABLE_TEXT),
    GREET_MESSAGES_COOLDOWN("greet-messages.cooldown", ConfigFile.VARIABLE_TEXT),
    GREET_MESSAGES_PER_TYPE_COOLDOWN("greet-messages.per-type-cooldown", ConfigFile.VARIABLE_TEXT),
    GREET_MESSAGES_REQUIRED_REPUTATION("greet-messages.required-reputation", ConfigFile.VARIABLE_TEXT),

    REGIONALITY_ENABLED("regionality.enabled");

    public enum ConfigFile { MAIN, GUI, LOOT, VARIABLE_TEXT }

    private final String path;
    private final ConfigFile configFile;
    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    Config(String path) {
        this.path = path;
        this.configFile = ConfigFile.MAIN;
    }

    Config(String path, ConfigFile configFile) {
        this.path = path;
        this.configFile = configFile;
    }

    private FileConfiguration getFileConfig() {
        return switch (configFile) {
            case GUI -> plugin.getGuiConfig();
            case LOOT -> plugin.getLootConfig();
            case VARIABLE_TEXT -> plugin.getVariableTextConfig();
            default -> plugin.getConfig();
        };
    }

    public boolean asBool() {
        return getFileConfig().getBoolean(path);
    }

    /**
     * Reads a flag, falling back to {@code defaultValue} when the key isn't in the file.
     * <p>
     * Matters for options added after a server's config was written: {@link #asBool()} reports a
     * missing key as {@code false}, which turns a new feature off on every existing install and
     * looks exactly like the feature not working.
     */
    public boolean asBool(boolean defaultValue) {
        return getFileConfig().getBoolean(path, defaultValue);
    }

    public int asInt() {
        return getFileConfig().getInt(path);
    }

    public int asInt(int defaultValue) {
        return getFileConfig().getInt(path, defaultValue);
    }

    public String asString() {
        return getFileConfig().getString(path);
    }

    public String asString(String defaultValue) {
        return getFileConfig().getString(path, defaultValue);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public @NotNull String asStringTranslated(String defaultValue) {
        return PluginUtils.translate(asString(defaultValue));
    }

    public double asDouble() {
        double val = getFileConfig().getDouble(path);
        if (val != 0.0d) return val;

        // If the value is 0.0 or the key is missing, return a safe default.
        switch (this) {
            case SPEED_MODIFIER_WALK: return 1.0d;
            case SPEED_MODIFIER_SPRINT: return 1.4d;
            case SPEED_MODIFIER_SWIM: return 1.5d;
            case SPEED_MODIFIER_EAT: return 0.6d;
            case MELEE_ATTACK_RANGE: return 8.0d;
            case RANGE_WEAPON_POWER: return 1.6d;
            case MELEE_ATTACK_JUMP_CHANCE: return 0.08d;
            case BACK_UP_JUMP_CHANCE: return 0.1d;
            case CHANCE_OF_WEARING_HALLOWEEN_MASK: return 0.25d;
            case HOSTILE_DETECTION_RANGE: return 16.0d;
            case VILLAGER_MAX_HEALTH: return 20.0d;
            case ATTACK_DAMAGE: return 2.0d;
        }
        return val;
    }

    public double asDouble(double defaultValue) {
        return getFileConfig().getDouble(path, defaultValue);
    }

    public long asLong() {
        return getFileConfig().getLong(path);
    }

    public float asFloat() {
        return (float) asDouble();
    }

    public float asFloat(float defaultValue) {
        return (float) asDouble(defaultValue);
    }

    public @NotNull List<String> asStringList() {
        return getFileConfig().getStringList(path);
    }
}
