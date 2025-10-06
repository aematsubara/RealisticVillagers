package me.matsubara.realisticvillagers.files;

import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Messages {

    private final RealisticVillagers plugin;

    private @Setter FileConfiguration configuration;

    private static final double NEARBY_SEARCH_RANGE = 30.0d;

    public Messages(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.plugin.saveResource("messages.yml");
    }

    public void sendRandomGiftMessage(Player player, IVillagerNPC npc, @Nullable GiftCategory category) {
        InteractionTargetType target = InteractionTargetType.getInteractionTarget(npc, player);
        String type = category != null ? ".success." + category.name() : ".fail";
        send(player, npc, getRandomMessage("gift." + target.getName() + type));
    }

    public void sendRandomInteractionMessage(Player player, IVillagerNPC npc, @NotNull GUIInteractType interactType, boolean success) {
        InteractionTargetType target = InteractionTargetType.getInteractionTarget(npc, player);

        boolean checkSuccess = !interactType.isGreet() && !interactType.isInsult() && !interactType.isBeProudOf();
        String path = interactType.getName() + "." + target.getName() + (checkSuccess ? "." + (success ? "success" : "fail") : "");

        send(player, npc, getRandomMessage(path));
    }

    public void send(Player player, IVillagerNPC npc, @NotNull Message message) {
        send(player, npc, getRandomMessage(message.getPath()));
    }

    public void send(Player player, IVillagerNPC npc, @NotNull String message) {
        if (message.isEmpty()) return;

        String playerName = player.getName();
        String fullVillagerName = getVillagerTitleName(npc);

        String formattedMessage = Config.VILLAGER_MESSAGE_FORMAT.asStringTranslated()
                .replace("%name%", fullVillagerName)
                .replace("%message%", message)
                .replace("%villager-name%", npc.getVillagerName())
                .replace("%player-name%", playerName)
                .replace("%random-villager-name%", getNearbyRandom(npc, null))
                .replace("%random-player-name%", getNearbyRandom(npc, playerName));

        player.sendMessage(formattedMessage);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private String getNearbyRandom(@NotNull IVillagerNPC npc, @Nullable String exceptPlayer) {
        VillagerTracker tracker = plugin.getTracker();
        for (Entity nearby : npc.bukkit().getNearbyEntities(NEARBY_SEARCH_RANGE, NEARBY_SEARCH_RANGE, NEARBY_SEARCH_RANGE)) {
            if (exceptPlayer == null) {
                // If villager is valid, then the optional won't be empty.
                if (!(nearby instanceof Villager villager) || tracker.isInvalid(villager)) continue;

                String name = plugin.getConverter()
                        .getNPC(villager)
                        .get()
                        .getVillagerName();

                return Objects.requireNonNullElse(name, Config.UNKNOWN.asStringTranslated());
            } else if (nearby instanceof Player player
                    && !player.getName().equals(exceptPlayer)) return player.getName();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (exceptPlayer != null) {
            List<String> offlineNames = Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName)
                    .filter(Predicate.not(exceptPlayer::equals))
                    .toList();
            // If there isn't any offline player (shouldn't happen), a random villager name will be used.
            if (!offlineNames.isEmpty()) return offlineNames.get(random.nextInt(offlineNames.size()));
        }

        // If there isn't any villager nearby, we pick a random name.
        return tracker.getRandomNameBySex(PluginUtils.getRandomSex());
    }

    public void send(CommandSender sender, Message message) {
        send(sender, message, null);
    }

    public void send(CommandSender sender, @NotNull Message message, @Nullable UnaryOperator<String> operator) {
        // Only villager messages are single line.
        for (String line : getMessages(message.getPath())) {
            if (!line.isEmpty()) sender.sendMessage(operator != null ? operator.apply(line) : line);
        }
    }

    public String getVillagerTitleName(@NotNull IVillagerNPC npc) {
        String villagerName = npc.getVillagerName();

        if (npc.is(Villager.Profession.NONE)
                || !Config.SHOW_TITLE_IN_VILLAGER_CHAT_MESSAGE.asBool()
                || !(npc.bukkit() instanceof Villager villager)) {
            return villagerName;
        }

        return villagerName + " " + getVillagerTitle(villager.getProfession(), npc.isMale());
    }

    private @NotNull String getVillagerTitle(Villager.Profession profession, boolean isMale) {
        String article = (isMale ? Config.VILLAGER_TITLE_ARTICLE_MALE : Config.VILLAGER_TITLE_ARTICLE_FEMALE).asString();
        return article + " " + plugin.getProfessionFormatted(profession, isMale);
    }

    private String getRandomMessage(String path) {
        List<String> messages = getMessages(path);
        return messages.isEmpty() ? "" : messages.get(RandomUtils.nextInt(0, messages.size()));
    }

    @SuppressWarnings("unchecked")
    private List<String> getMessages(String path) {
        if (!configuration.contains(path, true)) return Collections.emptyList();

        List<String> messages;

        Object object = configuration.get(path);
        if (object instanceof String string) {
            messages = Lists.newArrayList(string);
        } else if (object instanceof List<?> list) {
            try {
                messages = Lists.newArrayList((List<String>) list);
            } catch (ClassCastException exception) {
                return Collections.emptyList();
            }
        } else return Collections.emptyList();

        return PluginUtils.translate(messages);
    }

    @SuppressWarnings({"unchecked", "unused"})
    public void sendMessages(CommandSender sender, @NotNull Message message) {
        if (!(configuration.get(message.getPath()) instanceof List<?> messages)) return;

        try {
            for (String messageString : (List<String>) messages) {
                sender.sendMessage(PluginUtils.translate(messageString));
            }
        } catch (ClassCastException ignored) {

        }
    }

    @Getter
    public enum Message {
        ON_HIT("reaction.on-hit"),
        MARRRY_SUCCESS("marry.success"),
        MARRY_FAIL_MARRIED_TO_GIVER("marry.fail.married-to-giver"),
        MARRY_FAIL_MARRIED_TO_OTHER("marry.fail.married-to-other"),
        MARRY_FAIL_PLAYER_MARRIED("marry.fail.player-married"),
        MARRY_FAIL_LOW_REPUTATION("marry.fail.low-reputation"),
        MARRY_END("marry.end"),
        SET_HOME_EXPECTING("set-home.expecting"),
        SET_HOME_SUCCESS("set-home.success"),
        SET_HOME_FAIL("set-home.fail"),
        PROCREATE_FAIL_HAS_BABY("procreate.fail.has-baby"),
        PROCREATE_FAIL_LOW_REPUTATION("procreate.fail.low-reputation"),
        PROCREATE_COOLDOWN("procreate.cooldown"),
        BABY_GROW,
        BABY_CAN_NOT_SPAWN,
        BABY_COUNTDOWN,
        BABY_SPAWNED,
        DIVORCE_NORMAL("divorce.normal"),
        DIVORCE_PAPERS("divorce.papers"),
        CLERIC_DIVORCE_PAPERS("cleric.divorce-papers"),
        CLERIC_NOT_MARRIED("cleric.not-married"),
        BED_OCCUPIED,
        BED_ESTABLISHED,
        BED_INVALID,
        INTERACT_FAIL_FIGHTING_OR_RAID("interact-fail.fighting-or-raid"),
        INTERACT_FAIL_PROCREATING("interact-fail.procreating"),
        INTERACT_FAIL_EXPECTING_GIFT_FROM_YOU("interact-fail.expecting-gift-from-you"),
        INTERACT_FAIL_EXPECTING_GIFT_FROM_SOMEONE("interact-fail.expecting-gift-from-someone"),
        INTERACT_FAIL_EXPECTING_BED_FROM_YOU("interact-fail.expecting-bed-from-you"),
        INTERACT_FAIL_EXPECTING_BED_FROM_SOMEONE("interact-fail.expecting-bed-from-someone"),
        INTERACT_FAIL_INTERACTING("interact-fail.interacting"),
        INTERACT_FAIL_TRADING("interact-fail.trading"),
        INTERACT_FAIL_OTHER_EXPECTING_GIFT("interact-fail.other-expecting-gift"),
        INTERACT_FAIL_OTHER_EXPECTING_BED("interact-fail.other-expecting-bed"),
        INTERACT_FAIL_IN_COOLDOWN("interact-fail.in-cooldown"),
        INTERACT_FAIL_NOT_MARRIED("interact-fail.not-allowed.not-married"),
        INTERACT_FAIL_NOT_FAMILY("interact-fail.not-allowed.not-family"),
        INTERACT_FAIL_NOT_A_KID("interact-fail.not-allowed.not-a-kid"),
        INTERACT_FAIL_NOT_AN_ADULT("interact-fail.not-allowed.not-an-adult"),
        INTERACT_FAIL_ONLY_PARTNER_OR_NON_FAMILY_ADULT("interact-fail.not-allowed.only-partner-or-non-family-adult"),
        INTERACT_FAIL_NONE("interact-fail.not-allowed.none"),
        INTERACT_FAIL_RENAME_NOT_ALLOWED("interact-fail.rename-not-allowed"),
        INTERACT_FAIL_ALREADY_ALIVE("interact-fail.already-alive"),
        GIFT_EXPECTING("gift.expecting"),
        GIFT_EXPECTING_FAIL("gift.expecting-fail"),
        RIGHT_CLICK_GIFT,
        THROW_GIFT,
        SELECT_BED,
        RELOADING,
        RELOAD,
        NO_PERMISSION,
        INVALID_COMMAND,
        NO_MINESKIN_API_KEY,
        NO_SKIN_CACHED,
        FOLLOW_ME_START("follow-me.start"),
        FOLLOW_ME_STOP("follow-me.stop"),
        FOLLOW_ME_LOW_REPUTATION("follow-me.low-reputation"),
        STAY_HERE_START("stay-here.start"),
        STAY_HERE_STOP("stay-here.stop"),
        STAY_HERE_LOW_REPUTATION("stay-here.low-reputation"),
        NO_TRADES,
        WHISTLE_TELEPORTED("whistle-teleported"),
        WHISTLE_ERROR("whistle-error"),
        WHISTLE_NO_FAMILY("whistle-no-family"),
        UNKNOWN_PLAYER,
        HAS_NEVER_PLAYER_BEFORE("players-gui.has-never-played-before"),
        PLAYER_IS_FAMILY_MEMBER("players-gui.player-is-family-member"),
        NOT_YOURSELF("players-gui.not-yourself"),
        INVALID_NAME("players-gui.invalid-name"),
        ALREADY_ADDED("players-gui.already-added"),
        PLAYERS_ADDED("players-gui.added"),
        PLAYERS_REMOVED("players-gui.removed"),
        NOT_MARRIED,
        DIVORCED,
        SKIN_CLEARED("skin.cleared"),
        SKIN_NOT_CLEARED("skin.not-cleared"),
        SKIN_AT_LEAST_ONE("skin.at-least-one"),
        SKIN_REMOVED("skin.removed"),
        SKIN_WAIT_WHILE_CREATING("skin.wait-while-creating"),
        SKIN_NOT_FOUND("skin.not-found"),
        SKIN_TEXTURE_NOT_FOUND("skin.texture-not-found"),
        SKIN_INVALID_SEX("skin.invalid-sex"),
        SKIN_INVALID_AGE_STAGE("skin.invalid-age-stage"),
        SKIN_DIFFERENT_SEX("skin.different-sex"),
        SKIN_DIFFERENT_AGE_STAGE("skin.different-age-stage"),
        SKIN_ADDED("skin.added"),
        SKIN_ERROR("skin.error"),
        SKIN_VILLAGER_SAME_SKIN("skin.villager-same-skin"),
        SKIN_DISGUISED("skin.disguised"),
        ONLY_FROM_PLAYER,
        ONLY_FROM_CONSOLE,
        INVALID_NUMBER;

        private final String path;

        Message() {
            this.path = name().toLowerCase(Locale.ROOT).replace("_", "-");
        }

        Message(String path) {
            this.path = path;
        }
    }
}