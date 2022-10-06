package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public enum Config {
    DISABLE_INTERACTIONS("disable-interactions"),
    MAX_GOSSIP_TOPICS("max-gossip-topics"),
    DISABLE_SPECIAL_PRICES("disable-special-prices"),
    ATTACK_PLAYER_WEARING_MONSTER_SKULL("attack-player-wearing-monster-skull"),
    ATTACK_PLAYER_PLAYING_GOAT_HORN_SEEK("attack-player-playing-goat-horn-seek"),
    GOAT_HORN_SEEK_RANGE("goat-horn-seek-range"),
    MELEE_ATTACK_COOLDOWN("melee-attack-cooldown"),
    VILLAGER_FIX_IRON_GOLEM_WITH_IRON("villager-fix-iron-golem-with-iron"),
    VILLAGER_FIX_IRON_GOLEM_COOLDOWN("villager-fix-iron-golem-cooldown"),
    PARTNER_JOKE_ALWAYS_SUCCESS("partner-joke-always-success"),
    CHANCE_OF_CHAT_INTERACTION_SUCCESS("chance-of-chat-interaction-success"),

    ZOMBIE_INFECTION("zombie-infection"),
    WITCH_CONVERTION("witch-convertion"),

    BABY_GROW_COOLDOWN("baby-grow-cooldown"),
    PROCREATION_COOLDOWN("procreation-cooldown"),

    ARROWS_PASS_THROUGH_OTHER_VILLAGERS("arrows-pass-through-other-villagers"),

    VILLAGER_MAX_HEALTH("villager-max-health"),

    TELEPORT_WHEN_FOLLOWING_IF_FAR_AWAY("teleport-when-following-if-far-away"),
    TELEPORT_WHEN_FOLLOWING_DISTANCE("teleport-when-following-distance"),

    VILLAGER_SPAWN_IRON_GOLEM("villager-spawn-iron-golem"),

    BAD_GIFT_REPUTATION("bad-gift-reputation"),
    WEDDING_RING_REPUTATION("wedding-ring-reputation"),
    BABY_REPUTATION("baby-reputation"),
    CHAT_INTERACT_REPUTATION("chat-interact-reputation"),

    WHO_CAN_MODIFY_VILLAGER_COMBAT("who-can-modify-villager-combat"),

    NONE("variable-text.none"),
    UNKNOWN("variable-text.unknown"),
    VILLAGER("variable-text.villager"),
    PLAYER("variable-text.player"),
    NO_CHILDRENS("variable-text.no-childrens"),
    KID("variable-text.kid"),
    ADULT("variable-text.adult"),
    BOY("variable-text.boy"),
    GIRL("variable-text.girl"),
    MALE("variable-text.sex.male"),
    FEMALE("variable-text.sex.female"),

    HOSTILE_DETECTION_RANGE("hostile-detection-range"),

    DROP_WHOLE_INVENTORY("drop-whole-inventory"),

    ATTACK_DAMAGE("attack-damage"),
    CHANCE_OF_WEARING_WEAPON("chance-of-wearing-weapon"),
    CHANCE_OF_WEARING_EACH_ARMOUR_ITEM("chance-of-wearing-each-armour-item"),
    CHANCE_OF_WEARING_SHIELD("chance-of-wearing-shield"),
    CHANCE_OF_WEARING_HALLOWEEN_MASK("chance-of-wearing-halloween-mask"),
    RANGE_WEAPON_POWER("range-weapon-power"),
    VILLAGER_INVENTORY_SIZE("villager-inventory-size"),

    MIN_AMOUNT_OF_ARROWS("min-amount-of-arrows"),
    MAX_AMOUNT_OF_ARROWS("max-amount-of-arrows"),

    DIVORCE_IF_REPUTATION_IS_LESS_THAN("divorce-if-reputation-is-less-than"),

    VILLAGER_TITLE_ARTICLE("villager-title-article"),
    SHOW_TITLE_IN_VILLAGER_CHAT_MESSAGE("show-title-in-villager-chat-message"),

    VILLAGER_ATTACK_PLAYER_DURING_RAID("villager-attack-player-during-raid"),

    VILLAGER_DEFEND_FAMILY_MEMBER("villager-defend-family-member"),
    VILLAGER_DEFEND_HERO_OF_THE_VILLAGE("villager-defend-hero-of-the-village"),

    REPUTATION_REQUIRED_TO_MARRY("reputation-required-to-marry"),
    REPUTATION_REQUIRED_TO_PROCREATE("reputation-required-to-procreate"),

    DIVORCE_REPUTATION_LOSS("divorce-reputation-loss"),
    DIVORCE_REPUTATION_LOSS_PAPERS("divorce-reputation-loss-papers"),

    TIME_TO_EXPECT("time-to-expect"),

    COMBAT_SEARCH_TITLE("input-gui.combat-search.title"),
    COMBAT_SEARCH_TEXT("input-gui.combat-search.text"),

    BABY_TITLE("input-gui.baby.title"),
    BABY_TEXT("input-gui.baby.text"),

    FISHING_ROD_CHANCE("fishing-rod-chance"),
    FISHING_COOLDOWN("fishing-cooldown"),

    USE_VILLAGER_SOUNDS("use-villager-sounds"),
    VILLAGER_MESSAGE_FORMAT("villager-message-format"),
    DISABLE_SKINS("disable-skins"),
    WORLDS_FILTER_TYPE("worlds-filter.type"),
    WORLDS_FILTER_WORLDS("worlds-filter.worlds"),
    DISABLE_NAMETAGS("disable-nametags"),
    TAME_COOLDOWN("tame-cooldown"),
    MELEE_ATTACK_JUMP_CHANCE("melee-attack-jump-chance"),
    WHO_CAN_MODIFY_VILLAGER_INVENTORY("who-can-modify-villager-inventory"),
    DISABLE_SPECIAL_PRICES_IF_ALLOWED_TO_MODIFY_INVENTORY("disable-special-prices-if-allowed-to-modify-inventory"),
    MELEE_ATTACK_RANGE("melee-attack-range");

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

    public String asStringTranslated() {
        return PluginUtils.translate(asString());
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

    public List<String> asStringList() {
        return plugin.getConfig().getStringList(path);
    }
}