package me.matsubara.realisticvillagers.listener;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerChatInteractionEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.anim.RainbowAnimation;
import me.matsubara.realisticvillagers.gui.types.*;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.task.BabyTask;
import me.matsubara.realisticvillagers.task.PreviewTask;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineskin.data.Skin;
import org.mineskin.data.Texture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class InventoryListeners implements Listener {

    private final RealisticVillagers plugin;

    private static final UnaryOperator<String> REPLACE_TIME = string -> string.replace("%time%",
            String.valueOf(Config.TIME_TO_EXPECT.asInt() / 20));

    // We want to ignore some actions to avoid issues (like duping).
    private static final Set<InventoryAction> IGNORE_ACTIONS = Set.of(
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR);

    public InventoryListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof InteractGUI interact)) return;

        if (event.getRawSlots().stream().noneMatch(integer -> integer < interact.getSize())) return;

        if (cancelEquipment(player, interact)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof InteractGUI gui)) return;

        if (gui.getTaskId() != -1) {
            plugin.getServer().getScheduler().cancelTask(gui.getTaskId());
            gui.setTaskId(-1);
        }

        IVillagerNPC npc = gui.getNPC();
        if (npc == null) return;

        if (gui.shouldStopInteracting()) npc.stopInteracting();

        if (!(gui instanceof EquipmentGUI)) return;
        if (!canModifyInventory(npc, player)) return;
        if (!(npc.bukkit() instanceof Villager villager)) return;

        Inventory storage = villager.getInventory();
        int size = storage.getSize();

        ItemStack[] contents = new ItemStack[size];
        System.arraycopy(inventory.getContents(), 0, contents, 0, size);

        storage.setContents(contents);

        int armorStart = size + 10;
        int armorEnd = armorStart + 6;

        EntityEquipment equipment = villager.getEquipment();
        if (equipment == null) return;

        ItemStack air = new ItemStack(Material.AIR);
        for (int i = armorStart; i < armorEnd; i++) {
            ItemStack item = inventory.getItem(i);
            equipment.setItem(
                    EquipmentGUI.ARMOR_SLOTS_ORDER[i - armorStart],
                    item != null ? item : air);
        }
    }

    private boolean cancelEquipment(Player player, InteractGUI interact) {
        return !(interact instanceof EquipmentGUI) || (interact.getNPC() != null && !canModifyInventory(interact.getNPC(), player));
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        if (inventory.getType() == InventoryType.PLAYER
                && IGNORE_ACTIONS.contains(event.getAction())
                && event.getView().getTopInventory().getHolder() instanceof InteractGUI interact
                && cancelEquipment(player, interact)) {
            event.setCancelled(true);
            return;
        }

        if (!(inventory.getHolder() instanceof InteractGUI interact)) return;

        ItemStack current = event.getCurrentItem();
        boolean isShiftClick = event.getClick().isShiftClick();

        VillagerTracker tracker = plugin.getTracker();

        IVillagerNPC npc = interact.getNPC();
        if (npc == null) {
            if (interact instanceof SkinGUI skin) {
                handleSkinGUI(event, skin);
                return;
            } else if (interact instanceof NewSkinGUI skin) {
                handleNewSkinGUI(event, skin);
                return;
            }

            if (!(interact instanceof WhistleGUI whistle)) return;

            event.setCancelled(true);

            if (current == null) return;
            if (isCustomItem(current, "close")) {
                closeInventory(player);
                return;
            } else if (isCustomItem(current, "previous")) {
                whistle.previousPage(isShiftClick);
                return;
            } else if (isCustomItem(current, "next")) {
                whistle.nextPage(isShiftClick);
                return;
            } else if (isCustomItem(current, "search")) {
                AtomicBoolean success = new AtomicBoolean();
                new AnvilGUI.Builder()
                        .onClick((slot, snapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                            String text = snapshot.getText();
                            if (text.isBlank()) return RealisticVillagers.CLOSE_RESPONSE;

                            openWhistleGUI(snapshot.getPlayer(), null, text);
                            success.set(true);

                            return RealisticVillagers.CLOSE_RESPONSE;
                        })
                        .title(Config.WHISTLE_SEARCH_TITLE.asStringTranslated())
                        .text(Config.WHISTLE_SEARCH_TEXT.asStringTranslated())
                        .itemLeft(new ItemStack(Material.PAPER))
                        .plugin(plugin)
                        .onClose(snapshot -> {
                            if (success.get()) return;
                            openWhistleGUI(snapshot.getPlayer(), whistle.getCurrentPage(), null);
                        })
                        .open((Player) event.getWhoClicked());
                return;
            } else if (isCustomItem(current, "clear-search")) {
                openWhistleGUI(player, whistle.getCurrentPage(), null);
                return;
            }

            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String villagerUUIDString = meta.getPersistentDataContainer().get(plugin.getVillagerUUIDKey(), PersistentDataType.STRING);
            if (villagerUUIDString == null || villagerUUIDString.isEmpty()) return;

            Location playerLocation = player.getLocation();

            UUID villagerUUID = UUID.fromString(villagerUUIDString);
            for (IVillagerNPC offline : tracker.getOfflineVillagers()) {
                if (!offline.getUniqueId().equals(villagerUUID)) continue;

                Villager bukkit = offline.bukkit() instanceof Villager villager ? villager : null;
                boolean teleported = true;
                if (bukkit != null) {
                    teleport(bukkit, playerLocation);
                } else {
                    Villager villager = plugin.getUnloadedOffline(offline) instanceof Villager temp ? temp : null;
                    if (villager != null) {
                        teleport(villager, playerLocation);
                    } else teleported = false;
                }

                plugin.getMessages().send(
                        player,
                        teleported ? Messages.Message.WHISTLE_TELEPORTED : Messages.Message.WHISTLE_ERROR,
                        message -> message.replace("%villager-name%", offline.getVillagerName()));
                break;
            }

            closeInventory(player);
            return;
        }

        if (interact instanceof EquipmentGUI equipment) {
            if (!canModifyInventory(npc, player)) {
                event.setCancelled(true);
            }

            if (current != null) {
                if (RainbowAnimation.isCachedBackground(equipment.getAnimation(), current)
                        || isCustomItem(current, "back")
                        || isCustomItem(current, "villager")) {
                    event.setCancelled(true);
                }

                if (isCustomItem(current, "back")) {
                    runTask(() -> new MainGUI(plugin, npc, player));
                }
            }
            return;
        }

        event.setCancelled(true);

        if (current == null) return;

        if (interact instanceof PlayersGUI players) {
            if (isCustomItem(current, "back")) {
                runTask(() -> new CombatSettingsGUI(plugin, npc, player));
                return;
            } else if (isCustomItem(current, "previous")) {
                players.previousPage(isShiftClick);
                return;
            } else if (isCustomItem(current, "next")) {
                players.nextPage(isShiftClick);
                return;
            } else if (isCustomItem(current, "search")) {
                players.setShouldStopInteracting(false);
                AtomicBoolean success = new AtomicBoolean();
                new AnvilGUI.Builder()
                        .onClick((slot, snapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                            String text = snapshot.getText();
                            if (text.isBlank()) return RealisticVillagers.CLOSE_RESPONSE;

                            openPlayersGUI(npc, snapshot.getPlayer(), null, text);
                            success.set(true);

                            return RealisticVillagers.CLOSE_RESPONSE;
                        })
                        .title(Config.PLAYERS_TITLE.asStringTranslated())
                        .text(Config.PLAYERS_TEXT.asStringTranslated())
                        .itemLeft(new ItemStack(Material.PAPER))
                        .plugin(plugin)
                        .onClose(snapshot -> {
                            if (success.get()) return;
                            openPlayersGUI(npc, snapshot.getPlayer(), players.getCurrentPage(), null);
                        })
                        .open(player);
                return;
            } else if (isCustomItem(current, "clear-search")) {
                players.setShouldStopInteracting(false);
                openPlayersGUI(npc, player, null, null);
                return;
            } else if (isCustomItem(current, "add-new-player")) {
                players.setShouldStopInteracting(false);
                openAddNewPlayerGUI(player, npc);
                return;
            }

            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String villagerUUID = meta.getPersistentDataContainer().get(plugin.getPlayerUUIDKey(), PersistentDataType.STRING);
            if (villagerUUID == null || villagerUUID.isEmpty()) return;

            UUID uuid = UUID.fromString(villagerUUID);

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.getName() == null) return; // Shouldn't happen.

            plugin.getMessages().send(player, Messages.Message.PLAYERS_REMOVED, string -> string.replace("%player-name%", offlinePlayer.getName()));
            npc.getPlayers().remove(uuid);

            closeInventory(player);
            return;
        }

        if (interact instanceof CombatGUI combat) {
            if (isCustomItem(current, "back")) {
                runTask(() -> new CombatSettingsGUI(plugin, npc, player));
            } else if (isCustomItem(current, "previous")) {
                combat.previousPage(isShiftClick);
            } else if (isCustomItem(current, "next")) {
                combat.nextPage(isShiftClick);
            } else if (isCustomItem(current, "search")) {
                combat.setShouldStopInteracting(false);
                AtomicBoolean success = new AtomicBoolean(false);
                new AnvilGUI.Builder()
                        .onClick((slot, snapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                            String text = snapshot.getText();
                            if (text.isBlank()) return RealisticVillagers.CLOSE_RESPONSE;

                            openCombatGUI(npc, snapshot.getPlayer(), null, text, combat.isAnimal());
                            success.set(true);

                            return RealisticVillagers.CLOSE_RESPONSE;
                        })
                        .onClose(snapshot -> {
                            if (success.get()) return;
                            openCombatGUI(npc, snapshot.getPlayer(), combat.getCurrent(), null, combat.isAnimal());
                        })
                        .title(Config.COMBAT_SEARCH_TITLE.asStringTranslated())
                        .text(Config.COMBAT_SEARCH_TEXT.asStringTranslated())
                        .itemLeft(new ItemStack(Material.PAPER))
                        .plugin(plugin)
                        .open((Player) event.getWhoClicked());
            } else if (isCustomItem(current, "clear-search")) {
                combat.setShouldStopInteracting(false);
                openCombatGUI(npc, player, null, null, combat.isAnimal());
            } else if (current.getItemMeta() != null) {
                ItemMeta meta = current.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();

                String type = container.get(plugin.getEntityTypeKey(), PersistentDataType.STRING);
                if (type == null) return;

                EntityType targetType = PluginUtils.getOrNull(EntityType.class, type);

                boolean enable = !npc.isTarget(targetType);
                if (enable) {
                    npc.addTarget(targetType);
                } else {
                    npc.removeTarget(targetType);
                }

                int slot = event.getRawSlot();
                inventory.setItem(slot > 18 ? slot : slot + 9, new ItemBuilder(enable ? combat.getEnabled() : combat.getDisabled())
                        .setData(plugin.getEntityTypeKey(), PersistentDataType.STRING, type)
                        .build());
            }

            return;
        }

        int reputation = npc.getReputation(player);

        if (interact instanceof CombatSettingsGUI settings) {
            if (isCustomItem(current, "players")) {
                settings.setShouldStopInteracting(false);
                if (npc.getPlayers().isEmpty()) {
                    openAddNewPlayerGUI(player, npc);
                } else {
                    openPlayersGUI(npc, player, null, null);
                }
            } else if (isCustomItem(current, "animals")) {
                settings.setShouldStopInteracting(false);
                openCombatGUI(npc, player, null, null, true);
            } else if (isCustomItem(current, "monsters")) {
                settings.setShouldStopInteracting(false);
                openCombatGUI(npc, player, null, null, false);
            } else if (isCustomItem(current, "back")) {
                runTask(() -> new MainGUI(plugin, npc, player));
            }
            return;
        }

        if (!(interact instanceof MainGUI main)) return;

        boolean isFamily = npc.isFamily(player, true);
        boolean isPartner = npc.isPartner(player);

        Messages messages = plugin.getMessages();

        if (!(npc.bukkit() instanceof Villager villager)) return;

        if (isCustomItem(current, "follow-me")) {
            handleFollorOrStay(npc, player, InteractType.FOLLOW_ME, false);
        } else if (isCustomItem(current, "stay-here")) {
            handleFollorOrStay(npc, player, InteractType.STAY_HERE, false);
        } else if (isCustomItem(current, "information")) {
            return;
        } else if (isCustomItem(current, "inspect-inventory")) {
            main.setShouldStopInteracting(false);
            runTask(() -> new EquipmentGUI(plugin, npc, player));
            return;
        } else if (isCustomItem(current, "gift")) {
            Messages.Message message = plugin.getExpectingManager().getGiftModeFromConfig().drop() ?
                    Messages.Message.THROW_GIFT :
                    Messages.Message.RIGHT_CLICK_GIFT;
            handleExpecting(player, npc, ExpectingType.GIFT, message, Messages.Message.GIFT_EXPECTING);
        } else if (isCustomItem(current, "procreate")) {
            // Return if it's a kid.
            if (conditionNotMet(player, villager.isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)) return;

            // Return if not married.
            if (conditionNotMet(player, isPartner, Messages.Message.INTERACT_FAIL_NOT_MARRIED)) return;

            if (reputation < Config.REPUTATION_REQUIRED_TO_PROCREATE.asInt()) {
                messages.send(player, npc, Messages.Message.PROCREATE_FAIL_LOW_REPUTATION);
                closeInventory(player);
                return;
            }

            long elapsed = System.currentTimeMillis() - npc.getLastProcreation(),
                    cooldown = Config.PROCREATION_COOLDOWN.asLong(),
                    leftMillis = cooldown - elapsed,
                    leftSeconds = (leftMillis / 1000L) % 60L;

            if (elapsed < cooldown && leftSeconds > 0) {
                String next = PluginUtils.formatMillis(leftMillis);
                messages.send(player, npc, Messages.Message.PROCREATE_FAIL_HAS_BABY);
                messages.send(player, Messages.Message.PROCREATE_COOLDOWN, string -> string.replace("%time%", next));
                closeInventory(player);
                return;
            }

            npc.setProcreatingWith(player.getUniqueId());
            new BabyTask(plugin, villager, player).runTaskTimer(plugin, 0L, 20L);
        } else if (isCustomItem(current, "divorce")) {
            // Return if it's a kid.
            if (conditionNotMet(player, villager.isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)) return;

            // Return if not married.
            if (conditionNotMet(player, isPartner, Messages.Message.INTERACT_FAIL_NOT_MARRIED)) return;

            // Only remove divorce papers if the villager isn't a cleric partner.
            boolean hasDivorcePapers = (isPartner && villager.getProfession() == Villager.Profession.CLERIC)
                    || removeDivorcePapers(player.getInventory());

            int lossReputation;
            if (hasDivorcePapers) {
                lossReputation = Config.DIVORCE_REPUTATION_LOSS_PAPERS.asInt();
            } else {
                lossReputation = Config.DIVORCE_REPUTATION_LOSS.asInt();
            }

            if (lossReputation > 1) npc.addMinorNegative(player, lossReputation);

            if (hasDivorcePapers) {
                messages.send(player, npc, Messages.Message.DIVORCE_PAPERS);
            } else {
                messages.send(player, npc, Messages.Message.DIVORCE_NORMAL);
            }

            // Divorce, remove and drop previous wedding ring.
            npc.divorceAndDropRing(player);
        } else if (isCustomItem(current, "combat")) {
            if (notAllowedToModify(player,
                    isPartner,
                    isFamily,
                    Config.WHO_CAN_MODIFY_VILLAGER_COMBAT,
                    true,
                    "realisticvillagers.bypass.combat")) return;
            main.setShouldStopInteracting(false);
            runTask(() -> new CombatSettingsGUI(plugin, npc, player));
            return;
        } else if (isCustomItem(current, "set-home")) {
            if (notAllowedToModify(player,
                    isPartner,
                    isFamily,
                    Config.WHO_CAN_MODIFY_VILLAGER_HOME,
                    true,
                    "realisticvillagers.bypass.sethome")) return;
            handleExpecting(player, npc, ExpectingType.BED, Messages.Message.SELECT_BED, Messages.Message.SET_HOME_EXPECTING);
        } else if (isCustomItem(current, "divorce-papers")) {
            // If it's (ask) papers item, then the villager is INDEED a cleric.
            if (!plugin.isMarried(player)) {
                messages.send(player, npc, Messages.Message.CLERIC_NOT_MARRIED);
                closeInventory(player);
                return;
            }

            if (plugin.getCooldownManager().canInteract(player, villager, "divorce-papers")) {
                messages.send(player, npc, Messages.Message.CLERIC_DIVORCE_PAPERS);
                npc.drop(plugin.getDivorcePapers());
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
            }
        } else if (isCustomItem(current, "trade") || isCustomItem(current, "no-trades")) {
            // VTL support.
            if (handleVTL(player, villager)) return;

            if (villager.isTrading()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_TRADING);
            } else {
                if (villager.getRecipes().isEmpty()) {
                    // Is a baby villager or has empty trades, shake head at player.
                    messages.send(player, npc, Messages.Message.NO_TRADES);
                    npc.shakeHead(player);
                } else {
                    // Start trading from villager instance so discounts are applied to the player.
                    plugin.getServer().getScheduler().runTask(plugin, () -> npc.startTrading(player));
                    return;
                }
            }
        } else if (current.getItemMeta() != null) {
            if (RainbowAnimation.isCachedBackground(main.getAnimation(), current)) return;

            ItemMeta meta = current.getItemMeta();

            PersistentDataContainer container = meta.getPersistentDataContainer();
            String type = container.get(plugin.getChatInteractionTypeKey(), PersistentDataType.STRING);
            if (type != null) {
                GUIInteractType interactType;
                if (type.equalsIgnoreCase("proud-of")) {
                    interactType = GUIInteractType.BE_PROUD_OF;
                } else {
                    interactType = PluginUtils.getOrNull(GUIInteractType.class, type);
                }

                boolean isProudOf;
                if ((isProudOf = interactType == GUIInteractType.BE_PROUD_OF) || interactType == GUIInteractType.FLIRT) {
                    if (isProudOf && conditionNotMet(player, !villager.isAdult(), Messages.Message.INTERACT_FAIL_NOT_A_KID)) {
                        return;
                    } else if (!isProudOf && (conditionNotMet(player, villager.isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)
                            || conditionNotMet(player, !npc.isFamily(player), Messages.Message.INTERACT_FAIL_ONLY_PARTNER_OR_NON_FAMILY_ADULT))) {
                        return;
                    }
                }

                if (plugin.getCooldownManager().canInteract(player, villager, interactType.getName())) {
                    handleChatInteraction(npc, interactType, player);
                } else {
                    messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
                }
            }
        }

        closeInventory(player);
    }

    private void teleport(@NotNull Villager villager, Location location) {
        // For some reason this is necessary since 1.21.8? the ID changes after a teleport?
        int previousId = villager.getEntityId();
        PluginUtils.teleportWithPassengers(villager, location);
        if (previousId != villager.getEntityId()) {
            plugin.getTracker().removeNPC(previousId);
        }
    }

    private void openAddNewPlayerGUI(Player player, IVillagerNPC npc) {
        AtomicBoolean success = new AtomicBoolean();
        new AnvilGUI.Builder()
                .onClick((slot, snapshot) -> {
                    Player opener = snapshot.getPlayer();
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                    Messages messages = plugin.getMessages();

                    String text = ChatColor.stripColor(snapshot.getText().strip());
                    if (plugin.getTracker().isInvalidNametag(text)) {
                        messages.send(opener, Messages.Message.INVALID_NAME);
                        return RealisticVillagers.CLOSE_RESPONSE;
                    }

                    @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(text);

                    String targetName = target.getName();
                    UUID targetUUID = target.getUniqueId();

                    if (targetName == null) {
                        messages.send(opener, Messages.Message.UNKNOWN_PLAYER);
                    } else if (opener.getUniqueId().equals(targetUUID)) {
                        messages.send(opener, Messages.Message.NOT_YOURSELF);
                    } else if (npc.getPlayers().contains(targetUUID)) {
                        messages.send(opener, Messages.Message.ALREADY_ADDED);
                    } else if (!target.hasPlayedBefore()) {
                        messages.send(opener, Messages.Message.HAS_NEVER_PLAYER_BEFORE);
                    } else if (npc.isFamily(targetUUID, true)) {
                        messages.send(opener, Messages.Message.PLAYER_IS_FAMILY_MEMBER);
                    } else {
                        success.set(true);
                        messages.send(opener, Messages.Message.PLAYERS_ADDED, string -> string.replace("%player-name%", targetName));
                        npc.getPlayers().add(targetUUID);
                        openPlayersGUI(npc, snapshot.getPlayer(), null, null);
                        return Collections.emptyList();
                    }

                    return RealisticVillagers.CLOSE_RESPONSE;
                })
                .onClose(snapshot -> {
                    if (success.get()) return;
                    npc.stopInteracting();
                })
                .title(Config.PLAYERS_TITLE.asStringTranslated())
                .text(Config.PLAYERS_TEXT.asStringTranslated())
                .itemLeft(new ItemStack(Material.PAPER))
                .plugin(plugin)
                .open(player);
    }

    public boolean isCustomItem(@NotNull ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && Objects.equals(meta.getPersistentDataContainer().get(plugin.getItemIdKey(), PersistentDataType.STRING), id);
    }

    private void openPlayersGUI(IVillagerNPC npc, Player player, @Nullable Integer page, @Nullable String keyword) {
        runTask(() -> new PlayersGUI(plugin, npc, player, npc.getPlayers().stream()
                .map(Bukkit::getOfflinePlayer)
                .collect(Collectors.toSet()), page, keyword));
    }

    private boolean notAllowedToModify(@NotNull Player player, boolean isPartner, boolean isFamily, @NotNull Config whoCanModify, boolean sendMessage, String permission) {
        if (player.hasPermission(permission)) return false;

        return switch (whoCanModify.asString("FAMILY").toUpperCase(Locale.ROOT)) {
            case "NONE" -> conditionNotMet(player, false, sendMessage ? Messages.Message.INTERACT_FAIL_NONE : null);
            case "PARTNER" ->
                    conditionNotMet(player, isPartner, sendMessage ? Messages.Message.INTERACT_FAIL_NOT_MARRIED : null);
            case "FAMILY" ->
                    conditionNotMet(player, isFamily, sendMessage ? Messages.Message.INTERACT_FAIL_NOT_FAMILY : null);
            default -> false;
        };
    }

    public void handleFollorOrStay(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull InteractType type, boolean forced) {
        String typeName = type.name(), typeShortName = typeName.split("_")[0];

        Config bypass = PluginUtils.getOrNull(Config.class, "FAMILY_BYPASS_ASK_TO_" + typeShortName);
        Config required = PluginUtils.getOrNull(Config.class, "REPUTATION_REQUIRED_TO_ASK_TO_" + typeShortName);
        if (bypass == null || required == null) return;

        Messages messages = plugin.getMessages();
        if (player.hasPermission("realisticvillagers.bypass." + type.name().toLowerCase(Locale.ROOT).replace("_", ""))
                || (bypass.asBool() && npc.isFamily(player, true))
                || npc.getReputation(player) >= required.asInt()) {

            if (forced) npc.setInteractingWithAndType(player.getUniqueId(), type);
            else npc.setInteractType(type);

            if (type == InteractType.STAY_HERE) npc.stayInPlace();

            messages.send(player, npc, Messages.Message.valueOf(typeName + "_START"));
        } else {
            messages.send(player, npc, Messages.Message.valueOf(typeName + "_LOW_REPUTATION"));
            npc.shakeHead(player);
        }
    }

    private boolean handleVTL(Player player, Villager villager) {
        Plugin vtl = plugin.getServer().getPluginManager().getPlugin("VillagerTradeLimiter");
        if (vtl == null) return false;

        runTask(() -> {
            if (plugin.getCompatibilityManager().handleVTL(vtl, player, villager)) closeInventory(player);
        });
        return true;
    }

    private void handleExpecting(Player player, @NotNull IVillagerNPC npc, ExpectingType checkType, Messages.Message fromServer, Messages.Message fromVillager) {
        Messages messages = plugin.getMessages();
        ExpectingManager expectingManager = plugin.getExpectingManager();

        // This villager is already expecting something from a player.
        if (npc.isExpecting()) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + checkType + "_FROM_SOMEONE"));
            return;
        }

        UUID playerUUID = player.getUniqueId();
        IVillagerNPC other = expectingManager.get(playerUUID);

        // Another villager is expecting something from this player.
        if (other != null && other.isExpecting()) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_OTHER_EXPECTING_" + other.getExpectingType()));
            return;
        }

        // Player is in cooldown.
        if (!plugin.getCooldownManager().canInteract(player, (Villager) npc.bukkit(), checkType.name().toLowerCase(Locale.ROOT))) {
            messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
            return;
        }

        // Start expecting.
        npc.startExpectingFrom(checkType, playerUUID, Config.TIME_TO_EXPECT.asInt());
        messages.send(player, fromServer, REPLACE_TIME);
        messages.send(player, npc, fromVillager);
        expectingManager.expect(playerUUID, npc);
    }

    private boolean removeDivorcePapers(@NotNull Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            if (!meta.getPersistentDataContainer().has(
                    plugin.getDivorcePapersKey(),
                    PersistentDataType.INTEGER)) continue;

            int newAmount = item.getAmount() - 1;
            if (newAmount > 0) {
                item.setAmount(newAmount);
            } else {
                inventory.clear(slot);
            }
            return true;
        }
        return false;
    }

    private boolean conditionNotMet(Player player, boolean condition, @Nullable Messages.Message message) {
        if (!condition && message != null) {
            plugin.getMessages().send(player, message);
            closeInventory(player);
        }
        return !condition;
    }

    public void handleChatInteraction(IVillagerNPC npc, @NotNull GUIInteractType interactType, @NotNull Player player) {
        boolean successByJoke = interactType.isJoke() && Config.PARTNER_JOKE_ALWAYS_SUCCESS.asBool() && npc.isPartner(player);
        boolean success = !interactType.isInsult()
                && (successByJoke
                || interactType.isGreet()
                || interactType.isBeProudOf()
                || ThreadLocalRandom.current().nextFloat() < Config.CHANCE_OF_CHAT_INTERACTION_SUCCESS.asFloat());

        VillagerChatInteractionEvent chatEvent = new VillagerChatInteractionEvent(npc, player, interactType, success);
        plugin.getServer().getPluginManager().callEvent(chatEvent);

        int amount = Config.CHAT_INTERACT_REPUTATION.asInt();
        if (amount > 1) {
            if (chatEvent.isSuccess()) {
                npc.addMinorPositive(player, amount);
            } else {
                npc.addMinorNegative(player, amount);
            }
        }

        // Stop being annoyed after a success chat interaction.
        if (success && Config.ANNOYING_METER_CLEAR_AFTER_SUCCESS_INTERACTION.asBool()) {
            plugin.getAnnoyingManager().stopBeingAnnoyed(player, npc);
        }

        EntityEffect effect = chatEvent.isSuccess() ? interactType.isFlirt() ? EntityEffect.VILLAGER_HEART : EntityEffect.VILLAGER_HAPPY : EntityEffect.VILLAGER_ANGRY;
        npc.bukkit().playEffect(effect);

        plugin.getMessages().sendRandomInteractionMessage(player, npc, interactType, chatEvent.isSuccess());
    }

    private void handleSkinGUI(@NotNull InventoryClickEvent event, SkinGUI skin) {
        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Player player = (Player) event.getWhoClicked();
        VillagerTracker tracker = plugin.getTracker();
        Messages messages = plugin.getMessages();

        ClickType click = event.getClick();
        boolean isShiftClick = click.isShiftClick();
        boolean isAdult = skin.isAdult();

        String currentSex = skin.isMale() ? "male" : "female";
        if (isCustomItem(current, "close")) {
            closeInventory(player);
            return;
        } else if (isCustomItem(current, "previous")) {
            skin.previousPage(isShiftClick);
            return;
        } else if (isCustomItem(current, "next")) {
            skin.nextPage(isShiftClick);
            return;
        } else if (isCustomItem(current, "search")) {
            AtomicBoolean success = new AtomicBoolean();
            new AnvilGUI.Builder()
                    .onClick((slot, snapshot) -> {
                        if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

                        String text = snapshot.getText();
                        if (text.isBlank()) return RealisticVillagers.CLOSE_RESPONSE;

                        runTask(() -> SkinGUI.openMenu(plugin, snapshot.getPlayer(), currentSex, isAdult, null, text));
                        success.set(true);

                        return RealisticVillagers.CLOSE_RESPONSE;
                    })
                    .title(Config.SKIN_SEARCH_TITLE.asStringTranslated())
                    .text(Config.SKIN_SEARCH_TEXT.asStringTranslated())
                    .itemLeft(new ItemStack(Material.PAPER))
                    .plugin(plugin)
                    .onClose(snapshot -> {
                        if (success.get()) return;
                        runTask(() -> SkinGUI.openMenu(plugin, snapshot.getPlayer(), currentSex, isAdult, skin.getCurrentPage(), null));
                    })
                    .open((Player) event.getWhoClicked());
            return;
        } else if (isCustomItem(current, "clear-search")) {
            openSkinGUI(player, currentSex, isAdult);
            return;
        } else if (isCustomItem(current, "male") || isCustomItem(current, "female")) {
            openSkinGUI(player, skin.isMale() ? "female" : "male", isAdult);
            return;
        } else if (isCustomItem(current, "adult") || isCustomItem(current, "kid")) {
            openSkinGUI(player, currentSex, !isAdult);
            return;
        } else if (isCustomItem(current, "add-new-skin")) {
            if (Config.MINESKIN_API_KEY.asString().isEmpty()) {
                messages.send(player, Messages.Message.NO_MINESKIN_API_KEY);
                closeInventory(player);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> new NewSkinGUI(plugin, player, skin));
            return;
        }

        // Handle profession switches.
        if (handleSkinGUISwitches(event, skin)) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        String skinData = meta.getPersistentDataContainer().get(plugin.getSkinDataKey(), PersistentDataType.STRING);
        if (skinData == null || skinData.isEmpty()) return;

        String[] data = skinData.split(":");
        if (data.length != 2) return;

        String sex = data[0];
        int id = Integer.parseInt(data[1]);

        if (click == ClickType.RIGHT) {
            String fileName = sex + ".yml";
            Pair<File, FileConfiguration> pair = tracker.getFile(fileName);
            FileConfiguration config = pair.getValue();

            ConfigurationSection noneSection = config.getConfigurationSection("none");
            if (noneSection != null && noneSection.getKeys(false).size() == 1) {
                messages.send(player, Messages.Message.SKIN_AT_LEAST_ONE);
                closeInventory(player);
                return;
            }

            for (Villager.Profession profession : Villager.Profession.values()) {
                String professionLower = profession.name().toLowerCase(Locale.ROOT);
                config.set(professionLower + "." + id, null);

                // Remove the profession section if empty.
                ConfigurationSection section = config.getConfigurationSection(professionLower);
                if (section != null && section.getKeys(false).isEmpty()) config.set(professionLower, null);
            }

            try {
                config.save(pair.getKey());
            } catch (IOException exception) {
                exception.printStackTrace();
            }

            messages.send(player, Messages.Message.SKIN_REMOVED);

            // Remove skin from villagers.
            for (World world : plugin.getServer().getWorlds()) {
                for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                    // At this point, invalid villagers shouldn't have a skin.
                    if (tracker.isInvalid(villager)) continue;

                    Optional<IVillagerNPC> online = plugin.getConverter().getNPC(villager);
                    if (online.isEmpty() || online.get().getSkinTextureId() != id) continue;

                    // Respawn NPC with a new texture.
                    tracker.refreshNPCSkin(villager, false);
                }
            }

            // Remove from cache and close inventory for players with the (skins) GUI open; this should close the current player too.
            (sex.equals("male") ? SkinGUI.CACHE_MALE_HEADS : SkinGUI.CACHE_FEMALE_HEADS).remove(id);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getOpenInventory().getTopInventory() instanceof SkinGUI) {
                    closeInventory(online);
                }
            }
            closeInventory(player);
            return;
        } else if (click == ClickType.MIDDLE) {
            String fileName = sex + ".yml";
            Pair<File, FileConfiguration> pair = tracker.getFile(fileName);
            FileConfiguration config = pair.getValue();

            ConfigurationSection noneSection = config.getConfigurationSection("none");
            if (noneSection == null) return;

            boolean forBabies = !config.getBoolean("none." + id + ".for-babies");
            config.set("none." + id + ".for-babies", forBabies);

            try {
                config.save(pair.getKey());

                // Refresh inventory.
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getOpenInventory().getTopInventory() instanceof SkinGUI) {
                        openSkinGUI(online, sex, isAdult);
                    }
                }
                openSkinGUI(player, sex, isAdult);

                // Remove skin from villagers if necessary.
                for (World world : plugin.getServer().getWorlds()) {
                    for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                        // At this point, invalid villagers shouldn't have a skin.
                        if (tracker.isInvalid(villager)) continue;

                        Optional<IVillagerNPC> online = plugin.getConverter().getNPC(villager);
                        IVillagerNPC npc;

                        // Ignore villager with different id.
                        if (online.isEmpty() || (npc = online.get()).getSkinTextureId() != id) continue;

                        // Ignore baby villagers with skin changed for babies.
                        if (forBabies && !villager.isAdult()) continue;

                        // Ignore adult villagers with skin changed for adults or kids if had the skin as a kid.
                        // In this case, we don't care about the state of "forBabies".
                        if (villager.isAdult() && npc.getKidSkinTextureId() == id) continue;

                        // Reseting the skin so a new one is generated.
                        npc.setSkinTextureId(0);

                        // Respawn NPC with a new texture.
                        tracker.refreshNPCSkin(villager, false);
                    }
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            return;
        }

        String profession = skin.getCurrentProfession().toLowerCase(Locale.ROOT).replace("_", "-");

        TextureProperty textures = tracker.getTextures(sex, profession, id);
        if (textures.getName().equals("error")) {
            // This will take a bit of time since it's running in another thread...
            messages.send(player, Messages.Message.SKIN_WAIT_WHILE_CREATING);

            // Create skin and THEN disguise.
            CompletableFuture<Skin> future = tracker.createSkin(player, sex, isAdult, profession, id);
            if (future != null) {
                future.thenAcceptAsync(skinObject -> {
                    // For some unknown reason, needs to be done sync.
                    Texture texture = skinObject.data.texture;
                    runTask(() -> startPreview(player, new TextureProperty("textures", texture.value, texture.signature)));
                }, tracker.getMineskinClient().getRequestExecutor());
            } else {
                VillagerTracker.SkinRelatedData tempData = new VillagerTracker.SkinRelatedData(sex, profession, id, null, null, null);
                plugin.getLogger().severe("Failed to generate a new skin when trying to create: " + tempData + "!");
            }

            closeInventory(player);
            return;
        }

        startPreview(player, textures);
        closeInventory(player);
    }

    private void startPreview(@NotNull Player player, TextureProperty textures) {
        PreviewTask currentPreview = plugin.getTracker().getPreviews().remove(player.getUniqueId());
        if (currentPreview != null && !currentPreview.isCancelled()) {
            currentPreview.cancel();
        }

        new PreviewTask(plugin, player, textures).runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    private boolean handleSkinGUISwitches(@NotNull InventoryClickEvent event, SkinGUI skin) {
        // noinspection DataFlowIssue | We know that the current item isn't null.
        if (!isCustomItem(event.getCurrentItem(), "profession")) return false;

        Map<String, ItemStack> professionItems = skin.getProfessionItems();

        String nextProfession = getNextProfession(event, skin, professionItems);
        skin.setCurrentProfession(nextProfession);

        event.getInventory().setItem(event.getRawSlot(), professionItems.get(nextProfession));

        plugin.getTracker().getSelectedProfession().put(event.getWhoClicked().getUniqueId(), nextProfession);
        return true;
    }

    private String getNextProfession(@NotNull InventoryClickEvent event, @NotNull SkinGUI skin, @NotNull Map<String, ItemStack> map) {
        ArrayList<String> professions = new ArrayList<>(map.keySet());

        int currentProfessionIndex = professions.indexOf(Objects.requireNonNullElse(skin.getCurrentProfession(), "NONE"));
        int nextProfessionIndex = currentProfessionIndex + (event.getClick().isRightClick() ? 1 : -1);

        return professions.get(nextProfessionIndex >= map.size() ? 0 : nextProfessionIndex < 0 ? skin.getProfessionItems().size() - 1 : nextProfessionIndex);
    }

    private void handleNewSkinGUI(@NotNull InventoryClickEvent event, NewSkinGUI skin) {
        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Player player = (Player) event.getWhoClicked();
        VillagerTracker tracker = plugin.getTracker();

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Messages messages = plugin.getMessages();
        SkinGUI source = skin.getPreviousSource();

        if (isCustomItem(current, "back")) {
            runTask(() -> SkinGUI.openMenu(plugin,
                    player,
                    source.isMale() ? "male" : "female",
                    source.isAdult(),
                    source.getCurrentPage(),
                    source.getKeyword()));
        }

        if (isCustomItem(current, "from-console")) {
            messages.send(player, Messages.Message.ONLY_FROM_CONSOLE);
            closeInventory(player);
            return;
        }

        if (!isCustomItem(current, "from-player")) return;

        // Add new skin from a player.
        AtomicBoolean success = new AtomicBoolean();
        new AnvilGUI.Builder()
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    Player opener = snapshot.getPlayer();

                    String result = snapshot.getText();
                    if (plugin.getTracker().isInvalidNametag(result)) {
                        return RealisticVillagers.CLOSE_RESPONSE;
                    }

                    success.set(true);

                    Player target = Bukkit.getPlayer(result);
                    GameProfile profile;

                    String sex = source.isMale() ? "male" : "female";

                    // Player is online, get texture data from the profile.
                    if (target != null && (profile = plugin.getConverter().getPlayerProfile(target)) != null) {
                        Property property = profile.getProperties().get("textures").iterator().next();
                        tracker.addNewSkin(opener, null, "none", sex, source.isAdult(), property.getValue(), property.getSignature());
                        return RealisticVillagers.CLOSE_RESPONSE;
                    }

                    // Player is offline, we need to get the texture from minecraft servers...
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            URL profiles = new URL("https://api.mojang.com/users/profiles/minecraft/" + result);
                            InputStreamReader profilesReader = new InputStreamReader(profiles.openStream());
                            String uuid = JsonParser.parseReader(profilesReader).getAsJsonObject().get("id").getAsString();

                            URL textures = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                            InputStreamReader texturesReader = new InputStreamReader(textures.openStream());
                            JsonObject asJson = JsonParser.parseReader(texturesReader).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();

                            String texture = asJson.get("value").getAsString();
                            String signature = asJson.get("signature").getAsString();

                            if (texture == null || signature == null) {
                                messages.send(opener, Messages.Message.SKIN_NOT_FOUND);
                                return;
                            }

                            tracker.addNewSkin(opener, null, "none", sex, source.isAdult(), texture, signature);
                        } catch (IOException exception) {
                            if (exception instanceof FileNotFoundException) {
                                messages.send(opener, Messages.Message.SKIN_NOT_FOUND);
                            } else {
                                exception.printStackTrace();
                            }
                        }
                    });

                    // This will take a bit of time since it's running in another thread...
                    messages.send(opener, Messages.Message.SKIN_WAIT_WHILE_CREATING);
                    return RealisticVillagers.CLOSE_RESPONSE;
                })
                .title(Config.NEW_SKIN_TITLE.asStringTranslated())
                .text(Config.NEW_SKIN_TEXT.asStringTranslated())
                .itemLeft(new ItemStack(Material.PAPER))
                .plugin(plugin)
                .onClose(snapshot -> {
                    if (success.get()) return;
                    runTask(() -> new NewSkinGUI(plugin, snapshot.getPlayer(), skin.getPreviousSource()));
                })
                .open((Player) event.getWhoClicked());
    }

    private void openCombatGUI(IVillagerNPC npc, Player player, @Nullable Integer page, @Nullable String keyword, boolean isAnimal) {
        runTask(() -> new CombatGUI(plugin, npc, player, page, keyword, isAnimal));
    }

    private void openWhistleGUI(Player player, @Nullable Integer page, @Nullable String keyword) {
        runTask(() -> plugin.openWhistleGUI(player, page, keyword));
    }

    private void openSkinGUI(Player player, String sex, boolean isAdult) {
        runTask(() -> SkinGUI.openMenu(plugin, player, sex, isAdult, null, null));
    }

    private void closeInventory(@NotNull Player player) {
        runTask(player::closeInventory);
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    public boolean canModifyInventory(IVillagerNPC npc, Player player) {
        return !notAllowedToModifyInventoryOrName(player,
                npc,
                Config.WHO_CAN_MODIFY_VILLAGER_INVENTORY,
                "realisticvillagers.bypass.inventory");
    }

    public boolean notAllowedToModifyInventoryOrName(@NotNull Player player,
                                                     @NotNull IVillagerNPC npc,
                                                     Config whoCanModify,
                                                     String permission) {
        return notAllowedToModify(player, npc.isPartner(player), npc.isFamily(player, true), whoCanModify, false, permission);
    }
}