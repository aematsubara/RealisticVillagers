package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerChatInteractionEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.CombatGUI;
import me.matsubara.realisticvillagers.gui.types.EquipmentGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.task.BabyTask;
import me.matsubara.realisticvillagers.util.ItemBuilder;
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

import java.text.SimpleDateFormat;
import java.util.Date;
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

        MainGUI main = (MainGUI) interact;
        boolean isClericPartner = current.isSimilar(main.getPapers()) && npc.isPartner(player.getUniqueId());

        if (current.isSimilar(main.getFollow())) {
            npc.setInteractType(InteractType.FOLLOWING);
            plugin.getMessages().send(npc, player, Messages.Message.FOLLOW_ME_START);
        } else if (current.isSimilar(main.getStay())) {
            npc.setInteractType(InteractType.STAY);
            npc.stayInPlace();
            plugin.getMessages().send(npc, player, Messages.Message.STAY_HERE_START);
        } else if (current.isSimilar(main.getInfo())) {
            return;
        } else if (current.isSimilar(main.getInspect())) {
            main.setShouldStopInteracting(false);
            new EquipmentGUI(plugin, player, npc);
            return;
        } else if (current.isSimilar(main.getGift())) {
            if (!npc.isExpectingGift()) {
                npc.startExpectingFrom(ExpectingType.GIFT, player.getUniqueId(), Config.TIME_TO_EXPECT.asInt());
                player.sendMessage(REPLACE_TIME.apply(plugin.getMessages().getRandomMessage(Messages.Message.THROW_GIFT)));
                plugin.getMessages().send(npc, player, Messages.Message.GIFT_EXPECTING);
                plugin.getExpectingManager().expect(player.getUniqueId(), npc);
            } else {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_EXPECTING_GIFT_FROM_SOMEONE));
            }
        } else if (current.isSimilar(main.getProcreate())) {
            if (npc.getReputation(player.getUniqueId()) < Config.REPUTATION_REQUIRED_TO_PROCREATE.asInt()) {
                plugin.getMessages().send(npc, player, Messages.Message.PROCREATE_FAIL_LOW_REPUTATION);
                closeInventory(player);
                return;
            }

            long lastProcreation = npc.getLastProcreation();
            long elapsedTime = System.currentTimeMillis() - lastProcreation;

            int procreationCooldown = Config.PROCREATION_COOLDOWN.asInt();

            if (elapsedTime <= procreationCooldown) {
                SimpleDateFormat nextProcreateFormat = new SimpleDateFormat("mm:ss");
                String next = nextProcreateFormat.format(new Date(procreationCooldown - elapsedTime));
                plugin.getMessages().send(npc, player, Messages.Message.PROCREATE_FAIL_HAS_BABY);
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.PROCREATE_COOLDOWN).replace("%time%", next));
                closeInventory(player);
                return;
            }

            npc.setProcreatingWith(player.getUniqueId());
            new BabyTask(plugin, npc.bukkit(), player).runTaskTimer(plugin, 0L, 20L);
        } else if (current.isSimilar(main.getDivorce()) || isClericPartner) {
            ItemStack divorcePapers = new ItemBuilder(main.getPapers()).clearLore().build();
            boolean hasDivorcePapers = player.getInventory().containsAtLeast(divorcePapers, 1) || isClericPartner;

            int reputation;
            if (hasDivorcePapers) {
                reputation = Config.DIVORCE_REPUTATION_LOSS_PAPERS.asInt();
            } else {
                reputation = Config.DIVORCE_REPUTATION_LOSS.asInt();
            }

            npc.addMinorNegative(player.getUniqueId(), reputation);

            if (hasDivorcePapers) {
                plugin.getMessages().send(npc, player, Messages.Message.DIVORCE_PAPERS);
            } else {
                plugin.getMessages().send(npc, player, Messages.Message.DIVORCE_NORMAL);
            }

            if (hasDivorcePapers && !isClericPartner) player.getInventory().removeItem(divorcePapers);

            // Divorce, remove and drop previous wedding ring.
            npc.divorceAndDropRing(player);
        } else if (current.isSimilar(main.getCombat())) {
            main.setShouldStopInteracting(false);
            new CombatGUI(plugin, player, npc, null);
            return;
        } else if (current.isSimilar(main.getHome())) {
            if (!npc.isExpectingBed()) {
                npc.startExpectingFrom(ExpectingType.BED, player.getUniqueId(), Config.TIME_TO_EXPECT.asInt());
                player.sendMessage(REPLACE_TIME.apply(plugin.getMessages().getRandomMessage(Messages.Message.SELECT_BED)));
                plugin.getMessages().send(npc, player, Messages.Message.SET_HOME_EXPECTING);
                plugin.getExpectingManager().expect(player.getUniqueId(), npc);
            } else {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_EXPECTING_BED_FROM_SOMEONE));
            }
        } else if (current.isSimilar(main.getPapers())) {
            if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), "divorce-papers")) {
                plugin.getMessages().send(npc, player, Messages.Message.CLERIC_DIVORCE_PAPERS);
                npc.drop(new ItemBuilder(main.getPapers()).clearLore().build());
            } else {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_IN_COOLDOWN));
            }
        } else if (current.isSimilar(main.getTrade()) || current.isSimilar(main.getNoTrades())) {
            if (npc.bukkit().isTrading()) {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_TRADING));
            } else {
                if (npc.bukkit().getRecipes().isEmpty()) return;

                // Start trading from villager instance so discounts are applied to the player.
                plugin.getServer().getScheduler().runTask(plugin, () -> npc.startTrading(player));
                return;
            }
        } else if (current.getItemMeta() != null) {
            ItemMeta meta = current.getItemMeta();

            PersistentDataContainer container = meta.getPersistentDataContainer();
            String type = container.get(plugin.getChatInteractionTypeKey(), PersistentDataType.STRING);
            if (type != null) {
                VillagerChatInteractionEvent.ChatType chatType;
                if (type.equalsIgnoreCase("proud-of")) {
                    chatType = VillagerChatInteractionEvent.ChatType.BE_PROUD_OF;
                } else {
                    chatType = VillagerChatInteractionEvent.ChatType.valueOf(type);
                }
                if (plugin.getCooldownManager().canInteract(player, npc.bukkit(), chatType.getName())) {
                    handleChatInteraction(npc, chatType, player);
                } else {
                    player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_IN_COOLDOWN));
                }
            }
        }

        closeInventory(player);
    }

    public void handleChatInteraction(IVillagerNPC npc, VillagerChatInteractionEvent.ChatType type, Player player) {
        UUID playerUUID = player.getUniqueId();

        boolean successByJoke = type.isJoke() && Config.PARTNER_JOKE_ALWAYS_SUCCESS.asBool() && npc.isPartner(playerUUID);
        boolean success = !type.isInsult()
                && (successByJoke
                || type.isGreet()
                || type.isBeProudOf()
                || ThreadLocalRandom.current().nextFloat() < Config.CHANCE_OF_CHAT_INTERACTION_SUCCESS.asFloat());

        VillagerChatInteractionEvent chatEvent = new VillagerChatInteractionEvent(npc, player, type, success);
        plugin.getServer().getPluginManager().callEvent(chatEvent);

        int amount = Math.max(2, Config.CHAT_INTERACT_REPUTATION.asInt());
        if (chatEvent.isSuccess()) {
            npc.addMinorPositive(playerUUID, amount);
        } else {
            npc.addMinorNegative(playerUUID, amount);
        }

        EntityEffect effect = chatEvent.isSuccess() ? type.isFlirt() ? EntityEffect.VILLAGER_HEART : EntityEffect.VILLAGER_HAPPY : EntityEffect.VILLAGER_ANGRY;
        npc.bukkit().playEffect(effect);


        InteractionTargetType targetType = InteractionTargetType.getInteractionTarget(npc, player);
        String message = plugin.getMessages().getRandomInteractionMessage(targetType, type, chatEvent.isSuccess());
        plugin.getMessages().send(npc, player, message);
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