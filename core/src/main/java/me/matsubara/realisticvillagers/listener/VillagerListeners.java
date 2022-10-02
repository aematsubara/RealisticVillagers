package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.InteractionTargetType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerPickGiftEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.gui.types.MainGUI;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import org.bukkit.EntityEffect;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("ClassCanBeRecord")
public final class VillagerListeners implements Listener {

    private final RealisticVillagers plugin;

    private final static MethodHandle MODIFIERS = Reflection.getFieldGetter(EntityDamageEvent.class, "modifiers");

    public VillagerListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenericGameEvent(GenericGameEvent event) {
        GameEvent gameEvent = event.getEvent();

        // RING_BELL is deprecated and shouldn't be used, but due to having the same key as BLOCK_CHANCE,
        // RING BELL is called because of the map replacing the duplicated key.
        if (gameEvent != GameEvent.BLOCK_CHANGE && gameEvent != GameEvent.RING_BELL) return;

        if (event.getLocation().getBlock().getType() != Material.BELL) return;

        // Play swing hand animation when ringing bell.
        if (event.getEntity() instanceof Villager villager) {
            villager.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!Config.ARROWS_PASS_THROUGH_OTHER_VILLAGERS.asBool()) return;

        if (!(event.getEntity().getShooter() instanceof Villager)) return;
        if (!(event.getHitEntity() instanceof Villager)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        if (plugin.getVillagerTracker().isInvalid(villager)) return;

        // Update villager skin when changing job after 1 tick since this event is called before changing job.
        plugin.getServer().getScheduler().runTask(plugin, () -> {

            // Remove previous.
            plugin.getVillagerTracker().removeNPC(villager.getEntityId());

            // Create with new skin.
            plugin.getVillagerTracker().spawnNPC(villager);

            // Give fishing rod.
            if (event.getProfession() == Villager.Profession.FISHERMAN) {
                EntityEquipment equipment = villager.getEquipment();
                if (equipment == null) return;

                if (ThreadLocalRandom.current().nextFloat() >= Config.FISHING_ROD_CHANCE.asFloat()) return;

                ItemStack previous = equipment.getItemInMainHand();
                if (ItemStackUtils.isWeapon(previous)) villager.getInventory().addItem(previous);

                equipment.setItemInMainHand(new ItemStack(Material.FISHING_ROD));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLivingEntity(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;
        if (!(event.getTarget() instanceof Villager)) return;

        // Prevent iron golem attacking villagers (they might hit them by accident with a bow/crossbow).
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory open = player.getOpenInventory().getTopInventory();
            if (!(open.getHolder() instanceof InteractGUI interact)) continue;

            if (interact.getNPC().bukkit().equals(villager)) player.closeInventory();
        }

        if (!Config.DROP_WHOLE_INVENTORY.asBool()) return;
        if (!plugin.isEnabledIn(villager.getWorld())) return;

        List<ItemStack> drops = event.getDrops();
        drops.clear();

        addToDrops(drops, villager.getInventory().getContents());

        EntityEquipment equipment = villager.getEquipment();
        if (equipment != null) {
            addToDrops(drops, equipment.getItemInMainHand(), equipment.getItemInOffHand());
            addToDrops(drops, equipment.getArmorContents());
        }
    }

    private void addToDrops(List<ItemStack> drops, ItemStack... contents) {
        for (ItemStack item : contents) {
            if (item != null) drops.add(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (((Villager) event.getEntity()).getProfession() != Villager.Profession.FARMER) return;

        if (!plugin.isEnabledIn(event.getEntity().getWorld())) return;

        // Prevent farmer villager trampling farmlands.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Material type = event.getBlock().getType();
        if (!type.isAir() && type != Material.COMPOSTER) return;

        // Play swing hand animation when removing crop or using composter.
        if (event.getEntity() instanceof Villager villager) villager.swingMainHand();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (Config.DISABLE_INTERACTIONS.asBool()) return;
        if (!plugin.isEnabledIn(event.getPlayer().getWorld())) return;

        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (plugin.getVillagerTracker().isInvalid(villager)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }

        // Prevent opening villager inventory.
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Prevent interacting with villager if it's fighting.
        if (npc.isFighting() || npc.isInsideRaid()) {
            player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_FIGHTING_OR_RAID));
            return;
        }

        if (npc.isProcreating()) {
            player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_PROCREATING));
            return;
        }

        if (npc.isExpectingGift()) {
            if (npc.isExpectingGiftFrom(player.getUniqueId())) {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_EXPECTING_GIFT_FROM_YOU));
            } else {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_EXPECTING_GIFT_FROM_SOMEONE));
            }
            return;
        }

        if (npc.isExpectingBed()) {
            if (npc.isExpectingBedFrom(player.getUniqueId())) {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_EXPECTING_BED_FROM_YOU));
            } else {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_EXPECTING_BED_FROM_SOMEONE));
            }
            return;
        }

        if (npc.isInteracting()) {
            if (!npc.getInteractingWith().equals(player.getUniqueId())) {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_INTERACTING));
            } else if (npc.isFollowing()) {
                plugin.getMessages().send(npc, player, Messages.Message.FOLLOW_ME_STOP);
                npc.stopInteracting();
            } else if (npc.isStayingInPlace()) {
                plugin.getMessages().send(npc, player, Messages.Message.STAY_HERE_STOP);
                npc.stopInteracting();
                npc.stopStayingInPlace();
            }
            // Otherwise, is in GUI so, do nothing.
            return;
        }

        if (villager.isTrading()) {
            player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_TRADING));
            return;
        }

        IVillagerNPC other = plugin.getExpectingManager().get(player.getUniqueId());
        if (other != null) {
            if (other.isExpectingGift()) {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_OTHER_EXPECTING_GIFT));
            } else {
                player.sendMessage(plugin.getMessages().getRandomMessage(Messages.Message.INTERACT_FAIL_OTHER_EXPECTING_BED));
            }
            return;
        }

        // Open custom GUI.
        new MainGUI(plugin, player, npc);

        // Set interacting with id.
        npc.setInteractingWithAndType(player.getUniqueId(), InteractType.GUI);
    }

    @EventHandler
    public void onVillagerPickGift(VillagerPickGiftEvent event) {
        IVillagerNPC npc = event.getNPC();

        Player player = event.getGifter();
        UUID playerUUID = player.getUniqueId();

        ItemStack gift = event.getGift();

        int reputation = npc.getReputation(playerUUID);
        int repRequiredToMarry = Config.REPUTATION_REQUIRED_TO_MARRY.asInt();

        boolean isRing = gift.isSimilar(plugin.getRing().getRecipe().getResult());

        boolean alreadyMarriedWithPlayer = isRing && npc.isPartner(playerUUID);

        boolean successByRing = isRing
                && npc.bukkit().isAdult()
                && reputation >= repRequiredToMarry
                && !npc.isFamily(playerUUID, false)
                && !npc.hasPartner()
                && !plugin.isMarried(player)
                && !alreadyMarriedWithPlayer;

        GiftCategory category = plugin.getGiftManager().getCategory(npc, gift);
        boolean success = successByRing || (!isRing && category != null) || alreadyMarriedWithPlayer;

        int amount;
        if (success) {
            amount = successByRing ? Config.WEDDING_RING_REPUTATION.asInt() : alreadyMarriedWithPlayer ? 0 : category.reputation();
        } else {
            amount = isRing ? 0 : Config.BAD_GIFT_REPUTATION.asInt();
        }

        if (amount != 0) {
            if (success) {
                npc.addMinorPositive(playerUUID, amount);
            } else {
                npc.addMinorNegative(playerUUID, amount);
            }
        }

        InteractionTargetType target = InteractionTargetType.getInteractionTarget(npc, player);

        if (successByRing) {
            npc.bukkit().playEffect(EntityEffect.VILLAGER_HEART);
            plugin.getMessages().send(npc, player, Messages.Message.MARRRY_SUCCESS);
            npc.setPartner(playerUUID, false);
            player.getPersistentDataContainer().set(
                    plugin.getMarriedWith(),
                    PersistentDataType.STRING,
                    npc.bukkit().getUniqueId().toString());
            return;
        }

        if (success) {
            npc.bukkit().playEffect(EntityEffect.VILLAGER_HAPPY);
        } else if (isRing && !npc.isFamily(playerUUID, false) && npc.bukkit().isAdult()) {
            npc.bukkit().playEffect(EntityEffect.VILLAGER_ANGRY);

            Messages.Message message;
            if (npc.hasPartner()) {
                message = Messages.Message.MARRY_FAIL_MARRIED_TO_OTHER;
            } else if (plugin.isMarried(player)) {
                message = Messages.Message.MARRY_FAIL_PLAYER_MARRIED;
            } else {
                message = Messages.Message.MARRY_FAIL_LOW_REPUTATION;
            }

            plugin.getMessages().send(npc, player, message);
            dropRing(npc, gift);
            return;
        }

        if (success && alreadyMarriedWithPlayer) {
            plugin.getMessages().send(npc, player, Messages.Message.MARRY_FAIL_MARRIED_TO_GIVER);
            return;
        }

        if (!success && isRing && !npc.bukkit().isAdult()) {
            dropRing(npc, gift);
        }

        plugin.getMessages().send(npc, player, plugin.getMessages().getRandomGiftMessage(target, category));

        ItemStackUtils.setBetterWeaponInMaindHand(npc.bukkit(), gift);
        ItemStackUtils.setArmorItem(npc.bukkit(), gift);
    }

    private void dropRing(IVillagerNPC npc, ItemStack gift) {
        npc.drop(gift);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> npc.bukkit().getInventory().removeItem(plugin.getRing().getRecipe().getResult()),
                2L);
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;
        if (npc.isFishing()) npc.toggleFishing();

        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;

        if (villager.getTarget() == null && byEntity.getDamager() instanceof Player player) {
            plugin.getMessages().send(npc, player, Messages.Message.ON_HIT);
        }

        if (!npc.isDamageSourceBlocked()) return;

        try {
            EntityDamageEvent.DamageModifier modifier = EntityDamageEvent.DamageModifier.BLOCKING;
            double base = event.getDamage(EntityDamageEvent.DamageModifier.BASE);
            ((Map<EntityDamageEvent.DamageModifier, Double>) MODIFIERS.invoke(event)).put(modifier, base);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}