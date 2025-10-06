package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum Config {
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
    PARTNER_JOKE_ALWAYS_SUCCESS("partner-joke-always-success"),
    CHANCE_OF_CHAT_INTERACTION_SUCCESS("chance-of-chat-interaction-success"),

    ZOMBIE_INFECTION("zombie-infection"),
    WITCH_CONVERTION("witch-convertion"),
    WITCH_CONVERTION_FROM_VILLAGER_TRIDENT("witch-convertion-from-villager-trident"),

    BABY_GROW_COOLDOWN("baby-grow-cooldown"),
    PROCREATION_COOLDOWN("procreation-cooldown"),

    ARROWS_PASS_THROUGH_OTHER_VILLAGERS("arrows-pass-through-other-villagers"),
    ARROW_STATUS("arrow-status"),

    CUSTOM_NAME_SHOW_JOB_BLOCK("custom-nametags.show-job-block"),
    CUSTOM_NAME_SHADOW("custom-nametags.shadow"),
    CUSTOM_NAME_SEE_THROUGH("custom-nametags.see-through"),
    CUSTOM_NAME_TEXT_OPACITY("custom-nametags.text-opacity"),
    CUSTOM_NAME_VILLAGER_LINES("custom-nametags.lines.villager"),
    CUSTOM_NAME_TRADER_LINES("custom-nametags.lines.wandering-trader"),

    SKIN_PREVIEW_SECONDS("skin-preview.seconds"),
    SKIN_PREVIEW_MESSAGE("skin-preview.message"),
    SKIN_PREVIEW_RAINBOW_MESSAGE("skin-preview.rainbow-message"),

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

    DEAD("variable-text.dead"),
    INFINITE("variable-text.infinite"),
    NONE("variable-text.none"),
    UNKNOWN("variable-text.unknown"),
    VILLAGER("variable-text.villager"),
    PLAYER("variable-text.player"),
    CONSOLE("variable-text.console"),
    NO_PARTNERS("variable-text.no-partners"),
    NO_PARTNER_CURRENTLY("variable-text.no-partner-currently"),
    NO_CHILDRENS("variable-text.no-childrens"),
    NO_EFFECTS("variable-text.no-effects"),
    KID("variable-text.kid"),
    ADULT("variable-text.adult"),
    BOY("variable-text.boy"),
    GIRL("variable-text.girl"),
    MALE("variable-text.sex.male"),
    FEMALE("variable-text.sex.female"),

    HOSTILE_DETECTION_RANGE("hostile-detection-range"),

    DROP_WHOLE_INVENTORY("drop-whole-inventory"),

    ATTACK_DAMAGE("attack-damage"),
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

    IGNORE_SEX_WHEN_PROCREATING("villager-farm.ignore-sex-when-procreating"),
    ALLOW_PARTNER_CHEATING("villager-farm.allow-partner-cheating"),
    ALLOW_PARTNER_CHEATING_FOR_ALL("villager-farm.allow-partner-cheating-for-all"),
    ALLOW_PROCREATION_BETWEEN_FAMILY_MEMBERS("villager-farm.allow-procreation-between-family-members"),

    DIVORCE_REPUTATION_LOSS("divorce-reputation-loss"),
    DIVORCE_REPUTATION_LOSS_PAPERS("divorce-reputation-loss-papers"),

    TIME_TO_EXPECT("time-to-expect"),

    COMBAT_SEARCH_TITLE("input-gui.combat-search.title"),
    COMBAT_SEARCH_TEXT("input-gui.combat-search.text"),

    BABY_TITLE("input-gui.baby.title"),
    BABY_TEXT("input-gui.baby.text"),
    BABY_INVALID_NAME("input-gui.baby.invalid-name"),

    WHISTLE_SEARCH_TITLE("input-gui.whistle-search.title"),
    WHISTLE_SEARCH_TEXT("input-gui.whistle-search.text"),

    SKIN_SEARCH_TITLE("input-gui.skin-search.title"),
    SKIN_SEARCH_TEXT("input-gui.skin-search.text"),

    NEW_SKIN_TITLE("input-gui.new-skin.title"),
    NEW_SKIN_TEXT("input-gui.new-skin.text"),

    PLAYERS_TITLE("input-gui.players.title"),
    PLAYERS_TEXT("input-gui.players.text"),

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
    ACRONYM_SECOND("variable-text.acronym.second"),
    ACRONYM_MINUTE("variable-text.acronym.minute"),
    ACRONYM_HOUR("variable-text.acronym.hour"),
    ACRONYM_DAY("variable-text.acronym.day"),
    LOOT_CHEST_ENABLED("loot-chest.enabled"),
    LOOT_CHEST_REQUIRED_SIGN_LINE("loot-chest.required-sign-line"),
    LOOT_CHEST_NITWIT_SHUFFLE_INVENTORY("loot-chest.nitwit-shuffle-inventory"),
    LOOT_CHEST_COOLDOWN("loot-chest.cooldown"),
    LOOT_CHEST_PER_CHEST_COOLDOWN("loot-chest.per-chest-cooldown"),
    LOOT_CHEST_ALLOW_BABIES("loot-chest.allow-babies"),
    REPUTATION_REQUIRED_TO_ASK_TO_FOLLOW("reputation-required-to-ask-to-follow"),
    FAMILY_BYPASS_ASK_TO_FOLLOW("family-bypass-ask-to-follow"),
    REPUTATION_REQUIRED_TO_ASK_TO_STAY("reputation-required-to-ask-to-stay"),
    FAMILY_BYPASS_ASK_TO_STAY("family-bypass-ask-to-stay"),
    STAY_STROLL_AROUND("stay-stroll-around"),
    INITIAL_REPUTATION_AT_BIRTH("initial-reputation-at-birth"),
    SPAWN_LOOT_FORCE_EQUIP("spawn-loot.force-equip"),
    VILLAGER_ADOPTS_ABANDONED_PET("villager-adopts-abandoned-pet"),
    VILLAGER_CROSSBOW_FIREWORK_DAMAGES_OTHER_VILLAGERS("villager-crossbow-firework-damages-other-villagers"),
    GREET_MESSAGES_ENABLED("greet-messages.enabled"),
    GREET_MESSAGES_RANGE("greet-messages.range"),
    GREET_MESSAGES_COOLDOWN("greet-messages.cooldown"),
    GREET_MESSAGES_PER_TYPE_COOLDOWN("greet-messages.per-type-cooldown"),
    GREET_MESSAGES_REQUIRED_REPUTATION("greet-messages.required-reputation"),
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
    INCREASE_BABY_SCALE("increase-baby-scale");

    private final String path;
    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    Config(String path) {
        this.path = path;
    }

    public boolean asBool() {
        return plugin.getConfig().getBoolean(path);
    }

    public int asInt() {
        return plugin.getConfig().getInt(path);
    }

    public String asString() {
        return plugin.getConfig().getString(path);
    }

    public String asString(String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public @NotNull String asStringTranslated(String defaultValue) {
        return PluginUtils.translate(asString(defaultValue));
    }

    public double asDouble() {
        return plugin.getConfig().getDouble(path);
    }

    public long asLong() {
        return plugin.getConfig().getLong(path);
    }

    public float asFloat() {
        return (float) asDouble();
    }

    public @NotNull List<String> asStringList() {
        return plugin.getConfig().getStringList(path);
    }
}