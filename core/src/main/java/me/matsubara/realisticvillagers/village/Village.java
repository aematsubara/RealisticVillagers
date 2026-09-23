package me.matsubara.realisticvillagers.village;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A settlement: its identity, where it sits, and what it owns.
 * <p>
 * This is a data model only. Residents are intentionally <b>not</b> stored here — villagers
 * move, die and get replaced, so membership is resolved live from the world by
 * {@link VillageManager} instead of being cached and going stale.
 * <p>
 * The centre starts at the village bell (auto-detected) and can later be moved through the
 * mayor's menu; {@link #isCenterOverridden()} records which of the two is in effect so a
 * re-detection never silently undoes the player's choice.
 */
@Getter
public final class Village {

    private final UUID id;
    private final String worldName;

    /** Player-facing name, or {@code null} to fall back to a generated one. */
    private @Setter @Nullable String name;

    private int centerX;
    private int centerY;
    private int centerZ;

    /** {@code true} once the mayor's menu has moved the centre off the detected bell. */
    private boolean centerOverridden;

    /** Custom radius in blocks, or {@code -1} to use the configured default. */
    private int radius = -1;

    /**
     * Where the village bell actually stands.
     * <p>
     * Tracked separately from the centre. The centre is fixed once so the settlement doesn't
     * wander, but the bell itself can be picked up and moved, and the map should show it where
     * it really is rather than at the middle of the village.
     */
    private int bellX;
    private int bellY;
    private int bellZ;

    /** The sitting mayor, or {@code null} while the seat is vacant (triggers an election). */
    private @Setter @Nullable UUID mayor;

    /** The platform the sitting mayor won on; governs trade prices and build cost/time. */
    private @Setter @Nullable ElectoralProgram mayorProgram;

    /**
     * Whether this settlement's buildings have already been copied into blueprints.
     * <p>
     * Recorded once and only once. Saving the same village on every pass would fill the folder
     * with copies of the same houses, and re-reading them is work done for nothing.
     */
    private @Setter boolean recorded;

    private final VillageStorage storage = new VillageStorage();

    public Village(UUID id, String worldName, int centerX, int centerY, int centerZ) {
        this.id = id;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        setBell(centerX, centerY, centerZ);
    }

    /** Records where the bell stands right now. */
    public void setBell(int x, int y, int z) {
        this.bellX = x;
        this.bellY = y;
        this.bellZ = z;
    }

    public @Nullable World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    /** The centre as a location, or {@code null} when the world isn't loaded. */
    public @Nullable Location getCenter() {
        World world = getWorld();
        return world == null ? null : new Location(world, centerX + 0.5d, centerY, centerZ + 0.5d);
    }

    /** Moves the centre because the bell was (re)detected there. Ignored once overridden. */
    public void setDetectedCenter(int x, int y, int z) {
        if (centerOverridden) return;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
    }

    /** Moves the centre on the player's explicit instruction, pinning it against re-detection. */
    public void overrideCenter(int x, int y, int z) {
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.centerOverridden = true;
    }

    /** Hands control of the centre back to bell auto-detection. */
    public void clearCenterOverride() {
        this.centerOverridden = false;
    }

    /**
     * Half-width and half-depth of the settlement, or {@code -1} while it still uses the radius.
     * <p>
     * Villages are not square. Vanilla lays them along a road, so one is routinely twice as long
     * as it is wide, and forcing a square onto that shape means the only way to take in the house
     * at the far end is to blow the boundary out on all four sides — which drags in everything
     * else within reach as well. Two extents let the boundary follow the settlement instead.
     */
    private int extentX = -1;
    private int extentZ = -1;

    public int getExtentX(int configDefault) {
        return extentX > 0 ? extentX : getEffectiveRadius(configDefault);
    }

    public int getExtentZ(int configDefault) {
        return extentZ > 0 ? extentZ : getEffectiveRadius(configDefault);
    }

    /**
     * Widens the settlement to take in a building, in one axis or both.
     * <p>
     * Only ever grows, and never moves the centre. Both matter: shrinking would drop residents
     * out of their own village between one scan and the next, and moving the centre pushes the
     * villagers themselves outside the radius they are looked up by — which is how a village ends
     * up with no residents, no unemployed and therefore no election.
     *
     * @return whether anything actually changed.
     */
    public boolean growToFit(int wantX, int wantZ, int configDefault, int limit) {
        int nowX = getExtentX(configDefault);
        int nowZ = getExtentZ(configDefault);

        int newX = Math.min(limit, Math.max(nowX, wantX));
        int newZ = Math.min(limit, Math.max(nowZ, wantZ));

        if (newX == nowX && newZ == nowZ) return false;

        extentX = newX;
        extentZ = newZ;
        return true;
    }

    public boolean hasCustomRadius() {
        return radius > 0;
    }

    /** Sets a custom radius, or pass a non-positive value to fall back to the config default. */
    public void setRadius(int radius) {
        this.radius = radius > 0 ? radius : -1;
    }

    public int getEffectiveRadius(int configDefault) {
        return hasCustomRadius() ? radius : configDefault;
    }

    public boolean hasMayor() {
        return mayor != null;
    }

    /**
     * Whether {@code location} falls inside the settlement.
     * <p>
     * A <b>square</b> centred on the village, because that is the shape everything draws: the
     * radar outlines a square and the border particles trace a square. Measuring membership as a
     * circle instead meant the four corners of every drawn border were outside the village that
     * border claimed to mark — a player could walk across the visible line and nothing would
     * happen, which is exactly how it looked from the inside.
     * <p>
     * Distance is measured horizontally so a village covers the full column of its area — a
     * resident in a basement or on a roof still belongs to it.
     */
    public boolean contains(@Nullable Location location, int configDefault) {
        if (location == null) return false;

        World world = location.getWorld();
        if (world == null || !world.getName().equals(worldName)) return false;

        double dx = Math.abs(location.getX() - (centerX + 0.5d));
        double dz = Math.abs(location.getZ() - (centerZ + 0.5d));
        return dx <= getExtentX(configDefault) && dz <= getExtentZ(configDefault);
    }

    public @NotNull String getDisplayName() {
        return name != null && !name.isEmpty() ? name : "Village " + centerX + ", " + centerZ;
    }

    public void save(@NotNull ConfigurationSection section) {
        section.set("world", worldName);
        section.set("name", name);
        section.set("center.x", centerX);
        section.set("center.y", centerY);
        section.set("center.z", centerZ);
        section.set("center-overridden", centerOverridden);
        section.set("bell.x", bellX);
        section.set("bell.y", bellY);
        section.set("bell.z", bellZ);
        section.set("radius", radius);
        section.set("recorded", recorded);
        section.set("extent.x", extentX);
        section.set("extent.z", extentZ);
        section.set("mayor", mayor != null ? mayor.toString() : null);
        section.set("mayor-program", mayorProgram != null ? mayorProgram.getKey() : null);

        // Rewritten wholesale so items that dropped to zero don't linger from an earlier save.
        section.set("storage", null);
        storage.save(section.createSection("storage"));
    }

    public static @Nullable Village load(@NotNull String id, @NotNull ConfigurationSection section) {
        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) return null;

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Village village = new Village(
                uuid,
                worldName,
                section.getInt("center.x"),
                section.getInt("center.y"),
                section.getInt("center.z"));

        village.name = section.getString("name");
        village.centerOverridden = section.getBoolean("center-overridden", false);
        village.setBell(
                section.getInt("bell.x", village.centerX),
                section.getInt("bell.y", village.centerY),
                section.getInt("bell.z", village.centerZ));
        village.radius = section.getInt("radius", -1);
        village.recorded = section.getBoolean("recorded", false);
        village.extentX = section.getInt("extent.x", -1);
        village.extentZ = section.getInt("extent.z", -1);

        String mayor = section.getString("mayor");
        if (mayor != null && !mayor.isEmpty()) {
            try {
                village.mayor = UUID.fromString(mayor);
            } catch (IllegalArgumentException ignored) {
                // A corrupt mayor id just leaves the seat vacant; an election will fill it.
            }
        }

        village.mayorProgram = ElectoralProgram.byKey(section.getString("mayor-program"));

        village.storage.load(section.getConfigurationSection("storage"));
        return village;
    }
}
