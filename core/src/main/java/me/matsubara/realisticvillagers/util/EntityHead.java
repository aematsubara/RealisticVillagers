package me.matsubara.realisticvillagers.util;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum EntityHead {

    // Commons.
    ZOMBIE(null),
    SKELETON(null),
    CREEPER(null),
    WITCH("fce6604157fc4ab5591e4bcf507a749918ee9c41e357d47376e0ee7342074c90"),
    SPIDER("c87a96a8c23b83b32a73df051f6b84c2ef24d25ba4190dbe74f11138629b5aef"),
    ZOMBIE_VILLAGER("c45c11e0327035649ca0600ef938900e25fd1e38017422bc9740e4cda2cba892"),
    ENDERMAN("96c0b36d53fff69a49c7d6f3932f2b0fe948e032226d5e8045ec58408a36e951"),
    SLIME("895aeec6b842ada8669f846d65bc49762597824ab944f22f45bf3bbb941abe6c"),
    CAVE_SPIDER("604d5fcb289fe65b6786682e1c736c3f7b16f39d940e3d2f41cf0040704c6282"),
    SILVERFISH("da91dab8391af5fda54acd2c0b18fbd819b865e1a8f1d623813fa761e924540"),
    STRAY("6572747a639d2240feeae5c81c6874e6ee7547b599e74546490dc75fa2089186"),
    HUSK("9b9da6b8d06cd28d441398b96766c3b4f370de85c7898205e5c429f178a24597"),
    PHANTOM("746830da5f83a3aaed838a99156ad781a789cfcf13e25beef7f54a86e4fa4"),
    DROWNED("c84df79c49104b198cdad6d99fd0d0bcf1531c92d4ab6269e40b7d3cbbb8e98c"),

    // Raiders.
    VINDICATOR("4f6fb89d1c631bd7e79fe185ba1a6705425f5c31a5ff626521e395d4a6f7e2"),
    VEX("5e7330c7d5cd8a0a55ab9e95321535ac7ae30fe837c37ea9e53bea7ba2de86b"),
    ILLUSIONER("512512e7d016a2343a7bff1a4cd15357ab851579f1389bd4e3a24cbeb88b"),
    EVOKER("630ce775edb65db8c2741bdfae84f3c0d0285aba93afadc74900d55dfd9504a5"),
    RAVAGER("1cb9f139f9489d86e410a06d8cbc670c8028137508e3e4bef612fe32edd60193"),

    // Water
    GUARDIAN("495290e090c238832bd7860fc033948c4d031353533ac8f67098823b7f667f1c"),
    ELDER_GUARDIAN("4340a268f25fd5cc276ca147a8446b2630a55867a2349f7ca107c26eb58991"),

    // Nether
    BLAZE("b20657e24b56e1b2f8fc219da1de788c0c24f36388b1a409d0cd2d8dba44aa3b"),
    GHAST("de8a38e9afbd3da10d19b577c55c7bfd6b4f2e407e44d4017b23be9167abff02"),
    MAGMA_CUBE("a1c97a06efde04d00287bf20416404ab2103e10f08623087e1b0c1264a1c0f0c"),
    WITHER_SKELETON(null),
    HOGLIN("4409dc402a9fc3c7b892c44e5cd34a4a01d44419d05df8316f2e2d862ae0ba9c"),
    ZOGLIN("3c8c7c5d0556cd6629716e39188b21e7c0477479f242587bf19e0bc76b322551"),
    PIGLIN("d71b3aee182b9a99ed26cbf5ecb47ae90c2c3adc0927dde102c7b30fdf7f4545"),
    PIGLIN_BRUTE("3e300e9027349c4907497438bac29e3a4c87a848c50b34c21242727b57f4e1cf"),
    ZOMBIFIED_PIGLIN("8954d0d1c286c1b34fb091841c06aed741a1bf9b65b9a430e4e5ca1d1c4b9f6f"),

    // End.
    ENDERMITE("5a1a0831aa03afb4212adcbb24e5dfaa7f476a1173fce259ef75a85855"),
    SHULKER("f2c642cfe33814767d692d27599c8bef4f5a306fc210d4b50a580b7040f02b18"),

    // Bosses.
    WITHER("ee280cefe946911ea90e87ded1b3e18330c63a23af5129dfcfe9a8e166588041"),
    ENDER_DRAGON(null),
    WARDEN("6cf3674b2ddc0ef7c39e3b9c6b58677de5cf377d2eb073f2f3fe50919b1ca4c9");

    private final String url;
    private static final Map<EntityHead, ItemStack> cacheHeads = new HashMap<>();

    static {
        for (EntityHead skull : values()) {
            cacheHeads.put(skull, skull.getHead());
        }
    }

    EntityHead(String url) {
        this.url = url;
    }

    public @Nullable EntityType getType() {
        try {
            return EntityType.valueOf(name());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String getUrl() {
        return url;
    }

    public ItemStack getHead() {
        if (cacheHeads.containsKey(this)) return cacheHeads.get(this);

        Material headMaterial = null;
        if (this == ENDER_DRAGON) headMaterial = Material.DRAGON_HEAD;
        if (this == ZOMBIE) headMaterial = Material.ZOMBIE_HEAD;
        if (this == SKELETON) headMaterial = Material.SKELETON_SKULL;
        if (this == WITHER_SKELETON) headMaterial = Material.WITHER_SKELETON_SKULL;
        if (this == CREEPER) headMaterial = Material.CREEPER_HEAD;

        ItemStack head;
        if (headMaterial != null) {
            head = new ItemStack(headMaterial);
        } else {
            head = PluginUtils.createHead(url, true);
        }

        return head;
    }
}