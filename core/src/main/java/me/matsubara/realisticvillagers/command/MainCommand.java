package me.matsubara.realisticvillagers.command;

import com.google.common.collect.Lists;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Shape;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final RealisticVillagers plugin;

    private static final List<String> COMMAND_ARGS = Lists.newArrayList("reload", "give_ring", "give_whistle", "give_divorce_papers", "force_divorce");
    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&6&lRealisticVillagers &f&oCommands &c(optional) <required>",
            "&e/rv reload &f- &7Reload configuration files.",
            "&e/rv give_ring (player) &f- &7Gives a wedding ring.",
            "&e/rv give_whistle (player) &f- &7Gives a whistle.",
            "&e/rv give_divorce_papers (player) &f- &7Gives divorce papers.",
            "&e/rv force_divorce (player) &f- &7Forces the divorce of a player.",
            "&8----------------------------------------").map(PluginUtils::translate).toList();

    public MainCommand(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender, "realisticvillagers.help")) return true;

        if (args.length == 0 || args.length > 2 || !COMMAND_ARGS.contains(args[0].toLowerCase())) {
            for (String line : HELP) {
                sender.sendMessage(line);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("force_divorce")) {
            if (!hasPermission(sender, "realisticvillagers.forcedivorce")) return true;
            handleForceDivorce(sender, args);
            return true;
        }

        if (getItemCommand(sender, args, "give_ring", plugin.getRing())) return true;
        if (getItemCommand(sender, args, "give_whistle", plugin.getWhistle())) return true;
        if (getItemCommand(sender, args, "give_divorce_papers", plugin.getDivorcePapers())) return true;

        if (!hasPermission(sender, "realisticvillagers.reload")) return true;

        boolean skinsDisabled = Config.DISABLE_SKINS.asBool();
        boolean nametagsDisabled = Config.DISABLE_NAMETAGS.asBool();

        plugin.reloadConfig();
        plugin.getGiftManager().loadGiftCategories();

        Messages messages = plugin.getMessages();
        messages.reloadConfig();
        messages.send(sender, Messages.Message.RELOAD);

        Bukkit.removeRecipe(plugin.getRing().getKey());
        plugin.setRing(plugin.createWeddingRing());

        Bukkit.removeRecipe(plugin.getWhistle().getKey());
        plugin.setWhistle(plugin.createWhistle());

        plugin.reloadDefaultTargetEntities();
        plugin.reloadWantedItems();
        plugin.reloadLoots();

        VillagerTracker tracker = plugin.getTracker();
        handleChangedOption(
                skinsDisabled,
                Config.DISABLE_SKINS.asBool(),
                (npc, state) -> {
                    if (tracker.isInvalid(npc.bukkit(), true)) return;

                    if (state) {
                        tracker.removeNPC(npc.bukkit().getEntityId());
                        npc.sendSpawnPacket();
                    } else {
                        npc.sendDestroyPacket();
                        tracker.spawnNPC(npc.bukkit());
                    }
                });

        handleChangedOption(
                nametagsDisabled,
                Config.DISABLE_NAMETAGS.asBool(),
                (npc, state) -> tracker.refreshNPC(npc.bukkit()));

        return true;
    }

    private void handleForceDivorce(CommandSender sender, String[] args) {
        @SuppressWarnings("deprecation") OfflinePlayer offline = args.length > 1 ? Bukkit.getOfflinePlayer(args[1]) : sender instanceof Player ? (Player) sender : null;
        if (offline == null || !offline.hasPlayedBefore()) {
            plugin.getMessages().sendMessages(sender, Messages.Message.UNKNOWN_PLAYER);
            return;
        }

        UUID partnerUUID = null;

        Player player = offline.getPlayer();
        if (player == null) {
            File playerFile = plugin.getTracker().getPlayerNBTFile(offline.getUniqueId());
            if (playerFile != null && (partnerUUID = plugin.getConverter().getPartnerUUIDFromPlayerNBT(playerFile)) != null) {
                plugin.getConverter().removePartnerFromPlayerNBT(playerFile);
            }
        } else {
            String uuidString = player.getPersistentDataContainer().get(plugin.getMarriedWith(), PersistentDataType.STRING);
            if (uuidString != null) partnerUUID = UUID.fromString(uuidString);
            player.getPersistentDataContainer().remove(plugin.getMarriedWith());
        }

        if (partnerUUID == null) {
            plugin.getMessages().send(
                    sender,
                    Messages.Message.NOT_MARRIED,
                    string -> string.replace("%player-name%", Objects.requireNonNullElse(offline.getName(), "???")));
            return;
        }

        for (IVillagerNPC offlineVillager : plugin.getTracker().getOfflineVillagers()) {
            if (!offlineVillager.getUniqueId().equals(partnerUUID)) continue;

            Villager bukkit = offlineVillager.bukkit();
            if (bukkit == null) {
                bukkit = plugin.getUnloadedOffline(offlineVillager);
                if (bukkit == null) continue;
            }

            Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(bukkit);

            IVillagerNPC npc = optional.orElse(null);
            if (npc == null) continue;

            npc.divorceAndDropRing(player);
            break;
        }

        // At this point, either the player or the villager (or both) should be divorced.
        plugin.getMessages().send(
                sender,
                Messages.Message.DIVORCED,
                string -> string.replace("%player-name%", Objects.requireNonNullElse(offline.getName(), "???")));
    }

    private boolean getItemCommand(CommandSender sender, String[] args, String itemGetter, Shape shape) {
        return getItemCommand(sender, args, itemGetter, shape.getResult());
    }

    private boolean getItemCommand(CommandSender sender, String[] args, String itemGetter, ItemStack item) {
        if (!args[0].equalsIgnoreCase(itemGetter)) return false;

        boolean isOther = args.length > 1;

        Player target = isOther ? Bukkit.getPlayer(args[1]) : sender instanceof Player ? (Player) sender : null;
        if (!hasPermission(sender, "realisticvillagers." + itemGetter
                .replaceFirst("_", isOther ? ".other." : ".")
                .replace("_", ""))) return true;

        if (target != null) target.getInventory().addItem(item);
        else plugin.getMessages().sendMessages(sender, Messages.Message.UNKNOWN_PLAYER);

        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.getMessages().send(sender, Messages.Message.NO_PERMISSION);
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("realisticvillagers")) return null;
        return args.length == 1 ? StringUtil.copyPartialMatches(args[0], COMMAND_ARGS, new ArrayList<>()) : null;
    }

    private void handleChangedOption(boolean previous, boolean current, BiConsumer<IVillagerNPC, Boolean> consumer) {
        if (previous == current) return;

        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Villager villager)) continue;

                Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(villager);
                if (npc.isEmpty()) continue;

                consumer.accept(npc.get(), current);
            }
        }
    }
}