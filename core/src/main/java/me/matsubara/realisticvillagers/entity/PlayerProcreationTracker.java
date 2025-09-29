package me.matsubara.realisticvillagers.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProcreationTracker {

    // Stores player procreation times (player1 -> (player2 -> timestamp))
    private final Map<UUID, Map<UUID, Long>> procreationTimes = new HashMap<>();

    public void setLastProcreation(UUID player1, UUID player2) {
        long currentTime = System.currentTimeMillis();


        procreationTimes.putIfAbsent(player1, new HashMap<>());
        procreationTimes.putIfAbsent(player2, new HashMap<>());

        procreationTimes.get(player1).put(player2, currentTime);
        procreationTimes.get(player2).put(player1, currentTime);
    }


    public long getLastProcreation(UUID player1, UUID player2) {
        return procreationTimes.getOrDefault(player1, new HashMap<>()).getOrDefault(player2, 0L);
    }
}