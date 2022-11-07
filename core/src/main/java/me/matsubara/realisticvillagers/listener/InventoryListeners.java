package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerChatInteractionEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.CombatGUI;
import me.matsubara.realisticvillagers.gui.types.EquipmentGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.task.BabyTask;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

public final class InventoryListeners implements Listener {

    private final RealisticVillagers plugin;
    private final UnaryOperator<String> REPLACE_TIME = string -> string.replace("%time%", String.valueOf(Config.TIME_TO_EXPECT.asInt() / 20));

    public InventoryListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof InteractGUI gui)) return;

        IVillagerNPC npc = gui.getNPC();
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        if (inventory.getType() == InventoryType.PLAYER
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && event.getView().getTopInventory().getHolder() instanceof InteractGUI interact
                && (!(interact instanceof EquipmentGUI) || !canModifyInventory(interact.getNPC(), player))) {
            event.setCancelled(true);
            return;
        }

        if (!(inventory.getHolder() instanceof InteractGUI interact)) return;

        IVillagerNPC npc = interact.getNPC();
        ItemStack current = event.getCurrentItem();

        if (interact instanceof EquipmentGUI equipment) {
            if (!canModifyInventory(npc, player)) {
                event.setCancelled(true);
            }

            if (current != null) {
                if (current.isSimilar(equipment.getBorder())
                        || current.isSimilar(equipment.getHead())
                        || current.isSimilar(equipment.getClose())) {
                    event.setCancelled(true);
                }

                if (current.isSimilar(equipment.getClose())) closeInventory(player);
            }
            return;
        }

        if (current == null) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (interact instanceof CombatGUI combat) {
            if (current.isSimilar(combat.getNext())) {
                combat.nextPage(event.getClick().isShiftClick());
            } else if (current.isSimilar(combat.getPrevious())) {
                combat.previousPage(event.getClick().isShiftClick());
            } else if (current.isSimilar(combat.getClose())) {
                closeInventory(player);
            } else if (current.isSimilar(combat.getSearch())) {
                combat.setShouldStopInteracting(false);
                new AnvilGUI.Builder()
                        .onComplete((opener, text) -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> new CombatGUI(plugin, opener, npc, text));
                            return AnvilGUI.Response.close();
                        })
                        .title(Config.COMBAT_SEARCH_TITLE.asStringTranslated())
                        .text(Config.COMBAT_SEARCH_TEXT.asStringTranslated())
                        .itemLeft(new ItemStack(Material.PAPER))
                        .preventClose()
                        .plugin(plugin)
                        .open((Player) event.getWhoClicked());
            } else if (current.isSimilar(combat.getClearSearch())) {
                combat.setShouldStopInteracting(false);
                new CombatGUI(plugin, player, npc, null);
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

                inventory.setItem(event.getRawSlot(), new ItemBuilder(enable ? combat.getEnabled() : combat.getDisabled())
                        .setData(plugin.getEntityTypeKey(), PersistentDataType.STRING, type)
                        .build());
            }

            return;
        }

        UUID playerUUID = player.getUniqueId();
        int reputation = npc.getReputation(playerUUID);

        MainGUI main = (MainGUI) interact;
        boolean isClericPartner = current.isSimilar(main.getPapers()) && npc.isPartner(playerUUID);

        Messages messages = plugin.getMessages();
        ExpectingManager expectingManager = plugin.getExpectingManager();

        if (current.isSimilar(main.getFollow())) {
            int required = Config.REPUTATION_REQUIRED_TO_ASK_TO_FOLLOW.asInt();
            if (reputation >= required) {
                npc.setInteractType(InteractType.FOLLOWING);
                messages.send(player, npc, Messages.Message.FOLLOW_ME_START);
            } else {
                messages.send(player, npc, Messages.Message.FOLLOW_ME_LOW_REPUTATION);
                npc.shakeHead(player);
            }
        } else if (current.isSimilar(main.getStay())) {
            int required = Config.REPUTATION_REQUIRED_TO_ASK_TO_STAY.asInt();
            if (reputation >= required) {
                npc.setInteractType(InteractType.STAY);
                npc.stayInPlace();
                messages.send(player, npc, Messages.Message.STAY_HERE_START);
            } else {
                messages.send(player, npc, Messages.Message.STAY_HERE_LOW_REPUTATION);
                npc.shakeHead(player);
            }
        } else if (current.isSimilar(main.getInfo())) {
            return;
        } else if (current.isSimilar(main.getInspect())) {
            main.setShouldStopInteracting(false);
            new EquipmentGUI(plugin, player, npc);
            return;
        } else if (current.isSimilar(main.getGift())) {
            if (!npc.isExpectingGift()) {
                IVillagerNPC other = expectingManager.get(playerUUID);
                if (other != null && other.isExpectingGift()) {
                    messages.send(player, Messages.Message.INTERACT_FAIL_OTHER_EXPECTING_GIFT);
                } else if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), "gift")) {
                    npc.startExpectingFrom(ExpectingType.GIFT, playerUUID, Config.TIME_TO_EXPECT.asInt());
                    messages.send(player, Messages.Message.THROW_GIFT, REPLACE_TIME);
                    messages.send(player, npc, Messages.Message.GIFT_EXPECTING);
                    expectingManager.expect(playerUUID, npc);
                } else {
                    messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
                }
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_EXPECTING_GIFT_FROM_SOMEONE);
            }
        } else if (current.isSimilar(main.getProcreate())) {
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
        } else if (current.isSimilar(main.getDivorce()) || isClericPartner) {
            ItemStack divorcePapers = new ItemBuilder(main.getPapers()).clearLore().build();
            boolean hasDivorcePapers = player.getInventory().containsAtLeast(divorcePapers, 1) || isClericPartner;

            int lossReputation;
            if (hasDivorcePapers) {
                lossReputation = Config.DIVORCE_REPUTATION_LOSS_PAPERS.asInt();
            } else {
                lossReputation = Config.DIVORCE_REPUTATION_LOSS.asInt();
            }

            npc.addMinorNegative(playerUUID, lossReputation);

            if (hasDivorcePapers) {
                messages.send(player, npc, Messages.Message.DIVORCE_PAPERS);
            } else {
                messages.send(player, npc, Messages.Message.DIVORCE_NORMAL);
            }

            if (hasDivorcePapers && !isClericPartner) player.getInventory().removeItem(divorcePapers);

            // Divorce, remove and drop previous wedding ring.
            npc.divorceAndDropRing(player);
        } else if (current.isSimilar(main.getCombat())) {
            main.setShouldStopInteracting(false);
            new CombatGUI(plugin, player, npc, null);
            return;
        } else if (current.isSimilar(main.getHome())) {
            IVillagerNPC other = expectingManager.get(playerUUID);
            if (other != null && other.isExpectingBed()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_OTHER_EXPECTING_BED);
            } else if (!npc.isExpectingBed()) {
                npc.startExpectingFrom(ExpectingType.BED, playerUUID, Config.TIME_TO_EXPECT.asInt());
                messages.send(player, Messages.Message.SELECT_BED, REPLACE_TIME);
                messages.send(player, npc, Messages.Message.SET_HOME_EXPECTING);
                expectingManager.expect(playerUUID, npc);
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_EXPECTING_BED_FROM_SOMEONE);
            }
        } else if (current.isSimilar(main.getPapers())) {
            if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), "divorce-papers")) {
                messages.send(player, npc, Messages.Message.CLERIC_DIVORCE_PAPERS);
                npc.drop(new ItemBuilder(main.getPapers()).clearLore().build());
            } else {
                messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
            }
        } else if (current.isSimilar(main.getTrade()) || current.isSimilar(main.getNoTrades())) {
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
                if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), interactType.getName())) {
                    handleChatInteraction(npc, interactType, player);
                } else {
                    messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
                }
            }
        }

        closeInventory(player);
    }

    public void handleChatInteraction(IVillagerNPC npc, GUIInteractType interactType, Player player) {
        UUID playerUUID = player.getUniqueId();

        boolean successByJoke = interactType.isJoke() && Config.PARTNER_JOKE_ALWAYS_SUCCESS.asBool() && npc.isPartner(playerUUID);
        boolean success = !interactType.isInsult()
                && (successByJoke
                || interactType.isGreet()
                || interactType.isBeProudOf()
                || ThreadLocalRandom.current().nextFloat() < Config.CHANCE_OF_CHAT_INTERACTION_SUCCESS.asFloat());

        VillagerChatInteractionEvent chatEvent = new VillagerChatInteractionEvent(npc, player, interactType, success);
        plugin.getServer().getPluginManager().callEvent(chatEvent);

        int amount = Math.max(2, Config.CHAT_INTERACT_REPUTATION.asInt());
        if (chatEvent.isSuccess()) {
            npc.addMinorPositive(playerUUID, amount);
        } else {
            npc.addMinorNegative(playerUUID, amount);
        }

        EntityEffect effect = chatEvent.isSuccess() ? interactType.isFlirt() ? EntityEffect.VILLAGER_HEART : EntityEffect.VILLAGER_HAPPY : EntityEffect.VILLAGER_ANGRY;
        npc.bukkit().playEffect(effect);

        plugin.getMessages().sendRandomInteractionMessage(player, npc, interactType, chatEvent.isSuccess());
    }

    private void closeInventory(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, player::closeInventory);
    }

    public static boolean canModifyInventory(IVillagerNPC npc, Player player) {
        boolean isFamily = npc.isFamily(player.getUniqueId(), true);
        String modifyInventory = Config.WHO_CAN_MODIFY_VILLAGER_INVENTORY.asString();
        return modifyInventory.equalsIgnoreCase("everyone") || (modifyInventory.equalsIgnoreCase("family") && isFamily);
    }
}