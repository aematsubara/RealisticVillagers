package me.matsubara.realisticvillagers.listener;


import me.matsubara.realisticvillagers.gui.types.MayorGUI;
import me.matsubara.realisticvillagers.village.MayorManager;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageManager;
import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.hologram.HologramMenu;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import org.apache.commons.lang3.Validate;
import org.bukkit.ChatColor;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.*;

public final class VillagerListeners extends SimplePacketListenerAbstract implements Listener {

    private final RealisticVillagers plugin;

    private static final MethodHandle MODIFIERS = Reflection.getFieldGetter(EntityDamageEvent.class, "modifiers");
    public static final boolean HOLOGRAM_SUPPORTED = XReflection.supports(19, 4);

    // A held right-click makes the client resend the interact packet several times a second.
    // Without this, every resend after the one that opened the menu is read as a fresh click
    // and immediately toggles it closed again (see the isInteracting() branch below).
    private static final long MENU_REOPEN_DEBOUNCE_MILLIS = 300L;

    public VillagerListeners(RealisticVillagers plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

        WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        int id = wrapper.getEntityId();

        Optional<NPC> npc;
        if ((npc = plugin.getTracker().getNPC(id)).isEmpty()) {
            return;
        }

        // PlayerInteractEntityEvent won't be called if this one is cancelled.
        // With this change, we fix the client freezing for some seconds when right-clicking a villager.
        Player player = event.getPlayer();
        LivingEntity bukkit = npc.get().getNpc().bukkit();
        EquipmentSlot slot = wrapper.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND;

        if (handleInteract(player, slot, action, bukkit)) {
            event.setCancelled(true);
        }

        // The real packet is eaten above regardless of sneaking, same as any other claimed click
        // — cancelling it here is the one thing that keeps NMS from ever running its own default
        // interaction (a vanilla trade window) once nothing further down the chain cancels the
        // Bukkit event either, which is exactly what a sneaking click used to fall through to:
        // ValhallaMMO's own "too unhappy to trade" branch, and several of its other early exits,
        // never call setCancelled — they just answer and return — so a packet left alive for them
        // to answer was a packet vanilla was still free to act on once they were done with it.
        //
        // A synthetic copy of the same click is fired here instead, purely so another plugin gets
        // a genuine event to react to. A sneaking right-click is ValhallaMMO's own gesture for
        // "tell me why I can't trade" — its happiness breakdown has nothing to run off without
        // this. Whatever it does with the synthetic copy stays contained to its own listener and
        // to chat; a merchant window it opens as a result of THIS click is not one this plugin
        // asked for, and the guard in onValhallaMerchantOpen closes it the same as any other
        // uninvited one — sneaking shows the numbers, never the trade screen.
        if (player.isSneaking() && bukkit instanceof Villager villager) {
            plugin.getServer().getPluginManager().callEvent(new PlayerInteractEntityEvent(player, villager, slot));
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenericGameEvent(@NotNull GenericGameEvent event) {
        GameEvent gameEvent = event.getEvent();

        // RING_BELL is deprecated and shouldn't be used, but due to having the same key as BLOCK_CHANCE,
        // RING BELL is called because of the map replacing the duplicated key.
        if (gameEvent != GameEvent.BLOCK_CHANGE && gameEvent != GameEvent.RING_BELL) return;

        if (event.getLocation().getBlock().getType() != Material.BELL) return;

        // Play swing hand animation when ringing a bell.
        if (event.getEntity() instanceof Villager villager) {
            villager.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!Config.ARROWS_PASS_THROUGH_OTHER_VILLAGERS.asBool()) return;

        // It's a custom villager since vanilla ones can't shoot arrows.
        if (!(event.getEntity().getShooter() instanceof Villager)) return;
        if (!(event.getHitEntity() instanceof Villager)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerCareerChange(@NotNull VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();

        VillagerTracker tracker = plugin.getTracker();
        if (tracker.isInvalid(villager)) return;

        // If the profession didn't change, this is a trade level-up.
        // Don't refresh the NPC skin — just update the nametag to reflect the new level.
        Villager.Profession currentProfession = villager.getProfession();
        Villager.Profession newProfession = event.getProfession();
        if (currentProfession == newProfession) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                tracker.getNPC(villager.getEntityId()).ifPresent(npc -> {
                    for (org.bukkit.entity.Player player : npc.getSeeingPlayers()) {
                        npc.refreshNametags(player);
                    }
                });
            }, 15L);
            return;
        }

        // Update villager skin when changing a job after 1 tick since this event is called before changing a job.
        // Respawn NPC with the new profession texture.
        plugin.getServer().getScheduler().runTask(plugin, () -> tracker.refreshNPCSkin(villager, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerAcquireTrade(@NotNull org.bukkit.event.entity.VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        VillagerTracker tracker = plugin.getTracker();
        if (tracker.isInvalid(villager)) return;

        // Trade level-up: just refresh nametags (level display)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tracker.getNPC(villager.getEntityId()).ifPresent(npc -> {
                for (org.bukkit.entity.Player player : npc.getSeeingPlayers()) {
                    npc.refreshNametags(player);
                }
            });
        }, 15L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(@NotNull EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        if (!(event.getTarget() instanceof Villager)) return;

        // Prevent iron golem attacking villagers (they might hit them by accident with a bow/crossbow).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        // Her figure rides an entity of its own, and an entity does not go away because the one
        // carrying it did. Taken off her here, while she is still here to take it off — a moment
        // later there is no passenger list to find it in, and it stands in the grass where she
        // fell for as long as the server is up.
        if (plugin.getPlayerAppearanceManager() != null) {
            plugin.getPlayerAppearanceManager().remove(villager);
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory open = player.getOpenInventory().getTopInventory();
            if (!(open.getHolder() instanceof InteractGUI interact)) continue;

            IVillagerNPC npc = interact.getNPC();
            if (npc != null && npc.bukkit().equals(villager)) player.closeInventory();
        }

        // Czyść stan oczekiwania i interakcji przy śmierci villgera,
        // żeby gracz mógł ponownie zainicjować gift/bed
        Optional<IVillagerNPC> npcOptional = plugin.getConverter().getNPC(villager);
        if (npcOptional.isPresent()) {
            IVillagerNPC deadNpc = npcOptional.get();
            if (deadNpc.isExpecting()) {
                UUID expectingFrom = deadNpc.getExpectingFrom();
                String cooldownKey = deadNpc.getExpectingType().name().toLowerCase(Locale.ROOT);
                deadNpc.stopExpecting();
                if (expectingFrom != null) {
                    Player waitingPlayer = plugin.getServer().getPlayer(expectingFrom);
                    if (waitingPlayer != null) {
                        plugin.getCooldownManager().removeCooldown(waitingPlayer, cooldownKey);
                    }
                }
            }
            if (deadNpc.isInteracting()) {
                deadNpc.stopInteracting();
            }
        }

        if (!Config.DROP_WHOLE_INVENTORY.asBool()) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        List<ItemStack> drops = event.getDrops();
        drops.clear();

        Collections.addAll(drops, villager.getInventory().getContents());

        EntityEquipment equipment = villager.getEquipment();
        if (equipment != null) {
            Collections.addAll(drops, equipment.getItemInMainHand(), equipment.getItemInOffHand());
            Collections.addAll(drops, equipment.getArmorContents());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(@NotNull EntityInteractEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;
        if (event.getBlock().getType() != Material.FARMLAND) return;

        Villager villager = (Villager) event.getEntity();
        if (villager.getProfession() != Villager.Profession.FARMER) return;

        // Prevent farmer villager trampling farmlands.
        if (!plugin.getTracker().isInvalid(villager, true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        Material type = event.getBlock().getType();
        if (!type.isAir() && type != Material.COMPOSTER) return;

        // Play swing hand animation when removing crop or using composter.
        if (event.getEntity() instanceof Villager villager) villager.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        preventChangeSkinItemUse(event, event.getItemInHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        preventChangeSkinItemUse(event, event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(@NotNull PlayerArmorStandManipulateEvent event) {
        preventChangeSkinItemUse(event, event.getPlayerItem());
    }

    // Changed the priority to LOW to support VTL.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        // Sneaking on a villager is handled once, at the packet level (onPacketPlayReceive) — the
        // real click there is claimed and its packet eaten exactly like any other, but a
        // synthetic copy of the same event is then fired deliberately, purely so another plugin
        // gets a genuine event to react to. This handler sees that synthetic copy too, being a
        // perfectly ordinary PlayerInteractEntityEvent, and calling handleInteract again here for
        // the very click it already ran once would run its side effects twice over — a lead
        // toggling follow on and immediately back off, a whistle summoning family a second time —
        // for no reason, since this plugin's own menu never opens on a sneaking click either way.
        //
        // Scoped to villagers only: a sneaking click on anything else never went through
        // onPacketPlayReceive's own villager handling in the first place — the packet-level
        // tracker lookup bails on a non-villager id regardless of sneaking, and nothing fires a
        // synthetic copy for it — so this real event is that click's first and only stop, and
        // skipping it here would quietly turn off preventChangeSkinItemUse the moment a player
        // sneaks at anything that isn't a villager.
        if (event.getPlayer().isSneaking() && event.getRightClicked() instanceof Villager) return;

        if (handleInteract(event.getPlayer(), event.getHand(), null, event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /**
     * Closes any ValhallaMMO trading window that opened on its own for a villager this plugin
     * manages, so its own menu is always what a right-click shows first.
     * <p>
     * Exists because cancelling {@link PlayerInteractEntityEvent} above is not enough on its own.
     * Reading ValhallaMMO's own bytecode found its interact handler registered at
     * {@code EventPriority.HIGHEST} — after this plugin's {@code LOW} — and it never checks
     * {@code event.isCancelled()} before acting, so it opens its trading window regardless of
     * whether this plugin already claimed the click. Left alone, a raw right-click on a villager
     * Valhalla already considers one of its own custom merchants would show Valhalla's window on
     * the spot, a tick before this plugin's own menu — with its "Trade" button — ever appeared.
     * <p>
     * {@code MONITOR} so it runs dead last, after Valhalla's own {@code InventoryOpenEvent} hook
     * has had its say, and cancelling the event at that point still stops the window from being
     * shown — Bukkit only sends the open to the client once every handler has run.
     * <p>
     * {@link me.matsubara.realisticvillagers.compatibility.ValhallaCompatibility#isExpecting} is
     * what keeps this from closing the one window that is meant to open this way: the moment this
     * plugin's own "Trade" button asks Valhalla for it.
     * <p>
     * That includes a sneaking click's own synthetic event (see {@link #onPacketPlayReceive}) —
     * deliberately, and this is the one place that decision is actually made. Valhalla treats
     * sneaking as "tell me why I can't trade" and answers with a happiness breakdown in chat, but
     * nothing about computing that breakdown stops it from also opening the real trading window
     * once happiness turns out to be fine after all — sneaking only adds the commentary, it does
     * not ask Valhalla to stop short of trading. Reading the numbers is meant to be the whole of
     * what a sneaking click does, so any window that click produces is uninvited the same as a
     * plain click's would be, and is closed here exactly the same way. Only the "Trade" button's
     * own mark lets a window through, sneaking included.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onValhallaMerchantOpen(@NotNull InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory merchant)) return;
        if (!(merchant.getMerchant() instanceof Villager villager)) return;
        if (plugin.getTracker().isInvalid(villager)) return;

        if (me.matsubara.realisticvillagers.compatibility.ValhallaCompatibility
                .isExpecting(event.getPlayer().getUniqueId(), villager.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean handleInteract(@NotNull Player player, EquipmentSlot hand, @Nullable WrapperPlayClientInteractEntity.InteractAction action, Entity entity) {
        ItemStack item = player.getInventory().getItem(hand);
        boolean cancel = preventChangeSkinItemUse(null, item);

        if (!(entity instanceof Villager villager)) return cancel;

        VillagerTracker tracker = plugin.getTracker();

        if (Config.DISABLE_INTERACTIONS.asBool()) return cancel;
        if (tracker.isInvalid(villager, true)) return cancel;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return cancel;

        if (hand != EquipmentSlot.HAND) return true;
        // MC 26.x sends INTERACT_AT (not INTERACT) when right-clicking player-type entities.
        // Allow both; ATTACK is already blocked before reaching this method.
        if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) return true;

        plugin.getServer().getScheduler().runTask(plugin, (() -> {
            Messages messages = plugin.getMessages();

            // Don't open GUI if using the whistle.
            ItemMeta meta;
            if (item != null && (meta = item.getItemMeta()) != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(plugin.getIsWhistleKey(), PersistentDataType.INTEGER)) return;

                if (container.has(plugin.getSkinDataKey(), PersistentDataType.STRING)) {
                    handleChangeSkinItem(player, npc, item);
                    return;
                }

                if (item.getType() == Material.NAME_TAG && meta.hasDisplayName()) {
                    handleRename(player, npc, item);
                    return;
                }

                if (item.getType() == Material.LEAD) {
                    if (npc.isInteracting() && npc.getInteractingWith().equals(player.getUniqueId()) && npc.isFollowing()) {
                        messages.send(player, npc, Messages.Message.FOLLOW_ME_STOP);
                        npc.stopInteracting();
                    } else {
                        plugin.getInventoryListeners().handleFollorOrStay(npc, player, InteractType.FOLLOW_ME, true);
                    }
                    return;
                }
            }

            // Prevent interacting with villager if it's fighting or fleeing.
            if (npc.isFighting() || npc.isInsideRaid()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_FIGHTING_OR_RAID);
                return;
            }
            String currentActivity = npc.getActivityName("").toLowerCase(java.util.Locale.ROOT);
            if (currentActivity.equals("hide") || currentActivity.equals("panic")) {
                messages.send(player, Messages.Message.INTERACT_FAIL_FIGHTING_OR_RAID);
                return;
            }

            if (npc.isProcreating()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_PROCREATING);
                return;
            }

            if (isExpecting(player, npc, ExpectingType.GIFT, item)) return;
            if (isExpecting(player, npc, ExpectingType.BED, item)) return;

            // Shift+rightclick nie otwiera GUI
            if (player.isSneaking()) return;

            if (npc.isInteracting()) {
                if (!npc.getInteractingWith().equals(player.getUniqueId())) {
                    messages.send(player, Messages.Message.INTERACT_FAIL_INTERACTING);
                } else if (hologramEnabled() && plugin.getHologramManager().hasMenu(player.getUniqueId())) {
                    // Hologram check must come before isFollowing and isStayingInPlace: when ORDER:FOLLOW
                    // is active the villager is in FOLLOW_ME mode, but the hologram owns the session —
                    // close() handles all state cleanup. Without this ordering, right-clicking would fire
                    // FOLLOW_ME_STOP and leave the hologram orphaned.
                    HologramMenu menu = plugin.getHologramManager().getMenuForPlayer(player.getUniqueId());
                    if (menu == null || System.currentTimeMillis() - menu.getOpenedAt() >= MENU_REOPEN_DEBOUNCE_MILLIS) {
                        plugin.getHologramManager().closeMenu(player.getUniqueId());
                    }
                } else if (npc.isFollowing()) {
                    if (hologramEnabled()) {
                        plugin.getHologramManager().openMenu(player, npc);
                    } else {
                        messages.send(player, npc, Messages.Message.FOLLOW_ME_STOP);
                        npc.stopInteracting();
                    }
                } else if (npc.isStayingInPlace()) {
                    if (hologramEnabled()) {
                        plugin.getHologramManager().openMenu(player, npc);
                    } else {
                        messages.send(player, npc, Messages.Message.STAY_HERE_STOP);
                        npc.stopInteracting();
                        npc.stopStayingInPlace();
                    }
                }
                // Otherwise, is in GUI so, do nothing.
                return;
            }

            if (villager.isTrading()) {
                messages.send(player, Messages.Message.INTERACT_FAIL_TRADING);
                return;
            }

            if (plugin.getAnnoyingManager().isVillagerAnnoyed(player, npc)) return;

            // The mayor is not an ordinary tradesman: it gets the settlement screen instead of
            // the usual villager menu, and freezes so it doesn't wander off mid-conversation.
            if (openMayorScreen(player, npc, villager)) return;

            // Open hologram menu or fall back to chest GUI.
            if (hologramEnabled()) {
                plugin.getHologramManager().openMenu(player, npc);
            } else {
                new MainGUI(plugin, npc, player);
            }

            // Set interacting with id.
            npc.setInteractingWithAndType(player.getUniqueId(), InteractType.GUI);
        }));

        return true;
    }

    // All this is checked in the invoker method.
    private void handleRename(Player player, IVillagerNPC npc, ItemStack item) {
        if (plugin.getInventoryListeners().notAllowedToModifyInventoryOrName(player, npc, Config.WHO_CAN_MODIFY_VILLAGER_NAME, "realisticvillagers.bypass.rename")) {
            plugin.getMessages().send(player, Messages.Message.INTERACT_FAIL_RENAME_NOT_ALLOWED);
            return;
        }

        @SuppressWarnings("DataFlowIssue") String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        if (name.length() < 3) return;

        npc.setVillagerName(name);

        // Refresh skin.
        plugin.getTracker().refreshNPCSkin(npc.bukkit(), false);

        player.getInventory().removeItem(new ItemBuilder(item.clone())
                .setAmount(1)
                .build());
    }

    private boolean isExpecting(Player player, @NotNull IVillagerNPC npc, ExpectingType checkType, @Nullable ItemStack item) {
        if (!npc.isExpecting()) return false;

        ExpectingType expecting = npc.getExpectingType();
        if (expecting != checkType) return false;

        UUID playerUUID = player.getUniqueId();
        Messages messages = plugin.getMessages();

        // This villager is expecting something from another player.
        if (!npc.getExpectingFrom().equals(playerUUID)) {
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_SOMEONE"));
            return true;
        }

        // This villager is expecting something from this player, can't open the menu.
        ExpectingManager expectingManager = plugin.getExpectingManager();
        boolean isRightClickGift = expecting.isGift() && expectingManager.getGiftModeFromConfig().rightClick();
        if (!player.isSneaking() || isRightClickGift) {
            if (isRightClickGift && npc.bukkit() instanceof InventoryHolder holder) {
                if (item != null && !item.getType().isAir()) {
                    // Remove one unit.
                    ItemStack unit = new ItemBuilder(item)
                            .setAmount(1)
                            .build();

                    // Remove from player inventory, add to villager inventory.
                    player.getInventory().removeItem(unit);
                    holder.getInventory().addItem(unit);

                    // Handle.
                    expectingManager.handleVillagerPickUp(npc, item, playerUUID, player, null);
                } else {
                    // No item in hand — inform player what to hold
                    messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_YOU"));
                }
                return true;
            }
            messages.send(player, Messages.Message.valueOf("INTERACT_FAIL_EXPECTING_" + expecting + "_FROM_YOU"));
            return true;
        }

        // This villager is expecting something from this player (sneaking, non-gift mode), stop interaction.
        if (expecting.isGift()) {
            messages.send(player, npc, Messages.Message.valueOf("GIFT_EXPECTING_FAIL"));
        } else {
            messages.send(player, npc, Messages.Message.valueOf("SET_HOME_FAIL"));
        }
        npc.stopExpecting();
        plugin.getCooldownManager().removeCooldown(player, checkType.name().toLowerCase(Locale.ROOT));
        return true;
    }

    private boolean hologramEnabled() {
        return HOLOGRAM_SUPPORTED && plugin.getHologramManager().isMenuEnabled();
    }

    /**
     * Opens the settlement screen if this villager is its village's mayor.
     *
     * @return {@code true} when the mayor screen was opened, meaning the caller must not go on
     * to open the ordinary villager menu.
     */
    private boolean openMayorScreen(Player player, IVillagerNPC npc, Villager villager) {
        VillageManager villages = plugin.getVillageManager();
        MayorManager mayors = plugin.getMayorManager();
        if (villages == null || mayors == null || !villages.isEnabled()) return false;

        // Ask the cheap, direct question first: is this villager a mayor anywhere? Resolving its
        // village can momentarily fail (meeting-point memory still settling after a chunk load),
        // and when it did the mayor fell through to the ordinary villager menu.
        if (!villages.isMayorOfAnyVillage(villager.getUniqueId())) return false;

        Village village = villages.getVillage(villager);
        if (village == null) {
            // Known to be a mayor but its village isn't resolvable this instant — swallow the
            // interaction rather than opening the wrong screen.
            return true;
        }

        // Prefer the hologram screen; the chest GUI is the fallback for servers that turned the
        // hologram menu off. Both carry a map — the hologram one drawn in the world, the chest
        // one as a grid of tiles — so switching menus costs the settlement view nothing.
        if (hologramEnabled()) {
            plugin.getHologramManager().openMenu(player, npc, true);
        } else {
            npc.stayInPlace();
            player.openInventory(new MayorGUI(plugin, village, player).getInventory());
        }

        npc.setInteractingWithAndType(player.getUniqueId(), InteractType.GUI);
        return true;
    }

    private boolean preventChangeSkinItemUse(@Nullable Cancellable cancellable, ItemStack item) {
        ItemMeta meta;
        if (item == null || (meta = item.getItemMeta()) == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.has(plugin.getSkinDataKey(), PersistentDataType.STRING)) {
            if (cancellable != null) cancellable.setCancelled(true);
            return true;
        }

        return false;
    }

    private void handleChangeSkinItem(Player player, @NotNull IVillagerNPC npc, @NotNull ItemStack handItem) {
        ItemMeta meta = handItem.getItemMeta();
        Validate.notNull(meta);

        Messages messages = plugin.getMessages();
        VillagerTracker tracker = plugin.getTracker();
        LivingEntity living = npc.bukkit();

        VillagerTracker.SkinRelatedData relatedData = tracker.getRelatedData(living, "none");
        TextureProperty property = relatedData.property();

        if (property != null && property.getName().equals("error")) {
            messages.send(player, Messages.Message.SKIN_ERROR);
            plugin.getLogger().severe(property.getValue());
            return;
        }

        String skinData = meta.getPersistentDataContainer().get(plugin.getSkinDataKey(), PersistentDataType.STRING);
        if (skinData == null || skinData.isEmpty()) return;

        String[] data = skinData.split(":");
        if (data.length != 2) return;

        String sex = data[0];
        boolean isMale = sex.equals("male");
        String sexFormatted = (isMale ? Config.MALE : Config.FEMALE).asString();

        if (!sex.equalsIgnoreCase(npc.getSex())) {
            messages.send(player, Messages.Message.SKIN_DIFFERENT_SEX, string -> string.replace("%sex%", sexFormatted));
            return;
        }

        int id = Integer.parseInt(data[1]);
        if (id == npc.getSkinTextureId()) {
            messages.send(player, Messages.Message.SKIN_VILLAGER_SAME_SKIN);
            return;
        }

        boolean isAdult = !(living instanceof Villager villager) || villager.isAdult(), forBabies = relatedData.storage().getBoolean("none." + id + ".for-babies");
        if ((isAdult && forBabies) || (!isAdult && !forBabies)) {
            messages.send(player, Messages.Message.SKIN_DIFFERENT_AGE_STAGE, string -> string.replace("%age-stage%", (forBabies ? Config.KID : Config.ADULT).asString()));
            return;
        }

        // Here, we change the id of the villager, so then we can check if the skin exists.
        npc.setSkinTextureId(id);

        int skinId = tracker.getRelatedData(living, "none", false).id();
        if (skinId == -1) {
            messages.send(player, Messages.Message.SKIN_TEXTURE_NOT_FOUND);
            return;
        }

        messages.send(player, Messages.Message.SKIN_DISGUISED, string -> string
                .replace("%id%", String.valueOf(skinId))
                .replace("%sex%", sexFormatted)
                .replace("%profession%", plugin.getProfessionFormatted(PluginUtils.getProfessionOrType(living), isMale))
                .replace("%age-stage%", isAdult ? Config.ADULT.asString() : Config.KID.asString()));

        tracker.refreshNPCSkin(living, false);

        player.getInventory().removeItem(new ItemBuilder(handItem.clone())
                .setAmount(1)
                .build());
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        tryToDefendPlayer(event);

        if (!(event.getEntity() instanceof AbstractVillager villager)) return;
        if (plugin.getTracker().isInvalid(villager, true)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;
        if (npc.isFishing()) npc.toggleFishing();

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            if ((XReflection.MINOR_NUMBER != 20 && XReflection.PATCH_NUMBER != 5)
                    && event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                    && !villager.isAdult()
                    && !Config.DISABLE_SKINS.asBool()
                    && !Config.INCREASE_BABY_SCALE.asBool()
                    && !villager.getLocation().getBlock().getType().isSolid()) {
                // Prevent baby villagers suffocating when their hitbox remain small with enabled skins.
                event.setCancelled(true);
            }
            return;
        }

        if (byEntity.getDamager() instanceof Firework firework
                && firework.getShooter() instanceof Villager
                && !Config.VILLAGER_CROSSBOW_FIREWORK_DAMAGES_OTHER_VILLAGERS.asBool()) {
            event.setCancelled(true);
            return;
        }

        boolean alive = villager.getHealth() - event.getFinalDamage() > 0.0d;

        // Don't send messages if villager died.
        if (villager.getTarget() == null && byEntity.getDamager() instanceof Player player && alive) {
            plugin.getMessages().send(player, npc, Messages.Message.ON_HIT);
        }

        float blocking = (float) (-event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING));
        if (!npc.isDamageSourceBlocked() && blocking <= 0.0f) return;

        try {
            EntityDamageEvent.DamageModifier modifier = EntityDamageEvent.DamageModifier.BLOCKING;
            double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            ((Map<EntityDamageEvent.DamageModifier, Double>) MODIFIERS.invoke(event)).put(modifier, base);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private void tryToDefendPlayer(@NotNull EntityDamageEvent event) {
        if (!Config.VILLAGER_DEFEND_ATTACK_PLAYERS.asBool()) return;

        if (!(event.getEntity() instanceof Player player)
                || !(event instanceof EntityDamageByEntityEvent byEntity)
                || !(byEntity.getDamager() instanceof Player damager)) return;

        for (Entity nearby : player.getNearbyEntities(16.0d, 16.0d, 16.0d)) {
            if (!(nearby instanceof Villager villager)) continue;

            Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

            // If the NPC can't attack or the damager is part of the family of the NPC, continue.
            IVillagerNPC npc = optional.orElse(null);
            if (npc == null
                    || !npc.canAttack()
                    || npc.isFamily(damager, true)) return;

            if (npc.isFamily(player, true) && Config.VILLAGER_DEFEND_FAMILY_MEMBER.asBool()) {
                npc.attack(damager);
                continue;
            }

            Raid raid;
            if (player.hasPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE)
                    || ((raid = player.getWorld().locateNearestRaid(player.getLocation(), 5)) != null
                    && raid.getHeroes().contains(player.getUniqueId()))
                    && Config.VILLAGER_DEFEND_HERO_OF_THE_VILLAGE.asBool()) {
                npc.attack(damager);
                continue;
            }

            if (player.getUniqueId().equals(npc.getInteractingWith()) && Config.VILLAGER_DEFEND_FOLLOWING_PLAYER.asBool()) {
                npc.attack(damager);
            }
        }
    }
}
