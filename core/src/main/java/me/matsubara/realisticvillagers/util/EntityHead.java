package me.matsubara.realisticvillagers.util;

import lombok.Getter;
import me.matsubara.realisticvillagers.data.EntityCategory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum EntityHead {
    ALLAY(EntityCategory.ANIMAL, "beea845cc0b58ff763decffe11cd1c845c5d09c3b04fe80b0663da5c7c699eb3"),
    ARMADILLO(EntityCategory.ANIMAL, "9852b33ba294f560090752d113fe728cbc7dd042029a38d5382d65a2146068b7"),
    AXOLOTL(EntityCategory.ANIMAL, "a773d8875c98911538e100ac02e17a2763ae7f71258224b8bf9557b3d5dde006"),
    BAT(EntityCategory.ANIMAL, "3820a10db222f69ac2215d7d10dca47eeafa215553764a2b81bafd479e7933d1"),
    BEE(EntityCategory.ANIMAL, "4420c9c43e095880dcd2e281c81f47b163b478f58a584bb61f93e6e10a155f31"),
    BLAZE(EntityCategory.MONSTER, "b20657e24b56e1b2f8fc219da1de788c0c24f36388b1a409d0cd2d8dba44aa3b"),
    BOGGED(EntityCategory.MONSTER, "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"),
    BREEZE(EntityCategory.MONSTER, "a275728af7e6a29c88125b675a39d88ae9919bb61fdc200337fed6ab0c49d65c"),
    CAMEL(EntityCategory.ANIMAL, "ba4c95bfa0b61722255389141b505cf1a38bad9b0ef543de619f0cc9221ed974"),
    CAT(EntityCategory.ANIMAL, "4fd10c8e75f67398c47587d25fc146f311c053cc5d0aeab8790bce36ee88f5f8"),
    CAVE_SPIDER(EntityCategory.MONSTER, "604d5fcb289fe65b6786682e1c736c3f7b16f39d940e3d2f41cf0040704c6282"),
    CHICKEN(EntityCategory.ANIMAL, "1638469a599ceef7207537603248a9ab11ff591fd378bea4735b346a7fae893"),
    COD(EntityCategory.ANIMAL, "7892d7dd6aadf35f86da27fb63da4edda211df96d2829f691462a4fb1cab0"),
    COPPER_GOLEM(EntityCategory.ANIMAL, "99e24e94dbe42e230d83293a77d61ff7101a8c68ab68bbc6a93f9630fb2fdb4"),
    COW(EntityCategory.ANIMAL, "d6551840955f524367580f11b35228938b6786397a8f2e8c8cc6b0eb01b5db3d"),
    CREAKING(EntityCategory.MONSTER, "ad49294f7b4202aa85186a1c347735636ce218ec4d48188bca6d36f36d8c0785"),
    CREEPER(EntityCategory.MONSTER, null),
    DOLPHIN(EntityCategory.ANIMAL, "8e9688b950d880b55b7aa2cfcd76e5a0fa94aac6d16f78e833f7443ea29fed3"),
    DONKEY(EntityCategory.ANIMAL, "399bb50d1a214c394917e25bb3f2e20698bf98ca703e4cc08b42462df309d6e6"),
    DROWNED(EntityCategory.MONSTER, "c84df79c49104b198cdad6d99fd0d0bcf1531c92d4ab6269e40b7d3cbbb8e98c"),
    ELDER_GUARDIAN(EntityCategory.MONSTER, "4340a268f25fd5cc276ca147a8446b2630a55867a2349f7ca107c26eb58991"),
    ENDERMAN(EntityCategory.MONSTER, "96c0b36d53fff69a49c7d6f3932f2b0fe948e032226d5e8045ec58408a36e951"),
    ENDERMITE(EntityCategory.MONSTER, "5a1a0831aa03afb4212adcbb24e5dfaa7f476a1173fce259ef75a85855"),
    ENDER_DRAGON(EntityCategory.MONSTER, null),
    EVOKER(EntityCategory.MONSTER, "630ce775edb65db8c2741bdfae84f3c0d0285aba93afadc74900d55dfd9504a5"),
    FOX(EntityCategory.ANIMAL, "d8954a42e69e0881ae6d24d4281459c144a0d5a968aed35d6d3d73a3c65d26a"),
    FROG(EntityCategory.ANIMAL, "2ca4a8e494582c62aaa2c92474b16d69cd63baa3d3f50a4b631d6559ca0f33f5"),
    GHAST(EntityCategory.MONSTER, "de8a38e9afbd3da10d19b577c55c7bfd6b4f2e407e44d4017b23be9167abff02"),
    GIANT(EntityCategory.MONSTER, null),
    GLOW_SQUID(EntityCategory.ANIMAL, "285bc63b5317387e86cb308732c9671796902759e48c30219de9555fefeb436b"),
    GOAT(EntityCategory.ANIMAL, "f03330398a0d833f53ae8c9a1cb393c74e9d31e18885870e86a2133d44f0c63c"),
    GUARDIAN(EntityCategory.MONSTER, "495290e090c238832bd7860fc033948c4d031353533ac8f67098823b7f667f1c"),
    HAPPY_GHAST(EntityCategory.ANIMAL, "a1a36cb93d01675c4622dd5c8d872110911ec12c372e89afa8ba03862867f6fb"),
    HOGLIN(EntityCategory.MONSTER, "4409dc402a9fc3c7b892c44e5cd34a4a01d44419d05df8316f2e2d862ae0ba9c"),
    HORSE(EntityCategory.ANIMAL, "413813dd45ed0ef838448cf6f631c157c23f9650c5ae451e978a53383312fe"),
    HUSK(EntityCategory.MONSTER, "9b9da6b8d06cd28d441398b96766c3b4f370de85c7898205e5c429f178a24597"),
    ILLUSIONER(EntityCategory.MONSTER, "512512e7d016a2343a7bff1a4cd15357ab851579f1389bd4e3a24cbeb88b"),
    LLAMA(EntityCategory.ANIMAL, "2a5f10e6e6232f182fe966f501f1c3799d45ae19031a1e4941b5dee0feff059b"),
    MAGMA_CUBE(EntityCategory.MONSTER, "a1c97a06efde04d00287bf20416404ab2103e10f08623087e1b0c1264a1c0f0c"),
    MULE(EntityCategory.ANIMAL, "46dcda265e57e4f51b145aacbf5b59bdc6099ffd3cce0a661b2c0065d80930d8"),
    MUSHROOM_COW(EntityCategory.ANIMAL, "2b52841f2fd589e0bc84cbabf9e1c27cb70cac98f8d6b3dd065e55a4dcb70d77", "MOOSHROOM"),
    OCELOT(EntityCategory.ANIMAL, "51f07e3f2e5f256bfade666a8de1b5d30252c95e98f8a8ecc6e3c7b7f67095"),
    PANDA(EntityCategory.ANIMAL, "dca096eea506301bea6d4b17ee1605625a6f5082c71f74a639cc940439f47166"),
    PARROT(EntityCategory.ANIMAL, "a4ba8d66fecb1992e94b8687d6ab4a5320ab7594ac194a2615ed4df818edbc3"),
    PHANTOM(EntityCategory.MONSTER, "746830da5f83a3aaed838a99156ad781a789cfcf13e25beef7f54a86e4fa4"),
    PIG(EntityCategory.ANIMAL, "bee8514892f3d78a32e8456fcbb8c6081e21b246d82f398bd969fec19d3c27b3"),
    PIGLIN(EntityCategory.MONSTER, "d71b3aee182b9a99ed26cbf5ecb47ae90c2c3adc0927dde102c7b30fdf7f4545"),
    PIGLIN_BRUTE(EntityCategory.MONSTER, "3e300e9027349c4907497438bac29e3a4c87a848c50b34c21242727b57f4e1cf"),
    PILLAGER(EntityCategory.MONSTER, "32fb80a6b6833e31d9ce8313a54777645f9c1e55b810918a706e7bcc8d35a5a2"),
    POLAR_BEAR(EntityCategory.ANIMAL, "c4fe926922fbb406f343b34a10bb98992cee4410137d3f88099427b22de3ab90"),
    PUFFERFISH(EntityCategory.ANIMAL, "292350c9f0993ed54db2c7113936325683ffc20104a9b622aa457d37e708d931"),
    RABBIT(EntityCategory.ANIMAL, "c1db38ef3c1a1d59f779a0cd9f9e616de0cc9acc7734b8facc36fc4ea40d0235"),
    RAVAGER(EntityCategory.MONSTER, "1cb9f139f9489d86e410a06d8cbc670c8028137508e3e4bef612fe32edd60193"),
    SALMON(EntityCategory.ANIMAL, "b770d917d1ccc12f3a1cf73fe7de8c6548f4a842086923c7bb4446bcc7aaebfa"),
    SHEEP(EntityCategory.ANIMAL, "30f50394c6d7dbc03ea59fdf504020dc5d6548f9d3bc9dcac896bb5ca08587a"),
    SHULKER(EntityCategory.MONSTER, "f2c642cfe33814767d692d27599c8bef4f5a306fc210d4b50a580b7040f02b18"),
    SILVERFISH(EntityCategory.MONSTER, "da91dab8391af5fda54acd2c0b18fbd819b865e1a8f1d623813fa761e924540"),
    SKELETON(EntityCategory.MONSTER, null),
    SKELETON_HORSE(EntityCategory.ANIMAL, "47effce35132c86ff72bcae77dfbb1d22587e94df3cbc2570ed17cf8973a"),
    SLIME(EntityCategory.MONSTER, "895aeec6b842ada8669f846d65bc49762597824ab944f22f45bf3bbb941abe6c"),
    SNIFFER(EntityCategory.ANIMAL, "fe5a8341c478a134302981e6a7758ea4ecfd8d62a0df4067897e75502f9b25de"),
    SNOWMAN(EntityCategory.ANIMAL, "e6f20aec528c3968dd8164f9d9336b081b3a2c7ecf189cf73df6f925e5a4ed14", "SNOW_GOLEM"),
    SPIDER(EntityCategory.MONSTER, "c87a96a8c23b83b32a73df051f6b84c2ef24d25ba4190dbe74f11138629b5aef"),
    SQUID(EntityCategory.ANIMAL, "01433be242366af126da434b8735df1eb5b3cb2cede39145974e9c483607bac"),
    STRAY(EntityCategory.MONSTER, "6572747a639d2240feeae5c81c6874e6ee7547b599e74546490dc75fa2089186"),
    STRIDER(EntityCategory.ANIMAL, "18a9adf780ec7dd4625c9c0779052e6a15a451866623511e4c82e9655714b3c1"),
    TADPOLE(EntityCategory.ANIMAL, "b23ebf26b7a441e10a86fb5c2a5f3b519258a5c5dddd6a1a75549f517332815b"),
    TRADER_LLAMA(EntityCategory.ANIMAL, "e89a2eb17705fe7154ab041e5c76a08d41546a31ba20ea3060e3ec8edc10412c"),
    TROPICAL_FISH(EntityCategory.ANIMAL, "36389acd7c8280d2c8085e6a6a91e182465347cc898db8c2d9bb148e0271c3e5"),
    TURTLE(EntityCategory.ANIMAL, "0a4050e7aacc4539202658fdc339dd182d7e322f9fbcc4d5f99b5718a"),
    VEX(EntityCategory.MONSTER, "5e7330c7d5cd8a0a55ab9e95321535ac7ae30fe837c37ea9e53bea7ba2de86b"),
    VINDICATOR(EntityCategory.MONSTER, "4f6fb89d1c631bd7e79fe185ba1a6705425f5c31a5ff626521e395d4a6f7e2"),
    WARDEN(EntityCategory.MONSTER, "6cf3674b2ddc0ef7c39e3b9c6b58677de5cf377d2eb073f2f3fe50919b1ca4c9"),
    WITCH(EntityCategory.MONSTER, "fce6604157fc4ab5591e4bcf507a749918ee9c41e357d47376e0ee7342074c90"),
    WITHER(EntityCategory.MONSTER, "ee280cefe946911ea90e87ded1b3e18330c63a23af5129dfcfe9a8e166588041"),
    WITHER_SKELETON(EntityCategory.MONSTER, null),
    WOLF(EntityCategory.ANIMAL, "d0498de6f5b09e0ce35a7292fe50b79fce9065d9be8e2a87c7a13566efb26d72"),
    ZOGLIN(EntityCategory.MONSTER, "3c8c7c5d0556cd6629716e39188b21e7c0477479f242587bf19e0bc76b322551"),
    ZOMBIE(EntityCategory.MONSTER, null),
    ZOMBIE_HORSE(EntityCategory.ANIMAL, "d22950f2d3efddb18de86f8f55ac518dce73f12a6e0f8636d551d8eb480ceec"),
    ZOMBIE_VILLAGER(EntityCategory.MONSTER, "c45c11e0327035649ca0600ef938900e25fd1e38017422bc9740e4cda2cba892"),
    ZOMBIFIED_PIGLIN(EntityCategory.MONSTER, "8954d0d1c286c1b34fb091841c06aed741a1bf9b65b9a430e4e5ca1d1c4b9f6f");

    private final EntityCategory category;
    private final @Nullable String url;
    private final String[] otherNames;

    private static final Map<EntityHead, ItemStack> CACHE_HEADS = new HashMap<>();

    static {
        for (EntityHead skull : values()) {
            CACHE_HEADS.put(skull, skull.getHead());
        }
    }

    EntityHead(EntityCategory category, @Nullable String url, String... otherNames) {
        this.category = category;
        this.url = url;
        this.otherNames = otherNames;
    }

    public @Nullable EntityType getType() {
        EntityType type = PluginUtils.getOrNull(EntityType.class, name());
        if (type != null) return type;

        int index = 0;
        while (type == null && index != otherNames.length) {
            type = PluginUtils.getOrNull(EntityType.class, otherNames[index++]);
        }

        return type;
    }

    public ItemStack getHead() {
        if (CACHE_HEADS.containsKey(this)) return CACHE_HEADS.get(this);

        Material headMaterial = null;
        if (this == ENDER_DRAGON) headMaterial = Material.DRAGON_HEAD;
        if (this == ZOMBIE || this == GIANT) headMaterial = Material.ZOMBIE_HEAD;
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

    public @Nullable Material getSpawnEgg() {
        EntityType type = getType();
        if (type == null) return null;

        ItemFactory factory = Bukkit.getItemFactory();
        if (this == GIANT) return factory.getSpawnEgg(EntityType.ZOMBIE);

        // Illusioner is an unused entity, so it doesn't have a spawn egg (yet), use the squid one (similar colors).
        if (this == ILLUSIONER) return factory.getSpawnEgg(EntityType.SQUID);

        return factory.getSpawnEgg(type);
    }
}