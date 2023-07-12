package me.matsubara.realisticvillagers.compatibility;

import com.magmaguy.elitemobs.entitytracker.EntityTracker;
import org.bukkit.entity.Villager;

public class EMCompatibility implements Compatibility {

    @Override
    public boolean shouldTrack(Villager villager) {
        return !EntityTracker.isNPCEntity(villager)
                && !EntityTracker.isProjectileEntity(villager)
                && !EntityTracker.isEliteMob(villager)
                && !EntityTracker.isSuperMob(villager)
                && !EntityTracker.isVisualEffect(villager);
    }
}