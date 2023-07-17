package me.matsubara.realisticvillagers.listener;

import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
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
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
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
import java.util.function.UnaryOperator;

public final class InventoryListeners implements Listener {

    private final RealisticVillagers plugin;
    private final UnaryOperator<String> REPLACE_TIME = string -> string.replace("%time%", String.valueOf(Config.TIME_TO_EXPECT.asInt() / 20));

    public InventoryListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
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

        if (!canModifyInventory(npc, (Player) event.getPlayer())) return;

        int size = npc.bukkit().getInventory().getSize();

        ItemStack[] contents = new ItemStack[size];
        System.arraycopy(inventory.getContents(), 0, contents, 0, size);

        npc.bukkit().getInventory().setContents(contents);

        int armorStart = size + 10;
        int armorEnd = armorStart + 6;

        EntityEquipment equipment = npc.bukkit().getEquipment();
        if (equipment == null) return;

        ItemStack air = new ItemStack(Material.AIR);
        for (int i = armorStart; i < armorEnd; i++) {
            ItemStack item = inventory.getItem(i);
            equipment.setItem(
                    EquipmentGUI.ARMOR_SLOTS_ORDER[i - armorStart],
                    item != null ? item : air);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        if (inventory.getType() == InventoryType.PLAYER
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getView().getTopInventory().getHolder() instanceof InteractGUI interact
                && (!(interact instanceof EquipmentGUI) || (interact.getNPC() != null && !canModifyInventory(interact.getNPC(), player)))) {
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
            if (current.isSimilar(whistle.getClose())) {
                closeInventory(player);
                return;
            } else if (current.isSimilar(whistle.getPrevious())) {
                whistle.previousPage(isShiftClick);
                return;
            } else if (current.isSimilar(whistle.getNext())) {
                whistle.nextPage(isShiftClick);
                return;
            } else if (current.isSimilar(whistle.getSearch())) {
                new AnvilGUI.Builder()
                        .onClick((slot, snapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                            openWhistleGUI(snapshot.getPlayer(), snapshot.getText());
                            return RealisticVillagers.CLOSE_RESPONSE;
                        })
                        .title(Config.WHISTLE_SEARCH_TITLE.asStringTranslated())
                        .text(Config.WHISTLE_SEARCH_TEXT.asStringTranslated())
                        .itemLeft(new ItemStack(Material.PAPER))
                        .plugin(plugin)
                        .open((Player) event.getWhoClicked());
                return;
            } else if (current.isSimilar(whistle.getClearSearch())) {
                openWhistleGUI(player, null);
                return;
            }

            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String villagerName = meta.getPersistentDataContainer().get(plugin.getVillagerNameKey(), PersistentDataType.STRING);
            if (villagerName == null || villagerName.isEmpty()) return;

            for (IVillagerNPC offline : tracker.getOfflineVillagers()) {
                if (!offline.getVillagerName().equalsIgnoreCase(villagerName)) continue;

                Villager bukkit = offline.bukkit();
                boolean teleported = true;
                if (bukkit != null) bukkit.teleport(player);
                else {
                    Villager villager = plugin.getUnloadedOffline(offline);
                    if (villager != null) villager.teleport(player);
                    else teleported = false;
                }

                plugin.getMessages().send(
                        player,
                        teleported ? Messages.Message.WHISTLE_TELEPORTED : Messages.Message.WHISTLE_ERROR,
                        message -> message.replace("%villager-name%", villagerName));
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
                        || current.isSimilar(equipment.getHead())
                        || current.isSimilar(equipment.getClose())) {
                    event.setCancelled(true);
                }

                if (current.isSimilar(equipment.getClose())) closeInventory(player);
            }
            return;
        }

        event.setCancelled(true);

        if (current == null) return;

        if (interact instanceof CombatGUI combat) {
            if (current.isSimilar(combat.getClose())) {
                closeInventory(player);
            } else if (current.isSimilar(combat.getPrevious())) {
                combat.previousPage(isShiftClick);
            } else if (current.isSimilar(combat.getNext())) {
                combat.nextPage(isShiftClick);
            } else if (current.isSimilar(combat.getSearch())) {
                combat.setShouldStopInteracting(false);
                new AnvilGUI.Builder()
                        .onClick((slot, snapshot) -> {
                            if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                            openCombatGUI(npc, snapshot.getPlayer(), snapshot.getText());
                            return RealisticVillagers.CLOSE_RESPONSE;
                        })
                        .title(Config.COMBAT_SEARCH_TITLE.asStringTranslated())
                        .text(Config.COMBAT_SEARCH_TEXT.asStringTranslated())
                        .itemLeft(new ItemStack(Material.PAPER))
                        .plugin(plugin)
                        .open((Player) event.getWhoClicked());
            } else if (current.isSimilar(combat.getClearSearch())) {
                combat.setShouldStopInteracting(false);
                openCombatGUI(npc, player, null);
            } else if (current.getItemMeta() != null) {
                ItemMeta meta = current.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();

                String type = container.get(plugin.getEntityTypeKey(), PersistentDataType.STRING);
                if (type == null) return;

                EntityType targetType = EntityType.valueOf(type);

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

        UUID playerUUID = player.getUniqueId();
        int reputation = npc.getReputation(playerUUID);

        MainGUI main = (MainGUI) interact;

        boolean isFamily = npc.isFamily(player.getUniqueId(), true);
        boolean isPartner = npc.isPartner(playerUUID);

        Messages messages = plugin.getMessages();

        if (current.isSimilar(main.getFollow())) {
            handleFollorOrStay(npc, player, InteractType.FOLLOW_ME);
        } else if (current.isSimilar(main.getStay())) {
            handleFollorOrStay(npc, player, InteractType.STAY_HERE);
        } else if (current.isSimilar(main.getInfo())) {
            return;
        } else if (current.isSimilar(main.getInspect())) {
            main.setShouldStopInteracting(false);
            runTask(() -> new EquipmentGUI(plugin, npc, player));
            return;
        } else if (current.isSimilar(main.getGift())) {
            handleExpecting(player, npc, ExpectingType.GIFT, Messages.Message.THROW_GIFT, Messages.Message.GIFT_EXPECTING);
        } else if (current.isSimilar(main.getProcreate())) {
            // Return if it's a kid.
            if (conditionNotMet(player, npc.bukkit().isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)) return;

            // Return if not married.
            if (conditionNotMet(player, isPartner, Messages.Message.INTERACT_FAIL_NOT_MARRIED)) return;

            if (reputation < Config.REPUTATION_REQUIRED_TO_PROCREATE.asInt()) {
                messages.send(player, npc, Messages.Message.PROCREATE_FAIL_LOW_REPUTATION);
                closeInventory(player);
                return;
            }

            long lastProcreation = npc.getLastProcreation();
            long elapsedTime = System.currentTimeMillis() - lastProcreation;

            int procreationCooldown = Config.PROCREATION_COOLDOWN.asInt();

            if (elapsedTime <= procreationCooldown) {
                String next = PluginUtils.getTimeString(procreationCooldown - elapsedTime);
                messages.send(player, npc, Messages.Message.PROCREATE_FAIL_HAS_BABY);
                messages.send(player, Messages.Message.PROCREATE_COOLDOWN, string -> string.replace("%time%", next));
                closeInventory(player);
                return;
            }

            npc.setProcreatingWith(playerUUID);
            new BabyTask(plugin, npc.bukkit(), player).runTaskTimer(plugin, 0L, 20L);
        } else if (current.isSimilar(main.getDivorce())) {
            // Return if it's a kid.
            if (conditionNotMet(player, npc.bukkit().isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)) return;

            // Return if not married.
            if (conditionNotMet(player, isPartner, Messages.Message.INTERACT_FAIL_NOT_MARRIED)) return;

            // Only remove divorce papers if the villager isn't a cleric partner.
            boolean hasDivorcePapers = (isPartner && npc.bukkit().getProfession() == Villager.Profession.CLERIC)
                    || removeDivorcePapers(player.getInventory());

            int lossReputation;
            if (hasDivorcePapers) {
                lossReputation = Config.DIVORCE_REPUTATION_LOSS_PAPERS.asInt();
            } else {
                lossReputation = Config.DIVORCE_REPUTATION_LOSS.asInt();
            }

            if (lossReputation > 1) npc.addMinorNegative(playerUUID, lossReputation);

            if (hasDivorcePapers) {
                messages.send(player, npc, Messages.Message.DIVORCE_PAPERS);
            } else {
                messages.send(player, npc, Messages.Message.DIVORCE_NORMAL);
            }

            // Divorce, remove and drop previous wedding ring.
            npc.divorceAndDropRing(player);
        } else if (current.isSimilar(main.getCombat())) {
            if (notAllowedToModify(player, isPartner, isFamily, Config.WHO_CAN_MODIFY_VILLAGER_COMBAT, true)) return;
            main.setShouldStopInteracting(false);
            openCombatGUI(npc, player, null);
            return;
        } else if (current.isSimilar(main.getHome())) {
            if (notAllowedToModify(player, isPartner, isFamily, Config.WHO_CAN_MODIFY_VILLAGER_HOME, true)) return;
            handleExpecting(player, npc, ExpectingType.BED, Messages.Message.SELECT_BED, Messages.Message.SET_HOME_EXPECTING);
        } else if (current.isSimilar(main.getPapers())) {
            // If it's (ask) papers item, then the villager is INDEED a cleric.
            if (!plugin.isMarried(player)) {
                messages.send(player, npc, Messages.Message.CLERIC_NOT_MARRIED);
                closeInventory(player);
                return;
            }

            if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), "divorce-papers")) {
                messages.send(player, npc, Messages.Message.CLERIC_DIVORCE_PAPERS);
                npc.drop(plugin.getDivorcePapers());
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
            }
        } else if (current.isSimilar(main.getTrade()) || current.isSimilar(main.getNoTrades())) {
            // VTL support.
            if (handleVTL(player, npc.bukkit())) return;

            if (npc.bukkit().isTrading()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_TRADING);
            } else {
                if (npc.bukkit().getRecipes().isEmpty()) {
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
                    interactType = GUIInteractType.valueOf(type);
                }

                boolean isProudOf;
                if ((isProudOf = interactType == GUIInteractType.BE_PROUD_OF) || interactType == GUIInteractType.FLIRT) {
                    if (isProudOf && conditionNotMet(player, !npc.bukkit().isAdult(), Messages.Message.INTERACT_FAIL_NOT_A_KID)) {
                        return;
                    } else if (!isProudOf && (conditionNotMet(player, npc.bukkit().isAdult(), Messages.Message.INTERACT_FAIL_NOT_AN_ADULT)
                            || conditionNotMet(player, !npc.isFamily(player.getUniqueId()), Messages.Message.INTERACT_FAIL_ONLY_PARTNER_OR_NON_FAMILY_ADULT))) {
                        return;
                    }
                }

                if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), interactType.getName())) {
                    handleChatInteraction(npc, interactType, player);
                } else {
                    messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
                }
            }
        }

        closeInventory(player);
    }

    private boolean notAllowedToModify(Player player, boolean isPartner, boolean isFamily, @NotNull Config whoCanModify, boolean sendMessage) {
        return switch (whoCanModify.asString("FAMILY").toUpperCase()) {
            case "NONE" -> conditionNotMet(player, false, sendMessage ? Messages.Message.INTERACT_FAIL_NONE : null);
            case "PARTNER" ->
                    conditionNotMet(player, isPartner, sendMessage ? Messages.Message.INTERACT_FAIL_NOT_MARRIED : null);
            case "FAMILY" ->
                    conditionNotMet(player, isFamily, sendMessage ? Messages.Message.INTERACT_FAIL_NOT_FAMILY : null);
            default -> false;
        };
    }

    private void handleFollorOrStay(@NotNull IVillagerNPC npc, @NotNull Player player, @NotNull InteractType type) {
        String typeName = type.name(), typeShortName = typeName.split("_")[0];
        Config bypass = Config.valueOf("FAMILY_BYPASS_ASK_TO_" + typeShortName);
        Config required = Config.valueOf("REPUTATION_REQUIRED_TO_ASK_TO_" + typeShortName);

        Messages messages = plugin.getMessages();
        if ((bypass.asBool() && npc.isFamily(player.getUniqueId(), true)) || npc.getReputation(player.getUniqueId()) >= required.asInt()) {
            npc.setInteractType(type);
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

        // Other villager is expecting something from this player.
        if (other != null && other.isExpecting()) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_OTHER_EXPECTING_" + other.getExpectingType()));
            return;
        }

        // Player is in cooldown.
        if (!plugin.getCooldownManager().canInteract(player, npc.bukkit(), checkType.name().toLowerCase())) {
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
        UUID playerUUID = player.getUniqueId();

        boolean successByJoke = interactType.isJoke() && Config.PARTNER_JOKE_ALWAYS_SUCCESS.asBool() && npc.isPartner(playerUUID);
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
                npc.addMinorPositive(playerUUID, amount);
            } else {
                npc.addMinorNegative(playerUUID, amount);
            }
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
        if (current.isSimilar(skin.getClose())) {
            closeInventory(player);
            return;
        } else if (current.isSimilar(skin.getPrevious())) {
            skin.previousPage(isShiftClick);
            return;
        } else if (current.isSimilar(skin.getNext())) {
            skin.nextPage(isShiftClick);
            return;
        } else if (current.isSimilar(skin.getSearch())) {
            new AnvilGUI.Builder()
                    .onClick((slot, snapshot) -> {
                        if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                        openSkinGUI(snapshot.getPlayer(), currentSex, isAdult, snapshot.getText());
                        return RealisticVillagers.CLOSE_RESPONSE;
                    })
                    .title(Config.SKIN_SEARCH_TITLE.asStringTranslated())
                    .text(Config.SKIN_SEARCH_TEXT.asStringTranslated())
                    .itemLeft(new ItemStack(Material.PAPER))
                    .plugin(plugin)
                    .open((Player) event.getWhoClicked());
            return;
        } else if (current.isSimilar(skin.getClearSearch())) {
            openSkinGUI(player, currentSex, isAdult, null);
            return;
        } else if (current.isSimilar(skin.getMale()) || current.isSimilar(skin.getFemale())) {
            openSkinGUI(player, skin.isMale() ? "female" : "male", isAdult, null);
            return;
        } else if (current.isSimilar(skin.getAdult()) || current.isSimilar(skin.getKid())) {
            openSkinGUI(player, currentSex, !isAdult, null);
            return;
        } else if (current.isSimilar(skin.getClearSkin())) {
            messages.send(player, tracker.clearSkin(player, true) ? Messages.Message.SKIN_CLEARED : Messages.Message.SKIN_NOT_CLEARED);
            return;
        } else if (current.isSimilar(skin.getNewSkin())) {
            if (Config.MINESKIN_API_KEY.asString().isEmpty()) {
                messages.send(player, Messages.Message.NO_MINESKIN_API_KEY);
                closeInventory(player);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> new NewSkinGUI(plugin, player, skin.isMale(), isAdult));
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
            FileConfiguration config = pair.getSecond();

            ConfigurationSection noneSection = config.getConfigurationSection("none");
            if (noneSection != null && noneSection.getKeys(false).size() == 1) {
                messages.send(player, Messages.Message.SKIN_AT_LEAST_ONE);
                closeInventory(player);
                return;
            }

            for (Villager.Profession profession : Villager.Profession.values()) {
                String professionLower = profession.name().toLowerCase();
                config.set(professionLower + "." + id, null);

                // Remove profession section if empty.
                ConfigurationSection section = config.getConfigurationSection(professionLower);
                if (section != null && section.getKeys(false).isEmpty()) config.set(professionLower, null);
            }

            try {
                config.save(pair.getFirst());
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

            // Remove skin from players.
            Iterator<Map.Entry<UUID, Pair<Integer, PropertyMap>>> iterator = tracker.getOldProperties().entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Pair<Integer, PropertyMap>> next = iterator.next();
                if (next.getValue().getFirst() != id) continue;

                // Shouldn't be null.
                Player disguised = Bukkit.getPlayer(next.getKey());
                if (disguised == null) continue;

                tracker.clearSkin(disguised, false);
                iterator.remove();
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
            FileConfiguration config = pair.getSecond();

            ConfigurationSection noneSection = config.getConfigurationSection("none");
            if (noneSection == null) return;

            boolean forBabies = !config.getBoolean("none." + id + ".for-babies");
            config.set("none." + id + ".for-babies", forBabies);

            try {
                config.save(pair.getFirst());

                // Refresh inventory.
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getOpenInventory().getTopInventory() instanceof SkinGUI) {
                        openSkinGUI(online, sex, isAdult, null);
                    }
                }
                openSkinGUI(player, sex, isAdult, null);

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

        String profession = skin.getCurrentProfession().name().toLowerCase();

        WrappedSignedProperty textures = tracker.getTextures(sex, profession, id);
        if (textures.getName().equals("error")) {
            // This will take a bit of time since it's running in another thread...
            messages.send(player, Messages.Message.SKIN_WAIT_WHILE_CREATING);

            // Create skin and THEN disguise.
            CompletableFuture<Skin> future = tracker.createSkin(player, sex, isAdult, profession, id);
            if (future != null) {
                future.thenAcceptAsync(skinObject -> {
                    // For some unknown reason, needs to be done sync.
                    Texture texture = skinObject.data.texture;
                    runTask(() -> tracker.disguisePlayer(player, new VillagerTracker.SkinRelatedData(
                            sex,
                            profession,
                            id,
                            null,
                            null,
                            new WrappedSignedProperty("textures", texture.value, texture.signature)), isAdult));
                }, tracker.getMineskinClient().getRequestExecutor());
            } else {
                VillagerTracker.SkinRelatedData tempData = new VillagerTracker.SkinRelatedData(sex, profession, id, null, null, null);
                plugin.getLogger().severe("Failed to generate a new skin when trying to create: " + tempData + "!");
            }

            closeInventory(player);
            return;
        }

        tracker.disguisePlayer(player, new VillagerTracker.SkinRelatedData(
                sex,
                profession,
                id,
                null,
                null,
                new WrappedSignedProperty("textures", textures.getValue(), textures.getSignature())), isAdult);

        closeInventory(player);
    }

    private boolean handleSkinGUISwitches(@NotNull InventoryClickEvent event, SkinGUI skin) {
        ItemStack current = event.getCurrentItem();
        if (current == null) return true;

        Map<Villager.Profession, ItemStack> map = skin.getProfessionItems();

        boolean isCurrent = false;
        int index = -1;
        for (Map.Entry<Villager.Profession, ItemStack> entry : map.entrySet()) {
            index++;
            if (current.isSimilar(entry.getValue())) {
                isCurrent = true;
                break;
            }
        }

        if (!isCurrent) return false;

        int nextIndex = index + (event.getClick().isRightClick() ? 1 : -1);
        if (nextIndex >= map.size()) nextIndex = 0;
        if (nextIndex < 0) nextIndex = skin.getProfessionItems().size() - 1;

        Map.Entry<Villager.Profession, ItemStack> nextEntry = new ArrayList<>(map.entrySet()).get(nextIndex);

        event.getInventory().setItem(event.getRawSlot(), nextEntry.getValue());

        Villager.Profession selectedProfession = nextEntry.getKey();
        skin.setCurrentProfession(selectedProfession);

        plugin.getTracker().getSelectedProfession().put(event.getWhoClicked().getUniqueId(), selectedProfession);
        return true;
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

        if (current.isSimilar(skin.getFromConsole())) {
            messages.send(player, Messages.Message.ONLY_FROM_CONSOLE);
            closeInventory(player);
            return;
        }

        if (!current.isSimilar(skin.getFromPlayer())) return;

        // Add new skin from a player.
        new AnvilGUI.Builder()
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();
                    Player opener = snapshot.getPlayer();
                    String result = snapshot.getText();

                    Player target = Bukkit.getPlayer(result);
                    GameProfile profile;

                    String sex = skin.isMale() ? "male" : "female";

                    // Player is online, get texture data from the profile.
                    if (target != null && (profile = plugin.getConverter().getPlayerProfile(target)) != null) {
                        Property property = profile.getProperties().get("textures").iterator().next();
                        tracker.addNewSkin(opener, null, "none", sex, skin.isAdult(), property.getValue(), property.getSignature());
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

                            tracker.addNewSkin(opener, null, "none", sex, skin.isAdult(), texture, signature);
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
                .open((Player) event.getWhoClicked());
    }

    private void openCombatGUI(IVillagerNPC npc, Player player, @Nullable String keyword) {
        runTask(() -> new CombatGUI(plugin, npc, player, keyword));
    }

    private void openWhistleGUI(Player player, @Nullable String keyword) {
        runTask(() -> plugin.openWhistleGUI(player, keyword));
    }

    private void openSkinGUI(Player player, String sex, boolean isAdult, @Nullable String keyword) {
        runTask(() -> SkinGUI.openMenu(plugin, player, sex, isAdult, null, keyword));
    }

    private void closeInventory(@NotNull Player player) {
        runTask(player::closeInventory);
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    public boolean canModifyInventory(IVillagerNPC npc, Player player) {
        return !notAllowedToModifyInventoryOrName(player, npc, Config.WHO_CAN_MODIFY_VILLAGER_INVENTORY);
    }

    public boolean notAllowedToModifyInventoryOrName(@NotNull Player player, @NotNull IVillagerNPC npc, Config whoCanModify) {
        UUID playerUUID = player.getUniqueId();
        return notAllowedToModify(player, npc.isFamily(playerUUID, true), npc.isPartner(playerUUID), whoCanModify, false);
    }
}