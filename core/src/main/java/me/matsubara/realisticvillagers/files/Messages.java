package me.matsubara.realisticvillagers.files;

import com.google.common.collect.Lists;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

public final class Messages {

    private final RealisticVillagers plugin;

    private @Setter FileConfiguration configuration;

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

        Villager.Profession profession = npc.bukkit().getProfession();

        String name;
        if (npc.is(Villager.Profession.NONE) || !Config.SHOW_TITLE_IN_VILLAGER_CHAT_MESSAGE.asBool()) {
            name = npc.getVillagerName();
        } else {
            name = npc.getVillagerName() + " " + getVillagerTitle(profession);
        }

        String formattedMessage = Config.VILLAGER_MESSAGE_FORMAT.asStringTranslated()
                .replace("%name%", name)
                .replace("%message%", message);

        player.sendMessage(formattedMessage);
    }

    public void send(CommandSender sender, Message message) {
        send(sender, message, null);
    }

    public void send(CommandSender sender, @NotNull Message message, @Nullable UnaryOperator<String> operator) {
        // Only villager messages are single line, other are multi.
        for (String line : getMessages(message.getPath())) {
            if (!line.isEmpty()) sender.sendMessage(operator != null ? operator.apply(line) : line);
        }
    }

    public @NotNull String getVillagerTitle(Villager.Profession profession) {
        return Config.VILLAGER_TITLE_ARTICLE.asString() + " " + plugin.getProfessionFormatted(profession);
    }

    private String getRandomMessage(String path) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> messages = getMessages(path);
        return messages.isEmpty() ? "" : messages.get(random.nextInt(messages.size()));
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

    public enum Message {
        ON_HIT("reaction.on-hit"),
        RAN_AWAY("reaction.ran-away"),
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
        BABY_GROW("baby-grow"),
        BABY_CAN_NOT_SPAWN("baby-can-not-spawn"),
        BABY_COUNTDOWN("baby-countdown"),
        BABY_SPAWNED("baby-spawned"),
        DIVORCE_NORMAL("divorce.normal"),
        DIVORCE_PAPERS("divorce.papers"),
        CLERIC_DIVORCE_PAPERS("cleric.divorce-papers"),
        CLERIC_NOT_MARRIED("cleric.not-married"),
        BED_OCCUPIED("bed-occupied"),
        BED_ESTABLISHED("bed-established"),
        BED_INVALID("bed-invalid"),
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
        THROW_GIFT("throw-gift"),
        SELECT_BED("select-bed"),
        RELOADING("reloading"),
        RELOAD("reload"),
        NO_PERMISSION("no-permission"),
        INVALID_COMMAND("invalid-command"),
        NO_MINESKIN_API_KEY("no-mineskin-api-key"),
        NO_SKIN_CACHED("no-skin-cached"),
        FOLLOW_ME_START("follow-me.start"),
        FOLLOW_ME_STOP("follow-me.stop"),
        FOLLOW_ME_LOW_REPUTATION("follow-me.low-reputation"),
        STAY_HERE_START("stay-here.start"),
        STAY_HERE_STOP("stay-here.stop"),
        STAY_HERE_LOW_REPUTATION("stay-here.low-reputation"),
        NO_TRADES("no-trades"),
        WHISTLE_TELEPORTED("whistle-teleported"),
        WHISTLE_ERROR("whistle-error"),
        WHISTLE_NO_FAMILY("whistle-no-family"),
        UNKNOWN_PLAYER("unknown-player"),
        NOT_MARRIED("not-married"),
        DIVORCED("divorced"),
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
        SKIN_SAME_SKIN("skin.same-skin"),
        SKIN_VILLAGER_SAME_SKIN("skin.villager-same-skin"),
        SKIN_DISGUISED("skin.disguised"),
        ONLY_FROM_PLAYER("only-from-player"),
        ONLY_FROM_CONSOLE("only-from-console"),
        INVALID_NUMBER("invalid-number");
        private final String path;

        Message(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}