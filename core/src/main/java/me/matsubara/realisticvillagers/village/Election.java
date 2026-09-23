package me.matsubara.realisticvillagers.village;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A running election for a vacant mayor's seat.
 * <p>
 * Votes are weighted rather than counted: a player's voice is worth more than a settler's,
 * so an inhabited server is decided by its players while an empty one still resolves on its
 * own instead of stalling the village forever.
 */
@Getter
public final class Election {

    private final UUID villageId;
    private final List<Candidate> candidates;
    private final long endsAt;

    /** Voter → the candidate they backed. Also enforces one vote each. */
    private final Map<UUID, UUID> votes = new HashMap<>();

    /** Voters that are players, which is what makes their vote weigh more. */
    private final Set<UUID> playerVoters = new HashSet<>();

    private static final Random RANDOM = new Random();

    public Election(UUID villageId, List<Candidate> candidates, long durationMillis) {
        this.villageId = villageId;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.endsAt = System.currentTimeMillis() + durationMillis;
    }

    /** One villager standing for office, with the platform they drew. */
    @Getter
    public static final class Candidate {

        private final UUID villagerId;
        private final ElectoralProgram program;

        /**
         * What this villager was before it put on the campaign robes.
         * <p>
         * Kept so a loser can be restored exactly. Assuming they were unemployed is what let
         * failed restores pile up: a villager stuck as a nitwit no longer counts as unemployed,
         * so the next election recruited someone else, and eventually the whole reserve pool
         * had been converted.
         */
        private final org.bukkit.entity.Villager.Profession originalProfession;

        public Candidate(UUID villagerId, ElectoralProgram program, org.bukkit.entity.Villager.Profession originalProfession) {
            this.villagerId = villagerId;
            this.program = program;
            this.originalProfession = originalProfession;
        }
    }

    public boolean isFinished() {
        return System.currentTimeMillis() >= endsAt;
    }

    public long getRemainingMillis() {
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    public long getRemainingSeconds() {
        return getRemainingMillis() / 1000L;
    }

    public boolean hasVoted(@Nullable UUID voter) {
        return voter != null && votes.containsKey(voter);
    }

    public boolean isCandidate(@Nullable UUID villagerId) {
        return getCandidate(villagerId) != null;
    }

    public @Nullable Candidate getCandidate(@Nullable UUID villagerId) {
        if (villagerId == null) return null;
        for (Candidate candidate : candidates) {
            if (candidate.getVillagerId().equals(villagerId)) return candidate;
        }
        return null;
    }

    /**
     * Records a vote.
     *
     * @return {@code false} if the voter already voted, the candidate isn't standing, or the
     * election is over — in every case nothing is recorded.
     */
    public boolean vote(@Nullable UUID voter, @Nullable UUID candidateId, boolean isPlayer) {
        if (voter == null || candidateId == null) return false;
        if (isFinished() || hasVoted(voter) || !isCandidate(candidateId)) return false;

        votes.put(voter, candidateId);
        if (isPlayer) playerVoters.add(voter);

        return true;
    }

    /** Total weight backing this candidate. */
    public int getWeightedVotes(@Nullable UUID candidateId, int playerWeight, int villagerWeight) {
        if (candidateId == null) return 0;

        int total = 0;
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            if (!entry.getValue().equals(candidateId)) continue;
            total += playerVoters.contains(entry.getKey()) ? playerWeight : villagerWeight;
        }
        return total;
    }

    public int getTotalVotes() {
        return votes.size();
    }

    /**
     * The candidate with the most weight behind them.
     * <p>
     * Ties — including the "nobody voted at all" case on an empty server — are broken by
     * drawing lots among the joint leaders, so an election always produces a mayor.
     */
    public @Nullable Candidate getWinner(int playerWeight, int villagerWeight) {
        if (candidates.isEmpty()) return null;

        int best = -1;
        List<Candidate> leaders = new ArrayList<>();

        for (Candidate candidate : candidates) {
            int score = getWeightedVotes(candidate.getVillagerId(), playerWeight, villagerWeight);
            if (score > best) {
                best = score;
                leaders.clear();
                leaders.add(candidate);
            } else if (score == best) {
                leaders.add(candidate);
            }
        }

        if (leaders.isEmpty()) return null;
        return leaders.get(RANDOM.nextInt(leaders.size()));
    }

    /** Candidates ordered by weight, strongest first — used to render the ballot. */
    public @NotNull List<Candidate> getRanked(int playerWeight, int villagerWeight) {
        List<Candidate> ranked = new ArrayList<>(candidates);
        ranked.sort((a, b) -> Integer.compare(
                getWeightedVotes(b.getVillagerId(), playerWeight, villagerWeight),
                getWeightedVotes(a.getVillagerId(), playerWeight, villagerWeight)));
        return ranked;
    }
}
