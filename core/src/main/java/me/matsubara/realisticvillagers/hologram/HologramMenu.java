package me.matsubara.realisticvillagers.hologram;

import me.matsubara.realisticvillagers.village.RadarPanel;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageManager;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GUIInteractType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HologramMenu {

    private final RealisticVillagers plugin;
    private final Player player;
    private final IVillagerNPC npc;
    private MenuState state = MenuState.MAIN;

    // Top-level menu keys with dedicated states — never treated as a navigable custom sub-menu.
    private static final Set<String> RESERVED_MENU_IDS = Set.of("main", "talk", "interactions");
    // Name of the active config-defined sub-menu (only meaningful while state == CUSTOM).
    private @Nullable String customMenuName = null;
    // Current page index for a *paged* custom sub-menu (only meaningful while state == CUSTOM).
    private int customPage = 0;
    // Lets BACK return to whichever menu was open before navigating deeper, however deep that is.
    private final Deque<NavTarget> navHistory = new ArrayDeque<>();
    private record NavTarget(MenuState state, @Nullable String customMenuName, int customPage) {}

    private final List<TextDisplay> menuDisplays      = new ArrayList<>();
    private final List<Double>      menuHalfWidths    = new ArrayList<>();
    private final List<TextDisplay> headDisplays      = new ArrayList<>();
    private final Map<UUID, MenuAction> displayActionMap = new HashMap<>();

    // Info panel (left side)
    private final List<TextDisplay> infoDisplays      = new ArrayList<>();
    private final List<Double>      infoHalfWidths    = new ArrayList<>();
    private final Map<UUID, MenuAction> infoActionMap = new HashMap<>();
    private @Nullable Integer hoveredInfoIdx = null;
    private org.bukkit.Location infoPanelBase;
    private int infoSection = 0;
    private int childrenPage = 0;
    private boolean infoPanelVisible = false;
    private boolean awaitingDivorceConfirm = false;
    private static final int INFO_SECTIONS = 3;

    private @Nullable Integer hoveredDisplayIdx = null;

    // Radar view state. Kept per open menu so one player's zoom doesn't affect another's.
    // Zoom is the fraction of the village radius shown: 1.0 is the whole settlement.
    private double radarZoom = 1.0d;
    private boolean radarResidents = true;
    private boolean radarMayor = true;
    private @Nullable RadarPanel radarPanel;
    private me.matsubara.realisticvillagers.village.VillageBuildings.@Nullable Footprint selectedBuilding;
    private me.matsubara.realisticvillagers.village.VillageBuildings.@Nullable Footprint hoveredBuilding;

    private org.bukkit.Location menuBase;
    // Player-right unit vector (updated every tick via calculateMenuBase).
    private double rightX, rightZ;

    private int tickTaskId = -1;
    private boolean closed = false;
    private boolean frozeVillager = false;
    private int tickCount = 0;
    private VillagerOrder menuOrder = VillagerOrder.WANDERER;
    // Holding right-click makes the client resend the interact packet several times a second;
    // without this, each resend after the first toggles the just-opened menu closed again.
    private final long openedAt = System.currentTimeMillis();

    public long getOpenedAt() {
        return openedAt;
    }

    // ── Config-driven constants (read once via helpers, never cached as static) ──

    private double sideOffset()           { return hcfg().getDouble("hologram.menu.side-offset",            1.5);  }
    private double lineSpacing()          { return hcfg().getDouble("hologram.menu.line-spacing",           0.30); }
    private double headSpacing()          { return hcfg().getDouble("hologram.head-display.line-spacing",   0.22); }
    private double autoCloseDistSq()      { double d = hcfg().getDouble("hologram.menu.auto-close-distance", 7.0); return d * d; }
    private double charWidthBlocks()      { return hcfg().getDouble("hologram.menu.raycast.char-width-blocks", 0.14); }
    private double segmentThresholdSq()   { double t = hcfg().getDouble("hologram.menu.raycast.segment-threshold", 0.25); return t * t; }
    private int    childrenPerPage()      { return hcfg().getInt("hologram.info-panel.children-per-page", 3); }

    private FileConfiguration hcfg() {
        FileConfiguration cfg = plugin.getHologramConfig();
        return cfg != null ? cfg : new org.bukkit.configuration.file.YamlConfiguration();
    }

    /** Reads a label from holograms.yml and translates & colour codes. Falls back to {@code def}. */
    private String label(String path, String def) {
        return colorStr(hcfg().getString(path, def), def);
    }

    /** Reads an ARGB colour from holograms.yml at the given prefix (e.g. "hologram.menu.background"). */
    private Color bgColor(String prefix) {
        FileConfiguration c = hcfg();
        int a = c.getInt(prefix + ".alpha", 64);
        int r = c.getInt(prefix + ".red",   0);
        int g = c.getInt(prefix + ".green", 0);
        int b = c.getInt(prefix + ".blue",  0);
        return Color.fromARGB(a, r, g, b);
    }

    /** Returns the ordered list of raw menu-item maps at the given config list path. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> menuItems(String listPath) {
        List<?> raw = hcfg().getList(listPath, Collections.emptyList());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map) result.add((Map<String, Object>) o);
        }
        return result;
    }

    /** Translates & colour codes in {@code raw} and fills in {@code %player-name%} /
     *  {@code %villager-name%}; falls back to {@code def} when raw is null. */
    @SuppressWarnings("deprecation")
    private String colorStr(String raw, String def) {
        String text = ChatColor.translateAlternateColorCodes('&', raw != null ? raw : def);
        return text.replace("%player-name%", player.getName())
                .replace("%villager-name%", npc.getVillagerName());
    }

    /** Reads {@code key} from an item map and applies colour translation; falls back to {@code def}. */
    private String itemText(Map<String, Object> item, String key, String def) {
        Object v = item.get(key);
        return colorStr(v != null ? String.valueOf(v) : null, def);
    }

    public HologramMenu(RealisticVillagers plugin, Player player, IVillagerNPC npc) {
        this(plugin, player, npc, false);
    }

    /**
     * @param mayorMode opens straight onto the settlement screen instead of the ordinary
     *                  villager menu — used when this villager holds the mayor's seat.
     */
    public HologramMenu(RealisticVillagers plugin, Player player, IVillagerNPC npc, boolean mayorMode) {
        this.plugin = plugin;
        this.player = player;
        this.npc = npc;
        if (mayorMode) this.state = MenuState.MAYOR;
    }

    public void open() {
        org.bukkit.Location vilLoc = npc.bukkit().getLocation();

        // Detect order BEFORE freezing, so menuOrder reflects true villager state.
        menuOrder = detectCurrentOrder();

        calculateMenuBase(vilLoc);
        if (hcfg().getBoolean("hologram.head-display.enabled", true)) spawnHeadDisplay(vilLoc);
        // currentLines() rather than the main menu directly, so an initial state set by the
        // constructor (the mayor screen) is honoured.
        spawnMenuLines(currentLines());

        if (menuOrder == VillagerOrder.FOLLOW) {
            // Villager is already following — stayInPlace() would cancel the follow AI, so skip it.
            frozeVillager = false;
        } else {
            // Freeze immediately + next tick to override any queued walk-to-player AI goal.
            frozeVillager = !npc.isStayingInPlace();
            npc.stayInPlace();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!closed) npc.stayInPlace();
            });
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (closed) { cancel(); return; }
                tickCount++;
                LivingEntity bukkit = npc.bukkit();
                if (!player.isOnline() || bukkit == null || !bukkit.isValid()) { close(false); return; }
                if (player.getLocation().distanceSquared(bukkit.getLocation()) > autoCloseDistSq()) { close(false, true); return; }
                if (isInCombatOrFleeing()) { close(false, true); return; }

                org.bukkit.Location vilLoc = bukkit.getLocation();
                repositionAll(vilLoc);
                updateHoverHighlight();
                if (tickCount % 10 == 0) updateHeadDisplay(vilLoc);

                // Follow the player by moving the existing panel, exactly like the menu lines
                // above. Rebuilding it instead would respawn every tile and read as a jump.
                if (state == MenuState.RADAR) {
                    repositionRadar();
                    updateBuildingHover();
                }
            }
        };
        tickTaskId = task.runTaskTimer(plugin, 2L, 2L).getTaskId();
    }

    // ── Positioning ────────────────────────────────────────────────────────────

    /**
     * Computes menuBase using the actual player→villager direction.
     * Player's right = toVillager × Up = (-dz, 0, dx).
     * Fixed MENU_TOP_HEIGHT keeps the panel at a constant Y across all menu states.
     */
    private void calculateMenuBase(@NotNull org.bukkit.Location vilLoc) {
        double dx = vilLoc.getX() - player.getLocation().getX();
        double dz = vilLoc.getZ() - player.getLocation().getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) { dx = 1.0; dz = 0.0; } else { dx /= len; dz /= len; }

        // Player's right: toVillager × Up = (-dz, 0, dx).
        double rx = -dz, rz = dx;
        this.rightX = rx;
        this.rightZ = rz;

        double topY = npc.bukkit().getHeight() + 0.05;
        double so = sideOffset();
        menuBase      = vilLoc.clone().add( rx * so, topY,  rz * so);
        infoPanelBase = vilLoc.clone().add(-rx * so, topY, -rz * so);
    }

    private void repositionAll(@NotNull org.bukkit.Location vilLoc) {
        calculateMenuBase(vilLoc);

        double ls = lineSpacing();
        for (int i = 0; i < menuDisplays.size(); i++) {
            menuDisplays.get(i).teleport(menuBase.clone().add(0, -i * ls, 0));
        }
        if (infoPanelVisible) {
            for (int i = 0; i < infoDisplays.size(); i++) {
                infoDisplays.get(i).teleport(infoPanelBase.clone().add(0, -i * ls, 0));
            }
        }
        repositionHead(vilLoc);
    }

    // ── Head display ──────────────────────────────────────────────────────────

    private void spawnHeadDisplay(@NotNull org.bukkit.Location vilLoc) {
        for (TextDisplay d : headDisplays) d.remove();
        headDisplays.clear();

        List<String> lines = buildHeadLines(vilLoc);
        // Spawn above the vanilla nametag (which sits at ~height+0.5).
        // Starting at height+0.7 clears the nametag and any profession labels.
        double baseY = vilLoc.getY() + npc.bukkit().getHeight() + hcfg().getDouble("hologram.head-display.height-offset", 0.7);
        for (int i = 0; i < lines.size(); i++) {
            org.bukkit.Location loc = vilLoc.clone();
            loc.setY(baseY + (lines.size() - 1 - i) * headSpacing());
            String lineText      = lines.get(i);
            float  headViewRange = (float) hcfg().getDouble("hologram.head-display.view-range", 8.0);
            boolean headSee      = hcfg().getBoolean("hologram.menu.see-through", true);
            Color   headBg       = bgColor("hologram.menu.background");
            TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setText(lineText);
                d.setBillboard(Display.Billboard.VERTICAL);
                d.setViewRange(headViewRange);
                // Cull by a box big enough to cover the text, not by its anchor point.
                d.setDisplayWidth(4.0f);
                d.setDisplayHeight(4.0f);
                d.setSeeThrough(headSee);
                d.setPersistent(false);
                d.setDefaultBackground(false);
                d.setBackgroundColor(headBg);
                d.setLineWidth(200);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
            });
            hidePrivately(display);
            headDisplays.add(display);
        }
    }

    private void repositionHead(@NotNull org.bukkit.Location vilLoc) {
        double baseY = vilLoc.getY() + npc.bukkit().getHeight() + hcfg().getDouble("hologram.head-display.height-offset", 0.7);
        int n = headDisplays.size();
        for (int i = 0; i < n; i++) {
            org.bukkit.Location loc = vilLoc.clone();
            loc.setY(baseY + (n - 1 - i) * headSpacing());
            headDisplays.get(i).teleport(loc);
        }
    }

    private void updateHeadDisplay(@NotNull org.bukkit.Location vilLoc) {
        List<String> lines = buildHeadLines(vilLoc);
        for (int i = 0; i < Math.min(lines.size(), headDisplays.size()); i++) {
            headDisplays.get(i).setText(lines.get(i));
        }
    }

    /** Builds head-display lines from the configurable format list in holograms.yml. */
    @SuppressWarnings("deprecation")
    private List<String> buildHeadLines(@NotNull org.bukkit.Location vilLoc) {
        List<String> lines = new ArrayList<>();
        if (!(npc.bukkit() instanceof Villager villager)) return lines;

        double hp = villager.getHealth() + villager.getAbsorptionAmount();
        AttributeInstance maxHpAttr = villager.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
        int food = npc.getFoodLevel();

        List<String> formats = hcfg().getStringList("hologram.head-display.lines");
        if (formats.isEmpty()) formats = List.of("&cHP: &f%hp%&7/&f%max-hp%  &aFood: &f%food%&7/&f20");

        for (String fmt : formats) {
            lines.add(ChatColor.translateAlternateColorCodes('&',
                    fmt.replace("%hp%",     String.format("%.1f", hp))
                       .replace("%max-hp%", String.format("%.0f", maxHp))
                       .replace("%food%",   String.valueOf(food))));
        }
        return lines;
    }

    private static String repColor(int rep) {
        if (rep >= 50)  return "§a";   // green
        if (rep >= 20)  return "§2";   // dark green
        if (rep >= 0)   return "§e";   // yellow
        if (rep >= -20) return "§6";   // gold
        return "§c";                   // red
    }

    // ── Menu spawning ──────────────────────────────────────────────────────────

    private void spawnMenuLines(List<MenuLine> lines) {
        calculateMenuBase(npc.bukkit().getLocation());

        for (TextDisplay d : menuDisplays) d.remove();
        menuDisplays.clear();
        menuHalfWidths.clear();
        displayActionMap.clear();
        hoveredDisplayIdx = null;

        double ls = lineSpacing();
        int menuLineWidth = hcfg().getInt("hologram.menu.line-width", 220);
        for (int i = 0; i < lines.size(); i++) {
            MenuLine line = lines.get(i);
            // halfWidth stored only for raycast segment; entity is always at menuBase (centered).
            double hw = estimateTextHalfWidth(line.text());
            menuHalfWidths.add(hw);
            org.bukkit.Location loc = menuBase.clone().add(0, -i * ls, 0);

            TextDisplay display = spawnTextDisplay(loc, line.text(), menuLineWidth);
            menuDisplays.add(display);

            if (line.action() != null) {
                displayActionMap.put(display.getUniqueId(), line.action());
            }
        }
    }

    private TextDisplay spawnTextDisplay(org.bukkit.Location loc, String text, int lineWidth) {
        return spawnTextDisplay(loc, text, lineWidth, TextDisplay.TextAlignment.LEFT);
    }

    private TextDisplay spawnTextDisplay(org.bukkit.Location loc, String text, int lineWidth, TextDisplay.TextAlignment alignment) {
        FileConfiguration c = hcfg();
        // Menu and info-panel share the same background/view settings; head display uses head-display section.
        float viewRange    = (float) c.getDouble("hologram.menu.view-range",  8.0);
        boolean seeThrough = c.getBoolean("hologram.menu.see-through",       true);
        Color   bg         = bgColor("hologram.menu.background");
        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setViewRange(viewRange);
            // Cull by a box big enough to cover the text, not by its anchor point.
            d.setDisplayWidth(4.0f);
            d.setDisplayHeight(4.0f);
            d.setSeeThrough(seeThrough);
            d.setPersistent(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(bg);
            d.setLineWidth(lineWidth);
            d.setAlignment(alignment);
        });
        hidePrivately(display);
        return display;
    }

    /**
     * Keeps a just-spawned hologram entity visible only to this menu's own player.
     * <p>
     * Every display here is a real, world-spawned entity, so without this every player standing
     * nearby would see another player's private menu, reputation, family details, mayor standing,
     * etc. floating over the villager's head.
     */
    private void hidePrivately(org.bukkit.entity.Entity entity) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) other.hideEntity(plugin, entity);
        }
    }

    /**
     * Hides every entity this menu currently owns from {@code viewer}.
     * <p>
     * Used for a player who joins the server (or otherwise (re)starts seeing entities) while
     * someone else already has this menu open — without this, a fresh join would show them a
     * panel that {@link #hidePrivately} only hid from players already online at spawn time.
     */
    public void hideFrom(@NotNull Player viewer) {
        if (viewer.equals(player)) return;
        for (TextDisplay d : menuDisplays) viewer.hideEntity(plugin, d);
        for (TextDisplay d : headDisplays) viewer.hideEntity(plugin, d);
        for (TextDisplay d : infoDisplays) viewer.hideEntity(plugin, d);
        if (radarPanel != null) radarPanel.hideFrom(viewer, plugin);
    }

    // ── Hover highlight (raycast) ──────────────────────────────────────────────

    private void updateHoverHighlight() {
        Integer newMenu = raycastMenuIndex();
        Integer newInfo = raycastInfoIndex();

        if (!Objects.equals(newMenu, hoveredDisplayIdx)) {
            if (hoveredDisplayIdx != null && hoveredDisplayIdx < menuDisplays.size()) {
                menuDisplays.get(hoveredDisplayIdx).setText(normalText(hoveredDisplayIdx));
            }
            if (newMenu != null && newMenu < menuDisplays.size()) {
                menuDisplays.get(newMenu).setText(highlightText(newMenu));
            }
            hoveredDisplayIdx = newMenu;
        }

        if (!Objects.equals(newInfo, hoveredInfoIdx)) {
            if (hoveredInfoIdx != null && hoveredInfoIdx < infoDisplays.size()) {
                infoDisplays.get(hoveredInfoIdx).setText(infoNormalText(hoveredInfoIdx));
            }
            if (newInfo != null && newInfo < infoDisplays.size()) {
                infoDisplays.get(newInfo).setText(infoHighlightText(newInfo));
            }
            hoveredInfoIdx = newInfo;
        }
    }

    /**
     * Casts a ray from the player's eye and returns the index of the nearest
     * clickable text line, or null if none is within threshold.
     *
     * Each text item is modelled as a horizontal LINE SEGMENT from its left edge
     * (menuBase at that Y) to its right edge (menuBase + right * fullTextWidth).
     * This works perfectly regardless of the player's horizontal angle because
     * TextDisplay uses Billboard.VERTICAL — the text always faces the player, so
     * the segment matches the visible text from every direction.
     */
    private @Nullable Integer raycastMenuIndex() {
        org.bukkit.Location eyeLoc = player.getEyeLocation();
        Vector origin = eyeLoc.toVector();
        Vector dir    = eyeLoc.getDirection();

        Integer bestIdx  = null;
        double  bestDist = segmentThresholdSq();
        double  ls       = lineSpacing();

        for (int i = 0; i < menuDisplays.size(); i++) {
            if (!displayActionMap.containsKey(menuDisplays.get(i).getUniqueId())) continue;

            double hw = i < menuHalfWidths.size() ? menuHalfWidths.get(i) : 0;
            // LEFT alignment: entity at left edge, text extends rightward.
            Vector leftEdge = menuBase.clone().add(0, -i * ls, 0).toVector();
            Vector segEnd   = leftEdge.clone().add(new Vector(rightX * hw * 2, 0, rightZ * hw * 2));

            double distSq = rayToSegmentDistSq(origin, dir, leftEdge, segEnd);
            if (distSq < bestDist) {
                bestDist = distSq;
                bestIdx  = i;
            }
        }
        return bestIdx;
    }

    private @Nullable Integer raycastInfoIndex() {
        if (infoPanelBase == null) return null;
        org.bukkit.Location eyeLoc = player.getEyeLocation();
        Vector origin = eyeLoc.toVector();
        Vector dir    = eyeLoc.getDirection();

        Integer bestIdx  = null;
        double  bestDist = segmentThresholdSq();
        double  ls       = lineSpacing();

        for (int i = 0; i < infoDisplays.size(); i++) {
            if (!infoActionMap.containsKey(infoDisplays.get(i).getUniqueId())) continue;

            double hw = i < infoHalfWidths.size() ? infoHalfWidths.get(i) : 0;
            // CENTER alignment: entity at center, text extends equally both sides.
            Vector center   = infoPanelBase.clone().add(0, -i * ls, 0).toVector();
            Vector segStart = center.clone().add(new Vector(-rightX * hw, 0, -rightZ * hw));
            Vector segEnd   = center.clone().add(new Vector( rightX * hw, 0,  rightZ * hw));

            double distSq = rayToSegmentDistSq(origin, dir, segStart, segEnd);
            if (distSq < bestDist) {
                bestDist = distSq;
                bestIdx  = i;
            }
        }
        return bestIdx;
    }

    /** Approximate half-width of the stripped text in world-space blocks. */
    @SuppressWarnings("deprecation")
    private double estimateTextHalfWidth(@NotNull String text) {
        String s = ChatColor.stripColor(text);
        if (s == null || s.isEmpty()) return 0;
        return s.length() * charWidthBlocks() / 2.0;
    }

    /**
     * Squared distance between ray R(t)=ro+t·rd (t≥0.3) and segment P(s)=a+s·(b−a) (s∈[0,1]).
     * Classic CCD closest-features formula — see "Real-Time Collision Detection" §5.1.
     */
    private static double rayToSegmentDistSq(
            @NotNull Vector ro, @NotNull Vector rd,
            @NotNull Vector a,  @NotNull Vector b) {
        Vector ab = b.clone().subtract(a);
        Vector w0 = ro.clone().subtract(a);

        double A = rd.dot(rd);
        double B = rd.dot(ab);
        double C = ab.dot(ab);
        double D = rd.dot(w0);
        double E = ab.dot(w0);

        double denom = A * C - B * B;
        double t, s;

        if (denom < 1e-10) {
            // Ray and segment nearly parallel — pick s=0, find t.
            s = 0.0;
        } else {
            // Unconstrained minimum.
            s = (A * E - B * D) / denom;
            s = Math.max(0.0, Math.min(1.0, s));
        }

        // Closest ray parameter for this s; enforce t ≥ 0.3 (ignore behind/too-close).
        t = Math.max(0.3, (B * s - D) / A);

        Vector rayPt = ro.clone().add(rd.clone().multiply(t));
        Vector segPt = a.clone().add(ab.clone().multiply(s));
        return rayPt.distanceSquared(segPt);
    }

    /** Builds the normal (non-highlighted) text for the given display index. */
    private String normalText(int displayIdx) {
        // Rebuild the current line list and return the text at displayIdx.
        List<MenuLine> lines = currentLines();
        if (displayIdx < lines.size()) return lines.get(displayIdx).text();
        return "";
    }

    /** Builds the highlighted version of the text at the given display index. */
    @SuppressWarnings("deprecation")
    private String highlightText(int displayIdx) {
        String base = normalText(displayIdx);
        // Try to replace a leading colour code + arrow (» or «) with bold yellow ▶.
        String h = base.replaceFirst("^(§[0-9a-fA-FkKlLmMnNoOrR]+)[»«] ", "§e§l▶ ");
        // Fallback for arbitrary text: just bold the stripped version.
        if (h.equals(base)) h = "§e§l" + ChatColor.stripColor(base);
        return h;
    }

    // ── Info panel ────────────────────────────────────────────────────────

    private void spawnInfoPanel(@NotNull org.bukkit.Location vilLoc) {
        for (TextDisplay d : infoDisplays) d.remove();
        infoDisplays.clear();
        infoHalfWidths.clear();
        infoActionMap.clear();
        hoveredInfoIdx = null;

        calculateMenuBase(vilLoc); // ensures infoPanelBase is set

        List<InfoLine> lines = buildInfoLines();
        double ls = lineSpacing();
        int infoLineWidth = hcfg().getInt("hologram.info-panel.line-width", 200);
        for (int i = 0; i < lines.size(); i++) {
            InfoLine line = lines.get(i);
            double hw = estimateTextHalfWidth(line.text());
            infoHalfWidths.add(hw);
            org.bukkit.Location loc = infoPanelBase.clone().add(0, -i * ls, 0);
            TextDisplay d = spawnTextDisplay(loc, line.text(), infoLineWidth, TextDisplay.TextAlignment.CENTER);
            infoDisplays.add(d);
            if (line.action() != null) infoActionMap.put(d.getUniqueId(), line.action());
        }
    }

    private void updateInfoPanel() {
        List<InfoLine> lines = buildInfoLines();
        int count = Math.min(lines.size(), infoDisplays.size());
        // Resize if section changed number of lines
        if (lines.size() != infoDisplays.size()) {
            spawnInfoPanel(npc.bukkit().getLocation());
            return;
        }
        infoActionMap.clear();
        hoveredInfoIdx = null;
        for (int i = 0; i < count; i++) {
            InfoLine line = lines.get(i);
            infoDisplays.get(i).setText(line.text());
            infoHalfWidths.set(i, estimateTextHalfWidth(line.text()));
            if (line.action() != null) infoActionMap.put(infoDisplays.get(i).getUniqueId(), line.action());
        }
    }

    private String infoNormalText(int idx) {
        List<InfoLine> lines = buildInfoLines();
        return idx < lines.size() ? lines.get(idx).text() : "";
    }

    @SuppressWarnings("deprecation")
    private String infoHighlightText(int idx) {
        String base = infoNormalText(idx);
        String h = base.replaceFirst("^(§[0-9a-fA-FkKlLmMnNoOrR]+)[»«] ", "§e§l▶ ");
        if (h.equals(base)) h = "§e§l" + ChatColor.stripColor(base);
        return h;
    }

    /** Returns the action that the info panel item at idx maps to, or null. */
    public @Nullable MenuAction getHoveredInfoAction() {
        if (hoveredInfoIdx == null) return null;
        if (hoveredInfoIdx >= infoDisplays.size()) return null;
        return infoActionMap.get(infoDisplays.get(hoveredInfoIdx).getUniqueId());
    }

    // ── Info section builders ──────────────────────────────────────────────

    private List<InfoLine> buildInfoLines() {
        List<InfoLine> lines = new ArrayList<>();
        String header;
        switch (infoSection) {
            case 0 -> { header = label("hologram.info-panel.section-headers.basic",  "&e&l— BASIC —");  addBasicLines(lines); }
            case 1 -> { header = label("hologram.info-panel.section-headers.stats",  "&c&l— STATS —");  addStatsLines(lines); }
            default -> { header = label("hologram.info-panel.section-headers.family","&d&l— FAMILY —"); addFamilyLines(lines); }
        }
        lines.add(0, new InfoLine(header, null));
        String pageText = label("hologram.labels.info.page-format", "&8[&7%current%&8/&7%total%&8]")
                .replace("%current%", String.valueOf(infoSection + 1))
                .replace("%total%",   String.valueOf(INFO_SECTIONS));
        lines.add(new InfoLine(pageText, null));
        lines.add(new InfoLine(label("hologram.info-panel.navigation.prev", "&e« &fPrev"), MenuAction.INFO_PREV));
        lines.add(new InfoLine(label("hologram.info-panel.navigation.next", "&e» &fNext"), MenuAction.INFO_NEXT));
        return lines;
    }

    private void addBasicLines(@NotNull List<InfoLine> out) {
        if (!(npc.bukkit() instanceof Villager v)) return;
        String sex  = npc.isMale() ? Config.MALE.asString() : Config.FEMALE.asString();
        String age  = v.isAdult() ? Config.ADULT.asString() : Config.KID.asString();
        String type = plugin.getVariableTextConfig().getString(
                "variable-text.type." + v.getVillagerType().name().toLowerCase(Locale.ROOT),
                v.getVillagerType().name());
        String prof = plugin.getProfessionFormatted(v.getProfession(), npc.isMale());
        out.add(new InfoLine(label("hologram.labels.info.name-prefix", "&7Name: &f") + npc.getVillagerName(), null));
        out.add(new InfoLine(label("hologram.labels.info.sex-prefix",  "&7Sex: &f")  + sex,                  null));
        out.add(new InfoLine(label("hologram.labels.info.age-prefix",  "&7Age: &f")  + age,                  null));
        out.add(new InfoLine(label("hologram.labels.info.type-prefix", "&7Type: &f") + type,                 null));
        out.add(new InfoLine(label("hologram.labels.info.prof-prefix", "&7Prof: &f") + prof
                + label("hologram.labels.info.level-prefix", " &8Lv.") + v.getVillagerLevel(), null));
    }

    private void addStatsLines(@NotNull List<InfoLine> out) {
        if (!(npc.bukkit() instanceof Villager v)) return;
        AttributeInstance maxHpAttr = v.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
        double hp    = v.getHealth() + v.getAbsorptionAmount();
        int rep      = npc.getReputation(player.getUniqueId());
        String repColor  = rep >= 50 ? "§a" : rep >= 0 ? "§e" : "§c";
        String none      = Config.NONE.asString();
        String activity  = npc.getActivityName(none);
        if (!activity.equalsIgnoreCase(none)) {
            activity = plugin.getVariableTextConfig().getString("variable-text.activity." + activity, activity);
        }
        String hpSep  = label("hologram.labels.info.hp-separator", "&7/&f");
        out.add(new InfoLine(label("hologram.labels.info.hp-prefix",   "&cHP: &f")   + String.format("%.1f", hp) + hpSep + String.format("%.0f", maxHp), null));
        out.add(new InfoLine(label("hologram.labels.info.food-prefix", "&aFood: &f") + npc.getFoodLevel() + label("hologram.labels.info.food-max", "&7/&f20"), null));
        out.add(new InfoLine(label("hologram.labels.info.rep-prefix",  "&6Rep: ")    + repColor + rep,  null));
        out.add(new InfoLine(label("hologram.labels.info.act-prefix",  "&eAct: &f")  + activity,        null));
    }

    private void addFamilyLines(@NotNull List<InfoLine> out) {
        var tracker = plugin.getTracker();
        String deadMark = Config.DEAD.asString();
        if (!deadMark.isEmpty()) deadMark = " " + deadMark;

        String partnerPrefix   = label("hologram.labels.info.partner-prefix",    "&dPartner: ");
        String unmarried       = label("hologram.labels.info.partner-unmarried",  "&7Unmarried");
        String fatherPrefix    = label("hologram.labels.info.father-prefix",      "&bFather: ");
        String motherPrefix    = label("hologram.labels.info.mother-prefix",      "&dMother: ");
        String unknownSuffix   = "§7" + Config.UNKNOWN.asString();
        String childrenNone    = label("hologram.labels.info.children-none",      "&7Children: &f");
        String childrenHeader  = label("hologram.labels.info.children-header",    "&7Children ");
        String bullet          = label("hologram.labels.info.children-bullet",    "&8 • &f");
        String prevChildren    = label("hologram.info-panel.navigation.prev-children", "&e« &fPrev children");
        String nextChildren    = label("hologram.info-panel.navigation.next-children", "&e» &fNext children");

        // Partner
        IVillagerNPC partner = npc.getPartner();
        if (partner == null) {
            out.add(new InfoLine(partnerPrefix + unmarried, null));
        } else {
            boolean alive = npc.isPartnerVillager()
                    ? tracker.getOffline(partner.getUniqueId()) != null
                    : Bukkit.getOfflinePlayer(partner.getUniqueId()).isOnline();
            out.add(new InfoLine(partnerPrefix + "§f" + partner.getVillagerName() + (alive ? "" : deadMark), null));
        }

        // Father
        IVillagerNPC father = npc.getFather();
        if (father == null) {
            out.add(new InfoLine(fatherPrefix + unknownSuffix, null));
        } else {
            boolean alive = npc.isFatherVillager()
                    ? tracker.getOffline(father.getUniqueId()) != null
                    : Bukkit.getOfflinePlayer(father.getUniqueId()).hasPlayedBefore();
            out.add(new InfoLine(fatherPrefix + "§f" + father.getVillagerName() + (alive ? "" : deadMark), null));
        }

        // Mother
        IVillagerNPC mother = npc.getMother();
        if (mother == null) {
            out.add(new InfoLine(motherPrefix + unknownSuffix, null));
        } else {
            boolean alive = tracker.getOffline(mother.getUniqueId()) != null;
            out.add(new InfoLine(motherPrefix + "§f" + mother.getVillagerName() + (alive ? "" : deadMark), null));
        }

        // Children
        List<IVillagerNPC> children = npc.getChildrens();
        int cpp = childrenPerPage();
        if (children.isEmpty()) {
            out.add(new InfoLine(childrenNone + Config.NO_CHILDRENS.asString(), null));
        } else {
            int totalPages = (children.size() + cpp - 1) / cpp;
            childrenPage = Math.min(childrenPage, totalPages - 1);
            int from = childrenPage * cpp;
            int to   = Math.min(from + cpp, children.size());
            out.add(new InfoLine(childrenHeader + "§8(" + (childrenPage + 1) + "§7/§8" + totalPages + "):", null));
            for (int i = from; i < to; i++) {
                IVillagerNPC child = children.get(i);
                boolean alive = tracker.getOffline(child.getUniqueId()) != null;
                out.add(new InfoLine(bullet + child.getVillagerName() + (alive ? "" : deadMark), null));
            }
            if (totalPages > 1) {
                out.add(new InfoLine(prevChildren, MenuAction.CHILDREN_PREV));
                out.add(new InfoLine(nextChildren, MenuAction.CHILDREN_NEXT));
            }
        }
    }

    private record InfoLine(String text, @Nullable MenuAction action) {}

    private List<MenuLine> currentLines() {
        return switch (state) {
            case MAIN         -> buildMainMenuLines();
            case TALK         -> buildTalkMenuLines();
            case INTERACTIONS -> buildInteractionsMenuLines();
            case CUSTOM       -> customMenuName != null ? buildCustomMenuLines(customMenuName) : List.of();
            case MAYOR        -> buildMayorMenuLines();
            case RADAR        -> buildRadarLines();
        };
    }

    // ── Mayor screens ──────────────────────────────────────────────────────────

    private @Nullable me.matsubara.realisticvillagers.village.Village mayorVillage() {
        var villages = plugin.getVillageManager();
        if (villages == null || !villages.isEnabled()) return null;

        return npc.bukkit() instanceof Villager villager ? villages.getVillage(villager) : null;
    }

    private List<MenuLine> buildMayorMenuLines() {
        List<MenuLine> lines = new ArrayList<>();

        var villages = plugin.getVillageManager();
        var mayors = plugin.getMayorManager();
        var village = mayorVillage();

        if (village == null || villages == null || mayors == null) {
            lines.add(new MenuLine(label("hologram.labels.mayor.unavailable", "&cNo settlement here."), null));
            lines.add(new MenuLine(label("hologram.menus.mayor.back", "&c« &fBack"), MenuAction.BACK));
            return lines;
        }

        lines.add(new MenuLine(label("hologram.labels.mayor.header", "&6&l— MAYOR —"), null));
        lines.add(new MenuLine(label("hologram.labels.mayor.village", "&7Village: &f")
                + village.getDisplayName(), null));
        lines.add(new MenuLine(label("hologram.labels.mayor.residents", "&7Residents: &f")
                + villages.getResidents(village).size(), null));

        int standing = villages.getMayorReputation(village, player);
        int required = mayors.getRequiredReputation();

        // Translate the whole composed line: label() only converts the config value, so the
        // colour codes concatenated around it here would otherwise reach the display raw.
        lines.add(new MenuLine(PluginUtils.translate((standing >= required ? "&a" : "&c")
                + hcfg().getString("hologram.labels.mayor.standing", "Standing: ")
                + standing + "&7/&f" + required), null));

        lines.add(new MenuLine(label("hologram.menus.mayor.radar", "&e» &fVillage radar"), MenuAction.MAYOR_RADAR));
        lines.add(new MenuLine(label("hologram.menus.mayor.storage", "&e» &fSettlement stores"), MenuAction.MAYOR_STORAGE));
        lines.add(new MenuLine(label("hologram.menus.mayor.commission", "&e» &fCommission a building"), MenuAction.MAYOR_COMMISSION));

        boolean showingBorders = plugin.getBorderVisualizer() != null
                && plugin.getBorderVisualizer().isShowing(player);
        lines.add(new MenuLine(toggle("hologram.menus.mayor.borders", "&e» &fShow borders: ", showingBorders),
                MenuAction.MAYOR_BORDERS));
        lines.add(new MenuLine(label("hologram.menus.mayor.rename", "&e» &fRename settlement"), MenuAction.MAYOR_RENAME));

        lines.add(new MenuLine(label("hologram.menus.mayor.back", "&c« &fBack"), MenuAction.BACK));

        return lines;
    }

    private List<MenuLine> buildRadarLines() {
        List<MenuLine> lines = new ArrayList<>();

        var villages = plugin.getVillageManager();
        var village = mayorVillage();

        if (village == null || villages == null) {
            lines.add(new MenuLine(label("hologram.labels.mayor.unavailable", "&cNo settlement here."), null));
            lines.add(new MenuLine(label("hologram.menus.mayor.back", "&c« &fBack"), MenuAction.BACK));
            return lines;
        }

        lines.add(new MenuLine(label("hologram.labels.radar.header", "&6&l— RADAR —"), null));

        // The map itself is drawn in the world as a block-display panel; these lines are just
        // its controls, which is why no map rows appear here.
        drawRadarPanel(village, villages);

        int radius = village.getEffectiveRadius(villages.getDefaultRadius());
        int shown = (int) Math.max(8.0d, radius * radarZoom);

        lines.add(new MenuLine(label("hologram.labels.radar.scale", "&8Showing &7")
                + shown + label("hologram.labels.radar.scale-suffix", "&8 blocks around the bell"), null));
        lines.add(new MenuLine(label("hologram.labels.radar.buildings", "&8Buildings: &7")
                + me.matsubara.realisticvillagers.village.VillageBuildings.detect(villages, village).size(), null));

        lines.add(new MenuLine(toggle("hologram.menus.radar.residents", "&e» &fResidents: ", radarResidents),
                MenuAction.RADAR_TOGGLE_RESIDENTS));
        lines.add(new MenuLine(toggle("hologram.menus.radar.mayor", "&e» &fMayor: ", radarMayor),
                MenuAction.RADAR_TOGGLE_MAYOR));
        lines.add(new MenuLine(label("hologram.menus.radar.zoom-in", "&e» &fZoom in"), MenuAction.RADAR_ZOOM_IN));
        lines.add(new MenuLine(label("hologram.menus.radar.zoom-out", "&e» &fZoom out"), MenuAction.RADAR_ZOOM_OUT));
        lines.add(new MenuLine(label("hologram.menus.mayor.back", "&c« &fBack"), MenuAction.BACK));

        return lines;
    }

    /** (Re)draws the in-world map panel for the current filters and zoom. */
    private void drawRadarPanel(
            me.matsubara.realisticvillagers.village.@NotNull Village village,
            me.matsubara.realisticvillagers.village.@NotNull VillageManager villages) {

        LivingEntity bukkit = npc.bukkit();
        if (bukkit == null || !bukkit.isValid()) return;

        double panelSize = hcfg().getDouble("hologram.radar.panel-size", 2.6d);

        if (radarPanel == null) radarPanel = new RadarPanel();

        radarPanel.draw(
                player,
                radarAnchor(bukkit),
                village,
                villages,
                radarResidents,
                radarMayor,
                panelSize,
                radarZoom,
                RadarPanel.readColors(hcfg().getConfigurationSection("hologram.radar.colors")),
                selectedBuilding,
                this::villagerHead);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) radarPanel.hideFrom(other, plugin);
        }
    }

    /**
     * The villager's own head, using the same skin the plugin renders them with — the one you
     * get from a villager that dies holding a cross.
     */
    private org.bukkit.inventory.@Nullable ItemStack villagerHead(@NotNull Villager villager) {
        IVillagerNPC resident = plugin.getConverter().getNPC(villager).orElse(null);

        String texture = plugin.getNPCTextureURL(resident);
        if (texture == null || texture.isEmpty()) return null;

        return new me.matsubara.realisticvillagers.util.ItemBuilder(org.bukkit.Material.PLAYER_HEAD)
                .setHead(texture, true)
                .build();
    }

    /** The building the player is pointing at on the map, or {@code null}. */
    public me.matsubara.realisticvillagers.village.VillageBuildings.@Nullable Footprint getHoveredBuilding() {
        if (state != MenuState.RADAR || radarPanel == null) return null;
        return radarPanel.hitTest(player);
    }

    /** Lights up whichever building the crosshair is over, recolouring in place. */
    private void updateBuildingHover() {
        if (radarPanel == null || !radarPanel.isVisible()) return;

        var hovered = radarPanel.hitTest(player);

        // Only touch the displays when the hover actually changed.
        if (java.util.Objects.equals(hovered, hoveredBuilding)) return;
        hoveredBuilding = hovered;

        radarPanel.applyHighlight(hoveredBuilding, selectedBuilding,
                RadarPanel.readColors(hcfg().getConfigurationSection("hologram.radar.colors")));
    }

    /**
     * Selects the building under the crosshair and reports it.
     *
     * @return true when a building was hit, so the caller knows the click was consumed.
     */
    public boolean clickBuilding() {
        var building = getHoveredBuilding();
        if (building == null) return false;

        // Clicking the selected building again deselects it.
        selectedBuilding = building.equals(selectedBuilding) ? null : building;

        if (selectedBuilding != null) describeBuilding(building);

        // Recolour in place rather than redrawing, so the map doesn't blink on every click.
        if (radarPanel != null) {
            radarPanel.applyHighlight(hoveredBuilding, selectedBuilding,
                    RadarPanel.readColors(hcfg().getConfigurationSection("hologram.radar.colors")));
        }

        return true;
    }

    /** Turns the in-world settlement border on or off for this player. */
    private void toggleVillageBorders() {
        var borders = plugin.getBorderVisualizer();
        if (borders == null) return;

        boolean showing = borders.toggle(player, mayorVillage());

        player.sendMessage(PluginUtils.translate(showing
                ? label("hologram.labels.mayor.borders-on", "&aVillage borders are now visible.")
                : label("hologram.labels.mayor.borders-off", "&7Village borders hidden.")));

        // Refresh so the toggle's on/off label matches what just happened.
        showState(MenuState.MAYOR);
    }

    /**
     * Lets the mayor's visitor rename the settlement.
     * <p>
     * The anvil opens with the current name already in it rather than blank, so the generated
     * name is a starting point to edit instead of something to retype from memory — which is
     * the whole reason a village is given a real name when it is found.
     */
    private void renameVillage() {
        me.matsubara.realisticvillagers.village.Village village = mayorVillage();
        if (village == null) {
            player.sendMessage(PluginUtils.translate(label("hologram.labels.mayor.unavailable", "&cNo settlement here.")));
            return;
        }

        String title = label("hologram.labels.mayor.rename-title", "&8Name this settlement");
        String current = village.getDisplayName();

        // Off the click: opening an inventory from inside the hologram's own click handling
        // leaves the menu holding a screen that is about to be replaced.
        plugin.getServer().getScheduler().runTask(plugin, () -> new net.wesjd.anvilgui.AnvilGUI.Builder()
                .plugin(plugin)
                .title(PluginUtils.translate(title))
                .text(current)
                .onClick((slot, state) -> {
                    if (slot != net.wesjd.anvilgui.AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.singletonList(
                                net.wesjd.anvilgui.AnvilGUI.ResponseAction.replaceInputText(state.getText()));
                    }

                    String name = state.getText().trim();
                    if (name.isEmpty() || name.length() > MAX_VILLAGE_NAME) {
                        player.sendMessage(PluginUtils.translate(
                                label("hologram.labels.mayor.rename-invalid", "&cThat name won't do.")));
                        return java.util.Collections.singletonList(
                                net.wesjd.anvilgui.AnvilGUI.ResponseAction.close());
                    }

                    village.setName(name);
                    player.sendMessage(PluginUtils.translate(
                            label("hologram.labels.mayor.rename-done", "&aThis settlement is now &f%village%&a.")
                                    .replace("%village%", name)));

                    return java.util.Collections.singletonList(
                            net.wesjd.anvilgui.AnvilGUI.ResponseAction.close());
                })
                .open(player));
    }

    /** Longest a settlement name may be, so it still fits a boss bar and the map's header. */
    private static final int MAX_VILLAGE_NAME = 32;

    /** Tells the player what the building they clicked is and what it is used for. */
    private void describeBuilding(me.matsubara.realisticvillagers.village.VillageBuildings.@NotNull Footprint building) {
        String kind = switch (building.type()) {
            case RESIDENTIAL -> label("hologram.labels.radar.type-residential", "&aResidential");
            case WORKPLACE -> label("hologram.labels.radar.type-workplace", "&6Workplace");
            case MIXED -> label("hologram.labels.radar.type-mixed", "&bHome & workshop");
            case UNKNOWN -> label("hologram.labels.radar.type-unknown", "&7Unknown");
        };

        player.sendMessage(PluginUtils.translate(label("hologram.labels.radar.building-header", "&8— Building —")));

        player.sendMessage(PluginUtils.translate(label("hologram.labels.radar.building-kind", "&7Type: &f%kind%")
                .replace("%kind%", kind)));

        // Renamed from building-size when height was added. The updater only ever adds keys it
        // cannot find, so an existing building-size line would have kept its old text and the
        // height would have had nowhere to appear; under a new name every install gets it.
        player.sendMessage(PluginUtils.translate(label("hologram.labels.radar.building-box",
                        "&7Size: &f%width%x%depth%x%height% &7blocks at &f%x%, %y%, %z%")
                .replace("%width%", String.valueOf(building.width()))
                .replace("%depth%", String.valueOf(building.depth()))
                .replace("%height%", String.valueOf(building.height()))
                .replace("%x%", String.valueOf((int) building.centerX()))
                .replace("%y%", String.valueOf(building.minY()))
                .replace("%z%", String.valueOf((int) building.centerZ()))));

        if (building.beds() > 0) {
            player.sendMessage(PluginUtils.translate(label("hologram.labels.radar.building-beds", "&7Sleeps: &f%count%")
                    .replace("%count%", String.valueOf(building.beds()))));
        }

        if (!building.professions().isEmpty()) {
            player.sendMessage(PluginUtils.translate(label("hologram.labels.radar.building-work", "&7Work: &f%trades%")
                    .replace("%trades%", String.join(", ", building.professions()))));
        }

        sendOutlineButton(building);
    }

    /**
     * Offers a clickable line that outlines the building in the world.
     * <p>
     * The quickest way to check the scanner is honest: click, then look at where the particles
     * land versus where the walls actually are. Kept as a button rather than firing on every
     * building click, because reading what a building is and asking to see it marked out are two
     * different questions, and only one of them wants particles in your face.
     * <p>
     * A chat click can only reach the plugin by running a command, so it runs one — an internal
     * sub-command that is left out of the help text and tab-completion, and handled before the
     * argument-count guard that would otherwise reject its six numbers.
     */
    private void sendOutlineButton(me.matsubara.realisticvillagers.village.VillageBuildings.@NotNull Footprint building) {
        var borders = plugin.getBorderVisualizer();
        LivingEntity bukkit = npc.bukkit();
        if (borders == null || bukkit == null || bukkit.getWorld() == null) return;

        String text = label("hologram.labels.radar.building-outline", "&b[Show this building in the world]");

        net.md_5.bungee.api.chat.TextComponent component =
                new net.md_5.bungee.api.chat.TextComponent(PluginUtils.translate(text));

        component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/rv outline " + building.minX() + " " + building.minZ()
                        + " " + building.maxX() + " " + building.maxZ()
                        + " " + building.minY() + " " + building.maxY()));

        player.spigot().sendMessage(component);
    }

    /**
     * Hands the player over to a chest screen without letting the villager walk away.
     * <p>
     * Opening an inventory closes this menu, and closing it normally releases the villager it had
     * frozen — so the mayor wandered off the moment its own storage was opened. Clearing the flag
     * first means close() leaves the freeze alone; whichever chest screen took over releases it
     * when the player finally shuts that.
     * <p>
     * Run off the click, so the menu isn't holding a screen that is about to be replaced.
     */
    private void handOver(@NotNull Runnable open) {
        frozeVillager = false;
        plugin.getServer().getScheduler().runTask(plugin, open);
    }

    /** Glides the map along with the player, without rebuilding it. */
    private void repositionRadar() {
        if (radarPanel == null || !radarPanel.isVisible()) return;

        LivingEntity bukkit = npc.bukkit();
        if (bukkit == null || !bukkit.isValid()) return;

        radarPanel.reposition(player, radarAnchor(bukkit), hcfg().getDouble("hologram.radar.panel-size", 2.6d));
    }

    /** Where the map hangs: opposite the menu text, above the villager's head. */
    private org.bukkit.@NotNull Location radarAnchor(@NotNull LivingEntity bukkit) {
        double panelSize = hcfg().getDouble("hologram.radar.panel-size", 2.6d);
        double gap = hcfg().getDouble("hologram.radar.side-offset", 0.9d);
        double heightOffset = hcfg().getDouble("hologram.radar.height-offset", 2.4d);

        double away = gap + panelSize / 2.0d;

        return bukkit.getLocation().clone().add(
                -rightX * away,
                bukkit.getHeight() + 0.05d + heightOffset,
                -rightZ * away);
    }

    /** Takes the map panel down. Safe to call when it was never shown. */
    private void hideRadarPanel() {
        if (radarPanel != null) {
            radarPanel.remove();
            radarPanel = null;
        }
        selectedBuilding = null;
        hoveredBuilding = null;
    }

    /** Reports the settlement's stores in chat — far more readable than as hologram lines. */
    /**
     * Opens the settlement's stores.
     * <p>
     * It used to print the contents to chat and stop there, which made the pool something you
     * could read and never use — no way to give goods towards a building, and no way to draw any
     * out. Exactly the gap the commission list had, and missed at the time because I only looked
     * at the one entry I was changing.
     * <p>
     * A chest, for the same reason the commission list is one: goods with amounts against them
     * are a grid, and items you pick up and put down are what a chest already does. A column of
     * floating text can show them but cannot be handed anything.
     */
    private void showMayorStorage() {
        var village = mayorVillage();
        if (village == null) return;

        handOver(() -> player.openInventory(
                new me.matsubara.realisticvillagers.gui.types.StorageGUI(plugin, village, player).getInventory()));
    }

    /** Tells the player whether they may commission a building, and why not when they can't. */
    /**
     * Opens the list of buildings, or says why it can't be opened.
     * <p>
     * It used to only ever print the status line and stop there, so from the hologram menu you
     * could be told you were allowed to commission a building and then have no way to commission
     * one. The list itself stays a chest: it is a grid of buildings with a table of materials
     * against each, which is what a chest is and what a column of floating text is not.
     */
    private void showCommissionStatus() {
        var village = mayorVillage();
        var mayors = plugin.getMayorManager();
        if (village == null || mayors == null) return;

        var result = mayors.canCommission(village, player);

        if (result.isAllowed()) {
            handOver(() -> player.openInventory(
                    new me.matsubara.realisticvillagers.gui.types.CommissionGUI(plugin, village, player).getInventory()));
            return;
        }

        String message = switch (result) {
            case ALLOWED -> label("hologram.labels.mayor.commission-allowed",
                    "&aThe mayor will hear your building plans.");
            case NO_MAYOR -> label("hologram.labels.mayor.commission-no-mayor",
                    "&cThis settlement has no mayor.");
            case LOW_REPUTATION -> label("hologram.labels.mayor.commission-low-reputation",
                    "&cYour standing with the mayor is too low.");
            case NO_FAMILY -> label("hologram.labels.mayor.commission-no-family",
                    "&cYou need a family among this village's residents.");
        };

        player.sendMessage(PluginUtils.translate(message));
    }

    private String toggle(String path, String def, boolean on) {
        return label(path, def) + (on
                ? label("hologram.labels.radar.on", "&aon")
                : label("hologram.labels.radar.off", "&7off"));
    }

    // ── Menu line builders ─────────────────────────────────────────────────────

    private List<MenuLine> buildMainMenuLines() {
        List<MenuLine> lines = new ArrayList<>();
        int rep = npc.getReputation(player.getUniqueId());
        lines.add(new MenuLine(repColor(rep) + label("hologram.labels.menu.rep-prefix", "Rep: ") + rep, null));

        boolean hasTrades = npc.bukkit() instanceof Villager v && !v.getRecipes().isEmpty()
                && v.getRecipes().stream().anyMatch(r -> r.getUses() < r.getMaxUses());

        for (Map<String, Object> item : menuItems("hologram.menus.main")) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            switch (id) {
                case "talk" ->
                    lines.add(new MenuLine(itemText(item, "text", "&e» &fTalk"), MenuAction.TALK));
                case "trade" -> {
                    if (hasTrades)
                        lines.add(new MenuLine(itemText(item, "text",        "&e» &fTrade"), MenuAction.TRADE));
                    else
                        lines.add(new MenuLine(itemText(item, "locked-text", "&8» &7Trade"), null));
                }
                case "interactions" ->
                    lines.add(new MenuLine(itemText(item, "text", "&e» &fInteraction"),  MenuAction.INTERACTIONS));
                case "informations" ->
                    lines.add(new MenuLine(itemText(item, "text", "&7» &fInformations"), MenuAction.INFORMATIONS));
                default -> addCustomLine(lines, item);
            }
        }
        return lines;
    }

    private List<MenuLine> buildTalkMenuLines() {
        boolean isAdult = !(npc.bukkit() instanceof Villager v) || v.isAdult();
        List<MenuLine> lines = new ArrayList<>();

        for (Map<String, Object> item : menuItems("hologram.menus.talk")) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            switch (id) {
                case "chat"     -> lines.add(new MenuLine(itemText(item, "text", "&a» &fChat"),     MenuAction.CHAT));
                case "greet"    -> lines.add(new MenuLine(itemText(item, "text", "&a» &fGreet"),    MenuAction.GREET));
                case "story"    -> lines.add(new MenuLine(itemText(item, "text", "&a» &fStory"),    MenuAction.STORY));
                case "joke"     -> lines.add(new MenuLine(itemText(item, "text", "&a» &fJoke"),     MenuAction.JOKE));
                case "insult"   -> lines.add(new MenuLine(itemText(item, "text", "&c» &fInsult"),   MenuAction.INSULT));
                case "flirt"    -> {
                    if (isAdult && !npc.isFamily(player, true))
                        lines.add(new MenuLine(itemText(item, "text", "&d» &fFlirt"), MenuAction.FLIRT));
                }
                case "proud-of" -> {
                    if (!isAdult)
                        lines.add(new MenuLine(itemText(item, "text", "&b» &fProud Of"), MenuAction.PROUD_OF));
                }
                case "back"     -> lines.add(new MenuLine(itemText(item, "text", "&c« &fBack"),     MenuAction.BACK));
                default -> addCustomLine(lines, item);
            }
        }
        return lines;
    }

    private List<MenuLine> buildInteractionsMenuLines() {
        boolean isAdult   = !(npc.bukkit() instanceof Villager v) || v.isAdult();
        boolean isPartner = npc.isPartner(player);
        boolean canOrder     = canOrder();
        boolean canInventory = plugin.getInventoryListeners().canModifyInventory(npc, player);
        boolean canCombat    = !plugin.getInventoryListeners().notAllowedToModifyInventoryOrName(
                player, npc, Config.WHO_CAN_MODIFY_VILLAGER_COMBAT,  "realisticvillagers.bypass.combat");
        boolean canHome      = !plugin.getInventoryListeners().notAllowedToModifyInventoryOrName(
                player, npc, Config.WHO_CAN_MODIFY_VILLAGER_HOME,    "realisticvillagers.bypass.sethome");

        String lockedSuffix = colorStr(hcfg().getString("hologram.menus.interactions.locked-suffix"), " &7🔒");
        List<MenuLine> lines = new ArrayList<>();

        for (Map<String, Object> item : menuItems("hologram.menus.interactions.items")) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            switch (id) {
                case "gift" ->
                    lines.add(new MenuLine(itemText(item, "text", "&e» &fGift"), MenuAction.GIFT));

                case "order" -> {
                    String prefix = itemText(item, "prefix", "&e» &fOrder: ");
                    String orderLabel = switch (menuOrder) {
                        case FOLLOW   -> itemText(item, "follow-text", "&aFollow");
                        case STAY     -> itemText(item, "stay-text",   "&eStay");
                        case WANDERER -> itemText(item, "wander-text", "&7Wander");
                    };
                    if (canOrder)
                        lines.add(new MenuLine(prefix + orderLabel, MenuAction.ORDER));
                    else
                        lines.add(new MenuLine("§7» §7Order: §7" + ChatColor.stripColor(orderLabel) + lockedSuffix, null));
                }

                case "inventory" ->
                    lines.add(condLine(canInventory, "§e» ", itemText(item, "text", "Inventory"),  MenuAction.INSPECT_INVENTORY, lockedSuffix));
                case "set-home"  ->
                    lines.add(condLine(canHome,      "§e» ", itemText(item, "text", "Set Home"),   MenuAction.SET_HOME,          lockedSuffix));
                case "combat"    ->
                    lines.add(condLine(canCombat,    "§e» ", itemText(item, "text", "Combat"),     MenuAction.COMBAT,            lockedSuffix));

                case "procreate" -> {
                    if (isPartner && isAdult)
                        lines.add(condLine(true, "§d» ", itemText(item, "text", "Procreate"), MenuAction.PROCREATE, lockedSuffix));
                }
                case "divorce" -> {
                    if (isPartner && !awaitingDivorceConfirm)
                        lines.add(new MenuLine(itemText(item, "text", "&c» &fDivorce"), MenuAction.DIVORCE));
                }
                case "divorce-confirm" -> {
                    if (isPartner && awaitingDivorceConfirm)
                        lines.add(new MenuLine(itemText(item, "text", "&c&l» Confirm?"), MenuAction.DIVORCE_CONFIRM));
                }
                case "divorce-cancel"  -> {
                    if (isPartner && awaitingDivorceConfirm)
                        lines.add(new MenuLine(itemText(item, "text", "&7» &fCancel"),   MenuAction.CANCEL_DIVORCE));
                }
                case "back" ->
                    lines.add(new MenuLine(itemText(item, "text", "&c« &fBack"), MenuAction.BACK));
                default -> addCustomLine(lines, item);
            }
        }
        return lines;
    }

    private boolean canOrder() {
        if (player.hasPermission("realisticvillagers.bypass.followme")) return true;
        Config bypass  = PluginUtils.getOrNull(Config.class, "FAMILY_BYPASS_ASK_TO_FOLLOW");
        Config required = PluginUtils.getOrNull(Config.class, "REPUTATION_REQUIRED_TO_ASK_TO_FOLLOW");
        if (bypass != null && bypass.asBool() && npc.isFamily(player, true)) return true;
        return required != null && npc.getReputation(player) >= required.asInt();
    }

    private MenuLine condLine(boolean canUse, String coloredPrefix, String lineLabel, MenuAction action, String lockedSuffix) {
        if (canUse) return new MenuLine(coloredPrefix + "§f" + lineLabel, action);
        return new MenuLine("§7» §7" + lineLabel + lockedSuffix, null);
    }

    /** True if "hologram.menus.<id>" defines a navigable sub-menu: either a flat list of
     *  items, or a paged menu (a section with a "pages" list under it). */
    private boolean isNavigableCustomMenu(String id) {
        String base = "hologram.menus." + id;
        FileConfiguration c = hcfg();
        return c.isList(base) || c.isList(base + ".pages");
    }

    /** True if "gui.custom.<id>" defines a config-driven chest GUI (see gui.yml). */
    private boolean isCustomGui(String id) {
        return plugin.getGuiConfig().isConfigurationSection("gui.custom." + id);
    }

    /** Evaluates an item's optional "requires:" block. With no block, everything passes.
     *  Supports "permission: <node>" and "min-reputation: <n>"; both are checked when present. */
    private boolean meetsRequirement(@NotNull Map<String, Object> item) {
        if (!(item.get("requires") instanceof Map<?, ?> requires)) return true;

        Object permission = requires.get("permission");
        if (permission != null && !player.hasPermission(String.valueOf(permission))) return false;

        Object minRep = requires.get("min-reputation");
        return !(minRep instanceof Number number) || npc.getReputation(player.getUniqueId()) >= number.intValue();
    }

    /** Adds a display line for any item whose {@code id} is not a recognised action.
     *  If the item has a "requires:" block the player doesn't satisfy, it's shown locked
     *  (greyed out with a padlock suffix) or hidden entirely, per "locked-display: suffix|hide"
     *  (default suffix). Otherwise, if the id matches another top-level "hologram.menus.<id>"
     *  entry the line opens it as a sub-menu (see {@link #isNavigableCustomMenu}); if it matches
     *  a "gui.custom.<id>" section in gui.yml it opens that chest GUI; if it matches a top-level
     *  key in messages/default.yml it starts that custom chat/conversation. Anything else is a
     *  plain non-clickable line — useful for separators, headers, or decorative text.
     *  The item must have a non-empty {@code text:} key; items without one are silently skipped. */
    @SuppressWarnings("deprecation")
    private void addCustomLine(List<MenuLine> lines, Map<String, Object> item) {
        Object v = item.get("text");
        if (v == null) return;
        String text = colorStr(String.valueOf(v), "");
        if (text.isEmpty()) return;

        if (!meetsRequirement(item)) {
            if (String.valueOf(item.getOrDefault("locked-display", "suffix")).equalsIgnoreCase("hide")) return;
            String lockedSuffix = colorStr(String.valueOf(item.getOrDefault("locked-suffix", " &7🔒")), " &7🔒");
            lines.add(new MenuLine("§7" + ChatColor.stripColor(text) + lockedSuffix, null));
            return;
        }

        String id = String.valueOf(item.getOrDefault("id", ""));
        if (!id.isEmpty() && !RESERVED_MENU_IDS.contains(id)) {
            if (isNavigableCustomMenu(id)) {
                lines.add(new MenuLine(text, MenuAction.CUSTOM_MENU, id));
                return;
            }
            if (isCustomGui(id)) {
                lines.add(new MenuLine(text, MenuAction.CUSTOM_GUI, id));
                return;
            }
            if (plugin.getMessages().isCustomChatType(id)) {
                lines.add(new MenuLine(text, MenuAction.CUSTOM_CHAT, id));
                return;
            }
        }
        lines.add(new MenuLine(text, null));
    }

    /** Builds the line list for a config-defined custom sub-menu, either flat or paged. */
    private List<MenuLine> buildCustomMenuLines(String menuName) {
        String base = "hologram.menus." + menuName;
        if (hcfg().isList(base + ".pages")) return buildPagedCustomMenuLines(menuName);

        List<MenuLine> lines = new ArrayList<>();
        for (Map<String, Object> item : menuItems(base)) {
            String id = String.valueOf(item.getOrDefault("id", ""));
            if (id.equals("back")) {
                lines.add(new MenuLine(itemText(item, "text", "&c« &fBack"), MenuAction.BACK));
            } else {
                addCustomLine(lines, item);
            }
        }
        return lines;
    }

    /** Builds the line list for one page of a paged custom sub-menu (see holograms.yml for the format). */
    @SuppressWarnings("unchecked")
    private List<MenuLine> buildPagedCustomMenuLines(String menuName) {
        String base = "hologram.menus." + menuName;
        FileConfiguration c = hcfg();

        List<?> rawPages = c.getList(base + ".pages", Collections.emptyList());
        if (rawPages.isEmpty()) return List.of();
        customPage = Math.max(0, Math.min(customPage, rawPages.size() - 1));

        Object rawPage = rawPages.get(customPage);
        Map<String, Object> page = rawPage instanceof Map ? (Map<String, Object>) rawPage : Collections.emptyMap();

        List<MenuLine> lines = new ArrayList<>();
        String header = itemText(page, "header", "");
        if (!header.isEmpty()) lines.add(new MenuLine(header, null));

        if (page.get("items") instanceof List<?> rawItems) {
            for (Object o : rawItems) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> item = (Map<String, Object>) o;
                String id = String.valueOf(item.getOrDefault("id", ""));
                if (id.equals("back")) {
                    lines.add(new MenuLine(itemText(item, "text", "&c« &fBack"), MenuAction.BACK));
                } else {
                    addCustomLine(lines, item);
                }
            }
        }

        if (rawPages.size() > 1) {
            String pageText = colorStr(c.getString(base + ".page-format"), "&8[&7%current%&8/&7%total%&8]")
                    .replace("%current%", String.valueOf(customPage + 1))
                    .replace("%total%",   String.valueOf(rawPages.size()));
            lines.add(new MenuLine(pageText, null));
            lines.add(new MenuLine(colorStr(c.getString(base + ".navigation.prev"), "&e« &fPrev"), MenuAction.CUSTOM_PAGE_PREV));
            lines.add(new MenuLine(colorStr(c.getString(base + ".navigation.next"), "&e» &fNext"), MenuAction.CUSTOM_PAGE_NEXT));
        }
        return lines;
    }

    /** Returns the custom sub-menu target of the currently hovered menu line, or null. */
    private @Nullable String hoveredCustomTarget() {
        if (hoveredDisplayIdx == null) return null;
        List<MenuLine> lines = currentLines();
        if (hoveredDisplayIdx >= lines.size()) return null;
        return lines.get(hoveredDisplayIdx).customTarget();
    }

    /** Reads actual NPC state. Must be called BEFORE stayInPlace() is applied. */
    private VillagerOrder detectCurrentOrder() {
        if (npc.isFollowing()) return VillagerOrder.FOLLOW;
        if (npc.isStayingInPlace()) return VillagerOrder.STAY;
        return VillagerOrder.WANDERER;
    }

    private enum VillagerOrder { FOLLOW, STAY, WANDERER }

    // ── Combat / flee check ────────────────────────────────────────────────────

    private boolean isInCombatOrFleeing() {
        if (npc.isFighting() || npc.isInsideRaid()) return true;
        String activity = npc.getActivityName("").toLowerCase(Locale.ROOT);
        return activity.equals("hide") || activity.equals("panic");
    }

    // ── Action dispatch ────────────────────────────────────────────────────────

    public void handleAction(@NotNull MenuAction action) {
        if (closed) return;

        switch (action) {
            case TALK        -> { pushHistory(); showState(MenuState.TALK); }
            case INTERACTIONS-> { pushHistory(); showState(MenuState.INTERACTIONS); }
            case MAYOR_RADAR -> { pushHistory(); showState(MenuState.RADAR); }
            case MAYOR_STORAGE -> showMayorStorage();
            case MAYOR_COMMISSION -> showCommissionStatus();
            case MAYOR_BORDERS -> toggleVillageBorders();
            case MAYOR_RENAME -> renameVillage();
            case RADAR_TOGGLE_RESIDENTS -> { radarResidents = !radarResidents; showState(MenuState.RADAR); }
            case RADAR_TOGGLE_MAYOR -> { radarMayor = !radarMayor; showState(MenuState.RADAR); }
            // Zoom narrows the area the panel covers, magnifying the middle of the village.
            case RADAR_ZOOM_IN -> { radarZoom = Math.max(0.25d, radarZoom - 0.25d); showState(MenuState.RADAR); }
            case RADAR_ZOOM_OUT -> { radarZoom = Math.min(2.0d, radarZoom + 0.25d); showState(MenuState.RADAR); }
            case CUSTOM_MENU -> {
                String target = hoveredCustomTarget();
                if (target != null) { pushHistory(); customPage = 0; showState(MenuState.CUSTOM, target); }
            }
            case CUSTOM_PAGE_PREV -> { customPage = Math.max(0, customPage - 1); showState(MenuState.CUSTOM, customMenuName); }
            case CUSTOM_PAGE_NEXT -> { customPage++; showState(MenuState.CUSTOM, customMenuName); }
            case CUSTOM_CHAT -> {
                String type = hoveredCustomTarget();
                if (type != null) dispatchCustomChat(type);
            }
            case CUSTOM_GUI -> {
                String guiName = hoveredCustomTarget();
                if (guiName != null) {
                    close(true);
                    plugin.getInventoryListeners().handleOpenCustomGUI(npc, player, guiName);
                }
            }
            case BACK        -> popState();

            case ORDER -> cycleOrder();

            case CHAT    -> dispatchChat(GUIInteractType.CHAT);
            case GREET   -> dispatchChat(GUIInteractType.GREET);
            case STORY   -> dispatchChat(GUIInteractType.STORY);
            case JOKE    -> dispatchChat(GUIInteractType.JOKE);
            case INSULT  -> dispatchChat(GUIInteractType.INSULT);
            case FLIRT   -> {
                if (!(npc.bukkit() instanceof Villager v) || !v.isAdult()) return;
                if (npc.isFamily(player, true)) return;
                dispatchChat(GUIInteractType.FLIRT);
            }
            case PROUD_OF -> {
                if (npc.bukkit() instanceof Villager v && v.isAdult()) return;
                dispatchChat(GUIInteractType.BE_PROUD_OF);
            }

            case TRADE -> {
                if (!(npc.bukkit() instanceof Villager villagerEntity)) return;
                close(false);
                // ValhallaMMO support: the same door the chest GUI's "Trade" item goes through,
                // so the two menus agree on whether a villager trades through this plugin or
                // through Valhalla's own interface. See InventoryListeners#startTrading.
                plugin.getInventoryListeners().startTrading(npc, player, villagerEntity, () -> npc.startTrading(player));
            }

            case FOLLOW_ME -> {
                plugin.getInventoryListeners().handleFollorOrStay(npc, player, InteractType.FOLLOW_ME, false);
                close(false);
            }
            case STAY_HERE -> {
                frozeVillager = false;
                plugin.getInventoryListeners().handleFollorOrStay(npc, player, InteractType.STAY_HERE, false);
                close(false);
            }

            case GIFT      -> { plugin.getInventoryListeners().handleGift(npc, player);      close(false); }
            case PROCREATE -> { plugin.getInventoryListeners().handleProcreate(npc, player); close(false); }
            case DIVORCE   -> { awaitingDivorceConfirm = true;  showState(MenuState.INTERACTIONS); }
            case DIVORCE_CONFIRM -> { plugin.getInventoryListeners().handleDivorce(npc, player); close(false); }
            case CANCEL_DIVORCE  -> { awaitingDivorceConfirm = false; showState(MenuState.INTERACTIONS); }
            case SET_HOME  -> { plugin.getInventoryListeners().handleSetHome(npc, player);   close(false); }
            case INFORMATIONS -> {
                if (infoPanelVisible) {
                    closeInfoPanel();
                } else {
                    infoPanelVisible = true;
                    spawnInfoPanel(npc.bukkit().getLocation());
                }
            }

            case INSPECT_INVENTORY -> {
                close(true);
                plugin.getInventoryListeners().handleInspectInventory(npc, player);
            }
            case COMBAT -> {
                boolean opened = plugin.getInventoryListeners().handleCombatSettingsGUI(npc, player);
                close(opened);
            }
            case INFO_PREV -> { infoSection = (infoSection - 1 + INFO_SECTIONS) % INFO_SECTIONS; childrenPage = 0; updateInfoPanel(); }
            case INFO_NEXT -> { infoSection = (infoSection + 1) % INFO_SECTIONS; childrenPage = 0; updateInfoPanel(); }
            case CHILDREN_PREV -> { childrenPage = Math.max(0, childrenPage - 1); updateInfoPanel(); }
            case CHILDREN_NEXT -> {
                int total = npc.getChildrens().size();
                int maxPage = (total - 1) / childrenPerPage();
                childrenPage = Math.min(maxPage, childrenPage + 1);
                updateInfoPanel();
            }
        }
    }

    /** Cycles through Follow → Stay → Wander → Follow and applies the change in-place. */
    private void cycleOrder() {
        VillagerOrder next = switch (menuOrder) {
            case FOLLOW   -> VillagerOrder.STAY;
            case STAY     -> VillagerOrder.WANDERER;
            case WANDERER -> VillagerOrder.FOLLOW;
        };
        applyOrder(next);
        menuOrder = next;
        // Rebuild the interactions sub-menu so the button text updates.
        showState(MenuState.INTERACTIONS);
    }

    private void applyOrder(@NotNull VillagerOrder order) {
        switch (order) {
            case FOLLOW -> {
                // Remove menu freeze first; otherwise FOLLOW_ME goal fights stayInPlace.
                npc.stopStayingInPlace();
                frozeVillager = false;
                plugin.getInventoryListeners().handleFollorOrStay(npc, player, InteractType.FOLLOW_ME, false);
            }
            case STAY -> {
                // handleFollorOrStay calls stayInPlace() internally.
                // Clear frozeVillager so close() does NOT call stopStayingInPlace() — stay is intentional.
                frozeVillager = false;
                plugin.getInventoryListeners().handleFollorOrStay(npc, player, InteractType.STAY_HERE, false);
            }
            case WANDERER -> {
                if (npc.isStayingInPlace()) npc.stopStayingInPlace();
                // Clear FOLLOW_ME interact type so follow AI deactivates while hologram stays open.
                npc.setInteractType(InteractType.GUI);
                frozeVillager = false;
            }
        }
    }

    private void dispatchChat(@NotNull GUIInteractType type) {
        if (!(npc.bukkit() instanceof Villager villager)) return;
        Messages messages = plugin.getMessages();
        if (plugin.getCooldownManager().canInteract(player, villager, type.getName())) {
            plugin.getInventoryListeners().handleChatInteraction(npc, type, player);
        } else {
            messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
        }
        close(false);
    }

    /** Same as {@link #dispatchChat}, but for a config-only custom chat/conversation type. */
    private void dispatchCustomChat(@NotNull String type) {
        if (!(npc.bukkit() instanceof Villager villager)) return;
        Messages messages = plugin.getMessages();
        if (plugin.getCooldownManager().canInteract(player, villager, type)) {
            plugin.getInventoryListeners().handleCustomChatInteraction(npc, type, player);
        } else {
            messages.send(player, Messages.Message.INTERACT_FAIL_IN_COOLDOWN);
        }
        close(false);
    }

    private void showState(MenuState newState) {
        showState(newState, null);
    }

    private void showState(MenuState newState, @Nullable String customName) {
        if (newState != MenuState.INTERACTIONS) awaitingDivorceConfirm = false;

        // The map panel belongs to the radar screen only; leaving it must take the tiles down,
        // or they'd hang in the world with nothing owning them.
        if (newState != MenuState.RADAR) hideRadarPanel();

        this.state = newState;
        this.customMenuName = newState == MenuState.CUSTOM ? customName : null;
        spawnMenuLines(currentLines());
    }

    /** Remembers the current menu so BACK can return to it, however deep the navigation goes. */
    private void pushHistory() {
        navHistory.addLast(new NavTarget(state, customMenuName, customPage));
    }

    /** Returns to the previous menu in the navigation history, or MAIN if there isn't one. */
    private void popState() {
        NavTarget target = navHistory.pollLast();
        if (target == null) {
            // The mayor's screen is a root, not a branch off the villager menu: backing out of
            // it closes the session rather than dropping into Talk/Trade for the mayor.
            if (state == MenuState.MAYOR) {
                close(false, true);
                return;
            }
            customPage = 0;
            showState(MenuState.MAIN);
        } else {
            customPage = target.customPage();
            showState(target.state(), target.customMenuName());
        }
    }

    private void closeInfoPanel() {
        for (TextDisplay d : infoDisplays) d.remove();
        infoDisplays.clear();
        infoHalfWidths.clear();
        infoActionMap.clear();
        hoveredInfoIdx = null;
        infoPanelVisible = false;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Normal close — always stops the NPC interaction (used by action buttons). */
    public void close(boolean keepInteracting) {
        close(keepInteracting, false);
    }

    /**
     * Full close.
     * @param keepInteracting true when opening another GUI immediately (COMBAT, INSPECT, etc.) —
     *                        the NPC must stay in interacting state for that GUI.
     * @param preserveOrder   true when the user explicitly closed the menu (right-click, distance)
     *                        without triggering an action — preserves FOLLOW/STAY mode so the
     *                        villager keeps its order after the hologram closes.
     */
    public void close(boolean keepInteracting, boolean preserveOrder) {
        if (closed) return;
        closed = true;

        // Before anything else: the map panel is world entities, and leaving them behind on a
        // close (including the auto-close on distance) would litter the world.
        hideRadarPanel();

        if (tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }

        if (frozeVillager) {
            LivingEntity bukkit = npc.bukkit();
            if (bukkit != null && bukkit.isValid()) npc.stopStayingInPlace();
            frozeVillager = false;
        }

        for (TextDisplay d : menuDisplays)  d.remove();
        for (TextDisplay d : headDisplays)  d.remove();
        for (TextDisplay d : infoDisplays)  d.remove();
        menuDisplays.clear();
        menuHalfWidths.clear();
        headDisplays.clear();
        displayActionMap.clear();
        infoDisplays.clear();
        infoHalfWidths.clear();
        infoActionMap.clear();

        plugin.getHologramManager().unregisterMenu(player.getUniqueId());

        // Don't stop interacting when keepInteracting=true OR when preserving a non-WANDER order
        // (so FOLLOW/STAY mode persists after the hologram closes).
        boolean shouldStop = !keepInteracting && (!preserveOrder || menuOrder == VillagerOrder.WANDERER);
        if (shouldStop && npc.isInteracting()
                && player.getUniqueId().equals(npc.getInteractingWith())) {
            npc.stopInteracting();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns the action that the player is currently aiming at (menu or info panel), or null. */
    public @Nullable MenuAction getHoveredAction() {
        MenuAction infoAction = getHoveredInfoAction();
        if (infoAction != null) return infoAction;

        if (hoveredDisplayIdx == null) return null;
        List<MenuLine> lines = currentLines();
        if (hoveredDisplayIdx >= lines.size()) return null;
        return lines.get(hoveredDisplayIdx).action();
    }

    public IVillagerNPC getNPC() { return npc; }
    public Player getPlayer()    { return player; }

    private record MenuLine(String text, @Nullable MenuAction action, @Nullable String customTarget) {
        private MenuLine(String text, @Nullable MenuAction action) {
            this(text, action, null);
        }
    }
}
