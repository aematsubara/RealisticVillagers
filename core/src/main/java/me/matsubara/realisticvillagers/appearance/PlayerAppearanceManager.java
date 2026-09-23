package me.matsubara.realisticvillagers.appearance;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player's figure, carried by an item display that rides her.
 * <p>
 * Riding, and never moved by hand. That distinction is the whole of this class. Every earlier
 * attempt kept the figure in place by pushing it there — a task that read her position each tick and
 * teleported the display after her — and every one of them trailed behind her, because a position
 * sent on a timer is a position from a moment ago. Twenty updates a second is still twenty, and a
 * screen drawing a hundred and forty frames sees every one of the gaps.
 * <p>
 * A passenger is not sent a position at all. The client is told once that this display rides her,
 * and from then on it places the display itself, in the same frame it places her, however many
 * frames it draws. There is nothing to keep up to date and so nothing that can fall behind.
 * <p>
 * The offset from her feet to her chest is not a movement either. It lives in the display's own
 * transformation — a translation the client applies after it has worked out where she is — so it
 * costs nothing per tick and cannot drift.
 * <p>
 * Turning is the one thing that is sent, and it is sent as a rotation rather than as a teleport.
 * A rotation packet does carry a position, but harmlessly: the client puts a passenger back on its
 * vehicle every tick, after the rotation has landed, so that position never survives to be drawn.
 * Earlier attempts sent rotations to a display the client did not believe was riding anything —
 * nothing put it back, and it stayed where the packet left it, on the ground.
 * <p>
 * And it is turned to face the way her chest faces, not the way she is looking. Those come apart
 * every time she glances sideways, which is what made the head-mounted version swing.
 */
public final class PlayerAppearanceManager {

    private final RealisticVillagers plugin;

    /** Marks a display as ours, so one left behind can be recognised rather than guessed at. */
    private final NamespacedKey key;

    /** Which display belongs to whom, and which figure it is currently showing. */
    private final Map<UUID, Carried> carried = new ConcurrentHashMap<>();

    /**
     * What each display is carrying.
     * <p>
     * The whole item, not the name of its model. Those two came apart the moment armour stopped
     * choosing a different model: putting on a breastplate changes every colour in the item and
     * changes the model's name not at all, so a check on the name said nothing had happened and the
     * new colours were never sent. She wore diamond and stayed the colour of skin.
     */
    private record Carried(@NotNull UUID display, @NotNull ItemStack figure) {}

    public PlayerAppearanceManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "PlayerFigure");
    }

    private static final double TORSO_TOP = FigurePose.TORSO_TOP;

    /**
     * How hard her stride, her falling and her landing pull on her, and how far the result may go.
     * <p>
     * Sized against the game's own numbers rather than by taste. A hop leaves the ground at 0.42
     * blocks a tick and lands having moved about three quarters of a block between two of these
     * calls; a fall from eighty reaches four. Those two are the ends of the range this has to tell
     * apart, and they still come out about five times apart.
     * <p>
     * Taken down to something under half of what was here first. The complaint that this was too
     * weak was never really about how far it went — it was that a jump and a fall off a cliff came
     * out the same, because the landing that mattered was being thrown away before it was ever
     * felt. Answering that with a great deal more movement fixed the wrong half: the contrast is
     * what was missing, and the contrast is kept here while the swing is not.
     * <p>
     * The stride is separately down to a third again, and the landings are deliberately untouched
     * beside it — those were judged right and there was no reason to disturb them to quiet a walk.
     * Being two numbers rather than one is what made that possible: her footfalls and what happens
     * when she lands are different events and were never the same figure.
     */
    private static final double STRIDE = 0.014d;
    private static final double IMPACT = 0.62d;
    private static final double IMPACT_SOFT = 12.0d;
    private static final double REACH = 0.15d;

    /**
     * Makes sure this player is carrying the figure she should be, and no other.
     * <p>
     * Called on a timer, but not because anything here needs one: the display keeps its own place.
     * It is called because what she should be carrying changes — she puts on armour, or changes her
     * mind about being a woman — and nothing announces that.
     */
    public void update(@NotNull Player player) {
        update(player, plugin.getShapeEquipment() == null
                ? null
                : plugin.getShapeEquipment().figureFor(player));
    }

    /**
     * The same for a villager.
     * <p>
     * The same on purpose. A villager's figure had been worn in her head slot, which is where it
     * turned with her head and vanished under a helmet; there is no reason for her to keep the worse
     * of the two arrangements now that the better one exists and is understood.
     */
    public void update(@NotNull Villager villager, @NotNull me.matsubara.realisticvillagers.entity.IVillagerNPC npc) {
        update(villager, plugin.getShapeEquipment() == null
                ? null
                : plugin.getShapeEquipment().figureFor(villager, npc));
    }

    private void update(@NotNull LivingEntity player, @Nullable ItemStack figure) {

        Carried current = carried.get(player.getUniqueId());
        ItemDisplay display = current == null ? null : find(player, current.display());

        if (figure == null) {
            remove(player);
            return;
        }

        if (display == null) {
            ItemDisplay spawned = attach(player, figure);
            if (spawned != null) carried.put(player.getUniqueId(), new Carried(spawned.getUniqueId(), figure));
            return;
        }

        // Sent again only when it is genuinely different — colours included, which is the point.
        if (!figure.equals(current.figure())) {
            display.setItemStack(figure);
            carried.put(player.getUniqueId(), new Carried(display.getUniqueId(), figure));
        }

        // Her facing and her movement change every pass, and both live in the transform rather
        // than in the item, so they are applied whether the item changed or not.
        follow(player);
        shape(display, player, figure);
    }

    /**
     * Spawns the display and hands it to her to carry.
     * <p>
     * The order matters. Everything about how the display looks is set inside the spawn, before the
     * client is ever told the entity exists, so it is never seen for a frame in the wrong place or
     * at the wrong size. Only then is it seated.
     */
    private @Nullable ItemDisplay attach(@NotNull LivingEntity player, @NotNull ItemStack figure) {
        try {
            ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, spawned -> {
                spawned.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                        player.getUniqueId().toString());

                spawned.setItemStack(figure);
                spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                spawned.setGravity(false);
                spawned.setRotation(displayYaw(player), 0.0f);

                // Never written to the world. A figure is a picture of somebody who is here now;
                // one saved to disk would be dug up by the next restart with nobody to ride.
                spawned.setPersistent(false);

                // What the client does when it is told this thing has moved. One tick, so a change
                // is drawn as a movement rather than a jump — it costs nothing while nothing moves,
                // and while she is riding nothing here sends a position at all.
                spawned.setTeleportDuration(1);

                // Exactly the gap between two of these being worked out, so a change is drawn as
                // movement that arrives just as the next one is decided — no longer, or the figure
                // is always on its way to where it should already be, which at a body's length out
                // in front of a turning glider is the difference between following her and lagging.
                spawned.setInterpolationDuration((int) TICKS_PER_PASS);
                spawned.setInterpolationDelay(0);

                // Lit by whatever lights her, which means carrying no light of its own.
                //
                // It used to be told it stood in full daylight, on the reasoning that a display
                // takes the light of the block it occupies and the block a figure occupies is the
                // one inside her body, which is dark. The reasoning is wrong on its own terms: a
                // display riding her is at her feet, in the very air block the game reads to light
                // her, so left alone it is lit exactly as she is. What the override actually bought
                // was a chest burning at noon on a body standing in the dark — and a figure brighter
                // than the woman wearing it is a worse fault than one a shade too dim.
                spawned.setBrightness(null);

                spawned.setShadowRadius(0.0f);
                spawned.setShadowStrength(0.0f);

                shape(spawned, player, figure);
            });

            // Kept off the screens that cannot draw it, before it is seated and broadcast.
            for (Player viewer : plugin.getServer().getOnlinePlayers()) {
                if (!draws(viewer)) viewer.hideEntity(plugin, display);
            }

            if (!player.addPassenger(display)) {
                display.remove();
                return null;
            }
            display.setRotation(displayYaw(player), 0.0f);

            // Seating her figure makes the server broadcast her passenger list, and that list is
            // the server's own: it knows about the figure and knows nothing about her name tag,
            // which is made of packets. So the name tag is knocked off the moment the figure is
            // put on, and has to be put back by the one piece of code that knows both exist.
            reseat(player);

            return display;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not give " + player.getName()
                    + " her figure: " + throwable.getMessage());
            return null;
        }
    }

    /**
     * Sends a villager's whole passenger list again, name tag and figure together.
     * <p>
     * Only for villagers, and only after something has changed who rides them. A player has no name
     * tag of ours to lose, so there is nothing there to put back.
     */
    private void reseat(@NotNull LivingEntity subject) {
        if (subject instanceof Player) return;

        var pool = plugin.getTracker() == null ? null : plugin.getTracker().getPool();
        if (pool == null) return;

        pool.getNPC(subject.getEntityId()).ifPresent(npc -> {
            for (Player watcher : subject.getWorld().getPlayers()) {
                if (watcher.canSee(subject)) npc.sendPassengers(watcher);
            }
        });
    }

    /**
     * Where the figure sits on her, and how big it is.
     * <p>
     * Both live in the transformation rather than in the display's position, which is what lets the
     * display ride her untouched. The client works out where she is, seats the display, and only
     * then applies this — so the figure is at her chest without anything ever having moved it there.
     * <p>
     * The drop is worked out rather than guessed. A passenger sits at a known height, and her skin's
     * own rows say where a chest is; the difference is how far down to go.
     */
    private void shape(@NotNull ItemDisplay display, @NotNull LivingEntity player, @NotNull ItemStack figure) {
        Traits traits = player instanceof Player woman
                ? Traits.of(woman.getUniqueId(), true, plugin.playerShapeRoll(woman))
                : Traits.of(player.getUniqueId(), plugin.getConverter().getNPC(player)
                        .map(me.matsubara.realisticvillagers.entity.IVillagerNPC::isFemale).orElse(false));

        // A bust if she has one, otherwise the weight she carries — which sits lower and is the one
        // shape a man can have.
        BodyShape body;
        double size;

        if (traits.hasBust() && traits.bustShape() != null && !isBuild(figure)) {
            body = traits.bustShape();
            size = traits.bust();
        } else if (traits.hasBuild()) {
            body = BodyShape.BUILD;
            size = 0.6d + 0.9d * traits.build();
        } else {
            return;
        }

        // Body rows 20 to 31 are her torso top to bottom, so a shape wearing rows 21 to 24 belongs
        // exactly where those rows are drawn on her.
        double band = TORSO_TOP - (body.skinV() + body.height() / 2.0d - 20.0d) / 16.0d;

        // Worked out before anything else, because it decides which seat to measure from.
        float lean = turn(player);

        // How much bigger or smaller than ordinary this body is.
        //
        // Every villager is given her own height through the scale attribute, and the game carries
        // the seat along with it — measured on a running server, a villager at half size seats her
        // passenger at 0.975 rather than 1.95, exactly half. So a fixed drop is right for one
        // height only and wrong by up to fourteen centimetres for the tallest and shortest, which
        // is what put their figures out of place.
        //
        // Everything this number does is multiplied by it or by one less than it, so at ordinary
        // size — every player, and any villager whose height came out plain — it changes nothing
        // whatsoever.
        double stature = stature(player);

        ShapeEquipment shapes = plugin.getShapeEquipment();

        // Two different questions, and they used to be one. Whether steel is holding her still is
        // asked of a breastplate; how far out the figure has to start is asked of everything she is
        // wearing, and a pair of leggings answers it even though it is not a plate over her chest.
        boolean plated = shapes != null && shapes.plateOf(player) != null;
        float clearance = shapes == null ? 0.0f : shapes.clearance(player);

        // Her own size, the scale everyone is drawn at on top of it, and her own height on top of
        // that — a tall villager is drawn larger everywhere, and a figure that ignored it would be
        // the one part of her that did not grow with the rest.
        double bigger = stature * FigurePose.MODEL_SCALE;
        float across = (float) bigger;

        org.bukkit.entity.Pose shown = shownPose(player);
        boolean asleep = shown == org.bukkit.entity.Pose.SLEEPING;

        // How far she is off her feet, which is how far her figure has settled into her.
        //
        // The same number the body is being turned by, and deliberately the same one. Crawling,
        // swimming and gliding all ease the body flat over about half a second, and a figure that
        // flattened on some other schedule would be a shape changing size while the body it belongs
        // to is still going over. A bed is the exception in the game as well as here: it lays her
        // out in a single frame, so there is nothing to ease into.
        float laid = asleep ? 1.0f : ramp(player);

        // One question asked once, for every posture there is.
        Posture posture = postureOf(player, band, lean, stature, clearance);

        // Armour is a shell, and a shell does not wobble. Her figure keeps its shape under a plate
        // and stops settling with her stride, because steel would not.
        //
        // Worked out even while it is thrown away, which is the whole point of the line. Skipping
        // the call under armour left the spring remembering where she stood when she put the plate
        // on; take it off a hundred blocks later and the first thing it sees is a hundred-block
        // step, which is either a violent lurch or — past the point where it is read as a teleport
        // — nothing at all. Neither is what taking off a breastplate should do.
        float wobble = settle(player, size);

        // And the one formula the whole table exists to feed: her chest, wherever the game put it,
        // less the seat her passenger is parked at. Every posture above answers those two and this
        // line never changes.
        Vector3f offset = new Vector3f(posture.chest()).sub(0.0f, (float) posture.seat(), 0.0f);

        // And the step between where she is and where her stand-in was put, if somebody else is
        // drawing her. A pure height, so it survives both the turn the display is given and the
        // half turn that converts into its basis — Y is the axis both of those turn about.
        LyingPlugins.Lying lying = LyingPlugins.of(player);
        if (lying != null) offset.y += lying.lift() * stature;
        // Keep roots attached: small, bounded motion in the torso's own axes, including in flight.
        float limit = body == BodyShape.BUILD ? 0.008f : 0.035f;
        float activity = asleep || shown == org.bukkit.entity.Pose.SPIN_ATTACK
                ? 0.0f : 1.0f - 0.85f * ramp(player);
        float travel = plated ? 0.0f
                : (float) (limit * Math.tanh(wobble / limit) * stature * activity);
        offset.add(FigurePose.tissueOffset(posture.shape(), travel));

        org.joml.Quaternionf turned = posture.shape();

        // The owner receives a zero scale through a client-only metadata packet. The real
        // transformation remains on the server, so every other player keeps seeing the figure.
        boolean fromHer = player instanceof Player && concealed.contains(player.getUniqueId());

        // A display left over from a build that lit its own figure. Cleared wherever it is found
        // rather than only where they are made, so a reload is enough to stop one glowing and
        // nobody has to walk out of range and back to be rid of it.
        if (display.getBrightness() != null) display.setBrightness(null);

        Transformation was = display.getTransformation();
        // Off her feet, the attachment point is unchanged and the shape is not. Tissue with the
        // ground under it settles towards her spine and spreads across her ribs, and it is that,
        // rather than anything about where the figure is anchored, which keeps a woman lying down
        // from wearing two blocks pointing at the ceiling.
        Vector3f scale = FigurePose.figureScale(body == BodyShape.BUILD,
                across, (float) (size * bigger), laid);
        Transformation next = new Transformation(offset, turned, scale, new org.joml.Quaternionf());
        org.bukkit.entity.Pose previousPose = displayedPoses.put(player.getUniqueId(), shown);
        if (!next.equals(was)) {
            // Hitbox/seat changes are immediate on the client; interpolating that correction
            // would leave the figure at the old seat during the first frame of the new pose.
            display.setInterpolationDuration(previousPose == shown ? 1 : 0);
            display.setTransformation(next);
            display.setInterpolationDelay(0);
        }

        // A villager who has just changed posture. She is drawn as a player, and a player only lies
        // along a bed if the client has been told which bed — a villager has no such field of her
        // own, so the only one it will ever have is one this plugin sends it. Falling asleep in
        // plain sight sends none, and the client then lays her out by her body angle while the
        // figure, which is placed by the bed, goes across her instead of on her.
        if (previousPose != shown) bedded(player);

        // Camera mode is not sent by the client. During poses that put the torso in front of the
        // eyes, hide it from the owner for the whole pose; the packet remains private to her.
        if (player instanceof Player wearer) {
            blindfold(wearer, display, fromHer, scale);
        }
    }

    /**
     * The field the display's size lives in, counting from the first field an entity has.
     * <p>
     * Twelve, and read out of the server rather than counted on fingers. An entity claims the first
     * eight — flags, air, name, name visible, silent, gravity, pose, frozen — and a display adds its
     * own after them in the order it declares them: two interpolations, the position one, the
     * translation, and then this. Nothing here may be guessed; a wrong number sets a different
     * property to zero and the failure would not look like a wrong number.
     */
    private static final int SCALE_FIELD = 12;

    /** Whose client has been told her figure is nothing, so it is known when to tell it otherwise. */
    private final Set<UUID> blinded = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Tells one client, and only that client, that her figure has no size.
     * <p>
     * A size of nothing rather than a removal, for the same reason as everywhere else in this
     * class: removing sends a packet that destroys the entity, and this entity is in her own
     * passenger list, so destroying it on her client unseats it for good. Sized to nothing it is
     * still there, still riding her, still drawn for everyone the server told the truth to.
     * <p>
     * Sent every pass while she is not to see it, because the server will send its own size out
     * again the moment anything about her figure genuinely changes, and that goes to her as well.
     * Five packets a second while she crawls is not worth arranging around. On the way out it is
     * sent once, and only if she had been told otherwise.
     */
    private void blindfold(@NotNull Player wearer, @NotNull ItemDisplay display,
                           boolean hide, @NotNull Vector3f real) {
        UUID id = wearer.getUniqueId();

        if (hide) blinded.add(id);
        else if (!blinded.remove(id)) return;

        com.github.retrooper.packetevents.util.Vector3f value = hide
                ? new com.github.retrooper.packetevents.util.Vector3f(0.0f, 0.0f, 0.0f)
                : new com.github.retrooper.packetevents.util.Vector3f(real.x, real.y, real.z);

        try {
            Object channel = com.github.retrooper.packetevents.PacketEvents.getAPI()
                    .getPlayerManager().getChannel(wearer);
            if (channel == null) return;

            com.github.retrooper.packetevents.PacketEvents.getAPI().getProtocolManager().sendPacket(
                    channel,
                    new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata(
                            display.getEntityId(),
                            java.util.List.of(new com.github.retrooper.packetevents.protocol.entity.data.EntityData<>(
                                    SCALE_FIELD,
                                    com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes.VECTOR3F,
                                    value))));
        } catch (Exception ignored) {
            // A figure she can see is a blemish; a stack trace five times a second is a fault.
        }
    }

    /**
     * How far the figure is currently displaced from where it belongs, in blocks.
     * <p>
     * Soft tissue does not go exactly where the body goes and does not arrive at the same moment.
     * It is dragged along a step behind, overshoots when the body stops, and settles. Everything
     * below is that one idea; none of it is decoration on top of the position, it is the position.
     * <p>
     * Worked as a spring rather than as a wave. A wave is cheaper and looks it: it keeps moving at
     * exactly the same rate whatever she does, so a walk and a fall from a roof read identically. A
     * spring is driven by what is actually happening to her — her stride, her falling, her landing —
     * and being driven is what makes it look like a consequence rather than an animation.
     * <p>
     * Two ticks of interpolation on top, so the client draws the change as movement instead of a
     * step. The server sends five of these a second; the screen draws a hundred and forty, and the
     * ones in between are the graphics card's to invent.
     */
    private float settle(@NotNull LivingEntity player, double size) {
        Spring spring = springs.computeIfAbsent(player.getUniqueId(), id -> new Spring());
        if (spring.lastTick == player.getTicksLived()) return spring.shown;
        spring.lastTick = player.getTicksLived();

        // On its own clock, and deliberately not on the one above it.
        //
        // Everything here — the stride, the softening of a landing, the damping — was measured
        // against a step of two ticks and judged right at that step. Where her figure is drawn now
        // has to be said every tick, because a body's length out in front of a turning glider does
        // not forgive being late; but how far her figure has swung does not, and re-reading her
        // movement twice as often would halve every delta this reads and quietly restyle a fall.
        // So the placement was made quicker and this was left exactly as fast as it was.
        if (++spring.passes < PHYSICS_EVERY) return spring.shown;
        spring.passes = 0;

        // Measured from where she actually went, not from getVelocity(). A player's velocity is
        // whatever the server last pushed her with — knockback, a piston, an explosion — and is
        // simply zero while she walks under her own power, because the client is doing the moving.
        // Reading it meant the spring was fed nothing at all for ordinary movement, which is why
        // the physics looked broken rather than merely wrong.
        org.bukkit.Location at = player.getLocation();

        double dx = at.getX() - spring.wasX;
        double dy = at.getY() - spring.wasY;
        double dz = at.getZ() - spring.wasZ;

        boolean first = !spring.started;
        spring.wasX = at.getX();
        spring.wasY = at.getY();
        spring.wasZ = at.getZ();
        spring.started = true;

        // A first reading has nothing to compare against and would read as a teleport from nowhere.
        if (first) {
            spring.shown = 0.0f;
            return 0.0f;
        }

        // Only an actual teleport is discarded, and it is measured across all three axes rather
        // than down. The old test threw away any vertical change over two blocks, which sounds
        // careful and was a disaster: a body falling reaches nearly four blocks in the time between
        // two of these, so every landing worth feeling was thrown away as impossible and the only
        // falls that survived were the ones too short to matter. Twelve blocks in a tenth of a
        // second is a hundred and twenty a second — nothing that falls, sprints or glides comes
        // near it, and nothing that does was moving under its own power.
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (moved > 12.0d) {
            spring.offset = 0.0d;
            spring.speed = 0.0d;
            spring.wasVertical = 0.0d;
            spring.shown = 0.0f;
            return 0.0f;
        }

        double across = Math.sqrt(dx * dx + dz * dz);

        // Her stride. The faster she goes the harder each footfall lands, up to a limit — beyond a
        // sprint there is no more stride to have, only more speed.
        double stride = 0.0d;
        if (player.isOnGround() && !player.isInsideVehicle() && across > 0.01d) {
            double pace = Math.min(1.0d, across / 0.22d);
            spring.walked += 0.9d * pace;
            stride = Math.sin(spring.walked) * STRIDE * pace;
        }

        // Falling and landing. What moves tissue is not speed but the change in it: standing still
        // in a lift does nothing, and hitting the ground does a great deal.
        double fall = dy - spring.wasVertical;
        spring.wasVertical = dy;

        // Bent rather than cut off. A landing from a hop and a landing from eighty blocks differ by
        // about five times here, where before they differed by nothing at all — and they still
        // cannot run away, because past a certain violence the curve flattens instead of the number
        // being thrown out.
        double drive = stride - IMPACT * Math.tanh(fall / IMPACT_SOFT);

        // One step for every tick this reading covers, because the numbers below describe a spring
        // settling at twenty a second. Integrating a tick's worth of physics less often than the
        // physics assumes is not a slower spring, it is a wrong one — so the count follows how far
        // apart the readings are rather than being written in as a bare two.
        for (int step = 0; step < PHYSICS_EVERY * TICKS_PER_PASS; step++) {
            spring.speed += (drive - spring.offset) * 0.36d;

            // Loosened from four tenths. Four was chosen to stop a jump ringing, and it did, by
            // killing the movement outright — a centimetre and a half of travel, a quarter of one
            // pixel, which is why none of this could be seen. At this figure it overshoots once,
            // comes back once, and is still inside about a second.
            spring.speed *= 0.58d;

            // The spring itself knows nothing about how big she is. It used to, and that was wrong:
            // multiplying inside the loop changes how the spring behaves rather than how far it
            // goes, so every size settled at its own speed. Size is applied to the answer instead.
            spring.offset += spring.speed;
        }

        // Hers, straight in proportion, and bent rather than clipped at the end. A hard limit is
        // what made a hop and a cliff read alike: both reached it, and a spring sitting on its
        // limit is not a spring. This bends towards the limit instead, so the worst fall still goes
        // furthest and nothing ever runs away.
        // Her taste applied last, to the finished travel, so turning it down changes how far she
        // moves and nothing about how she moves.
        double reach = REACH * size;
        spring.shown = (float) (reach * Math.tanh(spring.offset * size / reach));

        return spring.shown;
    }

    /**
     * How large this body is drawn, where one is ordinary.
     * <p>
     * The attribute rather than a trait, because the attribute is what the client is actually told
     * and therefore what it seats a passenger by. A villager gets hers from her traits, a player
     * has none and reads one, and anything else on the server that scales somebody is followed for
     * free.
     */
    private double stature(@NotNull LivingEntity player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        return scale == null ? 1.0d : scale.getValue();
    }

    /**
     * How far she is lying down, in degrees, where ninety is flat.
     * <p>
     * Read from her pose and from nothing else. Asking whether she is swimming was wrong and
     * crawling is what proved it: the game only raises the swimming flag while she is
     * <em>sprinting in water</em>, so someone dragging herself under a slab has the flag down and
     * the pose set to swimming all the same. Her figure went on standing upright inside a body
     * lying flat, which is exactly what the screenshot showed.
     * <p>
     * The pose is also the right thing to ask on its own merits: it is what the game picks her
     * hitbox by, and so what her passenger is seated by, and what the model is turned by. One
     * answer instead of three guesses that have to agree.
     * <p>
     * A quarter turn backwards, which is the way the game turns her and not the way that reads
     * naturally — the model it turns is drawn upside down to begin with. Her own pitch goes on top,
     * where the game puts it, so diving head-down leans further than flying level. Crawling she is
     * simply flat, because out of water the game ignores where she is looking.
     * <p>
     * The angle she is <em>going</em> to reach. How much of it she has reached is {@link #turn}.
     */
    private float target(@NotNull LivingEntity player, boolean gliding, boolean settling) {
        float pitch = player.getLocation().getPitch();

        float target;
        if (gliding) target = -90.0f - pitch;
        else if (settling) target = player.isInWater() ? -90.0f - pitch : -90.0f;
        else if (player.getPose() == org.bukkit.entity.Pose.SPIN_ATTACK) target = -90.0f - pitch;
        else target = 0.0f;

        return target;
    }

    /**
     * Turns her a little further towards lying flat, or back up, and says where that leaves her.
     * <p>
     * The game does not lay a body down in one frame and neither can this. Both curves are the
     * game's own, read out of it rather than invented: gliding eases in as {@code t²/100} clamped
     * to one, which is a half second from upright to flat, and swimming and crawling run a number
     * up and down by nine hundredths a tick between nought and one. Snapping instead was visible
     * precisely when it was least wanted — at the moment of diving in or opening a wing, which is
     * when someone is looking at what their body just did.
     * <p>
     * Counted here rather than asked for because neither number is offered: one is a field the
     * server keeps to itself and the other exists only in the client. Both are simple enough to
     * keep honestly, and keeping them costs two floats a player.
     * <p>
     * Note which of the two ends each curve has. Gliding stops the instant the wing closes — the
     * game tests the flag, not a fading number — while swimming eases out as well as in, and goes
     * on turning her after the pose has already gone back to standing. That is why the angle and
     * the seat are worked out separately: they genuinely disagree for about half a second.
     */
    private float turn(@NotNull LivingEntity player) {
        Turn turn = turns.computeIfAbsent(player.getUniqueId(), id -> new Turn());

        org.bukkit.entity.Pose pose = shownPose(player);
        boolean gliding = pose == org.bukkit.entity.Pose.FALL_FLYING;
        boolean afloat = pose == org.bukkit.entity.Pose.SWIMMING;

        int tick = player.getTicksLived();
        if (turn.lastTick == tick) return turn.lean;
        boolean first = turn.lastTick < 0;
        turn.lastTick = tick;
        if (first) {
            turn.glided = gliding ? 10.0f : 0.0f;
            turn.afloat = afloat ? 1.0f : 0.0f;
        }
        // Placement runs once per server tick, independently of movement event frequency.
        turn.glided = gliding ? Math.min(10.0f, turn.glided + TICKS_PER_PASS) : 0.0f;
        turn.afloat = afloat
                ? Math.min(1.0f, turn.afloat + 0.09f * TICKS_PER_PASS)
                : Math.max(0.0f, turn.afloat - 0.09f * TICKS_PER_PASS);

        float ramp;
        if (gliding) ramp = Math.min(1.0f, turn.glided * turn.glided / 100.0f);
        else if (pose == org.bukkit.entity.Pose.SPIN_ATTACK) ramp = 1.0f;
        else ramp = turn.afloat;

        float measuredRoll = roll(player, turn);
        turn.roll = gliding ? measuredRoll : 0.0f;

        turn.ramp = ramp;
        turn.lean = target(player, gliding, turn.afloat > 0.0f) * ramp;
        return turn.lean;
    }

    /** Client flight roll from this tick's measured movement; vertical views have no roll. */
    private float roll(@NotNull LivingEntity player, @NotNull Turn turn) {
        org.bukkit.Location at = player.getLocation();
        double dx = at.getX() - turn.wasX;
        double dz = at.getZ() - turn.wasZ;
        boolean first = !turn.seen || !at.getWorld().getUID().equals(turn.world);
        turn.wasX = at.getX();
        turn.wasZ = at.getZ();
        turn.world = at.getWorld().getUID();
        turn.seen = true;
        if (first || dx * dx + dz * dz > 144.0d) return 0.0f;
        return FigurePose.flightRoll(dx, dz, at.getYaw(), at.getPitch());
    }
    /** The roll she is carrying, without moving anything on. */
    private float rollOf(@NotNull LivingEntity player) {
        Turn turn = turns.get(player.getUniqueId());
        return turn == null ? 0.0f : turn.roll;
    }

    /** How far through lying down she is, nought to one, without moving it on. */
    private float ramp(@NotNull LivingEntity player) {
        Turn turn = turns.get(player.getUniqueId());
        return turn == null ? 0.0f : turn.ramp;
    }

    /** Where her turn stands, without moving it on. For reporting, which must not change anything. */
    private float lean(@NotNull LivingEntity player) {
        Turn turn = turns.get(player.getUniqueId());
        return turn == null ? 0.0f : turn.lean;
    }

    /**
     * Everything a posture has to say: where her chest is, which way her shape stands out of it,
     * and where the client parks her passenger.
     * <p>
     * Three answers, and every posture gives all three. What used to be here instead was a chain of
     * branches each carrying its own arithmetic, which is how gliding and swimming ended up sharing
     * numbers they should never have shared. Splitting them into templates makes the difference
     * between two postures a difference in a table rather than a difference in the code.
     *
     * @param chest where her chest is, in blocks from her feet, already turned and already her size.
     * @param shape which way the figure itself faces, which is not the same question.
     * @param seat  what the client seats her passenger at, hers and her size.
     */
    private record Posture(@NotNull Vector3f chest, @NotNull org.joml.Quaternionf shape, double seat) {}

    private @NotNull Posture postureOf(@NotNull LivingEntity player, double band, float lean,
                                       double stature, float clearance) {
        FigurePose.Placement placed = FigurePose.place(shownPose(player), player.getPose(),
                band, (float) stature,
                lean, ramp(player), rollOf(player), player.getLocation().getPitch(),
                player.getTicksLived(), bedFacing(player), clearance);
        return new Posture(placed.chest(), placed.rotation(), placed.seat());
    }
    /**
     * Which way the bed she is in points, or {@code null} if that cannot be established.
     * <p>
     * Asked of the block rather than of her, because her own facing while asleep is not the bed's
     * and the game lays her out by the bed. A player knows where she is sleeping; a villager does
     * not, and keeps it as the home she remembers instead. Either way it ends at a block, and the
     * block says which way it faces.
     */
    private org.bukkit.block.@Nullable BlockFace bedFacing(@NotNull LivingEntity player) {
        try {
            // A bed that was never built. Somebody lying on a lawn is lying in a bed all the same,
            // one their posing plugin invented at the bottom of the world and showed to nobody, and
            // the game lays them out along it exactly as it would a real one. There is no block here
            // to read — the block is a fiction sent straight to each client — so the plugin that
            // made it up is asked which way it made it point.
            LyingPlugins.Lying lying = LyingPlugins.of(player);
            if (lying != null) return lying.bed();

            org.bukkit.Location bed = null;

            if (player instanceof org.bukkit.entity.HumanEntity human && human.isSleeping()) {
                bed = human.getBedLocation();
            } else if (player instanceof Villager villager) {
                bed = villager.getMemory(org.bukkit.entity.memory.MemoryKey.HOME);
            }

            if (bed == null) return null;

            return bed.getBlock().getBlockData() instanceof org.bukkit.block.data.type.Bed data
                    ? data.getFacing()
                    : null;
        } catch (Exception ignored) {
            // Asking where somebody sleeps is not worth an exception on somebody's screen.
            return null;
        }
    }

    /**
     * Tells everyone watching a sleeping villager which bed she is in, again.
     * <p>
     * It is told once already, when she is first drawn for somebody, and once is not enough. A
     * villager is drawn on a client as a player, and a player lies along a bed only if the client
     * has been given a bed to lie along — a field a villager does not have and never sends, so the
     * only one that exists is the one this plugin puts there. A villager who was already on screen
     * when she lay down was never given it, and the client falls back to laying her out by her body
     * angle. Her figure is placed by the bed, so the two then disagree by up to a half turn and it
     * ends up standing across her rather than on her.
     * <p>
     * Sent on the pass her posture changes, which is also the only pass it can be noticed on: there
     * is no event for a villager going to bed, and the shape of her figure is worked out every pass
     * regardless. Nothing is sent for a player — hers is the client's own field and the client fills
     * it in.
     */
    private void bedded(@NotNull LivingEntity subject) {
        if (subject instanceof Player) return;
        if (!(subject instanceof Villager villager)) return;
        if (villager.getPose() != org.bukkit.entity.Pose.SLEEPING) return;

        org.bukkit.Location home = villager.getMemory(org.bukkit.entity.memory.MemoryKey.HOME);
        if (home == null) return;

        var pool = plugin.getTracker() == null ? null : plugin.getTracker().getPool();
        if (pool == null) return;

        pool.getNPC(villager.getEntityId()).ifPresent(npc -> {
            var where = new com.github.retrooper.packetevents.util.Vector3i(
                    home.getBlockX(), home.getBlockY(), home.getBlockZ());

            for (Player watcher : villager.getWorld().getPlayers()) {
                if (!watcher.canSee(villager)) continue;

                npc.metadata().queue(
                        me.matsubara.realisticvillagers.npc.modifier.MetadataModifier
                                .EntityMetadata.BED_POS, where).send(watcher);
            }
        });
    }

    /**
     * The posture a viewer actually sees her in, which the server does not always agree with.
     * <p>
     * Everything about where a figure goes is measured against the body somebody is looking at, and
     * for a player lying on the floor that body is usually not hers. The game has no lying-down to
     * offer, so the plugins that offer one lay a stand-in of her in a bed nobody can see and turn
     * the real woman invisible where she stands — and the server, asked what she is doing, answers
     * honestly that she is standing.
     * <p>
     * So the plugin doing it is asked first and the server second. Where nobody is posing anybody —
     * which is every villager and every player not lying on the floor — the two answers are the
     * same one and nothing about any of this applies.
     */
    private org.bukkit.entity.@NotNull Pose shownPose(@NotNull LivingEntity subject) {
        LyingPlugins.Lying lying = LyingPlugins.of(subject);
        return lying == null ? subject.getPose() : lying.pose();
    }

    /** Whether the game has her in a pose it draws lying down, which is what decides her seat. */
    private boolean lyingPose(@NotNull LivingEntity player) {
        return FigurePose.crossesFirstPersonCamera(shownPose(player), 0.0f);
    }

    private static final class Turn {
        int lastTick = -1;
        UUID world;
        float glided;
        float afloat;
        float lean;
        float ramp;
        float roll;
        double wasX;
        double wasZ;
        boolean seen;
    }

    private final Map<UUID, Turn> turns = new ConcurrentHashMap<>();
    private final Map<UUID, org.bukkit.entity.Pose> displayedPoses = new ConcurrentHashMap<>();

    /**
     * How many ticks pass between two of these, which is what every rate here is written in.
     * <p>
     * The game's own curves and the placement task both run once per tick. They are stepped
     * once per pass. Getting this wrong does not make the turn slower or faster in any
     * honest sense — it makes it a different curve from the one the body is following.
     */
    private static final float TICKS_PER_PASS = 1.0f;

    /** One player's tissue: where it is, how fast it is going, and how far into her stride she is. */
    /**
     * How many placement passes there are to one reading of her movement.
     * <p>
     * Two, because the settling was measured at that step and judged right there. Where she is
     * drawn is said every pass; how far she has swung is worked out on every second one and held
     * in between, which is what keeps the tuning honest while the placement got quicker.
     */
    private static final int PHYSICS_EVERY = 2;

    private static final class Spring {
        int lastTick = -1;
        double offset;
        double speed;
        double walked;
        int passes;
        float shown;
        double wasVertical;
        double wasX;
        double wasY;
        double wasZ;
        boolean started;
    }

    private final Map<UUID, Spring> springs = new ConcurrentHashMap<>();

    /**
     * The display this player is carrying, if it is still there and still hers.
     * <p>
     * Looked for among her passengers rather than by asking the world for an entity by id: a display
     * that came off her — because something else seated somebody, or the chunk went and came back —
     * is not one to reuse, and finding it any other way would leave it riding nothing.
     */
    private @Nullable ItemDisplay find(@NotNull LivingEntity player, @NotNull UUID id) {
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof ItemDisplay display
                    && display.getUniqueId().equals(id)
                    && display.isValid()) {
                return display;
            }
        }

        return null;
    }

    /**
     * How far she must have turned before the figure is turned after her, in degrees.
     * <p>
     * Below this the change is not worth a packet: a figure a degree out from her chest is a figure
     * nobody can tell is out, and sending one for every twitch of a mouse is how a cosmetic becomes
     * a bandwidth problem.
     */
    private static final float TURN = 1.5f;

    /**
     * Turns the figure to face the way she does.
     * <p>
     * By rotation, and never by teleport — that distinction is the whole reason this works. A
     * teleport carries a position, and a position sent to something riding her is a second opinion
     * about where it is; the two disagree, and the figure drops. A rotation packet carries a
     * position too, but harmlessly: the client puts a passenger back on its vehicle every tick,
     * after the rotation has landed, so the position in it never survives to be drawn.
     * <p>
     * That is the difference between this and every earlier attempt. The earlier ones sent rotations
     * to a display the client did not believe was riding anything, so there was nothing to put it
     * back and it stayed where the packet left it — on the ground it was spawned on.
     */
    public void follow(@NotNull LivingEntity player) {
        Carried current = carried.get(player.getUniqueId());
        if (current == null) return;

        ItemDisplay display = find(player, current.display());
        if (display == null) return;

        if (player instanceof Player watching) conceal(watching);

        float yaw = displayYaw(player);
        if (Math.abs(wrap(yaw - display.getLocation().getYaw())) < TURN) return;

        // Pitch is left flat on purpose. A chest does not tip when its owner looks at her feet, and
        // a figure that did would be the head-mounted version's fault all over again.
        display.setRotation(yaw, 0.0f);
    }

    /**
     * The yaw of the display's own coordinate system.
     * <p>
     * A real bed gives the renderer a cardinal direction, which already lives in
     * {@link FigurePose}; a yaw of 180 then only cancels the item's Y(180) basis conversion.
     * Commands such as {@code /lay} can put an entity in the sleeping pose without a bed. In that
     * case the client uses the entity's body rotation instead. Treating both cases as a real bed
     * left the figure circling the head while the body lay elsewhere.
     */
    private float displayYaw(@NotNull LivingEntity player) {
        if (shownPose(player) != org.bukkit.entity.Pose.SLEEPING) return bodyYaw(player);
        if (bedFacing(player) != null) return 180.0f;
        return 180.0f - bodyYaw(player);
    }

    /** The last body angle worked out for each player, for servers that cannot simply be asked. */
    private final Map<UUID, Body> bodies = new ConcurrentHashMap<>();

    /**
     * The way her chest is facing, which is not the way she is looking.
     * <p>
     * Those two come apart constantly and the difference is the whole of the complaint. Turning your
     * head does not turn your shoulders: the game lets a head swing about fifty degrees before the
     * body grudgingly follows, which is why a figure driven by where she is looking swings off her
     * chest every time she glances sideways.
     * <p>
     * Worked out here rather than asked of the server, and that is deliberate: the number a server
     * keeps for this varies by fork and did not move at all when strafing, which is exactly the case
     * that matters. The client's own rule is short enough to keep honestly — a body faces the way it
     * is travelling, not the way it is looking — so it is kept here, where it can be relied on.
     */
    private float bodyYaw(@NotNull LivingEntity player) {
        org.bukkit.Location at = player.getLocation();
        Body body = bodies.computeIfAbsent(player.getUniqueId(), id -> new Body(at));
        if (body.lastTick == player.getTicksLived()) return body.yaw;
        body.lastTick = player.getTicksLived();
        if (!at.getWorld().getUID().equals(body.world)
                || Math.hypot(at.getX() - body.wasX, at.getZ() - body.wasZ) > 12.0d) {
            body.yaw = at.getYaw();
            body.wasX = at.getX();
            body.wasZ = at.getZ();
            body.world = at.getWorld().getUID();
        }
        float head = at.getYaw();

        double dx = at.getX() - body.wasX;
        double dz = at.getZ() - body.wasZ;
        body.wasX = at.getX();
        body.wasZ = at.getZ();

        float target = body.yaw;

        // Flat, she goes where she looks, and the rule below stops helping and starts hurting.
        //
        // Two ways it fails once she is off her feet. Diving straight down there is almost no
        // sideways movement to read, so the rule holds whatever she was facing before and lets her
        // head come three quarters of a right angle round before dragging her chest after it — and
        // her chest is now a good block out in front of her, where being that far round is plain to
        // see. And what little sideways movement a dive does have is noise, which the rule below is
        // entitled to read as walking backwards and answer by spinning her a half turn, putting the
        // figure behind her and back again.
        //
        // Neither is a walking problem, and this is not a walking body. She is pointed the way she
        // is looking, which is what flying and swimming actually do.
        boolean flat = lyingPose(player) || lean(player) != 0.0f;

        if (flat) {
            // Snapped rather than eased, and that is the point of asking separately.
            //
            // The easing below is a body's shoulders coming round after its head, which is a real
            // thing a walking person does and is worth three tenths of a pass to draw. Flat it is
            // not a thing at all — she goes where she is pointed — and it is worth a great deal
            // more than it used to be, because her figure has swung out to a body's length from
            // the line it turns about. Three tenths of a turn behind, at that distance, is not a
            // figure lagging: it is a figure somewhere else.
            body.yaw = head;
            return body.yaw;
        }

        if (dx * dx + dz * dz > 0.0025d) {
            // Moved far enough this tick to say which way she is going. The threshold is the game's
            // own: below it a body is being nudged about rather than walking, and taking a direction
            // from it would have her chest twitching while she stands still.
            float travel = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0f;

            // Walking backwards is still walking forwards, seen from behind. Without this she
            // would spin to face her own footprints every time she stepped back from a chest.
            target = Math.abs(wrap(travel - head)) > 95.0f ? travel - 180.0f : travel;
        }

        // Eased rather than snapped, at the rate the game itself eases it, so the figure arrives
        // when her shoulders do rather than ahead of them.
        body.yaw += wrap(target - body.yaw) * 0.3f;

        // And a head can only be so far round before it takes the shoulders with it. This is what
        // keeps her chest still while she merely glances sideways.
        float off = wrap(head - body.yaw);
        if (Math.abs(off) > SHOULDER) body.yaw = head - Math.signum(off) * SHOULDER;

        return body.yaw;
    }

    /** One body's facing, and where it was last seen, which is what says which way it is going. */
    private static final class Body {
        int lastTick = -1;
        UUID world;
        float yaw;
        double wasX;
        double wasZ;

        Body(org.bukkit.Location at) {
            this.yaw = at.getYaw();
            this.wasX = at.getX();
            this.wasZ = at.getZ();
            this.world = at.getWorld().getUID();
        }
    }

    /**
     * How far a head may be turned from its own shoulders before it starts dragging them, in
     * degrees.
     * <p>
     * The game's number, not a guess. It was guessed at forty-five to begin with, and that guess
     * was visible: walking sideways turned the chest only two thirds of the way round, so the
     * figure sat at an angle to the body it belongs to.
     */
    private static final float SHOULDER = 75.0f;

    /**
     * The angle past which her own figure is taken off her screen, and the one it comes back at.
     * <p>
     * Worked out rather than picked. Her eyes are 1.62 blocks up, the figure sits about 1.31 up and
     * a third of a block in front, so it lies roughly forty-three degrees below where she is
     * looking — and it cannot be in shot until she looks at least that far down.
     * <p>
     * The first attempt used thirty-two, guessed at, and that guess was quietly ruining the whole
     * thing: thirty-two degrees is what you look down to mine, to take a stair, to check your feet.
     * Her figure was vanishing during ordinary play, in third person as well, which reads as the
     * feature being broken rather than as a threshold being wrong.
     * <p>
     * Two angles rather than one, so a head resting exactly on the line does not blink the figure in
     * and out twenty times a second.
     */
    private static final float LOOK_DOWN_HIDE = 50.0f;
    private static final float LOOK_DOWN_SHOW = 42.0f;

    /** Who currently cannot see her own figure, so it is hidden and shown only on the change. */
    private final Set<UUID> concealed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Keeps her own figure out of her own first-person view, without taking it out of her third.
     * <p>
     * There is no honest way to ask which of the two she is in. Nothing the client sends says
     * anything about the camera — not the movement packets, not the settings packet, nothing — so a
     * server that wants to behave differently in first person has to work out that it is in one.
     * <p>
     * What it can see is where she is looking. Her figure sits below her eyes, so in first person it
     * is out of frame until she looks down at it; in third the camera is behind her and looking down
     * simply tilts the view. So looking well down is taken as the moment to take it away, and it
     * comes back as soon as she looks up again.
     * <p>
     * What this used to cost is no longer charged. Hiding was done by making the figure nothing,
     * and a size is something the whole server is told — so looking at her own feet took her figure
     * away from everybody watching her. It is now sent to her client alone, so what she cannot see
     * and what nobody can see are finally two different things.
     */
    private void conceal(@NotNull Player player) {
        // Flight, swimming and the transition out of swimming place the torso directly in front
        // of the first-person camera. The server cannot read the camera mode, so the owner does
        // not receive her figure for the duration of the flat pose. Every other viewer still gets
        // the real scale from the server.
        if (FigurePose.crossesFirstPersonCamera(shownPose(player), lean(player))) {
            concealed.add(player.getUniqueId());
            return;
        }

        float pitch = player.getLocation().getPitch();

        if (pitch > LOOK_DOWN_HIDE) concealed.add(player.getUniqueId());
        else if (pitch < LOOK_DOWN_SHOW) concealed.remove(player.getUniqueId());
    }

    /** Degrees brought back into -180..180, so turning past south is not turning the long way. */
    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    /**
     * Whether a display of ours is genuinely riding her right now.
     * <p>
     * Asked of her passengers rather than of this class's own notes, because those two can disagree
     * — and when they do, the passengers are the ones the player can see.
     */
    public boolean isCarrying(@NotNull LivingEntity player) {
        Carried current = carried.get(player.getUniqueId());
        return current != null && find(player, current.display()) != null;
    }

    /**
     * Takes away any figure that is no longer riding anybody, and says how many there were.
     * <p>
     * A safety net rather than the plan. Death is handled where death happens and leaving where
     * leaving happens, but a figure is a separate entity from the body carrying it and there is
     * always one more way for the two to come apart than has been thought of — a chunk that
     * unloaded oddly, a plugin that moved somebody, a death this never heard about. The cost of
     * missing one is a piece of somebody standing in a field forever, which is worse than the cost
     * of looking.
     * <p>
     * Riding nothing is the whole test, and it is exact: these are seated the same tick they are
     * spawned, so one with no vehicle was orphaned rather than caught halfway through being born.
     */
    public int sweep() {
        int taken = 0;

        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) continue;
                if (display.getVehicle() != null) continue;

                display.remove();
                taken++;
            }
        }

        return taken;
    }

    /**
     * The display she was given, wherever it has ended up.
     * <p>
     * Looked up by name rather than among her passengers, because the moments that matter most
     * are the ones where it is no longer riding her. Dying throws a rider off, and so does being
     * put into spectator: by the time anything asks, her passenger list is empty and the figure is
     * standing in the grass at the spot she vanished from, which is precisely when something has
     * to be able to reach it. Asking her passengers there found nothing and left it there.
     */
    private @Nullable ItemDisplay display(@NotNull UUID id) {
        Entity entity = plugin.getServer().getEntity(id);
        return entity instanceof ItemDisplay display && display.isValid() ? display : null;
    }

    /**
     * Takes every figure off this viewer's screen, and leaves everyone else's alone.
     * <p>
     * For a client that has not got the pack the figures are made of. It cannot draw them, and
     * what it draws instead — the placeholder it uses for any model it cannot find — is worse
     * than seeing nothing at all, because it looks like the plugin's work rather than like a
     * missing download. Per viewer because it is a property of their client, not of the villager:
     * everyone else standing there still sees her as she is.
     */
    public void hideFrom(@NotNull Player viewer) {
        forEachDisplay(display -> viewer.hideEntity(plugin, display));
    }

    /** Gives them back, for a client that has just finished loading the pack. */
    public void showTo(@NotNull Player viewer) {
        forEachDisplay(display -> viewer.showEntity(plugin, display));
    }

    private void forEachDisplay(@NotNull java.util.function.Consumer<ItemDisplay> action) {
        for (Carried current : carried.values()) {
            ItemDisplay display = display(current.display());
            if (display == null) continue;

            try {
                action.accept(display);
            } catch (Throwable ignored) {
                // One figure that could not be hidden is one figure drawn oddly, not a broken pass.
            }
        }
    }

    /** Whether this viewer's client can draw the figures at all. */
    private boolean draws(@NotNull Player viewer) {
        var pack = plugin.getPackServer();

        // Nothing is being served from here, so nothing is known about what they hold: the server
        // may well be handing the pack out itself, and guessing "no" would take the figures away
        // from a setup that works.
        return pack == null || pack.hasPack(viewer);
    }

    /** Takes her figure away, for a player who has left or who should no longer have one. */
    public void remove(@NotNull LivingEntity player) {
        concealed.remove(player.getUniqueId());
        bodies.remove(player.getUniqueId());
        springs.remove(player.getUniqueId());
        turns.remove(player.getUniqueId());
        displayedPoses.remove(player.getUniqueId());
        blinded.remove(player.getUniqueId());
        LyingPlugins.forget(player.getUniqueId());

        Carried current = carried.remove(player.getUniqueId());
        if (current == null) return;

        ItemDisplay display = find(player, current.display());
        if (display == null) display = display(current.display());
        if (display != null) display.remove();
    }

    /**
     * Clears every figure, for a plugin being disabled.
     * <p>
     * Displays are not saved, so a full restart would lose them anyway — but a reload is not a
     * restart, and one left behind by a reload would ride her with nothing left to look after it.
     */
    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (Entity passenger : player.getPassengers()) {
                if (passenger instanceof ItemDisplay display
                        && display.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }

        carried.clear();
    }

    private boolean isBuild(@NotNull ItemStack figure) {
        String variant = variantOf(figure);
        return variant != null && variant.startsWith(BodyShape.BUILD.id() + "_s");
    }

    private @Nullable String variantOf(@NotNull ItemStack figure) {
        var meta = figure.getItemMeta();
        return meta == null || meta.getItemModel() == null ? null : meta.getItemModel().getKey();
    }
}
