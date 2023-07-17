package me.matsubara.realisticvillagers.command;

import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.SkinGUI;
import me.matsubara.realisticvillagers.manager.ReviveManager;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Shape;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final RealisticVillagers plugin;

    private static final List<String> SEX_LIST = List.of("male", "female");
    private static final List<String> SEX_USERS = List.of("add-skin", "set-skin", "skins");
    private static final List<String> AGE_STAGE_LIST = List.of("adult", "kid");
    private static final List<String> AGE_STAGE_USERS = List.of("add-skin", "skins");
    private static final List<String> COMMAND_ARGS = List.of(
            "reload",
            "give-ring",
            "give-whistle",
            "give-divorce-papers",
            "give-cross",
            "force-divorce",
            "add-skin",
            "set-skin",
            "skins");
    private static final List<String> HELP = Stream.of(
            "&8----------------------------------------",
            "&6&lRealisticVillagers &f&oCommands &c(optional) <required>",
            "&e/rv reload &f- &7Reload configuration files.",
            "&e/rv give-ring (player) &f- &7Gives a wedding ring.",
            "&e/rv give-whistle (player) &f- &7Gives a whistle.",
            "&e/rv give-divorce-papers (player) &f- &7Gives divorce papers.",
            "&e/rv give-cross (player) &f- &7Gives a cross.",
            "&e/rv force-divorce (player) &f- &7Forces the divorce of a player.",
            "&e/rv add-skin <sex> <age-stage> <texture> <signature> &f- &7Add a new skin (from the console).",
            "&e/rv set-skin <sex> <id> &f- &7Gives you an item to change the skin of a villager.",
            "&e/rv skins (sex) (age-stage) (page) &f- &7Manage all skins.",
            "&8----------------------------------------").map(PluginUtils::translate).toList();
    private static final List<String> SKIN_ID_ARGS = List.of("<id>");
    private static final List<String> TEXTURE_ARGS = List.of("<texture>");
    private static final List<String> SIGNATURE_ARGS = List.of("<signature>");

    public MainCommand(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (notAllowed(sender, "realisticvillagers.help")) return true;

        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();

        String subCommand;
        boolean noArgs = args.length == 0;
        if (noArgs || args.length > 5 || !COMMAND_ARGS.contains((subCommand = args[0]).toLowerCase())) {
            if (noArgs) HELP.forEach(sender::sendMessage);
            else messages.send(sender, Messages.Message.INVALID_COMMAND);
            return true;
        }

        if (subCommand.equalsIgnoreCase("force-divorce")) {
            if (notAllowed(sender, "realisticvillagers.forcedivorce")) return true;
            handleForceDivorce(sender, args);
            return true;
        }

        if (subCommand.equalsIgnoreCase("add-skin") && args.length == 5) {
            if (Config.MINESKIN_API_KEY.asString().isEmpty()) {
                messages.send(sender, Messages.Message.NO_MINESKIN_API_KEY);
                return true;
            }

            if (!(sender instanceof ConsoleCommandSender)) {
                messages.send(sender, Messages.Message.ONLY_FROM_CONSOLE);
                return true;
            }

            String sex = getSex(sender, args[1]);
            if (sex == null) return true;

            String ageStage = getAgeStage(sender, args[2]);
            if (ageStage == null) return true;

            tracker.addNewSkin(sender, null, "none", sex, ageStage.equals("adult"), args[3], args[4]);
            return true;
        }

        if (subCommand.equalsIgnoreCase("set-skin") && args.length == 3) {
            if (notFromPlayerAllowed(sender, "realisticvillagers.setskin")) return true;

            String sex = getSex(sender, args[1]);
            if (sex == null) return true;

            int id;
            try {
                id = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                messages.send(sender, Messages.Message.INVALID_NUMBER);
                return true;
            }

            WrappedSignedProperty textures = tracker.getTextures(sex, "none", id);
            if (textures.getName().equals("error")) {
                messages.send(sender, Messages.Message.SKIN_TEXTURE_NOT_FOUND);
                return true;
            }

            ((Player) sender).getInventory().addItem(new ItemBuilder(plugin.getItem("change-skin").build())
                    .setHead(textures.getValue(), false)
                    .setData(plugin.getSkinDataKey(), PersistentDataType.STRING, String.format("%s:%s", sex, id))
                    .replace("%skin-id%", id)
                    .replace("%sex%", (sex.equals("male") ? Config.MALE : Config.FEMALE).asString())
                    .build());
            return true;
        }

        if (subCommand.equalsIgnoreCase("skins")) {
            if (notFromPlayerAllowed(sender, "realisticvillagers.skins")) return true;

            String sex = getSex(sender, args.length > 1 ? args[1] : "male");
            if (sex == null) return true;

            String ageStage = getAgeStage(sender, args.length > 2 ? args[2].toLowerCase() : "adult");
            if (ageStage == null) return true;

            boolean isAdult = ageStage.equals("adult");

            int page;
            try {
                page = args.length > 3 ? Integer.parseInt(args[3]) : 1;
            } catch (NumberFormatException exception) {
                messages.send(sender, Messages.Message.INVALID_NUMBER);
                return true;
            }

            int amountOfPages = getAmountOfPages(sex, isAdult);
            if (amountOfPages == -1) {
                messages.send(sender, Messages.Message.INVALID_NUMBER);
                return true;
            }

            if (page < 1 || page > amountOfPages) {
                messages.send(sender, Messages.Message.INVALID_NUMBER);
                return true;
            }

            SkinGUI.openMenu(plugin, (Player) sender, sex, isAdult, page - 1, null);
            return true;
        }

        if (getItemCommand(sender, args, "give-ring", plugin.getRing())) return true;
        if (getItemCommand(sender, args, "give-whistle", plugin.getWhistle())) return true;
        if (getItemCommand(sender, args, "give-divorce-papers", plugin.getDivorcePapers())) return true;
        if (getItemCommand(sender, args, "give-cross", plugin.getCross())) return true;

        if (!subCommand.equalsIgnoreCase("reload")) {
            messages.send(sender, Messages.Message.INVALID_COMMAND);
            return true;
        }

        if (notAllowed(sender, "realisticvillagers.reload")) return true;

        boolean skinsDisabled = Config.DISABLE_SKINS.asBool();
        boolean nametagsDisabled = Config.DISABLE_NAMETAGS.asBool();
        boolean reviveEnabled = Config.REVIVE_ENABLED.asBool();

        messages.send(sender, Messages.Message.RELOADING);

        // Close all GUIs.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory() instanceof InteractGUI interact)) continue;
            interact.setShouldStopInteracting(true);
            plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
        }

        ReviveManager reviveManager = plugin.getReviveManager();

        // Cancel all revivals.
        for (ReviveManager.MonumentAnimation animation : reviveManager.getRunningTasks().values()) {
            plugin.getServer().getScheduler().cancelTask(animation.getTaskId());
        }

        SkinGUI.CACHE_MALE_HEADS.clear();
        SkinGUI.CACHE_FEMALE_HEADS.clear();

        // Update config.yml & messages.yml async since we modify a lot of files.
        CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Reload gift categories and update mineskin api-key.
            plugin.getGiftManager().loadGiftCategories();
            tracker.updateMineskinApiKey();

            messages.send(sender, Messages.Message.RELOAD);

            Bukkit.removeRecipe(plugin.getRing().getKey());
            plugin.setRing(plugin.createWeddingRing());

            Bukkit.removeRecipe(plugin.getWhistle().getKey());
            plugin.setWhistle(plugin.createWhistle());

            Bukkit.removeRecipe(plugin.getCross().getKey());
            plugin.setCross(plugin.createCross());

            plugin.reloadDefaultTargetEntities();
            plugin.reloadWantedItems();
            plugin.reloadLoots();

            handleChangedOption(
                    skinsDisabled,
                    Config.DISABLE_SKINS.asBool(),
                    (npc, state) -> {
                        Villager bukkit = npc.bukkit();
                        if (tracker.isInvalid(bukkit, true)) return;

                        if (state) {
                            tracker.removeNPC(bukkit.getEntityId());
                            npc.sendSpawnPacket();
                        } else {
                            npc.sendDestroyPacket();
                            tracker.spawnNPC(bukkit);
                        }
                    });

            handleChangedOption(
                    nametagsDisabled,
                    Config.DISABLE_NAMETAGS.asBool(),
                    (npc, state) -> {
                        // Only disable nametags if skins are enabled.
                        Villager bukkit = npc.bukkit();
                        if (!tracker.isInvalid(bukkit)) tracker.refreshNPCSkin(bukkit, false);
                    });

            if (reviveEnabled == Config.REVIVE_ENABLED.asBool()) return;

            // Register or unregister listeners from revive manager depending on the status; if previously was enabled, not it's disabled.
            if (reviveEnabled) {
                HandlerList.unregisterAll(reviveManager);
            } else {
                Bukkit.getPluginManager().registerEvents(reviveManager, plugin);
            }
        }));
        return true;
    }

    private String getSex(CommandSender sender, String string) {
        return getValidString(sender, string, SEX_LIST, Messages.Message.SKIN_INVALID_SEX);
    }

    private String getAgeStage(CommandSender sender, String string) {
        return getValidString(sender, string, AGE_STAGE_LIST, Messages.Message.SKIN_INVALID_AGE_STAGE);
    }

    private @Nullable String getValidString(CommandSender sender, @NotNull String string, @NotNull List<String> from, Messages.Message notFound) {
        String wanted = string.toLowerCase();
        if (from.contains(wanted)) return wanted;

        plugin.getMessages().send(sender, notFound);
        return null;
    }

    private void handleForceDivorce(CommandSender sender, String @NotNull [] args) {
        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();
        INMSConverter converter = plugin.getConverter();

        @SuppressWarnings("deprecation") OfflinePlayer offline = args.length > 1 ? Bukkit.getOfflinePlayer(args[1]) : sender instanceof Player ? (Player) sender : null;
        if (offline == null || !offline.hasPlayedBefore()) {
            messages.sendMessages(sender, Messages.Message.UNKNOWN_PLAYER);
            return;
        }

        UUID partnerUUID = null;

        Player player = offline.getPlayer();
        if (player == null) {
            File playerFile = tracker.getPlayerNBTFile(offline.getUniqueId());
            if (playerFile != null && (partnerUUID = converter.getPartnerUUIDFromPlayerNBT(playerFile)) != null) {
                converter.removePartnerFromPlayerNBT(playerFile);
            }
        } else {
            String uuidString = player.getPersistentDataContainer().get(plugin.getMarriedWith(), PersistentDataType.STRING);
            if (uuidString != null) partnerUUID = UUID.fromString(uuidString);
            player.getPersistentDataContainer().remove(plugin.getMarriedWith());
        }

        if (partnerUUID == null) {
            messages.send(
                    sender,
                    Messages.Message.NOT_MARRIED,
                    string -> string.replace("%player-name%", Objects.requireNonNullElse(offline.getName(), "???")));
            return;
        }

        for (IVillagerNPC offlineVillager : tracker.getOfflineVillagers()) {
            if (!offlineVillager.getUniqueId().equals(partnerUUID)) continue;

            Villager bukkit = offlineVillager.bukkit();
            if (bukkit == null) {
                bukkit = plugin.getUnloadedOffline(offlineVillager);
                if (bukkit == null) continue;
            }

            // In this case we don't need to ignore invalid villagers.
            IVillagerNPC npc = converter.getNPC(bukkit).orElse(null);
            if (npc == null) continue;

            npc.divorceAndDropRing(player);
            break;
        }

        // At this point, either the player or the villager (or both) should be divorced.
        messages.send(
                sender,
                Messages.Message.DIVORCED,
                string -> string.replace("%player-name%", Objects.requireNonNullElse(offline.getName(), "???")));
    }

    private boolean getItemCommand(CommandSender sender, String[] args, String itemGetter, @NotNull Shape shape) {
        return getItemCommand(sender, args, itemGetter, shape.getResult());
    }

    private boolean getItemCommand(CommandSender sender, String @NotNull [] args, String itemGetter, ItemStack item) {
        if (!args[0].equalsIgnoreCase(itemGetter)) return false;

        boolean isOther = args.length > 1;

        Player target = isOther ? Bukkit.getPlayer(args[1]) : sender instanceof Player ? (Player) sender : null;
        if (notAllowed(sender, "realisticvillagers." + itemGetter
                .replaceFirst("-", isOther ? ".other." : ".")
                .replace("-", ""))) return true;

        if (target != null) target.getInventory().addItem(item);
        else plugin.getMessages().sendMessages(sender, Messages.Message.UNKNOWN_PLAYER);

        return true;
    }

    private boolean notAllowed(@NotNull CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return false;
        plugin.getMessages().send(sender, Messages.Message.NO_PERMISSION);
        return true;
    }

    private boolean notFromPlayer(CommandSender sender) {
        if (sender instanceof Player) return false;
        plugin.getMessages().sendMessages(sender, Messages.Message.ONLY_FROM_PLAYER);
        return true;
    }

    private boolean notFromPlayerAllowed(CommandSender sender, String permission) {
        return notFromPlayer(sender) || notAllowed(sender, permission);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("realisticvillagers")) return null;

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], COMMAND_ARGS, new ArrayList<>());
        }

        if (args.length == 2) {
            // These require the sex list.
            if (SEX_USERS.contains(args[0].toLowerCase())) {
                return StringUtil.copyPartialMatches(args[1], SEX_LIST, new ArrayList<>());
            }
            // give_(item) & force-divorce require a player, so null will give a list with online players; empty list for reload or unknown subcommand.
            return args[0].equalsIgnoreCase("reload") || !COMMAND_ARGS.contains(args[0]) ? Collections.emptyList() : null;
        }

        // rv set-skin <sex> <id>
        if (args.length == 3 && args[0].equalsIgnoreCase("set-skin") && SEX_LIST.contains(args[1].toLowerCase())) {
            return StringUtil.copyPartialMatches(args[2], SKIN_ID_ARGS, new ArrayList<>());
        }

        // rv add-skin <sex> <age-stage> | rv skins (sex) (age-stage)
        if (args.length == 3 && AGE_STAGE_USERS.contains(args[0]) && SEX_LIST.contains(args[1].toLowerCase())) {
            return StringUtil.copyPartialMatches(args[2], AGE_STAGE_LIST, new ArrayList<>());
        }

        // rv skins (sex) (age-stage) (page)
        String sex, ageStage;
        if (args.length == 4
                && args[0].equalsIgnoreCase("skins")
                && SEX_LIST.contains((sex = args[1].toLowerCase()))
                && AGE_STAGE_LIST.contains((ageStage = args[2].toLowerCase()))) {
            int pages = getAmountOfPages(sex, ageStage.equals("adult"));
            if (pages != -1) return StringUtil.copyPartialMatches(
                    args[3],
                    IntStream.range(1, pages + 1).mapToObj(String::valueOf).collect(Collectors.toList()),
                    new ArrayList<>());
        }

        // rv add-skin <sex> <age-stage> <texture> <signature>
        boolean isTexture;
        if (((isTexture = args.length == 4) || args.length == 5)
                && args[0].equalsIgnoreCase("add-skin")
                && SEX_LIST.contains(args[1].toLowerCase())
                && AGE_STAGE_LIST.contains(args[2].toLowerCase())) {
            return StringUtil.copyPartialMatches(args[isTexture ? 3 : 4], isTexture ? TEXTURE_ARGS : SIGNATURE_ARGS, new ArrayList<>());
        }

        return Collections.emptyList();
    }

    private int getAmountOfPages(String sex, boolean isAdult) {
        int skins = (InteractGUI.getValidSize(plugin, "skin", 36) / 9 - 3) * 7;

        Pair<File, FileConfiguration> pair = plugin.getTracker().getFile(sex + ".yml");
        FileConfiguration config = pair.getSecond();

        ConfigurationSection section = config.getConfigurationSection("none");
        if (section == null) return -1;

        Set<String> keys = section.getKeys(false);
        keys.removeIf(key -> {
            boolean forBabies = config.getBoolean("none." + key + ".for-babies");
            return (isAdult && forBabies) || (!isAdult && !forBabies);
        });

        int pages = (int) (Math.ceil((double) keys.size() / skins));
        return pages == 0 ? 1 : pages;
    }

    private void handleChangedOption(boolean previous, boolean current, BiConsumer<IVillagerNPC, Boolean> consumer) {
        if (previous == current) return;

        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                plugin.getConverter().getNPC(villager).ifPresent(npc -> consumer.accept(npc, current));
            }
        }
    }
}