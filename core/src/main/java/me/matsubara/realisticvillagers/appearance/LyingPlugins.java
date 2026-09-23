package me.matsubara.realisticvillagers.appearance;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The posture another plugin has put a player in, asked of that plugin rather than of the server.
 * <p>
 * A player lying on the floor is almost never lying on the floor. The game has no such posture to
 * give out, so every plugin that offers one — GSit is the one nearly everybody runs — builds it out
 * of a bed: it lays a stand-in of her in a bed nobody can see, at the bottom of the world, and turns
 * the real woman invisible where she stands. What a viewer is looking at is not her.
 * <p>
 * That matters here and almost nowhere else. Everything this package does is placed against the body
 * the game draws, and if the body the game draws is somewhere other than where the server says she
 * is, a figure placed by the server's answer stands upright in mid-air while the woman it belongs to
 * lies on the grass beneath it. The server is not wrong; it is answering a different question.
 * <p>
 * So the question is put to the plugin instead. What comes back is the posture she is <em>drawn</em>
 * in and, when that posture is a bed, which way the bed it invented points. Both come off that
 * plugin's own published interface — the class it tells other plugins to call — so neither depends
 * on how it happens to be built inside.
 * <p>
 * Where she is drawn is barely asked at all, and that is the piece of luck this rests on: the
 * stand-in goes at her own feet, off by a fixed fraction of a block that depends only on which
 * posture it is. Two such fractions are written down here, and they are the one thing in this file
 * that is copied rather than asked for. They are worth copying because they are small — a pixel or
 * three — which is also why being wrong about one would cost less than leaving it out.
 * <p>
 * Everything else needs no measuring. Her figure goes on riding the real woman, which is what keeps
 * it in step with her frame for frame, and simply believes what it is told about the shape she is
 * in.
 * <p>
 * Nothing here is a dependency. If the plugin is absent — or present and changed — every question
 * comes back unanswered and the server's own answer stands, which is the right answer anyway for
 * everyone who is not being posed by anybody.
 */
final class LyingPlugins {

    private LyingPlugins() {}

    /**
     * A posture somebody else is drawing.
     *
     * @param pose the posture a viewer sees, which the game itself would deny she is in.
     * @param bed  which way the bed she was laid in points, or {@code null} if there is no bed —
     *             face down and spinning are drawn without one, and the game asks for none.
     * @param lift how far above her feet the stand-in's own feet are, per unit of her size.
     */
    record Lying(@NotNull Pose pose, @Nullable BlockFace bed, float lift) {}

    /**
     * The stand-in is not put exactly where she is, and how far out depends on the posture.
     * <p>
     * A body laid on its back sits a little higher than a body standing on the same spot, and one
     * dropped on its front a good deal lower; the plugin nudges its stand-in by a fixed amount for
     * each so the result rests on the ground rather than in it. None of that reaches the server —
     * the stand-in is a packet — so the same nudge is applied to the figure, which rides the real
     * woman and would otherwise sit a pixel or three off the body it belongs to.
     * <p>
     * Small enough that being wrong about them costs less than ignoring them, which is the whole
     * reason they are worth copying: without the first the figure floats about two pixels over a
     * sleeper, and without the second it hangs four over someone lying face down.
     */
    private static float liftOf(@NotNull Pose drawn) {
        return switch (drawn) {
            case SLEEPING -> 0.1125f;
            case SWIMMING -> -0.19f;
            default -> 0.0f;
        };
    }

    /**
     * What is being done to her, or {@code null} if the answer is simply "nothing".
     * <p>
     * The answer is kept for as long as one posture lasts. Not to save the calls — they are two —
     * but because the plugin works its bed out once, when she lies down, and never again. Asking it
     * afresh every tick would mean reading a seat that may since have been turned and quietly
     * disagreeing with the bed that is actually down there.
     */
    static @Nullable Lying of(@NotNull LivingEntity subject) {
        if (!(subject instanceof Player player)) return null;

        GSit gsit = GSit.get();
        if (gsit == null) return null;

        Object posture = gsit.postureOf(player);
        if (posture == null) {
            KNOWN.remove(player.getUniqueId());
            return null;
        }

        Held held = KNOWN.get(player.getUniqueId());
        if (held != null && held.posture() == posture) return held.lying();

        Lying lying = gsit.read(posture);
        if (lying == null) {
            KNOWN.remove(player.getUniqueId());
            return null;
        }

        KNOWN.put(player.getUniqueId(), new Held(posture, lying));
        return lying;
    }

    /** Forgets one player, for somebody who has left. */
    static void forget(@NotNull UUID player) {
        KNOWN.remove(player);
    }

    /**
     * One posture and what was read off it.
     * <p>
     * The posture is held to be compared by identity, never called: a new one means she lay down
     * again and the reading has to be taken again.
     */
    private record Held(@NotNull Object posture, @NotNull Lying lying) {}

    private static final Map<UUID, Held> KNOWN = new ConcurrentHashMap<>();

    /**
     * GSit's published interface, looked up once.
     * <p>
     * By name rather than by import, so this compiles and runs on a server that has never heard of
     * it. Everything named here is public API of that plugin — the class it tells other plugins to
     * call, and the four questions it offers — so there is nothing in this that depends on how it
     * happens to be built.
     */
    private static final class GSit {

        private final Method posing;
        private final Method postureOf;
        private final Method typeOf;
        private final Method drawnAs;
        private final Method seatOf;
        private final Method whereSat;

        private GSit(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> api = Class.forName("dev.geco.gsit.api.GSitAPI", false, loader);
            Class<?> posture = Class.forName("dev.geco.gsit.model.Pose", false, loader);
            Class<?> type = Class.forName("dev.geco.gsit.model.PoseType", false, loader);
            Class<?> seat = Class.forName("dev.geco.gsit.model.Seat", false, loader);

            this.posing = api.getMethod("isPlayerPosing", Player.class);
            this.postureOf = api.getMethod("getPoseByPlayer", Player.class);
            this.typeOf = posture.getMethod("getPoseType");
            this.drawnAs = type.getMethod("getPlayerPose");
            this.seatOf = posture.getMethod("getSeat");
            this.whereSat = seat.getMethod("getLocation");
        }

        private @Nullable Object postureOf(@NotNull Player player) {
            try {
                if (!Boolean.TRUE.equals(posing.invoke(null, player))) return null;
                return postureOf.invoke(null, player);
            } catch (Throwable throwable) {
                return null;
            }
        }

        private @Nullable Lying read(@NotNull Object posture) {
            try {
                Object type = typeOf.invoke(posture);
                if (type == null) return null;

                if (!(drawnAs.invoke(type) instanceof Pose drawn)) return null;

                // A bed is only ever invented for the postures the game draws lying in one. Face
                // down is drawn as swimming and spinning as a riptide, and the renderer does not
                // look for a bed in either — inventing one here would turn a body that is being
                // laid out by its own facing.
                if (drawn != Pose.SLEEPING) return new Lying(drawn, null, liftOf(drawn));

                Object seat = seatOf.invoke(posture);
                if (seat == null || !(whereSat.invoke(seat) instanceof Location where)) {
                    return new Lying(drawn, null, liftOf(drawn));
                }

                return new Lying(drawn, bedOf(where.getYaw()), liftOf(drawn));
            } catch (Throwable throwable) {
                return null;
            }
        }

        private static @Nullable GSit get() {
            if (LOOKED) return FOUND;

            synchronized (GSit.class) {
                if (LOOKED) return FOUND;

                try {
                    FOUND = new GSit(LyingPlugins.class.getClassLoader());
                } catch (Throwable throwable) {
                    // Not installed, or installed and no longer the plugin this was written
                    // against. Either way there is nothing to ask and the server's own answer is
                    // the one that stands.
                    FOUND = null;
                }

                LOOKED = true;
                return FOUND;
            }
        }

        private static volatile boolean LOOKED;
        private static volatile GSit FOUND;
    }

    /**
     * Which way the invented bed points, from the angle she was facing when she lay down.
     * <p>
     * The rule is the posing plugin's, written out here because a rule cannot be asked for — only
     * its answer, and its answer is not offered. It is four quadrants about the compass and then the
     * opposite one, which is why the head of the bed ends up behind her and her feet point the way
     * she was looking.
     * <p>
     * Being the same rule is the whole of the requirement. What it must agree with is the block that
     * plugin actually put under her, because that block is what the game reads to decide which way
     * to lay her out — and a figure laid out by a different bed from the body is a figure lying
     * across it.
     */
    private static @NotNull BlockFace bedOf(float yaw) {
        BlockFace facing;

        if (yaw >= 135.0f || yaw < -135.0f) facing = BlockFace.NORTH;
        else if (yaw < -45.0f) facing = BlockFace.EAST;
        else if (yaw < 45.0f) facing = BlockFace.SOUTH;
        else facing = BlockFace.WEST;

        return facing.getOppositeFace();
    }
}
