package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

public final class Messages {

    private final RealisticVillagers plugin;

    private File file;
    private FileConfiguration configuration;

    private static final String EMPTY = "";

    public Messages(RealisticVillagers plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    public void reloadConfig() {
        try {
            configuration = new YamlConfiguration();
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    public void sendRandomGiftMessage(Player player, IVillagerNPC npc, @Nullable GiftCategory category) {
        InteractionTargetType target = InteractionTargetType.getInteractionTarget(npc, player);
        String type = category != null ? ".success." + category.name() : ".fail";
        send(player, npc, getRandomMessage("gift." + target.getName() + type));
    }

    public void sendRandomInteractionMessage(Player player, IVillagerNPC npc, GUIInteractType interactType, boolean success) {
        InteractionTargetType target = InteractionTargetType.getInteractionTarget(npc, player);

        boolean checkSuccess = !interactType.isGreet() && !interactType.isInsult() && !interactType.isBeProudOf();
        String path = interactType.getName() + "." + target.getName() + (checkSuccess ? "." + (success ? "success" : "fail") : "");

        send(player, npc, getRandomMessage(path));
    }

    public void send(Player player, IVillagerNPC npc, Message message) {
        send(player, npc, getRandomMessage(message));
    }

    public void send(Player player, IVillagerNPC npc, String message) {
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

    public void send(CommandSender sender, Message message, @Nullable UnaryOperator<String> operator) {
        String messageString = getRandomMessage(message);
        if (!messageString.isEmpty()) {
            sender.sendMessage(operator != null ? operator.apply(messageString) : messageString);
        }
    }

    public String getVillagerTitle(Villager.Profession profession) {
        String professionName = plugin.getConfig().getString("variable-text.profession." + profession.name().toLowerCase());
        return Config.VILLAGER_TITLE_ARTICLE.asString() + " " + professionName;
    }

    public String getRandomMessage(Message message) {
        return getRandomMessage(message.getPath());
    }

    @SuppressWarnings("unchecked")
    private String getRandomMessage(String path) {
        if (!configuration.contains(path, true)) return EMPTY;

        String randomMessage;
        Object object = configuration.get(path);
        if (object instanceof String string) {
            randomMessage = string;
        } else if (object instanceof List<?> messages) {
            try {
                randomMessage = getRandomMessage((List<String>) messages);
            } catch (ClassCastException exception) {
                return EMPTY;
            }
        } else return EMPTY;

        return PluginUtils.translate(randomMessage);
    }

    public String getRandomMessage(List<String> list) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
    }

    @SuppressWarnings({"unchecked", "unused"})
    public void sendMessages(CommandSender sender, Message message) {
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
        CAN_NOT_SPAWN_BABY("can-not-spawn-baby"),
        DIVORCE_NORMAL("divorce.normal"),
        DIVORCE_PAPERS("divorce.papers"),
        CLERIC_DIVORCE_PAPERS("cleric.divorce-papers"),
        CLERIC_NOT_MARRIED("cleric.not-married"),
        BED_OCCUPIED("bed-occupied"),
        BED_ESTABLISHED("bed-established"),
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
        INTERACT_FAIL_NOT_ALLOWED("interact-fail.not-allowed"),
        GIFT_EXPECTING("gift.expecting"),
        GIFT_EXPECTING_FAIL("gift.expecting-fail"),
        THROW_GIFT("throw-gift"),
        SELECT_BED("select-bed"),
        RELOAD("reload"),
        NO_PERMISSION("no-permission"),
        FOLLOW_ME_START("follow-me.start"),
        FOLLOW_ME_STOP("follow-me.stop"),
        FOLLOW_ME_LOW_REPUTATION("follow-me.low-reputation"),
        STAY_HERE_START("stay-here.start"),
        STAY_HERE_STOP("stay-here.stop"),
        STAY_HERE_LOW_REPUTATION("stay-here.low-reputation"),
        NO_TRADES("no-trades"),
        WHISTLE_TELEPORTED("whistle-teleported"),
        WHISTLE_ERROR("whistle-error"),
        WHISTLE_NO_FAMILY("whistle-no-family");

        private final String path;

        Message(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}