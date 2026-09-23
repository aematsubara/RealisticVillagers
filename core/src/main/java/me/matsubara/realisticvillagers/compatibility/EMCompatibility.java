package me.matsubara.realisticvillagers.compatibility;

import org.bukkit.entity.Villager;
import java.lang.reflect.Method;

public class EMCompatibility implements Compatibility {

    @Override
    public boolean shouldTrack(Villager villager) {
        try {
            Class<?> clazz = Class.forName("com.magmaguy.elitemobs.tagger.PersistentTagger");
            return !(boolean) invokeStatic(clazz, "isNPC", villager)
                    && !(boolean) invokeStatic(clazz, "isEliteProjectile", villager)
                    && !(boolean) invokeStatic(clazz, "isEliteEntity", villager)
                    && !(boolean) invokeStatic(clazz, "isSuperMob", villager)
                    && !(boolean) invokeStatic(clazz, "isVisualEffect", villager);
        } catch (Exception e) {
            return true;
        }
    }

    private Object invokeStatic(Class<?> clazz, String methodName, Object arg) throws Exception {
        Method method = clazz.getMethod(methodName, org.bukkit.entity.LivingEntity.class);
        return method.invoke(null, arg);
    }
}