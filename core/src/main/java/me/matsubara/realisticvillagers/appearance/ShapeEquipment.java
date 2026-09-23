package me.matsubara.realisticvillagers.appearance;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The figures villagers and players wear, sent as equipment they do not
 * actually own.
 * <p>
 * Worn, not carried, and that is a choice made on stability rather than on
 * tidiness. A figure carried
 * by an entity riding her is in principle the better answer — it hangs off her
 * chest instead of her
 * head, so it does not turn when she looks around and does not argue with a
 * helmet. In practice it
 * spent its life being pulled between the server and the client: a passenger
 * cannot be rotated
 * without a packet that also carries a position, its own spawn packet unseats
 * it, and everything
 * that touches a passenger list fights everything else that touches it. Worn,
 * none of that exists.
 * The game draws it on her, and there is nothing left to keep in step.
 * <p>
 * The head slot, not the chest — not a preference either. A chest slot is drawn
 * as armour texture
 * layers stretched over the standard body, and an item with a model of its own
 * puts nothing there at
 * all; the game's own {@code Equippable} component carries a slot, a sound, a
 * texture asset and a
 * camera overlay, and no model anywhere. The head slot is the one the game
 * draws as a model, the same
 * route a custom hat takes, and the model carries its own step down from the
 * head to the chest.
 * <p>
 * Nothing goes into anyone's real equipment. The packet goes to whoever is
 * watching; her own slots
 * stay as empty as they were, so anything that reads or fills them is
 * unaffected.
 */
public final class ShapeEquipment {

    private final RealisticVillagers plugin;

    public ShapeEquipment(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /**
     * Who has been shown what: the figure each watcher was last sent about each
     * villager or player.
     * <p>
     * Per watcher rather than per subject, because a client forgets an entity it
     * stops tracking and
     * is told nothing when it picks it up again. Remembering only "she is wearing
     * this" would leave
     * anyone who walked away and came back seeing nothing for as long as she did
     * not change.
     * <p>
     * Entries for watchers who have gone out of range are dropped each pass, which
     * is what makes
     * coming back into range send it afresh, and what keeps this from growing
     * without end.
     */
    private final Map<UUID, Map<UUID, String>> shown = new ConcurrentHashMap<>();

    /**
     * Past this the figure is a few pixels and a watcher out here may not be
     * tracking her at all.
     */
    private static final double RANGE = 48.0d;

    /**
     * Sends a villager's figure to one watcher, for a villager just drawn for them.
     */
    public void send(@NotNull Player watcher, @NotNull Villager villager, @NotNull IVillagerNPC npc) {
        ItemStack shape = figureFor(villager, npc);
        if (shape == null)
            return;

        send(watcher, shape, villager.getEntityId());
        remember(villager.getUniqueId(), watcher, variantOf(shape));
    }

    /**
     * Brings every watcher up to date on a villager, sending only what has actually
     * changed.
     */
    public void sendToViewers(@NotNull Villager villager, @NotNull IVillagerNPC npc) {
        update(villager, figureFor(villager, npc));
    }

    /**
     * The one path both take.
     * <p>
     * A figure that has gone away — she has put on a breastplate, or taken off the
     * skin it was drawn
     * from — is not simply left unsent. Whoever was shown one is sent whatever she
     * is really wearing
     * on her head instead, which is usually nothing. Without that the last figure
     * stays on their
     * screen for good, since a packet nobody sends cannot undo one that was.
     */
    private void update(@NotNull LivingEntity subject, @Nullable ItemStack shape) {
        String variant = variantOf(shape);

        Map<UUID, String> seen = shown.computeIfAbsent(subject.getUniqueId(), id -> new ConcurrentHashMap<>());
        Set<UUID> near = new HashSet<>();

        // Taking a figure off used to be a single packet, sent the one pass on which it
        // went away.
        // That is not enough. There is no way from here to ask a client whether a
        // packet took, and a
        // clear that did not take leaves a figure standing on someone's chest for good,
        // with nothing
        // that will ever try again — the pass after, there is no longer anything to
        // notice. So the
        // moment a figure goes away it is undone repeatedly for a few passes.
        boolean undoing = shape == null && !seen.isEmpty();
        if (undoing)
            undo.put(subject.getUniqueId(), UNDO_PASSES);

        int left = undo.getOrDefault(subject.getUniqueId(), 0);

        for (Player watcher : subject.getWorld().getPlayers()) {
            boolean self = watcher.getUniqueId().equals(subject.getUniqueId());

            // All updates are for villagers now (NPCs). Player self-skip logic isn't
            // strictly necessary
            // since players aren't updated here anymore, but keeping self check for safety.
            if (self) {
                continue;
            }

            if (!watcher.canSee(subject))
                continue;
            if (watcher.getLocation().distanceSquared(subject.getLocation()) > RANGE * RANGE)
                continue;

            near.add(watcher.getUniqueId());

            if (shape == null) {
                if (left > 0)
                    restore(watcher, subject);
                continue;
            }

            if (variant != null && variant.equals(seen.get(watcher.getUniqueId())))
                continue;

            send(watcher, shape, subject.getEntityId());

            // Never a null value: this map cannot hold one, and a shape whose model went
            // missing
            // would take the whole pass down with it rather than simply being re-sent next
            // time.
            if (variant != null)
                seen.put(watcher.getUniqueId(), variant);
        }

        if (shape != null)
            undo.remove(subject.getUniqueId());

        if (shape == null) {
            seen.clear();
            if (left > 0)
                undo.put(subject.getUniqueId(), left - 1);
            else
                undo.remove(subject.getUniqueId());
        }

        // Watchers who have walked off are forgotten, so walking back sends it to them
        // again.
        seen.keySet().retainAll(near);
        if (seen.isEmpty() && !undo.containsKey(subject.getUniqueId()))
            shown.remove(subject.getUniqueId());
    }

    /** How many passes a figure keeps being taken off after it goes away. */
    private static final int UNDO_PASSES = 3;

    /**
     * Subjects whose figure is being taken off, and how many passes are left of
     * doing it.
     */
    private final Map<UUID, Integer> undo = new ConcurrentHashMap<>();

    /**
     * Puts back what she is really wearing, for undoing a figure someone was shown.
     * <p>
     * Both slots, and with her own items rather than with nothing. The figure can
     * have been sent to
     * either, so both have to be corrected — and correcting them with air would
     * strip a breastplate
     * she actually owns off every screen but her own.
     */
    private void restore(@NotNull Player watcher, @NotNull LivingEntity subject) {
        EntityEquipment equipment = subject.getEquipment();

        ItemStack helmet = equipment == null ? null : equipment.getHelmet();
        ItemStack chest = equipment == null ? null : equipment.getChestplate();

        PacketEvents.getAPI().getProtocolManager().sendPacket(
                SpigotReflectionUtil.getChannel(watcher),
                new WrapperPlayServerEntityEquipment(subject.getEntityId(), List.of(
                        new Equipment(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(
                                helmet == null ? new ItemStack(Material.AIR) : helmet)),
                        new Equipment(EquipmentSlot.CHEST_PLATE, SpigotConversionUtil.fromBukkitItemStack(
                                chest == null ? new ItemStack(Material.AIR) : chest)))));
    }

    private void remember(@NotNull UUID subject, @NotNull Player watcher, @Nullable String variant) {
        if (variant == null)
            return;
        shown.computeIfAbsent(subject, id -> new ConcurrentHashMap<>()).put(watcher.getUniqueId(), variant);
    }

    /**
     * Says it all again from scratch, for a player whose answer has just changed.
     * <p>
     * Called the moment she decides she is a man, or a woman again, rather than
     * leaving it to the
     * next pass. Two things follow from that. It lands at once instead of within a
     * second, which is
     * the difference between a command that worked and a command that appears not
     * to have. And it
     * does not depend on the note of who was shown what being right — that note is
     * thrown away here
     * and the truth is sent regardless, so a figure cannot outlive the answer that
     * put it there
     * even if the bookkeeping had drifted.
     */
    public void refresh(@NotNull Player player) {
        shown.remove(player.getUniqueId());
        undo.remove(player.getUniqueId());

        // A player's figure is no longer worn — it rides her, and the entity carrying it is the
        // manager's to make and unmake. Rebuilt at once rather than on the next pass, so a command
        // that changed her answer is a command whose effect can be seen.
        var players = plugin.getPlayerAppearanceManager();
        if (players != null) {
            players.remove(player);
            players.update(player);
        }
    }

    /**
     * Says it all again to everyone, for a setting that has just changed what gets
     * sent.
     * <p>
     * The note of who was shown what is thrown away first, or nothing would go out:
     * every watcher
     * is already down as having seen exactly the figure they are about to be sent,
     * and the only
     * thing that changed is which slot it goes in.
     */
    public void resend() {
        shown.clear();

        var players = plugin.getPlayerAppearanceManager();
        if (players == null) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            players.remove(player);
            players.update(player);
        }
    }

    /**
     * The figure a player should be carrying, or {@code null} if she should carry none.
     * <p>
     * Public because it is no longer this class that puts it on her. A villager's figure is worn, so
     * this class both decides it and sends it; a player's rides her on an entity of its own, and the
     * deciding is still the same question. Keeping the answer here is what stops the two paths
     * drifting into disagreeing about who has a figure at all.
     * <p>
     * A bust, and only for a woman. Nothing else a villager gets is given to a player: build is left
     * alone, and height is left alone because a player's height is not decoration — it decides her
     * hitbox, her reach and whether she fits through a gap.
     */
    public @Nullable ItemStack figureFor(@NotNull Player player) {
        if (!enabled() || !busts()) return null;
        if (!Config.APPEARANCE_PLAYERS.asBool(true)) return null;
        if (!carriable(player)) return null;
        if (!female(player)) return null;

        Traits traits = Traits.of(player.getUniqueId(), true, plugin.playerShapeRoll(player));
        if (!traits.hasBust() || traits.bustShape() == null) return null;

        BodyShape shape = traits.bustShape();

        List<Color> colors = colours(skinUrl(player), shape);
        if (colors == null) return null;

        // Armour does not hide her, it covers her. A breastplate is a shell pressed over the shape
        // underneath, so the shape stays and takes the metal's colour — which is also why it stops
        // moving: steel does not settle after a footfall.
        Color plate = plateOf(player);
        if (plate != null) colors = plated(shape, plate);

        return item(shape.variant(BodyShape.stepOf(traits.bust()), false), colors);
    }

    /**
     * The shape again in the metal of the plate over it, lit rather than painted flat.
     * <p>
     * One colour for every face was the first answer and it was the wrong shape of answer: the whole
     * point of the figure is that it stands out of her, and a solid block of the same grey standing
     * out of a solid block of the same grey stands out of nothing. There is no light in it, so the
     * eye is given no reason to read it as curved and reads it as a patch instead.
     * <p>
     * So each face is lifted or dropped by how far out its own cell reaches. The result is the same
     * metal everywhere, brighter where a breastplate would catch the light and darker where it turns
     * away, which is precisely what the armour texture under it is already doing to the flat parts of
     * her — the figure simply joins in.
     */
    private @NotNull List<Color> plated(@NotNull BodyShape shape, @NotNull Color plate) {
        List<BodyShape.Cell> cells = shape.cells();

        double deepest = 0.0d;
        for (BodyShape.Cell cell : cells) deepest = Math.max(deepest, cell.depth());
        if (deepest <= 0.0d) return java.util.Collections.nCopies(cells.size(), plate);

        List<Color> colors = new java.util.ArrayList<>(cells.size());
        for (BodyShape.Cell cell : cells) {
            // Hard, and deliberately harder than looks reasonable written down. The figure under a
            // plate is barely a pixel of relief standing on armour of exactly the same colour, at a
            // distance where a pixel of relief casts no shadow of its own — so if the colour does
            // not do the work, nothing does, and what the player sees is a breastplate with nothing
            // underneath it. Two thirds either way turns iron's one grey into a range from charcoal
            // to white, which is the difference between a shape and a patch.
            double light = 1.0d + 0.62d * tone(shape, cell, deepest);

            colors.add(Color.fromRGB(
                    channel(plate.getRed(), light),
                    channel(plate.getGreen(), light),
                    channel(plate.getBlue(), light)));
        }

        return colors;
    }

    /**
     * Which way this cell's surface faces, as light from -1 to 1.
     * <p>
     * From the slope, not from the depth. Depth alone says how far out a cell is, and lighting by it
     * makes the middle bright and the rim dark whichever way the rim actually turns — a target, not
     * a body. The slope says which way the surface points, so the top of the curve catches the sun
     * and the underside falls into shadow, which is the whole of what tells an eye something is
     * round.
     * <p>
     * The same sun as the shading the pack bakes for the worn route: from above, in front and a
     * little to her right, where Minecraft's own shading implies it is.
     */
    private static double tone(@NotNull BodyShape shape, BodyShape.@NotNull Cell cell, double deepest) {
        double x = cell.u() + 0.5d;
        double y = cell.v() + 0.5d;

        double across = shape.depthAt(x + 1.0d, y) - shape.depthAt(x - 1.0d, y);
        double down = shape.depthAt(x, y + 1.0d) - shape.depthAt(x, y - 1.0d);

        double nx = -across;
        double ny = -down;
        // Flatter than the true slope: the shape is a pixel or so deep and its honest normals would
        // all point very nearly the same way.
        double nz = 1.6d * deepest;

        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0.0d) return 0.0d;

        double lit = (nx * -0.38d + ny * -0.72d + nz * 0.58d) / length;

        // Flat body reads 0.58, so only the difference from flat is painted and a cell whose surface
        // is parallel to the torso comes out the plate's own colour.
        return Math.max(-1.0d, Math.min(1.0d, (lit - 0.58d) * 2.6d));
    }

    private static int channel(int value, double light) {
        return Math.max(0, Math.min(255, (int) Math.round(value * light)));
    }

    /**
     * The colour of the breastplate she is wearing, or {@code null} if she is wearing none.
     * <p>
     * Read from the item rather than from a list of names where it can be: leather is dyed, and a
     * dyed breastplate that came out iron-grey would be worse than no colour at all. The rest are
     * fixed materials with fixed colours, and those are taken from the armour textures themselves so
     * a figure sits in the same metal as the plate over it.
     */
    public @Nullable Color plateOf(@NotNull LivingEntity subject) {
        EntityEquipment equipment = subject.getEquipment();
        if (equipment == null) return null;

        ItemStack chest = equipment.getChestplate();
        if (!worn(chest)) return null;

        if (chest.getType() == Material.LEATHER_CHESTPLATE
                && chest.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta dyed) {
            return dyed.getColor();
        }

        // Asked of the name, not of a list. A list of the metals that existed when this was written
        // is a list that is wrong the moment one is added — copper arrived and was not on it, so
        // copper armour covered nothing. Anything the game itself calls a chestplate is one.
        String name = chest.getType().name();
        if (!name.endsWith("_CHESTPLATE")) return null;

        // Elytra fall out here without being named, which is right: they are wings strapped to a
        // back, not a shell over a chest, and the game does not call them a chestplate either.
        return switch (name) {
            case "IRON_CHESTPLATE" -> Color.fromRGB(0xD8D8D8);
            case "CHAINMAIL_CHESTPLATE" -> Color.fromRGB(0x9A9A9A);
            case "GOLDEN_CHESTPLATE" -> Color.fromRGB(0xE0C846);
            case "DIAMOND_CHESTPLATE" -> Color.fromRGB(0x6ECDC4);
            case "NETHERITE_CHESTPLATE" -> Color.fromRGB(0x554E4E);
            case "COPPER_CHESTPLATE" -> Color.fromRGB(0xC16A50);

            // A chestplate this was not written against — a newer metal, or one a data pack made
            // up. It still covers her, and a plain metal grey is a better answer than pretending
            // she is not wearing anything.
            default -> Color.fromRGB(0xB4B4B4);
        };
    }

    /** Forgets someone entirely, for a villager who died or a player who left. */
    public void forget(@NotNull UUID subject) {
        shown.remove(subject);
        undo.remove(subject);
    }

    /**
     * Sends the figure, and empties whichever slot it is not in.
     * <p>
     * Both slots every time, because the one being left has to be told it is empty.
     * Switching from
     * the head to the chest without that would leave the figure sitting on her head
     * as well as
     * wherever it went — two of her, and no way to tell which slot actually drew
     * one.
     */
    private void send(@NotNull Player watcher, @NotNull ItemStack shape, int entityId) {
        var worn = SpigotConversionUtil.fromBukkitItemStack(shape);
        var empty = SpigotConversionUtil.fromBukkitItemStack(new ItemStack(Material.AIR));

        PacketEvents.getAPI().getProtocolManager().sendPacket(
                SpigotReflectionUtil.getChannel(watcher),
                new WrapperPlayServerEntityEquipment(entityId, List.of(
                        new Equipment(EquipmentSlot.HELMET, worn),
                        new Equipment(EquipmentSlot.CHEST_PLATE, empty))));
    }

    /**
     * A villager's figure, or {@code null} if she shows none.
     * <p>
     * Which model she gets is decided here and nowhere else: her shape, the size
     * step it falls in,
     * and whether she has leg armour it must clear. All three are baked into the
     * pack as separate
     * models, because a worn item has no transform of its own to adjust at runtime.
     */

    /**
     * A villager's figure, or {@code null} if she shows none.
     * <p>
     * Which model she gets is decided here and nowhere else: her shape, the size
     * step it falls in,
     * and whether she has leg armour it must clear. All three are baked into the
     * pack as separate
     * models, because a worn item has no transform of its own to adjust at runtime.
     */
    public @Nullable ItemStack figureFor(@NotNull Villager villager, @NotNull IVillagerNPC npc) {
        if (!enabled())
            return null;
        if (!villager.isAdult())
            return null;

        Traits traits = Traits.of(villager.getUniqueId(), npc.isFemale());

        // A bust if she has one, otherwise her build; one slot, so one shape.
        BodyShape shape;
        double size;

        if (busts() && traits.hasBust() && traits.bustShape() != null) {
            shape = traits.bustShape();
            size = traits.bust();
        } else if (builds() && traits.hasBuild()) {
            shape = BodyShape.BUILD;
            size = 0.6d + 0.9d * traits.build();
        } else {
            return null;
        }

        List<Color> colors = colours(skinUrl(villager), shape);
        if (colors == null)
            return null;

        // The same as for a player, and it is the same line on purpose. A breastplate used to end
        // the matter for a villager — she was checked for one before anything else was worked out,
        // and if she had it she had no figure — so putting armour on a woman took her shape off and
        // taking it off put her shape back. A player never worked that way, and there was never a
        // reason for the two to disagree: the plate is a shell pressed over what is underneath, so
        // what is underneath stays and wears the metal.
        Color plate = plateOf(villager);
        if (plate != null) colors = plated(shape, plate);

        return item(shape.variant(BodyShape.stepOf(size), false), colors);
    }

    /**
     * The switches, read every time rather than remembered.
     * <p>
     * Every time, so that turning one off in the config and reloading takes effect
     * on the next pass
     * — and, more to the point, so that turning one off is enough. A figure that
     * had been sent is
     * taken back off by the same path that takes one off when armour goes on,
     * because from here
     * "she has no figure" is one answer however it was arrived at.
     */
    private boolean enabled() {
        // The pack has to be reaching players, or there is nothing to draw the figures out of.
        // Turning them off is not a lesser version of showing them: a client that cannot resolve
        // the model shows its placeholder instead, so the choice is between no figure and a
        // visibly broken one.
        return Config.APPEARANCE_BODY_SHAPES.asBool(true) && plugin.isShapesServed();
    }

    private boolean busts() {
        return Config.APPEARANCE_BUST.asBool(true);
    }

    /**
     * Whether weight is drawn on anyone who carries it.
     * <p>
     * Its own switch, apart from the bust, because it is the one shape a man can
     * have — roughly two
     * thirds of them — and a server that wants women to have a figure does not
     * necessarily want
     * everybody else to have a stomach.
     */
    private boolean builds() {
        return Config.APPEARANCE_BUILD.asBool(true);
    }

    /**
     * Whether this player chose to be a woman, read where the rest of the plugin
     * keeps it.
     */
    /**
     * Whether there is a body here to carry a figure at all.
     * <p>
     * A spectator and a corpse are the two states where there is not, and both used to leave one
     * behind. A figure rides its owner, and both of those stop being something anybody can see
     * ride: a spectator is drawn to nobody but herself, so every other client goes on drawing the
     * figure at the spot she was last seen at — hers, meanwhile, is the one client still drawing
     * it, right in front of her camera. A dead player keeps her entity until she respawns, so the
     * figure taken off her at the moment of death was promptly handed back a tick later and left
     * standing in the grass where she fell.
     * <p>
     * Asked here rather than at either of those two events, because a tick of this is what puts
     * the figure back — so this is the only place that can decide not to.
     */
    private boolean carriable(@NotNull Player player) {
        return !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean female(@NotNull Player player) {
        String sex = player.getPersistentDataContainer()
                .get(plugin.getPlayerSexKey(), PersistentDataType.STRING);

        return "female".equalsIgnoreCase(sex);
    }

    /**
     * How far out of her the figure has to start so that what she is wearing does not swallow it,
     * in blocks.
     * <p>
     * Armour is drawn as the body's own cubes grown outwards by a fixed amount, and there are two
     * amounts: the outer layer — helmet, breastplate, boots — grows by a whole pixel, and the inner
     * layer, which is the leggings, by half of one. The figure already starts a quarter of a pixel
     * out, at the jacket, so what is left to clear is whatever the difference comes to.
     * <p>
     * Leggings are the half of this that was missing, and they are why a stomach still disappeared
     * under armour after a breastplate had stopped hiding one. Nothing asked about them, so a
     * villager in trousers and no plate kept her figure at the jacket while the game drew a belt
     * over the top of it, half a pixel further out. The bottom of a belly is exactly where a pair of
     * leggings is opaque, so exactly the part that shows went inside them.
     */
    public float clearance(@NotNull LivingEntity subject) {
        EntityEquipment equipment = subject.getEquipment();
        if (equipment == null) return 0.0f;

        // Asked through plateOf so that an elytra is not mistaken for a shell over her chest. It is
        // worn in the same slot and the game returns it from the same call, but it is a pair of
        // wings on a back and there is nothing of it in front of her to clear.
        if (plateOf(subject) != null) return (1.0f - 0.25f) / 16.0f;
        if (worn(equipment.getLeggings())) return (0.5f - 0.25f) / 16.0f;

        return 0.0f;
    }

    private boolean worn(@Nullable ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    /**
     * Merges the visual appearance of a real helmet into the figure item.
     * <p>
     * The real helmet stays in the entity's real equipment; this touches only the
     * item sent by packet. The {@code equippable} component's {@code model} field
     * is
     * what the game reads to decide which armour texture to paint on the head — so
     * copying it here is what makes the helmet still look like a helmet even though
     * the slot is now carrying the figure. The figure's own {@code item_model}
     * remains untouched, so both are drawn: the helmet in the armour-texture pass
     * and the figure in the model pass.
     */
    private @NotNull ItemStack withHelmet(@NotNull ItemStack shape, @NotNull ItemStack helmet) {
        ItemMeta helmMeta = helmet.getItemMeta();
        if (helmMeta == null)
            return shape;

        ItemMeta shapeMeta = shape.getItemMeta();
        if (shapeMeta == null)
            return shape;

        try {
            NamespacedKey model = helmMeta.getEquippable().getModel();
            if (model == null) {
                // Vanilla helmets do not set an explicit model key in their equippable
                // component;
                // they implicitly use their material key. We must make it explicit on the
                // paper.
                model = helmet.getType().getKey();
            }
            if (model != null) {
                var equip = shapeMeta.getEquippable();
                equip.setModel(model);
                shapeMeta.setEquippable(equip);
            }
        } catch (Throwable ignored) {
            // Equippable API not present on this server version; helmet texture skipped.
        }

        shape.setItemMeta(shapeMeta);
        return shape;
    }

    /**
     * The shape a variant belongs to, without its size and armour suffixes.
     * <p>
     * The armour layers are drawn as shading, and shading does not have sizes: it
     * is painted on the
     * body's own pixels either way. So {@code bust_round_s3_a} and
     * {@code bust_round_s0} are the
     * same picture here, and both want {@code bust_round}.
     */
    private @NotNull String shapeOf(@NotNull String variant) {
        int cut = variant.indexOf("_s");
        return cut < 0 ? variant : variant.substring(0, cut);
    }

    /**
     * A cache key for what this watcher was last sent about a given subject.
     * <p>
     * Includes the equippable model as well as the item model. Without it, adding
     * or removing a helmet does not change the key — the figure variant stays the
     * same — so the cache says "already sent" and nothing goes out. The vanilla
     * equipment packet the server fires for the slot change then clears the figure
     * with nothing to put it back, and it is gone.
     */
    private @Nullable String variantOf(@Nullable ItemStack shape) {
        if (shape == null)
            return null;

        ItemMeta meta = shape.getItemMeta();
        if (meta == null || meta.getItemModel() == null)
            return null;

        String base = meta.getItemModel().getKey();

        try {
            NamespacedKey helmModel = meta.getEquippable().getModel();
            if (helmModel != null)
                return base + "#" + helmModel.getKey();
        } catch (Throwable ignored) {
            // Equippable API absent; key is the item model alone.
        }

        return base;
    }

    private @NotNull ItemStack item(@NotNull String variant, @NotNull List<Color> colors) {
        ItemStack item = new ItemStack(Material.PAPER);

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.setItemModel(new NamespacedKey(TraitPack.NAMESPACE, variant));

        // Worn on the chest, the model is not what gets drawn — armour is. So the item
        // is pointed at
        // an armour layer of the same figure, drawn as light and shadow on the torso,
        // and the model
        // it also carries is simply never reached. On the head it is the other way
        // round: the model
        // is drawn and no armour layer exists for that slot.
        try {
            var equippable = meta.getEquippable();

            equippable.setSlot(org.bukkit.inventory.EquipmentSlot.HEAD);

            meta.setEquippable(equippable);
        } catch (Throwable throwable) {
            // An older API than the component: the slot is still whatever the packet says.
        }

        CustomModelDataComponent data = meta.getCustomModelDataComponent();
        data.setColors(colors);
        meta.setCustomModelDataComponent(data);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * The colours for this shape, if they are ready.
     * <p>
     * The first villager or player wearing a given skin starts the download and
     * goes without for a
     * moment; everyone sharing it afterwards is served from memory.
     */
    private @Nullable List<Color> colours(@Nullable String url, @NotNull BodyShape shape) {
        if (url == null)
            return null;

        List<Color> ready = SkinImage.cached(url, shape);
        if (ready != null)
            return ready.isEmpty() ? null : ready;

        SkinImage.load(url, shape);
        return null;
    }

    /**
     * A player's skin, straight off her profile — the same picture everyone else is
     * looking at.
     */
    private @Nullable String skinUrl(@NotNull Player player) {
        try {
            var skin = player.getPlayerProfile().getTextures().getSkin();
            return skin == null ? null : skin.toString();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private @Nullable String skinUrl(@NotNull Villager villager) {
        try {
            var texture = plugin.getTracker().getTextures(villager);
            if (texture == null || "error".equals(texture.getName()))
                return null;

            String json = new String(java.util.Base64.getDecoder().decode(texture.getValue()),
                    java.nio.charset.StandardCharsets.UTF_8);

            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            return root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
        } catch (Throwable throwable) {
            return null;
        }
    }
}
