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
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final RealisticVillagers plugin;

    private static final List<String> COMMAND_ARGS = Lists.newArrayList("reload", "get_ring", "get_whistle", "get_divorce_papers");
    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&6&lRealisticVillagers &f&oCommands",
            "&e/rv reload &f- &7Reload configuration files.",
            "&e/rv get_ring &f- &7Get a wedding ring.",
            "&e/rv get_whistle &f- &7Get a whistle.",
            "&e/rv get_divorce_papers &f- &7Get divorce papers.",
            "&8----------------------------------------").map(PluginUtils::translate).toList();

    public MainCommand(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("realisticvillagers")) return true;

        if (!hasPermission(sender, "realisticvillagers.help")) return true;

        if (args.length != 1 || !COMMAND_ARGS.contains(args[0].toLowerCase())) {
            for (String line : HELP) {
                sender.sendMessage(line);
            }
            return true;
        }

        if (getItemCommand(sender, args[0], "get_ring", plugin.getRing())) return true;
        if (getItemCommand(sender, args[0], "get_whistle", plugin.getWhistle())) return true;
        if (getItemCommand(sender, args[0], "get_divorce_papers", plugin.getDivorcePapers())) return true;

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

    private boolean getItemCommand(CommandSender sender, String argument, String itemGetter, Shape shape) {
        return getItemCommand(sender, argument, itemGetter, shape.getResult());
    }

    private boolean getItemCommand(CommandSender sender, String argument, String itemGetter, ItemStack item) {
        if (!argument.equalsIgnoreCase(itemGetter)) return false;
        if (!hasPermission(sender, "realisticvillagers." + itemGetter)) return true;
        if (sender instanceof Player player) {
            player.getInventory().addItem(item);
        }
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