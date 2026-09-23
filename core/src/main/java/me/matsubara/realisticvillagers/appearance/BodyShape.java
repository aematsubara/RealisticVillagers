package me.matsubara.realisticvillagers.appearance;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A shape added to a villager's body, worked out rather than drawn.
 * <p>
 * This is the one piece both halves of the trick share. A resource pack model carries geometry but
 * its colours are fixed; an item's {@code custom_model_data} carries colours but no geometry.
 * Putting a villager's own skin on a shape nobody modelled means marrying the two — the pack holds
 * the shape with every face tinted from a list, and each villager supplies that list out of her own
 * skin. So the same walk over the same cells has to produce the pack's faces and, later, the
 * colours, which is why both come from here and nowhere else.
 * <p>
 * Every shape is a field of raised cosines over the body's own pixels. Changing a number here
 * changes what villagers look like; nothing is drawn by hand and nothing is baked into a file.
 */
public record BodyShape(
        @NotNull String id,
        int skinU,
        int skinV,
        int width,
        int height,
        double maxDepth,
        double @NotNull [] centres,
        double reach,
        double fullestRow,
        double rowReach,
        double lowerReach) {

    /**
     * Cells shallower than this are dropped: they would be a surface fighting the body's own.
     * <p>
     * Lower than it was, and it had to come down when the shapes did. This is an absolute depth in
     * skin pixels, so making every shape shallower to stop it reading as anatomy would also have
     * quietly eaten its outline — the shallow rim is exactly the part a softer shape has more of.
     * Cutting the threshold by the same proportion as the depths keeps the silhouette the
     * arithmetic asked for rather than trimming it as a side effect.
     */
    private static final double MIN_DEPTH = 0.10d;

    /**
     * The same test again, for the quarter-cells the pack builds its geometry from.
     * <p>
     * Half of it, because a cell is kept when any of its four corners is worth keeping, and its own
     * corners must not then be thrown away by a stricter rule than the one that kept the cell.
     */
    public static double minimumDepth() {
        return MIN_DEPTH * 0.5d;
    }

    /**
     * One cell: a small box standing out of the body, wearing one pixel of the skin.
     *
     * @param tint its place in the colour list, and so in the pack's {@code tintindex}.
     */
    public record Cell(int u, int v, double depth, int tint) {}

    /**
     * The busts a villager can be born with.
     * <p>
     * Four rather than one because size alone reads as the same woman photographed nearer and
     * further away. What actually differs between people is placement and spread — how far apart,
     * how high, how sharply it falls away — so those are what vary here, and size is applied on top
     * of whichever she got.
     * <p>
     * Every one of them is wider and shallower than it first was, and that is the answer to the
     * only complaint they ever drew: they looked too real. Depth is what made them read that way. A
     * shape standing two and a half pixels out of a torso four pixels thick is an anatomical study,
     * and no amount of care about where it sits changes that — the game draws a person out of eight
     * columns and twelve rows and expects a body to be suggested rather than modelled. So the
     * projection is down by about a third and the spread is up to meet it, which reads as a woman
     * at a glance and as part of the same drawing as the rest of her up close.
     */
    public static final List<BodyShape> BUSTS = List.of(
            // Round and average: the middle of the road, and the most common by weight of numbers.
            new BodyShape("bust_round", 20, 21, 8, 6, 1.4d,
                    new double[]{2.0d, 6.0d}, 2.9d, 3.0d, 3.2d, 0.82d),

            // Wider set and shallower — broader chest, less projection.
            new BodyShape("bust_wide", 20, 21, 8, 6, 1.15d,
                    new double[]{1.8d, 6.2d}, 3.1d, 3.0d, 3.2d, 0.82d),

            // Small and high, sitting close under the collar.
            new BodyShape("bust_small", 20, 21, 8, 5, 0.95d,
                    new double[]{2.1d, 5.9d}, 2.7d, 2.4d, 2.6d, 0.82d),

            // Full and heavy, reaching lower down the chest.
            new BodyShape("bust_full", 20, 21, 8, 7, 1.85d,
                    new double[]{2.0d, 6.0d}, 3.1d, 3.5d, 3.8d, 0.86d));

    /**
     * The torso a heavier villager carries.
     * <p>
     * Only ever added, never taken away, and that is a limit of the game rather than a choice: a
     * player's body is a fixed box and nothing can carve it thinner. So the slight villager is the
     * bare model and everyone heavier is that model plus this — which is the right way round
     * anyway, since it is weight that shows and thinness that is merely its absence.
     * <p>
     * Six rows from 25, so it stops one short of the hem. It used to run all seven to the waistline
     * and end there still half its depth, which is not a stomach: it is a shelf, and the legs came
     * out from under it. A belly is the one shape whose lower half matters more than its upper —
     * it has to tuck back in — so it reaches barely three fifths as far below its fullest row as
     * above, and finishes inside its own rows instead of at the edge of them.
     */
    public static final BodyShape BUILD = new BodyShape("build", 20, 25, 8, 6, 1.3d,
            new double[]{4.0d}, 5.0d, 3.3d, 3.8d, 0.62d);

    /** Everything the pack has to contain. */
    public static @NotNull List<BodyShape> all() {
        List<BodyShape> shapes = new ArrayList<>(BUSTS);
        shapes.add(BUILD);
        return shapes;
    }

    /**
     * How many sizes each shape is generated at.
     * <p>
     * A worn item carries no per-villager transform — what the pack says is what everyone gets — so
     * size has to be baked, one model per step. Five is enough that neighbours differ and few enough
     * that the pack stays a few dozen kilobytes.
     */
    public static final int SIZES = 5;

    /** The scale a given step stands for, from the smallest to the largest. */
    public static double sizeAt(int step) {
        return 0.6d + 0.9d * step / (double) (SIZES - 1);
    }

    /** The step a size falls in, so a villager and the pack agree on which model is hers. */
    public static int stepOf(double size) {
        int step = (int) Math.round((size - 0.6d) / 0.9d * (SIZES - 1));
        return Math.max(0, Math.min(SIZES - 1, step));
    }

    /** The name of one generated variant: the shape, its size, and whether it clears armour. */
    public @NotNull String variant(int step, boolean armoured) {
        return id() + "_s" + step + (armoured ? "_a" : "");
    }

    /**
     * Walks the shape and returns its cells.
     * <p>
     * The order is fixed — rows then columns — because it is also the order of the colour list. A
     * cell's tint is its position in that walk, so the pack and the villager agree without either
     * having to store a mapping between them.
     */
    public @NotNull List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();

        int tint = 0;
        for (int v = 0; v < height; v++) {
            for (int u = 0; u < width; u++) {
                double depth = depthAt(u, v);
                // Retain a pixel if any of its four geometry samples is visible. Colours
                // stay one per skin pixel even though the silhouette uses half-pixel cells.
                double visible = depth;
                for (int sy = 0; sy < 2; sy++) {
                    for (int sx = 0; sx < 2; sx++) {
                        visible = Math.max(visible, depthAt(u + 0.25d + sx * 0.5d, v + 0.25d + sy * 0.5d));
                    }
                }
                if (visible < MIN_DEPTH) continue;

                cells.add(new Cell(u, v, depth, tint++));
            }
        }

        return cells;
    }

    /**
     * How far the body stands out at this cell.
     * <p>
     * An ellipse in the body's own pixels says how far this cell is from the shape's middle, and one
     * curve turns that distance into a depth. Everything after it is a fade at an edge the ellipse
     * knows nothing about — the arm, the collar, the waist, the line between two lobes.
     */
    private double depthAt(int u, int v) {
        return depthAt(u + 0.5d, v + 0.5d);
    }

    /** Continuous surface, sampled independently of the skin's pixel resolution. */
    double depthAt(double x, double y) {
        if (x <= 0.0d || x >= width || y <= 0.0d || y >= height) return 0.0d;

        double nearest = Double.MAX_VALUE;
        for (double centre : centres) nearest = Math.min(nearest, Math.abs(x - centre));

        // Every shape reaches further above its fullest row than below it, and every shape wants
        // that for its own reason. A bust keeps its volume below the collar; a belly rises out of
        // the ribs over most of the torso and must be back at the body by the waistband, which is
        // the same rule with a harder number. It used to apply to busts alone, and a stomach still
        // at half depth on its last row is exactly what that omission looked like.
        double verticalReach = y > fullestRow
                ? Math.min(rowReach * lowerReach, height - fullestRow)
                : Math.min(rowReach, fullestRow);
        double rows = Math.abs(y - fullestRow);

        double radius = nearest * nearest / (reach * reach)
                + rows * rows / (verticalReach * verticalReach);
        if (radius >= 1.0d) return 0.0d;

        // A raised cosine rather than the front half of an ellipsoid.
        //
        // The ellipsoid is the honest shape of a ball and that is precisely what was wrong with it.
        // It leaves the skin at a right angle and comes to a full, separate curve, so each lobe read
        // as a body part sitting on a body rather than as part of one — which is the whole of "too
        // realistic". A raised cosine is flat where it meets the torso and flat again at its
        // fullest, so the same volume arrives as one soft swell with no rim to catch the light.
        //
        // A quarter of the old curve is kept back all the same. Pure cosine has no middle worth the
        // name and comes out as a smear; the paraboloid under it is what keeps a chest from reading
        // as a slab, while the cosine keeps it from reading as a pair of spheres.
        double distance = Math.sqrt(radius);
        double dome = 0.74d * 0.5d * (1.0d + Math.cos(Math.PI * distance))
                + 0.26d * (1.0d - radius);

        // A hint of a division rather than a cleft.
        //
        // It used to cut to little over a quarter depth across three quarters of a pixel, which at
        // this scale is a hard dark line down the middle of a chest — the single most anatomical
        // thing in the whole shape. Now it is a shallow dip spread over two pixels: enough that two
        // lobes can be told apart, not enough to draw a groove nobody asked to see.
        double separation = 1.0d;
        if (centres.length > 1) {
            double middle = (centres[0] + centres[centres.length - 1]) * 0.5d;
            double away = Math.min(1.0d, Math.abs(x - middle) / 1.9d);
            double eased = away * away * (3.0d - 2.0d * away);
            separation = 0.76d + 0.24d * eased;
        }

        // Taper at the torso edges instead of ending a full-depth column at the arm.
        double edge = Math.min(1.0d, Math.min(x, width - x) / 0.9d);
        edge = edge * edge * (3.0d - 2.0d * edge);

        // And the same at the top and bottom rows, for every shape rather than for busts alone. A
        // curve still a third of the way out on the row where it runs out of body does not end, it
        // stops — under a collar that is a ledge, and above a waistband it is the shelf the belly
        // used to be.
        double margin = Math.min(1.0d, Math.min(y, height - y) / 0.9d);
        double root = margin * margin * (3.0d - 2.0d * margin);

        return maxDepth * dome * separation * edge * root;
    }

    /** The skin pixel a cell wears, as {x, y} into a 64×64 skin. */
    public int[] skinPixel(@NotNull Cell cell) {
        return new int[]{skinU + cell.u(), skinV + cell.v()};
    }

    /** How many faces this shape has, and so how long a villager's colour list must be. */
    public int tints() {
        return cells().size();
    }
}
