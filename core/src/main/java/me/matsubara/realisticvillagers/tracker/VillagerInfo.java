package me.matsubara.realisticvillagers.tracker;

import java.util.UUID;

public class VillagerInfo {

    private final UUID uuid;
    private int id;
    private String lastKnownName;
    private long lastSeen;

    // After a week, consider as dead.
    public final static long CONSIDER_AS_DEAD = 604800000;

    public VillagerInfo(UUID uuid, int id, String lastKnownName) {
        this(uuid, id, lastKnownName, System.currentTimeMillis());
    }

    public VillagerInfo(UUID uuid, int id, String lastKnownName, long lastSeen) {
        this.uuid = uuid;
        this.id = id;
        this.lastKnownName = lastKnownName;
        this.lastSeen = lastSeen;
    }

    public UUID getUUID() {
        return uuid;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public void updateLastSeen() {
        setLastSeen(System.currentTimeMillis());
    }

    public boolean isDead() {
        return lastSeen == -1L || System.currentTimeMillis() - lastSeen > CONSIDER_AS_DEAD;
    }
}