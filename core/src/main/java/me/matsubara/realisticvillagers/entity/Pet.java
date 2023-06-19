package me.matsubara.realisticvillagers.entity;

import java.util.UUID;

public interface Pet {

    void tameByVillager(IVillagerNPC npc);

    boolean isTamedByVillager();

    UUID getOwnerUniqueId();
}