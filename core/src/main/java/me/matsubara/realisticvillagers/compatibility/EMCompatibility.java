package me.matsubara.realisticvillagers.compatibility;

import com.magmaguy.elitemobs.tagger.PersistentTagger;
import org.bukkit.entity.Villager;

public class EMCompatibility implements Compatibility {

    @Override
    public boolean shouldTrack(Villager villager) {
        return !PersistentTagger.isNPC(villager)
                && !PersistentTagger.isEliteProjectile(villager)
                && !PersistentTagger.isEliteEntity(villager)
                && !PersistentTagger.isSuperMob(villager)
                && !PersistentTagger.isVisualEffect(villager);
    }
}