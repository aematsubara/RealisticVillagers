package me.matsubara.realisticvillagers.files;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GiftCategory;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerChatInteractionEvent;
import me.matsubara.realisticvillagers.util.PluginUtils;
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

public final class Messages {

    private final RealisticVillagers plugin;

    private File file;
    private FileConfiguration configuration;

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

    public String getRandomGiftMessage(InteractionTargetType target, @Nullable GiftCategory category) {
        String type = category != null ? ".success." + category.name() : ".fail";
        List<String> messages = configuration.getStringList("gift." + target.getName() + type);
        return PluginUtils.translate(getRandomMessage(messages));
    }

    public String getRandomInteractionMessage(InteractionTargetType target, VillagerChatInteractionEvent.ChatType type, boolean success) {
        boolean checkSuccess = !type.isGreet() && !type.isInsult() && !type.isBeProudOf();
        String path = type.getName() + "." + target.getName() + (checkSuccess ? "." + (success ? "success" : "fail") : "");
        List<String> messages = configuration.getStringList(path);
        return PluginUtils.translate(getRandomMessage(messages));
    }

    public void send(IVillagerNPC npc, Player player, Message message) {
        send(npc, player, getRandomMessage(message));
    }

    public void send(IVillagerNPC npc, Player player, String message) {
        Villager.Profession profession = npc.bukkit().getProfession();

        String name;
        if (npc.is(Villager.Profession.NONE) || !Config.SHOW_TITLE_IN_VILLAGER_CHAT_MESSAGE.asBool()) {
            name = npc.getVillagerName();
        } else {
            name = npc.getVillagerName() + " " + getVillagerTitle(profession);
        }

        String formattedMessage = Config.VILLAGER_MESSAGE_FORMAT.asString()
                .replace("%name%", name)
                .replace("%message%", message);

        player.sendMessage(formattedMessage);
    }

    public String getVillagerTitle(Villager.Profession profession) {
        String professionName = plugin.getConfig().getString("variable-text.profession." + profession.name().toLowerCase());
        return Config.VILLAGER_TITLE_ARTICLE.asString() + " " + professionName;
    }

    public String getRandomMessage(Message message) {
        String randomMessage;

        Object value = configuration.get(message.getPath());
        if (value instanceof String string) {
            randomMessage = string;
        } else {
            List<String> messages = configuration.getStringList(message.getPath());
            randomMessage = getRandomMessage(messages);
        }

        return PluginUtils.translate(randomMessage);
    }

    public String getRandomMessage(List<String> list) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
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
        CLERIC_DIVORCE_PAPERS("cleric-divorce-papers"),
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
        GIFT_EXPECTING("gift.expecting"),
        GIFT_EXPECTING_FAIL("gift.expecting-fail"),
        THROW_GIFT("throw-gift"),
        SELECT_BED("select-bed"),
        RELOAD("reload"),
        NO_PERMISSION("no-permission"),
        WRONG_COMMAND("wrong-command"),
        FOLLOW_ME_START("follow-me.start"),
        FOLLOW_ME_STOP("follow-me.stop"),
        STAY_HERE_START("stay-here.start"),
        STAY_HERE_STOP("stay-here.stop");

        private final String path;

        Message(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}