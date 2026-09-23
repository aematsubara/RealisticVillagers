package me.matsubara.realisticvillagers.command;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GenderCommand implements CommandExecutor, TabCompleter {

    private final RealisticVillagers plugin;
    private static final List<String> SEX_LIST = List.of("male", "female");

    public GenderCommand(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        Messages messages = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            messages.send(sender, Messages.Message.ONLY_FROM_PLAYER);
            return true;
        }



        if (args.length < 1) {
            messages.send(sender, Messages.Message.GENDER_INVALID);
            return true;
        }

        String newSex = args[0].toLowerCase(Locale.ROOT);
        if (!newSex.equals("male") && !newSex.equals("female")) {
            messages.send(sender, Messages.Message.GENDER_INVALID);
            return true;
        }

        String currentSex = player.getPersistentDataContainer()
                .get(plugin.getPlayerSexKey(), PersistentDataType.STRING);

        // Answered once, and that answer stands.
        //
        // This had been opened up so that somebody who answered wrongly was not left wearing a
        // body they never chose. That was a real problem and this was the wrong place to fix it:
        // a gender that anybody can retype whenever they like is not an answer, it is a switch,
        // and half the plugin reads it — names, figures, how villagers speak to you — so it
        // changes under everyone standing nearby every time it is flipped.
        //
        // No permission opens this. The first attempt hung the exception on
        // realisticvillagers.genderset, which is granted to operators by default — so the one
        // person most likely to be testing the lock was the one person it did not apply to, and
        // it read as a lock that plainly did not work. Staff still have the way out, and it is a
        // different command: /rv genderset <player> <male|female> sets somebody's gender outright,
        // including their own. A server that wants the question reopened to everybody says so in
        // config.yml instead.
        boolean answered = currentSex != null && !currentSex.isEmpty();

        if (answered && (newSex.equalsIgnoreCase(currentSex)
                || !Config.GENDER_SELECTION_ALLOW_CHANGE.asBool(false))) {
            messages.send(player, Messages.Message.PLAYER_GENDER_ALREADY,
                    s -> s.replace("%gender%", currentSex));
            return true;
        }

        plugin.setPlayerSex(player, newSex);
        messages.send(player, Messages.Message.PLAYER_GENDER_SET,
                s -> s.replace("%gender%", newSex));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return StringUtil.copyPartialMatches(args[0], SEX_LIST, new ArrayList<>());
        return Collections.emptyList();
    }
}
