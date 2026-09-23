package me.matsubara.realisticvillagers.nms.v26_1;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;

import java.lang.reflect.Field;

/**
 * Entity-type constants resolved at class load via reflection.
 *
 * Purpur 26.2+ stores them on a separate {@code EntityTypes} class.
 * Leaf 26.1.x and other Paper forks keep them as static fields on
 * {@code EntityType} (standard Mojang mapping).  We try both so the
 * plugin works on either server flavour without referencing
 * {@code EntityTypes} at the bytecode level.
 */
@SuppressWarnings("unchecked")
public final class ET {

    public static final EntityType<Villager>       VILLAGER         = cast(getRaw("VILLAGER"));
    public static final EntityType<WanderingTrader> WANDERING_TRADER = cast(getRaw("WANDERING_TRADER"));
    public static final EntityType<Donkey>         DONKEY           = cast(getRaw("DONKEY"));
    public static final EntityType<Horse>          HORSE            = cast(getRaw("HORSE"));
    public static final EntityType<Mule>           MULE             = cast(getRaw("MULE"));
    public static final EntityType<Cat>            CAT              = cast(getRaw("CAT"));
    public static final EntityType<Parrot>         PARROT           = cast(getRaw("PARROT"));
    public static final EntityType<Camel>          CAMEL            = cast(getRaw("CAMEL"));
    public static final EntityType<Llama>          LLAMA            = cast(getRaw("LLAMA"));
    public static final EntityType<Wolf>           WOLF             = cast(getRaw("WOLF"));
    public static final EntityType<Creeper>        CREEPER          = cast(getRaw("CREEPER"));
    public static final EntityType<Witch>          WITCH            = cast(getRaw("WITCH"));
    public static final EntityType<Player>         PLAYER           = cast(getRaw("PLAYER"));
    public static final EntityType<FishingHook>    FISHING_BOBBER   = cast(getRaw("FISHING_BOBBER"));

    private static <T extends Entity> EntityType<T> cast(EntityType<? extends Entity> type) {
        return (EntityType<T>) type;
    }

    private static EntityType<? extends Entity> getRaw(String name) {
        // Purpur 26.2+: constants live on a separate EntityTypes class
        try {
            Class<?> cls = Class.forName("net.minecraft.world.entity.EntityTypes");
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return (EntityType<? extends Entity>) f.get(null);
        } catch (ClassNotFoundException ignored) {
            // Not a Purpur 26.2 server — fall through
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Leaf / Paper and older Purpur: constants are static fields on EntityType itself
        try {
            Field f = EntityType.class.getDeclaredField(name);
            f.setAccessible(true);
            return (EntityType<? extends Entity>) f.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private ET() {}
}
