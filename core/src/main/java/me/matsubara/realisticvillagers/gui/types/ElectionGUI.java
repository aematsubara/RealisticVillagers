package me.matsubara.realisticvillagers.gui.types;

import me.matsubara.realisticvillagers.village.Election;
import me.matsubara.realisticvillagers.village.ElectionManager;
import me.matsubara.realisticvillagers.village.ElectoralProgram;
import me.matsubara.realisticvillagers.village.Village;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The ballot a player sees when they click the village bell during an election.
 * <p>
 * Deliberately a standalone {@link InventoryHolder} rather than an {@code InteractGUI}: this
 * screen belongs to a bell and a village, not to one villager, and keeping it separate avoids
 * threading a fake NPC through the shared villager-GUI machinery.
 */
@Getter
public final class ElectionGUI implements InventoryHolder {

    private final RealisticVillagers plugin;
    private final UUID villageId;
    private final Inventory inventory;

    /** Slot → the candidate it represents, so a click maps straight back to a vote. */
    private final Map<Integer, UUID> candidateSlots = new HashMap<>();

    private static final int SIZE = 27;
    private static final int[] LAYOUT = {11, 13, 15, 10, 16, 12, 14};

    public ElectionGUI(@NotNull RealisticVillagers plugin, @NotNull Village village, @NotNull Election election, @NotNull Player viewer) {
        this.plugin = plugin;
        this.villageId = village.getId();

        String title = text("gui.election.title", "&8Election: %village%")
                .replace("%village%", village.getDisplayName())
                .replace("%time%", formatTime(election.getRemainingSeconds()));

        this.inventory = Bukkit.createInventory(this, SIZE, me.matsubara.realisticvillagers.util.PluginUtils.translate(title));

        build(village, election, viewer);
    }

    private void build(@NotNull Village village, @NotNull Election election, @NotNull Player viewer) {
        ElectionManager elections = plugin.getElectionManager();
        int playerWeight = elections.getPlayerVoteWeight();
        int villagerWeight = elections.getVillagerVoteWeight();

        List<Election.Candidate> candidates = election.getCandidates();
        boolean alreadyVoted = election.hasVoted(viewer.getUniqueId());

        for (int i = 0; i < candidates.size() && i < LAYOUT.length; i++) {
            Election.Candidate candidate = candidates.get(i);
            int slot = LAYOUT[i];

            inventory.setItem(slot, buildCandidateItem(
                    village,
                    candidate,
                    election.getWeightedVotes(candidate.getVillagerId(), playerWeight, villagerWeight),
                    alreadyVoted,
                    election.getVotes().get(viewer.getUniqueId())));

            candidateSlots.put(slot, candidate.getVillagerId());
        }

        // Timer sits apart from the candidates so a mis-click can't land on it.
        inventory.setItem(SIZE - 1, new ItemBuilder(Material.CLOCK)
                .setDisplayName(text("gui.election.timer", "&eTime left: &f%time%")
                        .replace("%time%", formatTime(election.getRemainingSeconds())))
                .build());
    }

    private @NotNull ItemStack buildCandidateItem(
            @NotNull Village village,
            @NotNull Election.Candidate candidate,
            int votes,
            boolean alreadyVoted,
            @Nullable UUID votedFor) {

        ElectoralProgram program = candidate.getProgram();

        List<String> lore = new ArrayList<>();
        lore.add(text("gui.election.program-line", "&7Program: &f%program%")
                .replace("%program%", programName(program)));
        lore.add("");
        lore.add(effectLine("gui.election.effect-trade", "&7Trade prices: %value%", program.getTradePriceMultiplier()));
        lore.add(effectLine("gui.election.effect-cost", "&7Build cost: %value%", program.getBuildCostMultiplier()));
        lore.add(effectLine("gui.election.effect-time", "&7Build time: %value%", program.getBuildTimeMultiplier()));
        lore.add(effectLine("gui.election.effect-harvest", "&7Resource gathering: %value%", program.getHarvestMultiplier()));
        lore.add("");
        lore.add(text("gui.election.votes-line", "&7Votes: &f%votes%").replace("%votes%", String.valueOf(votes)));

        boolean isMyPick = votedFor != null && votedFor.equals(candidate.getVillagerId());
        if (isMyPick) {
            lore.add(text("gui.election.your-vote", "&aYou voted for this candidate"));
        } else if (alreadyVoted) {
            lore.add(text("gui.election.already-voted", "&cYou already voted"));
        } else {
            lore.add(text("gui.election.click-to-vote", "&eClick to vote"));
        }

        // The candidate's own face, so a ballot reads as a choice between people rather than
        // between three identical sheets of paper. Falls back to a plain head when the skin
        // can't be resolved, and to paper when even that fails.
        ItemBuilder builder = headOf(candidate.getVillagerId());

        ItemStack item = builder
                .setDisplayName(text("gui.election.candidate-name", "&e%name%")
                        .replace("%name%", resolveName(village, candidate.getVillagerId())))
                .setLore(lore)
                .build();

        // Enchant glow marks the one you backed, now that the material no longer can.
        if (isMyPick) {
            try {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
            } catch (Throwable ignored) {
                // Enchantment names have moved between versions; the lore still says who you
                // voted for.
            }
        }

        return item;
    }

    /**
     * A head item wearing the candidate's own skin.
     * <p>
     * Uses the same texture the plugin renders the villager with, which is what makes the face
     * on the ballot the face standing by the bell.
     */
    private @NotNull ItemBuilder headOf(@NotNull UUID villagerId) {
        try {
            Villager villager = plugin.getVillageManager().findVillagerById(villagerId);
            IVillagerNPC npc = villager != null ? plugin.getConverter().getNPC(villager).orElse(null) : null;

            String texture = plugin.getNPCTextureURL(npc);
            if (texture != null && !texture.isEmpty()) {
                return new ItemBuilder(Material.PLAYER_HEAD).setHead(texture, true);
            }

            return new ItemBuilder(Material.PLAYER_HEAD);
        } catch (Throwable ignored) {
            return new ItemBuilder(Material.PAPER);
        }
    }

    /**
     * Renders a multiplier the way a player reads it: {@code 0.8} becomes {@code -20%}, and
     * green/red so the trade-off is obvious at a glance.
     */
    private @NotNull String effectLine(String path, String fallback, double multiplier) {
        int percent = (int) Math.round((multiplier - 1.0d) * 100.0d);

        String value;
        if (percent == 0) {
            value = "&7unchanged";
        } else if (percent < 0) {
            value = "&a" + percent + "%";
        } else {
            value = "&c+" + percent + "%";
        }

        return text(path, fallback).replace("%value%", value);
    }

    private @NotNull String resolveName(@NotNull Village village, @NotNull UUID villagerId) {
        // Direct id lookup rather than walking the resident list once per candidate.
        Villager villager = plugin.getVillageManager().findVillagerById(villagerId);
        if (villager == null) return "Candidate";

        IVillagerNPC npc = plugin.getConverter().getNPC(villager).orElse(null);
        return npc != null && npc.getVillagerName() != null ? npc.getVillagerName() : "Candidate";
    }

    private @NotNull String programName(@NotNull ElectoralProgram program) {
        String configured = plugin.getGuiConfig().getString("gui.election.programs." + program.getKey());
        if (configured != null && !configured.isEmpty()) return configured;

        // Fall back to a readable form of the key so a missing entry never shows "null".
        String key = program.getKey().replace('-', ' ');
        return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
    }

    private @NotNull String text(String path, String fallback) {
        // Screen text belongs to gui.yml, not the main config.
        String value = plugin.getGuiConfig().getString(path);
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private @NotNull String text(String path) {
        return text(path, "");
    }

    private static @NotNull String formatTime(long seconds) {
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
    }

    /** The candidate at this slot, or {@code null} when the slot isn't a candidate. */
    public @Nullable UUID getCandidateAt(int slot) {
        return candidateSlots.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
