package me.matsubara.realisticvillagers.village;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * A candidate's platform: one thing the settlement gains under them, and one it pays for.
 * <p>
 * Every program is deliberately a trade-off, so no candidate is strictly the best pick and
 * the vote is an actual decision. Multipliers are applied to the base value, so {@code 1.0}
 * means "unchanged", below {@code 1.0} is cheaper/faster and above is dearer/slower.
 */
public enum ElectoralProgram {

    /**
     * Cheap goods at the market, paid for with dearer and slower construction.
     * <p>
     * Every platform below is a genuine trade-off, and none of them is good at everything: a
     * program that lowered prices, cut costs, built quickly <i>and</i> filled the stores would
     * make the ballot a formality, because there would be nothing to weigh against it.
     */
    MERCHANT_FRIENDLY("merchant-friendly", 0.75d, 1.25d, 1.25d, 0.90d),

    /** Cheap construction, paid for at the trading stalls and in the fields. */
    BUILDER_FRIENDLY("builder-friendly", 1.30d, 0.70d, 1.00d, 0.85d),

    /** Everything gets built quickly, but haste costs materials and burns out the workers. */
    AMBITIOUS("ambitious", 1.15d, 1.35d, 0.60d, 0.80d),

    /** A tight-fisted administration: materials are saved by taking its time over everything. */
    FRUGAL("frugal", 1.00d, 0.75d, 1.45d, 0.90d),

    /** Popular at the stalls and on the site, and slow about absolutely everything. */
    POPULIST("populist", 0.80d, 0.85d, 1.60d, 0.70d),

    /** Everyone into the fields and the mines: the stores fill fast, and nothing else moves. */
    INDUSTRIOUS("industrious", 1.25d, 1.15d, 1.30d, 1.70d),

    /**
     * A quiet administration that lives off what it already has.
     * <p>
     * Cheap to trade with and quick to build — and it pays for both twice over, in materials and
     * in a workforce that brings in barely half of what it should. An easy platform to like until
     * the stores run dry, which is the trap it is meant to be.
     */
    LEISURELY("leisurely", 0.85d, 1.30d, 0.80d, 0.45d),

    /** Stores are guarded rather than worked: dear to trade with, but nothing is wasted. */
    STEWARD("steward", 1.20d, 0.70d, 1.15d, 1.15d);

    private final String key;
    private final double tradePriceMultiplier;
    private final double buildCostMultiplier;
    private final double buildTimeMultiplier;
    private final double harvestMultiplier;

    private static final Random RANDOM = new Random();

    ElectoralProgram(
            String key,
            double tradePriceMultiplier,
            double buildCostMultiplier,
            double buildTimeMultiplier,
            double harvestMultiplier) {
        this.key = key;
        this.tradePriceMultiplier = tradePriceMultiplier;
        this.buildCostMultiplier = buildCostMultiplier;
        this.buildTimeMultiplier = buildTimeMultiplier;
        this.harvestMultiplier = harvestMultiplier;
    }

    /** Config/message key for this program, e.g. {@code builder-friendly}. */
    public String getKey() {
        return key;
    }

    /** Applied to villager trade prices while this program's candidate governs. */
    public double getTradePriceMultiplier() {
        return tradePriceMultiplier;
    }

    /** Applied to the material cost of commissioned buildings. */
    public double getBuildCostMultiplier() {
        return buildCostMultiplier;
    }

    /** Applied to how long commissioned buildings take to raise. */
    public double getBuildTimeMultiplier() {
        return buildTimeMultiplier;
    }

    /**
     * Applied to how much the settlement's workers bring in.
     * <p>
     * The one multiplier a player can watch take effect without commissioning anything: the
     * stores visibly fill faster or slower under a new mayor, which is what makes an election
     * feel like it decided something.
     */
    public double getHarvestMultiplier() {
        return harvestMultiplier;
    }

    public static @NotNull ElectoralProgram random() {
        ElectoralProgram[] values = values();
        return values[RANDOM.nextInt(values.length)];
    }

    /**
     * Picks a program that isn't already taken, so the ballot never offers two identical
     * platforms. Falls back to a random one once every program is in use.
     */
    public static @NotNull ElectoralProgram randomExcluding(@NotNull java.util.Collection<ElectoralProgram> taken) {
        ElectoralProgram[] values = values();
        if (taken.size() >= values.length) return random();

        ElectoralProgram candidate;
        do {
            candidate = values[RANDOM.nextInt(values.length)];
        } while (taken.contains(candidate));

        return candidate;
    }

    public static @Nullable ElectoralProgram byKey(@Nullable String key) {
        if (key == null) return null;
        for (ElectoralProgram program : values()) {
            if (program.key.equalsIgnoreCase(key)) return program;
        }
        return null;
    }
}
