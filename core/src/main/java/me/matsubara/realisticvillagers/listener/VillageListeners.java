package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.gui.types.CommissionGUI;
import me.matsubara.realisticvillagers.gui.types.ElectionGUI;
import me.matsubara.realisticvillagers.gui.types.MayorGUI;
import me.matsubara.realisticvillagers.gui.types.RadarGUI;
import me.matsubara.realisticvillagers.gui.types.StorageGUI;
import me.matsubara.realisticvillagers.village.Blueprint;
import me.matsubara.realisticvillagers.village.BuildPreview;
import me.matsubara.realisticvillagers.village.ConstructionManager;
import me.matsubara.realisticvillagers.village.Election;
import me.matsubara.realisticvillagers.village.ElectoralProgram;
import me.matsubara.realisticvillagers.village.MayorManager;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageBuildings;
import me.matsubara.realisticvillagers.village.VillageManager;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Villager;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** Wires the settlement systems to the world: voting at the bell, and its ballot screen. */
public final class VillageListeners implements Listener {

    private final RealisticVillagers plugin;

    public VillageListeners(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BELL) return;

        VillageManager villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return;
        if (plugin.isDisabledIn(block.getWorld())) return;

        Village village = villages.getVillageAt(block.getLocation());
        if (village == null) return;

        Election election = plugin.getElectionManager().getElection(village);
        if (election == null) return;

        // Take over the click entirely: ringing the bell would send the settlers scattering,
        // which is the opposite of what someone walking up to vote wants.
        event.setCancelled(true);

        // Players who already voted still get the ballot — it doubles as the live tally, and
        // marks the candidate they backed.
        openBallot(event.getPlayer(), village, election);
    }

    private void openBallot(@NotNull Player player, @NotNull Village village, @NotNull Election election) {
        player.openInventory(new ElectionGUI(plugin, village, election, player).getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        // The mayor screen is read-only; without this its display items could be taken out.
        if (holder instanceof MayorGUI mayorGui) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) return;

            Village village = plugin.getVillageManager().getVillage(mayorGui.getVillageId());
            if (village == null) return;

            if (event.getRawSlot() == MayorGUI.SLOT_RADAR) {
                openRadar(player, village, 1.0d);
            } else if (event.getRawSlot() == MayorGUI.SLOT_COMMISSION) {
                openCommissions(player, village);
            } else if (event.getRawSlot() == MayorGUI.SLOT_STORAGE) {
                player.openInventory(new StorageGUI(plugin, village, player).getInventory());
            } else if (event.getRawSlot() == MayorGUI.SLOT_BORDERS) {
                var borders = plugin.getBorderVisualizer();
                if (borders != null) {
                    boolean showing = borders.toggle(player, village);
                    send(player,
                            showing ? "village.borders-on" : "village.borders-off",
                            showing ? "&aThe settlement's border is now visible." : "&7Border hidden.");
                }
                player.openInventory(new MayorGUI(plugin, village, player).getInventory());
            }
            return;
        }

        if (holder instanceof StorageGUI storageGui) {
            event.setCancelled(true);
            onStorageClick(event, storageGui);
            return;
        }

        if (holder instanceof CommissionGUI commissionGui) {
            event.setCancelled(true);
            onCommissionClick(event, commissionGui);
            return;
        }

        if (holder instanceof RadarGUI radarGui) {
            event.setCancelled(true);
            onRadarClick(event, radarGui);
            return;
        }

        if (!(holder instanceof ElectionGUI gui)) return;

        // Nothing in this screen is a real item, so no click may ever move anything.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(event.getClickedInventory())) return;

        UUID candidate = gui.getCandidateAt(event.getRawSlot());
        if (candidate == null) return;

        Village village = plugin.getVillageManager().getVillage(gui.getVillageId());
        Election election = plugin.getElectionManager().getElection(village);

        if (village == null || election == null) {
            player.closeInventory();
            send(player, "village.election-ended", "&cThat election has already ended.");
            return;
        }

        if (election.hasVoted(player.getUniqueId())) {
            send(player, "village.already-voted", "&cYou have already cast your vote.");
            return;
        }

        if (!plugin.getElectionManager().vote(village, player, candidate)) {
            send(player, "village.vote-failed", "&cYour vote could not be counted.");
            return;
        }

        send(player, "village.vote-cast", "&aYour vote has been counted.");

        // Reopen so the tally and the "your vote" marker reflect what just happened.
        openBallot(player, village, election);
    }

    private void openRadar(@NotNull Player player, @NotNull Village village, double zoom) {
        VillageManager villages = plugin.getVillageManager();
        if (villages == null) return;

        player.openInventory(new RadarGUI(plugin, village, villages, player, zoom).getInventory());
    }

    /**
     * Moves goods between a player and the settlement's stores.
     * <p>
     * Shift-clicking in the player's own inventory gives; clicking a stack in the stores takes.
     * Two different gestures because they are two different acts — one is a gift and the other
     * spends what the village was saving.
     */
    private void onStorageClick(@NotNull InventoryClickEvent event, @NotNull StorageGUI gui) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Village village = plugin.getVillageManager().getVillage(gui.getVillageId());
        if (village == null) {
            player.closeInventory();
            return;
        }

        // Clicked their own inventory: a shift-click hands the stack over.
        if (!event.getInventory().equals(event.getClickedInventory())) {
            if (!event.isShiftClick()) return;

            org.bukkit.inventory.ItemStack held = event.getCurrentItem();
            if (held == null || held.getType().isAir()) return;

            village.getStorage().add(held.getType(), held.getAmount());
            event.setCurrentItem(null);

            send(player, "village.storage-deposited", "&aGiven to the settlement's stores.");
            player.openInventory(new StorageGUI(plugin, village, player, gui.getPage()).getInventory());
            return;
        }

        int slot = event.getRawSlot();

        if (slot == StorageGUI.SLOT_BACK) {
            backToMayor(player, village);
            return;
        }

        if (slot == StorageGUI.SLOT_PREV) {
            player.openInventory(new StorageGUI(plugin, village, player, gui.getPage() - 1).getInventory());
            return;
        }

        if (slot == StorageGUI.SLOT_NEXT) {
            player.openInventory(new StorageGUI(plugin, village, player, gui.getPage() + 1).getInventory());
            return;
        }

        Material material = gui.getMaterialAt(slot);
        if (material == null) return;

        if (!gui.isMayWithdraw()) {
            send(player, "village.storage-denied", "&cYou need standing with the mayor to take from the stores.");
            return;
        }

        // A stack at a time, and only as much as there is room for. Handing over goods that
        // bounce off a full inventory would take them out of the stores and drop them nowhere.
        int wanted = Math.min(material.getMaxStackSize(), village.getStorage().count(material));
        if (wanted <= 0) return;

        int taken = village.getStorage().remove(material, wanted);
        if (taken <= 0) return;

        Map<Integer, org.bukkit.inventory.ItemStack> leftover =
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, taken));

        // Whatever wouldn't fit goes straight back, so nothing is ever lost between the two.
        for (org.bukkit.inventory.ItemStack rejected : leftover.values()) {
            village.getStorage().add(rejected.getType(), rejected.getAmount());
        }

        send(player, "village.storage-taken", "&aTaken from the settlement's stores.");
        player.openInventory(new StorageGUI(plugin, village, player, gui.getPage()).getInventory());
    }

    /** Opens an anvil holding the building's current name, ready to be edited. */
    private void renameBlueprint(@NotNull Player player, @NotNull Village village, @NotNull Blueprint blueprint) {
        String current = blueprint.getName();

        plugin.getServer().getScheduler().runTask(plugin, () -> new net.wesjd.anvilgui.AnvilGUI.Builder()
                .plugin(plugin)
                .title(PluginUtils.translate(text("gui.commission.rename-title", "&8Name this building")))
                .text(current)
                .onClick((slot, state) -> {
                    if (slot != net.wesjd.anvilgui.AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.singletonList(
                                net.wesjd.anvilgui.AnvilGUI.ResponseAction.replaceInputText(state.getText()));
                    }

                    boolean renamed = plugin.getBlueprints() != null
                            && plugin.getBlueprints().rename(blueprint, state.getText());

                    send(player,
                            renamed ? "village.blueprint-renamed" : "village.blueprint-rename-failed",
                            renamed ? "&aRenamed." : "&cThat name won't do, or is already taken.");

                    return java.util.List.of(
                            net.wesjd.anvilgui.AnvilGUI.ResponseAction.close(),
                            net.wesjd.anvilgui.AnvilGUI.ResponseAction.run(() ->
                                    player.openInventory(new CommissionGUI(plugin, village, player).getInventory())));
                })
                .open(player));
    }

    /** Reads a GUI string from gui.yml, where screen text belongs. */
    private @NotNull String text(String path, String fallback) {
        String value = plugin.getGuiConfig() == null ? null : plugin.getGuiConfig().getString(path);
        return value != null && !value.isEmpty() ? value : fallback;
    }

    /**
     * Goes back to the mayor's screen — the one this server actually uses.
     * <p>
     * These chest screens are opened from both menus, and Back used to always open the chest one.
     * On a hologram server that meant leaving through a door into a different building: the
     * hologram menu you came from vanished and a chest you had never opened appeared instead.
     */
    private void backToMayor(@NotNull Player player, @NotNull Village village) {
        var holograms = plugin.getHologramManager();
        MayorManager mayors = plugin.getMayorManager();

        if (holograms != null && holograms.isMenuEnabled() && mayors != null) {
            Villager mayor = mayors.getMayorEntity(village);
            var npc = mayor == null ? null : plugin.getConverter().getNPC(mayor).orElse(null);

            if (npc != null) {
                player.closeInventory();
                plugin.getServer().getScheduler().runTask(plugin, () -> holograms.openMenu(player, npc, true));
                return;
            }
        }

        player.openInventory(new MayorGUI(plugin, village, player).getInventory());
    }

    private void openCommissions(@NotNull Player player, @NotNull Village village) {
        MayorManager mayors = plugin.getMayorManager();

        // The same gate the mayor's screen reports. Checked again here because the screen only
        // says whether you may commission — it does not stop you opening the list.
        if (mayors != null && !mayors.canCommission(village, player).isAllowed()) {
            send(player, "village.commission-denied", "&cThe mayor won't take your plans.");
            return;
        }

        player.openInventory(new CommissionGUI(plugin, village, player).getInventory());
    }

    /** Picks a building to place, and hands the player its ghost to line up. */
    private void onCommissionClick(@NotNull InventoryClickEvent event, @NotNull CommissionGUI gui) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(event.getClickedInventory())) return;

        Village village = plugin.getVillageManager().getVillage(gui.getVillageId());
        if (village == null) {
            player.closeInventory();
            return;
        }

        if (event.getRawSlot() == CommissionGUI.SLOT_BACK) {
            backToMayor(player, village);
            return;
        }

        if (event.getRawSlot() == CommissionGUI.SLOT_PREV) {
            player.openInventory(new CommissionGUI(plugin, village, player, gui.getPage() - 1).getInventory());
            return;
        }

        if (event.getRawSlot() == CommissionGUI.SLOT_NEXT) {
            player.openInventory(new CommissionGUI(plugin, village, player, gui.getPage() + 1).getInventory());
            return;
        }

        String id = gui.getBlueprintAt(event.getRawSlot());
        if (id == null) return;

        Blueprint blueprint = plugin.getBlueprints() == null ? null : plugin.getBlueprints().get(id);
        if (blueprint == null) return;

        if (event.isShiftClick()) {
            if (blueprint.getOwner() == null) {
                send(player, "village.blueprint-shared",
                        "&7This building came with the server, so its name is not yours to change.");
                return;
            }

            renameBlueprint(player, village, blueprint);
            return;
        }

        // One at a time per settlement: two houses rising into each other is not something the
        // preview can warn about, since neither exists yet.
        if (plugin.getConstructionManager() != null && plugin.getConstructionManager().isBuilding(village)) {
            send(player, "village.already-building", "&cThis settlement is already building something.");
            return;
        }

        double multiplier = village.getMayorProgram() != null
                ? village.getMayorProgram().getBuildCostMultiplier()
                : 1.0d;

        for (java.util.Map.Entry<org.bukkit.Material, Integer> item
                : ConstructionManager.scaledCost(blueprint, multiplier).entrySet()) {
            if (village.getStorage().has(item.getKey(), item.getValue())) continue;

            send(player, "village.commission-short", "&cThe settlement's stores are short.");
            return;
        }

        // Closed so the ghost is visible; there is nothing to line up from inside a chest.
        player.closeInventory();

        var preview = plugin.getBuildPreview();
        if (preview != null) preview.start(player, village, blueprint);
    }

    /**
     * Turns the player's clicks into placing, turning or cancelling the ghost.
     * <p>
     * Runs at LOWEST and cancels the event outright while a preview is up: the same clicks would
     * otherwise break a block, place one, or open whatever the player is pointing at.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewClick(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();

        var preview = plugin.getBuildPreview();
        if (preview == null || !preview.isPreviewing(player)) return;

        event.setCancelled(true);

        // A right-click fires this once for each hand. Acting on both turned every rotation into
        // a half-turn and made the two-step confirm fire both steps on a single click, so the
        // off-hand copy is dropped here.
        if (event.getHand() != null && event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            // Left-click means "back one step", not "throw it away".
            //
            // Pinning stops the ghost following you, which is the point of it — but there was no
            // way back out. Getting the spot slightly wrong meant cancelling the whole thing and
            // walking through the menu again, and that is the difference between a fiddly tool
            // and an infuriating one.
            if (!player.isSneaking() && preview.unpin(player)) {
                send(player, "village.commission-unpinned", "&7Loose again — it follows you.");
                return;
            }

            preview.cancel(player);
            send(player, "village.commission-cancelled", "&7Building cancelled.");
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if (player.isSneaking()) {
            preview.rotate(player);
            return;
        }

        advancePreview(player, preview);
    }

    /** Pins the ghost, or builds it if it is already pinned. */
    private void advancePreview(@NotNull Player player, @NotNull BuildPreview preview) {
        if (preview.isBlocked(player)) {
            send(player, "village.commission-blocked",
                    "&cIt won't fit there — too close to another building, or outside the village.");
            return;
        }

        if (preview.pin(player)) {
            send(player, "village.commission-pinned",
                    "&ePlaced. &aRight-click or swap hands&e to build it, &asneak + right-click&e to turn it, "
                            + "&eleft-click&e to move it again.");
            return;
        }

        BuildPreview.Placement placement = preview.confirm(player);
        if (placement == null) return;

        Village village = plugin.getVillageManager().getVillage(placement.villageId());
        if (village == null) return;

        ElectoralProgram program = village.getMayorProgram();
        double cost = program != null ? program.getBuildCostMultiplier() : 1.0d;
        double time = program != null ? program.getBuildTimeMultiplier() : 1.0d;

        int seconds = Math.max(1, (int) Math.round(placement.blueprint().getSeconds() * time));

        boolean started = plugin.getConstructionManager() != null
                && plugin.getConstructionManager().begin(
                        village, placement.blueprint(), placement.origin(), placement.rotation(), cost, seconds);

        send(player,
                started ? "village.commission-started" : "village.commission-short",
                started ? "&aThe settlement has started building." : "&cThe settlement's stores are short.");
    }

    /**
     * Swapping hands does what the next right-click would.
     * <p>
     * Right-clicking empty air does not reliably reach a plugin — with nothing in hand and
     * nothing in reach there may be no interaction to report at all, which is exactly the case
     * when the ghost has been pushed out across a valley. Swapping hands always fires, so there
     * is always a key that works however far away the building is.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewSwap(@NotNull org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        var preview = plugin.getBuildPreview();
        if (preview == null || !preview.isPreviewing(player)) return;

        event.setCancelled(true);
        advancePreview(player, preview);
    }

    /**
     * Lets the scroll wheel push the ghost further away or pull it closer.
     * <p>
     * Held-slot changes are what a scroll actually is, so the event is taken over while a preview
     * is up and the hotbar left where it was. Scrolling is the natural gesture for "further out",
     * and without it a building can only be placed as far as the player can walk and look.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreviewScroll(@NotNull org.bukkit.event.player.PlayerItemHeldEvent event) {
        var preview = plugin.getBuildPreview();
        if (preview == null || !preview.isPreviewing(event.getPlayer())) return;

        event.setCancelled(true);
        if (preview.isPinned(event.getPlayer())) return;

        // The hotbar wraps, so 8 -> 0 is one step forward and 0 -> 8 is one step back.
        int delta = event.getNewSlot() - event.getPreviousSlot();
        if (delta > 4) delta -= 9;
        if (delta < -4) delta += 9;

        preview.push(event.getPlayer(), -delta * 2.0d);
    }

    /**
     * Handles a click on the chest map: zoom, back, or a building.
     * <p>
     * Zooming reopens the screen rather than editing it in place, because the whole grid changes
     * when the scale does — every tile covers different ground.
     */
    private void onRadarClick(@NotNull InventoryClickEvent event, @NotNull RadarGUI gui) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getInventory().equals(event.getClickedInventory())) return;

        Village village = plugin.getVillageManager().getVillage(gui.getVillageId());
        if (village == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        if (slot == RadarGUI.SLOT_BACK) {
            backToMayor(player, village);
            return;
        }

        if (slot == RadarGUI.SLOT_ZOOM_IN) {
            openRadar(player, village, gui.getZoom() - RadarGUI.ZOOM_STEP);
            return;
        }

        if (slot == RadarGUI.SLOT_ZOOM_OUT) {
            openRadar(player, village, gui.getZoom() + RadarGUI.ZOOM_STEP);
            return;
        }

        VillageBuildings.Footprint building = gui.getBuildingAt(slot);
        if (building == null) return;

        // Close first: the outline is drawn in the world, and there is no point marking out a
        // building behind a screen the player is still looking at.
        player.closeInventory();

        var borders = plugin.getBorderVisualizer();
        if (borders != null) borders.outlineBuilding(player, building, player.getWorld());
    }

    /**
     * Releases the mayor when its screen is closed.
     * <p>
     * Without this the villager stays flagged as "interacting", which keeps it locked onto the
     * player (it follows them around) and makes every later right-click fall into the
     * already-interacting branch, so the screen can never be opened again.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        // Every screen of ours, not just the mayor's.
        //
        // Opening one from the hologram menu closes that menu, which released the villager — so
        // the mayor wandered off while the player was still standing in its chest. Holding it for
        // all of them and releasing on close keeps it where the conversation started.
        java.util.UUID villageId;
        var holder = event.getInventory().getHolder();

        if (holder instanceof MayorGUI gui) villageId = gui.getVillageId();
        else if (holder instanceof StorageGUI gui) villageId = gui.getVillageId();
        else if (holder instanceof CommissionGUI gui) villageId = gui.getVillageId();
        else if (holder instanceof RadarGUI gui) villageId = gui.getVillageId();
        else return;

        Village village = plugin.getVillageManager().getVillage(villageId);
        if (village == null) return;

        Villager mayor = plugin.getMayorManager().getMayorEntity(village);
        if (mayor == null) return;

        plugin.getConverter().getNPC(mayor).ifPresent(npc -> {
            npc.stopInteracting();
            npc.stopStayingInPlace();
        });
    }

    /**
     * Turns the whole settlement on whoever raises a hand to its mayor.
     * <p>
     * The mayor is the one villager the settlement cannot replace on the spot: losing it empties
     * the seat, opens an election, locks the bell and stops the village governing itself until
     * the vote is over. Every resident that can hold a weapon answers an attack on it — not the
     * handful standing nearby, which is what the ordinary defence rules would give.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMayorAttacked(@NotNull org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager victim)) return;

        VillageManager villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return;
        if (plugin.isDisabledIn(victim.getWorld())) return;

        Village village = villages.getVillage(victim);
        if (village == null || !villages.isMayor(village, victim.getUniqueId())) return;

        org.bukkit.entity.Entity attacker = event.getDamager();

        // Follow a projectile back to whoever loosed it, or shooting the mayor from a distance
        // would be the one way to attack it for free.
        if (attacker instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof org.bukkit.entity.Entity shooter) {
            attacker = shooter;
        }

        if (!(attacker instanceof org.bukkit.entity.LivingEntity target)) return;
        if (target.getUniqueId().equals(victim.getUniqueId())) return;

        for (Villager resident : villages.getResidents(village)) {
            if (resident.getUniqueId().equals(victim.getUniqueId())) continue;

            plugin.getConverter().getNPC(resident).ifPresent(npc -> {
                if (!npc.canAttack()) return;
                npc.attack(target);
            });
        }
    }

    /**
     * Clears a grave when somebody hits it.
     * <p>
     * Graves stay until they are cleared, and a settlement that has been through a few raids ends
     * up with a field of headstones nobody wants. Hitting one is the obvious way to be rid of it,
     * and it takes the whole stone rather than the one piece that was struck.
     * <p>
     * Left to the same standing as commissioning: a grave is a record of somebody's villager, and
     * a passer-by should not be able to erase it.
     */
    /**
     * Picks up the blueprints kept inside a world the moment that world appears.
     * <p>
     * Covers worlds that arrive after start-up — a world manager creating one on demand, or an
     * admin loading one by hand. Their discovered buildings live in the world folder, so until
     * the folder exists there is nothing to read.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(@NotNull org.bukkit.event.world.WorldLoadEvent event) {
        if (plugin.getBlueprints() != null) plugin.getBlueprints().loadSoon();
        if (plugin.getVillageManager() != null) plugin.getVillageManager().ensureLoaded(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGraveHit(@NotNull org.bukkit.event.player.PlayerInteractEntityEvent event) {
        var graves = plugin.getGraveManager();
        if (graves == null) return;

        if (graves.graveOf(event.getRightClicked()) == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            send(player, "village.grave-remove-hint", "&7Sneak and right-click to clear this grave.");
            return;
        }

        MayorManager mayors = plugin.getMayorManager();
        Village village = plugin.getVillageManager().getVillageAt(event.getRightClicked().getLocation());

        if (village != null && mayors != null && !mayors.canCommission(village, player).isAllowed()) {
            send(player, "village.grave-denied", "&cThis is not your grave to clear.");
            return;
        }

        if (graves.removeAt(event.getRightClicked())) {
            send(player, "village.grave-removed", "&7The grave has been cleared.");
        }
    }

    /**
     * Buries a villager that died with no way back.
     * <p>
     * Read at MONITOR and before anything else touches the body: the villager's family is only
     * reachable while the entity still exists, so a grave built a tick later says "child of
     * unknown and unknown".
     * <p>
     * A villager carrying a cross is skipped — a cross means it can be revived, and a headstone
     * over somebody who is coming back is a lie the world keeps telling.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerBuried(@NotNull org.bukkit.event.entity.EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        var graves = plugin.getGraveManager();
        if (graves == null) return;
        if (plugin.isDisabledIn(villager.getWorld())) return;

        // A Villager already IS an InventoryHolder, so no test is needed to treat it as one.
        if (PluginUtils.hasAnyOf(villager, plugin.getIsCrossKey())) return;

        graves.bury(villager, plugin.getConverter().getNPC(villager).orElse(null), describeDeath(villager));
    }

    /**
     * How a villager died, in words worth carving.
     * <p>
     * "Entity attack" is the game's word for it and tells a reader nothing — it is the same
     * phrase whether a zombie got in, a skeleton shot from the dark or another player did it.
     * Naming what actually landed the blow is the whole point of writing it down.
     */
    private @NotNull String describeDeath(@NotNull Villager villager) {
        var damage = villager.getLastDamageCause();
        if (damage == null) return "Died of unknown causes";

        if (damage instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            org.bukkit.entity.Entity killer = byEntity.getDamager();

            // A projectile is the arrow, not the archer; the grave should name whoever loosed it.
            if (killer instanceof org.bukkit.entity.Projectile projectile
                    && projectile.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                killer = shooter;
            }

            String name = killer instanceof Player player
                    ? player.getName()
                    : PluginUtils.capitalizeFully(killer.getType().name().replace('_', ' '));

            return "Killed by " + name;
        }

        return switch (damage.getCause()) {
            case FALL -> "Fell";
            case DROWNING -> "Drowned";
            case FIRE, FIRE_TICK, LAVA -> "Burned";
            case STARVATION -> "Starved";
            case SUFFOCATION -> "Suffocated";
            case VOID -> "Lost to the void";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "Caught in a blast";
            case LIGHTNING -> "Struck by lightning";
            case POISON, MAGIC, WITHER -> "Poisoned";
            default -> "Died of unknown causes";
        };
    }

    /**
     * Announces a mayor's death to everyone standing in their village.
     * <p>
     * The seat falling vacant is the single event that most changes what a settlement does next —
     * an election opens, candidates are dressed, the bell locks. Players inside the village would
     * otherwise only find out by noticing the consequences.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMayorDeath(@NotNull org.bukkit.event.entity.EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        VillageManager villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return;
        if (plugin.isDisabledIn(villager.getWorld())) return;

        // Asked of the villager rather than searched for, so this stays per-village and never
        // walks every settlement on the server looking for a match.
        Village village = villages.getVillage(villager);
        if (village == null || !villages.isMayor(village, villager.getUniqueId())) return;

        var presence = plugin.getVillagePresence();
        if (presence != null) presence.announceMayorDeath(village);
    }

    /**
     * Keeps villages honest when their bell is moved.
     * <p>
     * The centre is normally learned from villagers' meeting-point memory, which lags well
     * behind a player picking the bell up and putting it down elsewhere. Reacting to the block
     * itself makes the move immediate, and drops the cached buildings and residents that were
     * measured against the old centre.
     */
    /**
     * Reacts to a bell being placed.
     * <p>
     * Deliberately does <b>not</b> move the settlement's centre. A bell marks where a village
     * was found, not where its middle is — letting it define the centre meant moving the bell
     * dragged the whole settlement with it and pushed outlying buildings past the boundary.
     * The centre is worked out from the buildings themselves.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBellPlaced(@NotNull BlockPlaceEvent event) {
        Village village = bellVillage(event.getBlock());
        if (village == null) return;

        // Record where the bell now stands so the map shows it in the right place. The village
        // centre deliberately stays put.
        village.setBell(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
    }

    /**
     * Stops the village bell being destroyed while its election is running.
     * <p>
     * The bell is where the vote happens: it carries the countdown and it is what players click
     * to cast a ballot. Letting it be broken mid-election would strand the vote with no way to
     * take part and no sign of what happened.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBellBreakDuringElection(@NotNull BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BELL) return;

        VillageManager villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return;

        Village village = villages.getVillageAt(event.getBlock().getLocation());
        if (village == null || !plugin.getElectionManager().hasElection(village)) return;

        event.setCancelled(true);
        send(event.getPlayer(), "village.bell-locked",
                "&cYou can't break this bell while an election is being held here.");
    }

    /**
     * Reacts to a bell being destroyed.
     * <p>
     * Only drops the caches measured against it. The centre is deliberately left where it was
     * and the mayor is never touched: losing a bell is not the village dissolving, and moving
     * the centre onto a bell that no longer exists would be meaningless.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBellBroken(@NotNull BlockBreakEvent event) {
        bellVillage(event.getBlock());
    }

    /** The village this bell belongs to, with its cached residents and buildings dropped. */
    private @Nullable Village bellVillage(@NotNull Block block) {
        if (block.getType() != Material.BELL) return null;

        VillageManager villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return null;
        if (plugin.isDisabledIn(block.getWorld())) return null;

        Village village = villages.getVillageAt(block.getLocation());
        if (village == null) return null;

        villages.invalidateResidents(village);
        VillageBuildings.invalidate(village);

        return village;
    }

    private void send(@NotNull Player player, String path, String fallback, @NotNull java.util.Map<String, String> values) {
        var messages = plugin.getMessages();
        var config = messages != null ? messages.getConfiguration() : null;

        String message = config != null ? config.getString(path) : null;
        if (message == null || message.isEmpty()) message = fallback;
        if (message.isEmpty()) return;

        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        player.sendMessage(PluginUtils.translate(message));
    }

    /** Sends a settlement message from configs/messages/system.yml, where system text belongs. */
    private void send(@NotNull Player player, String path, String fallback) {
        var messages = plugin.getMessages();
        var config = messages != null ? messages.getConfiguration() : null;

        String message = config != null ? config.getString(path) : null;
        if (message == null || message.isEmpty()) message = fallback;
        if (message.isEmpty()) return;

        player.sendMessage(PluginUtils.translate(message));
    }

    /** Convenience for other village code that needs the village a block belongs to. */
    public @Nullable Village getVillageAt(@Nullable Block block) {
        return block == null ? null : plugin.getVillageManager().getVillageAt(block.getLocation());
    }
}
