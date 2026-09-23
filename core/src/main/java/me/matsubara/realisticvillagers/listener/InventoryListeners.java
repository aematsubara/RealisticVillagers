package me.matsubara.realisticvillagers.listener;

import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import net.wesjd.anvilgui.AnvilGUI;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
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
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class InventoryListeners implements Listener {

    private final RealisticVillagers plugin;
    private final Map<UUID, ChatInputContext> pendingChatInput = new HashMap<>();

    private record ChatInputContext(Consumer<String> onInput, Runnable onCancel) {}

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
                closeInventory(player);
                pendingChatInput.put(player.getUniqueId(), new ChatInputContext(
                        text -> openWhistleGUI(player, null, text),
                        () -> openWhistleGUI(player, whistle.getCurrentPage(), null)));
                player.sendMessage("§e[RV] §fType your search term in chat, or type §ccancel§f to go back.");
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

        if (interact instanceof CustomGUI customGui) {
            event.setCancelled(true);
            handleCustomGUIClick(customGui, npc, player, event.getSlot());
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
                closeInventory(player);
                pendingChatInput.put(player.getUniqueId(), new ChatInputContext(
                        text -> openPlayersGUI(npc, player, null, text),
                        () -> openPlayersGUI(npc, player, players.getCurrentPage(), null)));
                player.sendMessage("§e[RV] §fType your search term in chat, or type §ccancel§f to go back.");
                return;
            } else if (isCustomItem(current, "clear-search")) {
                players.setShouldStopInteracting(false);
                openPlayersGUI(npc, player, null, null);
                return;
            } else if (isCustomItem(current, "add-new-player")) {
                players.setShouldStopInteracting(false);
                runTask(() -> openAddNewPlayerGUI(player, npc));
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
                closeInventory(player);
                pendingChatInput.put(player.getUniqueId(), new ChatInputContext(
                        text -> openCombatGUI(npc, player, null, text, combat.isAnimal()),
                        () -> openCombatGUI(npc, player, combat.getCurrent(), null, combat.isAnimal())));
                player.sendMessage("§e[RV] §fType your search term in chat, or type §ccancel§f to go back.");
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

        if (interact instanceof CombatSettingsGUI settings) {
            if (isCustomItem(current, "players")) {
                settings.setShouldStopInteracting(false);
                if (npc.getPlayers().isEmpty()) {
                    // Defer past the InventoryClickEvent so CombatSettingsGUI fully closes first.
                    runTask(() -> openAddNewPlayerGUI(player, npc));
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
                if (VillagerListeners.HOLOGRAM_SUPPORTED && plugin.getHologramManager().isMenuEnabled()) {
                    // Hologram system active: re-open the hologram (it was closed when CombatSettingsGUI opened).
                    settings.setShouldStopInteracting(false);
                    runTask(() -> plugin.getHologramManager().openMenu(player, npc));
                } else {
                    runTask(() -> new MainGUI(plugin, npc, player));
                }
            }
            return;
        }

        if (!(interact instanceof MainGUI main)) return;

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
            handleInspectInventory(npc, player);
            return;
        } else if (isCustomItem(current, "gift")) {
            handleGift(npc, player);
        } else if (isCustomItem(current, "procreate")) {
            handleProcreate(npc, player);
        } else if (isCustomItem(current, "divorce")) {
            handleDivorce(npc, player);
        } else if (isCustomItem(current, "combat")) {
            if (handleCombatSettingsGUI(npc, player)) main.setShouldStopInteracting(false);
            return;
        } else if (isCustomItem(current, "set-home")) {
            handleSetHome(npc, player);
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
                // ValhallaMMO support: tried first, and only its own answer decides whether there
                // is anything to trade. A villager it has taken over may carry no vanilla recipes
                // of her own at all — her trades live entirely in Valhalla's data, not on the
                // entity — so checking villager.getRecipes() ahead of it would shake its head at a
                // villager who has plenty to sell and simply isn't the one holding the list.
                // Declining falls back to exactly the vanilla check this replaced.
                startTrading(npc, player, villager, () -> {
                    if (villager.getRecipes().isEmpty()) {
                        // Is a baby villager or has empty trades, shake head at player.
                        messages.send(player, npc, Messages.Message.NO_TRADES);
                        npc.shakeHead(player);
                    } else {
                        // Start trading from villager instance so discounts are applied to the player.
                        npc.startTrading(player);
                    }
                });
                return;
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
        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(Config.PLAYERS_TITLE.asStringTranslated())
                .text(Config.PLAYERS_TEXT.asStringTranslated())
                .itemLeft(new ItemStack(Material.PLAYER_HEAD))
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(state.getText()));
                    }
                    String text = state.getText().trim();
                    Messages messages = plugin.getMessages();

                    if (plugin.getTracker().isInvalidNametag(text)) {
                        messages.send(player, Messages.Message.INVALID_NAME);
                        npc.stopInteracting();
                        return RealisticVillagers.CLOSE_RESPONSE;
                    }

                    @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(text);
                    String targetName = target.getName();
                    UUID targetUUID = target.getUniqueId();

                    if (targetName == null) {
                        messages.send(player, Messages.Message.UNKNOWN_PLAYER);
                    } else if (player.getUniqueId().equals(targetUUID)) {
                        messages.send(player, Messages.Message.NOT_YOURSELF);
                    } else if (npc.getPlayers().contains(targetUUID)) {
                        messages.send(player, Messages.Message.ALREADY_ADDED);
                    } else if (!target.hasPlayedBefore()) {
                        messages.send(player, Messages.Message.HAS_NEVER_PLAYER_BEFORE);
                    } else if (npc.isFamily(targetUUID, true)) {
                        messages.send(player, Messages.Message.PLAYER_IS_FAMILY_MEMBER);
                    } else {
                        messages.send(player, Messages.Message.PLAYERS_ADDED,
                                s -> s.replace("%player-name%", targetName));
                        npc.getPlayers().add(targetUUID);
                        openPlayersGUI(npc, player, null, null);
                        return RealisticVillagers.CLOSE_RESPONSE;
                    }

                    npc.stopInteracting();
                    return RealisticVillagers.CLOSE_RESPONSE;
                })
                .open(player);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ChatInputContext ctx = pendingChatInput.remove(uuid);
        if (ctx == null) return;

        event.setCancelled(true);
        String input = ChatColor.stripColor(event.getMessage().trim());
        if (input.equalsIgnoreCase("cancel") || input.isBlank()) {
            plugin.getServer().getScheduler().runTask(plugin, ctx.onCancel());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> ctx.onInput().accept(input));
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        pendingChatInput.remove(event.getPlayer().getUniqueId());
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

    public void handleGift(@NotNull IVillagerNPC npc, @NotNull Player player) {
        if (!(npc.bukkit() instanceof Villager villager)) return;
        Messages messages = plugin.getMessages();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) {
            messages.send(player, npc, Messages.Message.GIFT_EXPECTING);
        } else {
            boolean isRing = PluginUtils.isItem(mainHand, plugin.getIsRingKey());
            boolean isCross = PluginUtils.isItem(mainHand, plugin.getIsCrossKey());
            boolean isValidGift = isRing || isCross
                    || plugin.getGiftManager().getGift(mainHand.getType()) != null;
            if (!isValidGift) {
                int badRep = plugin.getConfig().getInt("bad-gift-reputation", 5);
                if (badRep > 1) npc.addMinorNegative(player, badRep);
                npc.bukkit().playEffect(EntityEffect.VILLAGER_ANGRY);
                plugin.getMessages().sendRandomGiftMessage(player, npc, null);
            } else if (plugin.getCooldownManager().isOnCooldown(player, villager, "gift")) {
                messages.send(player, npc, Messages.Message.GIFT_COOLDOWN);
            } else {
                ItemStack giftItem = mainHand.clone();
                giftItem.setAmount(1);
                if (mainHand.getAmount() > 1) {
                    mainHand.setAmount(mainHand.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
                plugin.getExpectingManager().handleGift(npc, player, giftItem);
                plugin.getCooldownManager().addCooldown(player, villager, "gift");
            }
        }
        closeInventory(player);
    }

    public void handleProcreate(@NotNull IVillagerNPC npc, @NotNull Player player) {
        if (!(npc.bukkit() instanceof Villager villager)) return;
        Messages messages = plugin.getMessages();

        if (conditionNotMet(player, villager.isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)) return;
        if (conditionNotMet(player, npc.isPartner(player), Messages.Message.INTERACT_FAIL_NOT_MARRIED)) return;

        if (!Config.IGNORE_SEX_WHEN_PROCREATING.asBool()) {
            String playerSex = player.getPersistentDataContainer()
                    .getOrDefault(plugin.getPlayerSexKey(), PersistentDataType.STRING, "male");
            String villagerSex = npc.getSex().isEmpty() ? "female" : npc.getSex();
            if (conditionNotMet(player, !playerSex.equalsIgnoreCase(villagerSex), Messages.Message.PROCREATE_FAIL_SAME_SEX)) return;
        }

        int reputation = npc.getReputation(player);
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
        closeInventory(player);
    }

    public void handleDivorce(@NotNull IVillagerNPC npc, @NotNull Player player) {
        if (!(npc.bukkit() instanceof Villager villager)) return;
        Messages messages = plugin.getMessages();
        boolean isPartner = npc.isPartner(player);

        if (conditionNotMet(player, villager.isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)) return;
        if (conditionNotMet(player, isPartner, Messages.Message.INTERACT_FAIL_NOT_MARRIED)) return;

        boolean hasDivorcePapers = (isPartner && villager.getProfession() == Villager.Profession.CLERIC)
                || removeDivorcePapers(player.getInventory());

        int lossReputation = hasDivorcePapers
                ? Config.DIVORCE_REPUTATION_LOSS_PAPERS.asInt()
                : Config.DIVORCE_REPUTATION_LOSS.asInt();

        if (lossReputation > 1) npc.addMinorNegative(player, lossReputation);

        if (hasDivorcePapers) {
            messages.send(player, npc, Messages.Message.DIVORCE_PAPERS);
        } else {
            messages.send(player, npc, Messages.Message.DIVORCE_NORMAL);
        }

        npc.divorceAndDropRing(player);
        closeInventory(player);
    }

    public void handleSetHome(@NotNull IVillagerNPC npc, @NotNull Player player) {
        boolean isPartner = npc.isPartner(player);
        boolean isFamily = npc.isFamily(player, true);
        if (notAllowedToModify(player, isPartner, isFamily,
                Config.WHO_CAN_MODIFY_VILLAGER_HOME, true, "realisticvillagers.bypass.sethome")) return;

        Messages messages = plugin.getMessages();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        boolean holdingBed = !mainHand.getType().isAir() && mainHand.getType().name().endsWith("_BED");
        if (holdingBed) {
            ItemStack markedBed = mainHand.clone();
            markedBed.setAmount(1);
            ItemMeta bedMeta = markedBed.getItemMeta();
            if (bedMeta != null) {
                bedMeta.getPersistentDataContainer().set(
                        plugin.getBedVillagerKey(), PersistentDataType.STRING,
                        npc.bukkit().getUniqueId().toString());
                bedMeta.setDisplayName(PluginUtils.translate("&5" + npc.getVillagerName() + "'s Bed"));
                bedMeta.setLore(Collections.singletonList(PluginUtils.translate("&7Place to assign as home")));
                try { bedMeta.setEnchantmentGlintOverride(true); } catch (Throwable ignored) {}
                markedBed.setItemMeta(bedMeta);
            }
            if (mainHand.getAmount() > 1) {
                mainHand.setAmount(mainHand.getAmount() - 1);
                player.getInventory().addItem(markedBed);
            } else {
                player.getInventory().setItemInMainHand(markedBed);
            }
            messages.send(player, Messages.Message.BED_MARKED, s -> s.replace("%villager-name%", npc.getVillagerName()));
        } else {
            messages.send(player, Messages.Message.SELECT_BED);
        }
        closeInventory(player);
    }

    public void handleInspectInventory(@NotNull IVillagerNPC npc, @NotNull Player player) {
        runTask(() -> new EquipmentGUI(plugin, npc, player));
    }

    public boolean handleCombatSettingsGUI(@NotNull IVillagerNPC npc, @NotNull Player player) {
        boolean isPartner = npc.isPartner(player);
        boolean isFamily = npc.isFamily(player, true);
        if (notAllowedToModify(player, isPartner, isFamily,
                Config.WHO_CAN_MODIFY_VILLAGER_COMBAT, true, "realisticvillagers.bypass.combat")) return false;
        runTask(() -> new CombatSettingsGUI(plugin, npc, player));
        return true;
    }

    public void handleOpenCustomGUI(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull String guiName) {
        runTask(() -> new CustomGUI(plugin, npc, player, guiName));
    }

    private void handleCustomGUIClick(@NotNull CustomGUI gui, @NotNull IVillagerNPC npc, @NotNull Player player, int slot) {
        List<?> actions = plugin.getGuiConfig().getList("gui.custom." + gui.getGuiName() + ".items." + slot + ".actions");
        if (actions != null) runCustomGuiActions(actions, npc, player, null);
    }

    /** Executes a custom GUI item's (or anvil submit's) "actions:" list. Each entry is either a
     *  bare string (currently only "close") or a single-key map — give-item, message, run-command
     *  (console), run-command-as-player, open-menu (another gui.custom.<name>), or open-anvil. */
    @SuppressWarnings("unchecked")
    private void runCustomGuiActions(@NotNull List<?> actions, @NotNull IVillagerNPC npc, @NotNull Player player, @Nullable String input) {
        for (Object raw : actions) {
            if (raw instanceof String string) {
                if (string.equalsIgnoreCase("close")) closeInventory(player);
                continue;
            }

            if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) continue;
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            switch (key) {
                case "give-item" -> giveCustomGuiItem(player, applyPlaceholders(String.valueOf(value), npc, player, input));
                case "message" -> player.sendMessage(PluginUtils.translate(applyPlaceholders(String.valueOf(value), npc, player, input)));
                case "run-command" -> plugin.getServer().dispatchCommand(
                        plugin.getServer().getConsoleSender(), applyPlaceholders(String.valueOf(value), npc, player, input));
                case "run-command-as-player" -> plugin.getServer().dispatchCommand(
                        player, applyPlaceholders(String.valueOf(value), npc, player, input));
                case "open-menu" -> {
                    String target = applyPlaceholders(String.valueOf(value), npc, player, input);
                    closeInventory(player);
                    runTask(() -> new CustomGUI(plugin, npc, player, target));
                }
                case "open-anvil" -> {
                    if (value instanceof Map) openCustomGuiAnvil(npc, player, (Map<String, Object>) value);
                }
                default -> {
                }
            }
        }
    }

    private void giveCustomGuiItem(@NotNull Player player, @NotNull String raw) {
        String[] parts = raw.split(":", 2);
        Material material = PluginUtils.getOrNull(Material.class, parts[0].trim().toUpperCase(Locale.ROOT));
        if (material == null) return;

        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        player.getInventory().addItem(new ItemStack(material, amount));
    }

    /** Opens an anvil text-input prompt from an "open-anvil:" action; the submitted text becomes
     *  %input% for the nested "actions:" list (see gui.yml for the format). */
    @SuppressWarnings("unchecked")
    private void openCustomGuiAnvil(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull Map<String, Object> config) {
        String title = applyPlaceholders(String.valueOf(config.getOrDefault("title", "")), npc, player, null);
        String text = applyPlaceholders(String.valueOf(config.getOrDefault("text", "")), npc, player, null);
        List<?> onSubmit = config.get("actions") instanceof List<?> list ? list : Collections.emptyList();

        closeInventory(player);
        runTask(() -> new AnvilGUI.Builder()
                .plugin(plugin)
                .title(PluginUtils.translate(title))
                .text(text)
                .onClick((slot, state) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.singletonList(AnvilGUI.ResponseAction.replaceInputText(state.getText()));
                    }
                    runCustomGuiActions(onSubmit, npc, player, state.getText());
                    return RealisticVillagers.CLOSE_RESPONSE;
                })
                .open(player));
    }

    private String applyPlaceholders(@NotNull String text, @NotNull IVillagerNPC npc, @NotNull Player player, @Nullable String input) {
        String replaced = text
                .replace("%player%", player.getName())
                .replace("%villager-name%", npc.getVillagerName())
                .replace("%reputation%", String.valueOf(npc.getReputation(player.getUniqueId())));
        return input != null ? replaced.replace("%input%", input) : replaced;
    }

    private boolean handleVTL(Player player, Villager villager) {
        Plugin vtl = plugin.getServer().getPluginManager().getPlugin("VillagerTradeLimiter");
        if (vtl == null) return false;

        runTask(() -> {
            if (plugin.getCompatibilityManager().handleVTL(vtl, player, villager)) closeInventory(player);
        });
        return true;
    }

    /**
     * Opens trading for this villager, through ValhallaMMO's own interface where that applies and
     * through {@code fallback} otherwise.
     * <p>
     * The single door both the hologram menu's "Trade" button and this chest GUI's "Trade" item
     * go through, so a server running ValhallaMMO gets the same behaviour from either one rather
     * than the two silently drifting apart as each grew its own copy of this decision.
     * <p>
     * {@code fallback} rather than a hardcoded {@code npc.startTrading(player)}: the two callers
     * do not agree on what "no Valhalla" should fall back to — the chest GUI still wants its own
     * "shake head, no trades" check first, the hologram menu does not — and the deciding whether
     * Valhalla applies is the only part actually worth sharing between them.
     * <p>
     * The player is marked with {@link me.matsubara.realisticvillagers.compatibility.ValhallaCompatibility#expect}
     * before either branch runs, whichever one ends up opening a window. That marking is what
     * tells apart, from the guard's side, a merchant window this call opened on purpose — Valhalla's
     * or the plugin's own vanilla one — from Valhalla opening one uninvited on a raw click; see
     * that method's note for why both branches need it and not just the one that goes through
     * Valhalla.
     */
    public void startTrading(IVillagerNPC npc, Player player, Villager villager, Runnable fallback) {
        Plugin valhalla = plugin.getServer().getPluginManager().getPlugin("ValhallaMMO");

        runTask(() -> {
            if (valhalla != null) {
                // Marked before the attempt, not after — Valhalla opens the real window off its
                // own schedule (see ValhallaCompatibility#expect), which can land before this
                // method would otherwise get around to marking it.
                me.matsubara.realisticvillagers.compatibility.ValhallaCompatibility.expect(
                        player.getUniqueId(), villager.getUniqueId());

                if (plugin.getCompatibilityManager().handleValhallaTrade(plugin, valhalla, player, villager)) return;
            }

            // Either Valhalla is not installed, or it was and had nothing configured for her
            // profession — fall back exactly as if Valhalla were not part of this at all.
            fallback.run();
        });
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
        if (other != null) {
            LivingEntity otherBukkit = other.bukkit();
            // Defensive cleanup: if the cached villager is no longer valid (unloaded/dead/wrong world
            // after a world reset) remove the stale entry so the player isn't permanently blocked.
            boolean stale = !other.isExpecting()
                    || otherBukkit == null
                    || !otherBukkit.isValid()
                    || !otherBukkit.getWorld().equals(player.getWorld());
            if (stale) {
                expectingManager.remove(playerUUID);
                // Always remove the cooldown on stale cleanup so the player is not left stranded.
                plugin.getCooldownManager().removeCooldown(player, checkType.name().toLowerCase(Locale.ROOT));
                if (other.isExpecting()) {
                    other.stopExpecting();
                }
            } else {
                messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_OTHER_EXPECTING_" + other.getExpectingType()));
                return;
            }
        }

        // Player is in cooldown.
        // Use isOnCooldown (check-only) so we don't prematurely register a cooldown entry
        // before the action actually completes – the real cooldown is added in handleVillagerPickUp
        // (or the bed handler) after the gift/bed is successfully processed.
        if (plugin.getCooldownManager().isOnCooldown(player, (Villager) npc.bukkit(), checkType.name().toLowerCase(Locale.ROOT))) {
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

        int delta = plugin.getMessages().getInteractionReputation(interactType.getName(), chatEvent.isSuccess());
        if (delta > 0) {
            npc.addMinorPositive(player, delta);
        } else if (delta < 0) {
            npc.addMinorNegative(player, -delta);
        }

        // Stop being annoyed after a success chat interaction.
        if (success && Config.ANNOYING_METER_CLEAR_AFTER_SUCCESS_INTERACTION.asBool()) {
            plugin.getAnnoyingManager().stopBeingAnnoyed(player, npc);
        }

        EntityEffect effect = chatEvent.isSuccess() ? interactType.isFlirt() ? EntityEffect.VILLAGER_HEART : EntityEffect.VILLAGER_HAPPY : EntityEffect.VILLAGER_ANGRY;
        npc.bukkit().playEffect(effect);

        String rawMsg = plugin.getMessages().sendRandomInteractionMessage(player, npc, interactType, chatEvent.isSuccess());
        if (!rawMsg.isEmpty()) {
            plugin.getHologramManager().showSpeech(npc.bukkit(), rawMsg);
        }
    }

    /** Same idea as {@link #handleChatInteraction}, but for a chat type that only exists in
     *  config (a hologram custom menu item whose id matches a top-level key in default.yml).
     *  Always behaves like CHAT: a global success chance, and a "<type>.<target>.[success|fail]"
     *  message path — no per-type behaviour flags (that's what the built-in enum types are for). */
    public void handleCustomChatInteraction(IVillagerNPC npc, @NotNull String type, @NotNull Player player) {
        boolean success = ThreadLocalRandom.current().nextFloat() < Config.CHANCE_OF_CHAT_INTERACTION_SUCCESS.asFloat();

        int delta = plugin.getMessages().getInteractionReputation(type, success);
        if (delta > 0) {
            npc.addMinorPositive(player, delta);
        } else if (delta < 0) {
            npc.addMinorNegative(player, -delta);
        }

        if (success && Config.ANNOYING_METER_CLEAR_AFTER_SUCCESS_INTERACTION.asBool()) {
            plugin.getAnnoyingManager().stopBeingAnnoyed(player, npc);
        }

        npc.bukkit().playEffect(success ? EntityEffect.VILLAGER_HAPPY : EntityEffect.VILLAGER_ANGRY);

        String rawMsg = plugin.getMessages().sendRandomCustomChatMessage(player, npc, type, success);
        if (!rawMsg.isEmpty()) {
            plugin.getHologramManager().showSpeech(npc.bukkit(), rawMsg);
        }
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
            closeInventory(player);
            pendingChatInput.put(player.getUniqueId(), new ChatInputContext(
                    text -> SkinGUI.openMenu(plugin, player, currentSex, isAdult, null, text),
                    () -> SkinGUI.openMenu(plugin, player, currentSex, isAdult, skin.getCurrentPage(), null)));
            player.sendMessage("§e[RV] §fType your search term in chat, or type §ccancel§f to go back.");
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
        closeInventory(player);
        pendingChatInput.put(player.getUniqueId(), new ChatInputContext(
                result -> {
                    if (plugin.getTracker().isInvalidNametag(result)) {
                        runTask(() -> new NewSkinGUI(plugin, player, skin.getPreviousSource()));
                        return;
                    }

                    Player target = Bukkit.getPlayer(result);
                    GameProfile profile;
                    String sex = source.isMale() ? "male" : "female";

                    if (target != null && (profile = plugin.getConverter().getPlayerProfile(target)) != null) {
                        Property property = profile.getProperties().get("textures").iterator().next();
                        tracker.addNewSkin(player, null, "none", sex, source.isAdult(), property.getValue(), property.getSignature());
                        return;
                    }

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
                                messages.send(player, Messages.Message.SKIN_NOT_FOUND);
                                return;
                            }

                            tracker.addNewSkin(player, null, "none", sex, source.isAdult(), texture, signature);
                        } catch (IOException exception) {
                            if (exception instanceof FileNotFoundException) {
                                messages.send(player, Messages.Message.SKIN_NOT_FOUND);
                            } else {
                                exception.printStackTrace();
                            }
                        }
                    });

                    messages.send(player, Messages.Message.SKIN_WAIT_WHILE_CREATING);
                },
                () -> runTask(() -> new NewSkinGUI(plugin, player, skin.getPreviousSource()))));
        player.sendMessage("§e[RV] §fType a player name in chat, or type §ccancel§f to go back.");
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