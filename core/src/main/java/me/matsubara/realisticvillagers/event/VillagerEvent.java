package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.event.Event;

public abstract class VillagerEvent extends Event {

    protected IVillagerNPC npc;

    public VillagerEvent(IVillagerNPC npc) {
        this.npc = npc;
    }

    public IVillagerNPC getNPC() {
        return npc;
    }
}