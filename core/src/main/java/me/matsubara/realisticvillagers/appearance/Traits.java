package me.matsubara.realisticvillagers.appearance;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a villager looks like, worked out from who she is.
 * <p>
 * Derived from the UUID rather than stored. A villager is the same every time the server starts,
 * on every server, without a line of configuration or a byte on disk — and two villagers standing
 * together differ because their ids do, not because something rolled dice at spawn and then had to
 * remember the answer.
 * <p>
 * Each trait reads its own slice of the id through its own salt, so changing the range of one never
 * shifts another, and no two traits are secretly the same number wearing different names.
 *
 * @param height how tall, as a multiple of ordinary.
 * @param build  how heavy, 0 for slight and 1 for stout.
 * @param bust   size of the bust, 0 when there is none.
 * @param bustShape which of the modelled busts she has, or {@code null} for none.
 */
public record Traits(double height, double build, double bust, @Nullable BodyShape bustShape) {

    /**
     * How far height may stray from ordinary, either way.
     * <p>
     * Eight percent is a good five centimetres on a person: plain to see standing side by side, and
     * never so odd that a villager reads as a different creature.
     */
    private static final double HEIGHT_SPREAD = 0.08d;

    /** Smallest and largest bust, as a multiple of the modelled shape. */
    private static final double BUST_MIN = 0.6d;
    private static final double BUST_MAX = 1.5d;

    /**
     * Below this, a villager is simply slight and carries no extra shape at all.
     * <p>
     * A body cannot be carved thinner than the model the game draws, so the slight end has to be
     * the bare body. Everyone above this line gets weight added instead.
     */
    private static final double BUILD_FLOOR = 0.35d;

    private static final long SALT_HEIGHT = 0x9E3779B97F4A7C15L;
    private static final long SALT_BUILD = 0x165667B19E3779F9L;
    private static final long SALT_BUST = 0xC2B2AE3D27D4EB4FL;
    private static final long SALT_SHAPE = 0x27D4EB2F165667C5L;

    public static @NotNull Traits of(@NotNull UUID id, boolean female) {
        return of(id, female, 0);
    }

    /**
     * The same, but drawn again from scratch.
     * <p>
     * A villager is born once and is whoever she is; her id is the only thing her body needs to come
     * from, and that is why none of this is stored anywhere. A player is not born — she decides she
     * is a woman, and may decide it again tomorrow, and the answer being fixed to an account would
     * mean the only way to a different one was a different account.
     * <p>
     * So a count of how many times she has changed her mind is folded in with the salts. Nothing is
     * random and nothing is written down: the same id and the same count always give the same woman.
     *
     * @param roll how many times this has been drawn before. Zero is the first, and is what a
     *             villager always passes, so villagers are untouched by any of this.
     */
    public static @NotNull Traits of(@NotNull UUID id, boolean female, int roll) {
        long twist = roll * 0x9E3779B97F4A7C15L;

        double height = 1.0d + HEIGHT_SPREAD * signed(id, SALT_HEIGHT + twist);

        // Build runs from slight to stout across everyone; the lower part of the range simply
        // means "no extra shape", which is what being slight looks like.
        double rawBuild = unit(id, SALT_BUILD + twist);
        double build = rawBuild < BUILD_FLOOR ? 0.0d : (rawBuild - BUILD_FLOOR) / (1.0d - BUILD_FLOOR);

        if (!female) return new Traits(height, build, 0.0d, null);

        double bust = BUST_MIN + (BUST_MAX - BUST_MIN) * unit(id, SALT_BUST + twist);

        int which = (int) (unit(id, SALT_SHAPE + twist) * BodyShape.BUSTS.size());
        BodyShape shape = BodyShape.BUSTS.get(Math.min(which, BodyShape.BUSTS.size() - 1));

        return new Traits(height, build, bust, shape);
    }

    /** Whether she gets a bust at all. */
    public boolean hasBust() {
        return bustShape != null && bust > 0.01d;
    }

    /** Whether she carries enough weight for it to show. */
    public boolean hasBuild() {
        return build > 0.02d;
    }

    /**
     * A number from 0 to 1, spread evenly, from the id and a salt.
     * <p>
     * A round of avalanche first, so ids differing by one bit do not give near-identical traits —
     * which matters, because villagers born in the same village often have neighbouring ids.
     */
    private static double unit(@NotNull UUID id, long salt) {
        long mixed = id.getMostSignificantBits() * 31L + id.getLeastSignificantBits() + salt;

        mixed ^= (mixed >>> 33);
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xC4CEB9FE1A85EC53L;
        mixed ^= (mixed >>> 33);

        return (mixed >>> 11) / (double) (1L << 53);
    }

    /** The same, from -1 to 1, for traits that vary either side of ordinary. */
    private static double signed(@NotNull UUID id, long salt) {
        return unit(id, salt) * 2.0d - 1.0d;
    }
}
