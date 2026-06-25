package fun.rich.features.impl.render;

import net.minecraft.item.*;
import net.minecraft.registry.Registries;

public final class InventoryParticlePresetRegistry {
    private static final InventoryParticlePreset DEFAULT = new InventoryParticlePreset(
            InventoryParticlePreset.Style.SOFT,
            0xFF9AD7FF,
            0xFFD7F2FF,
            0xFFFFFFFF,
            5.0f,
            4.0f,
            -14.0f,
            14.0f,
            -30.0f,
            -12.0f,
            34.0f,
            0.944f,
            0.42f,
            0.82f,
            2.8f,
            5.1f,
            -0.6f,
            2.1f,
            0.34f,
            0.72f,
            1,
            1,
            1,
            136,
            38
    );

    private static final InventoryParticlePreset FOOD = new InventoryParticlePreset(
            InventoryParticlePreset.Style.SOFT,
            0xFFFFD36A,
            0xFFFFF0A6,
            0xFFFFFFFF,
            5.4f,
            4.2f,
            -12.0f,
            12.0f,
            -26.0f,
            -8.0f,
            30.0f,
            0.948f,
            0.46f,
            0.88f,
            3.2f,
            5.9f,
            -0.5f,
            2.4f,
            0.38f,
            0.76f,
            1,
            2,
            1,
            120,
            34
    );

    private static final InventoryParticlePreset POTION = new InventoryParticlePreset(
            InventoryParticlePreset.Style.BUBBLE,
            0xFF74D8FF,
            0xFFA184FF,
            0xFFFFFFFF,
            4.8f,
            4.5f,
            -10.0f,
            10.0f,
            -24.0f,
            -6.0f,
            18.0f,
            0.956f,
            0.55f,
            1.08f,
            3.6f,
            6.4f,
            -0.3f,
            1.8f,
            0.42f,
            0.82f,
            1,
            2,
            2,
            88,
            24
    );

    private static final InventoryParticlePreset WEAPON = new InventoryParticlePreset(
            InventoryParticlePreset.Style.SPARK,
            0xFFFF7E57,
            0xFFFFC15A,
            0xFFFFFFFF,
            4.8f,
            3.8f,
            -26.0f,
            26.0f,
            -42.0f,
            -14.0f,
            44.0f,
            0.930f,
            0.34f,
            0.70f,
            2.8f,
            5.0f,
            -0.7f,
            1.2f,
            0.42f,
            0.85f,
            1,
            2,
            2,
            86,
            20
    );

    private static final InventoryParticlePreset TOOL = new InventoryParticlePreset(
            InventoryParticlePreset.Style.SHARD,
            0xFFB7D7FF,
            0xFFE6F3FF,
            0xFFFFFFFF,
            4.6f,
            3.5f,
            -18.0f,
            18.0f,
            -34.0f,
            -12.0f,
            38.0f,
            0.938f,
            0.40f,
            0.76f,
            3.0f,
            5.6f,
            -0.9f,
            1.0f,
            0.36f,
            0.74f,
            1,
            2,
            1,
            110,
            28
    );

    private static final InventoryParticlePreset ARMOR = new InventoryParticlePreset(
            InventoryParticlePreset.Style.RING,
            0xFFBDD2E8,
            0xFFF4FAFF,
            0xFFFFFFFF,
            4.0f,
            3.2f,
            -10.0f,
            10.0f,
            -18.0f,
            -6.0f,
            22.0f,
            0.952f,
            0.48f,
            0.92f,
            3.0f,
            5.4f,
            -0.4f,
            1.1f,
            0.34f,
            0.70f,
            1,
            1,
            1,
            128,
            34
    );

    private static final InventoryParticlePreset BLOCK = new InventoryParticlePreset(
            InventoryParticlePreset.Style.DUST,
            0xFF9EA9B2,
            0xFFD9E0E6,
            0xFFFFFFFF,
            5.6f,
            4.2f,
            -12.0f,
            12.0f,
            -18.0f,
            -4.0f,
            28.0f,
            0.952f,
            0.52f,
            0.95f,
            2.6f,
            4.2f,
            -0.8f,
            0.6f,
            0.28f,
            0.60f,
            1,
            1,
            0,
            150,
            44
    );

    private static final InventoryParticlePreset HOLY = new InventoryParticlePreset(
            InventoryParticlePreset.Style.HOLY,
            0xFFFFD76A,
            0xFFFFFFFF,
            0xFFFFF3B3,
            4.6f,
            3.6f,
            -14.0f,
            14.0f,
            -28.0f,
            -8.0f,
            24.0f,
            0.950f,
            0.56f,
            1.00f,
            3.8f,
            6.8f,
            -0.3f,
            1.6f,
            0.46f,
            0.92f,
            2,
            3,
            2,
            76,
            20
    );

    private static final InventoryParticlePreset VOID = new InventoryParticlePreset(
            InventoryParticlePreset.Style.VOID,
            0xFF8B6CFF,
            0xFF4CC9F0,
            0xFFFFFFFF,
            4.8f,
            3.8f,
            -14.0f,
            14.0f,
            -30.0f,
            -10.0f,
            20.0f,
            0.946f,
            0.52f,
            1.04f,
            3.4f,
            6.1f,
            -0.4f,
            1.6f,
            0.42f,
            0.86f,
            1,
            2,
            2,
            82,
            22
    );

    private static final InventoryParticlePreset EMBER = new InventoryParticlePreset(
            InventoryParticlePreset.Style.EMBER,
            0xFFFF6538,
            0xFFFFB347,
            0xFFFFFFFF,
            4.4f,
            3.6f,
            -12.0f,
            12.0f,
            -36.0f,
            -14.0f,
            46.0f,
            0.934f,
            0.34f,
            0.72f,
            2.6f,
            4.8f,
            -0.7f,
            0.9f,
            0.40f,
            0.80f,
            1,
            2,
            2,
            92,
            22
    );

    private static final InventoryParticlePreset DIAMOND = new InventoryParticlePreset(
            InventoryParticlePreset.Style.SHARD,
            0xFF63E4FF,
            0xFFD7FEFF,
            0xFFFFFFFF,
            4.8f,
            3.5f,
            -18.0f,
            18.0f,
            -34.0f,
            -12.0f,
            34.0f,
            0.940f,
            0.46f,
            0.86f,
            3.2f,
            6.0f,
            -0.7f,
            1.2f,
            0.40f,
            0.82f,
            1,
            2,
            2,
            86,
            24
    );

    private static final InventoryParticlePreset EMERALD = new InventoryParticlePreset(
            InventoryParticlePreset.Style.SHARD,
            0xFF3DDE83,
            0xFFB8FFD2,
            0xFFFFFFFF,
            4.8f,
            3.5f,
            -18.0f,
            18.0f,
            -34.0f,
            -12.0f,
            34.0f,
            0.940f,
            0.46f,
            0.86f,
            3.2f,
            6.0f,
            -0.7f,
            1.2f,
            0.40f,
            0.82f,
            1,
            2,
            2,
            88,
            24
    );

    private static final InventoryParticlePreset REDSTONE = new InventoryParticlePreset(
            InventoryParticlePreset.Style.EMBER,
            0xFFFF304A,
            0xFFFF8B8B,
            0xFFFFFFFF,
            4.4f,
            3.4f,
            -12.0f,
            12.0f,
            -34.0f,
            -12.0f,
            44.0f,
            0.936f,
            0.38f,
            0.74f,
            2.8f,
            4.8f,
            -0.8f,
            0.8f,
            0.40f,
            0.80f,
            1,
            2,
            1,
            98,
            24
    );

    private static final InventoryParticlePreset LAPIS = new InventoryParticlePreset(
            InventoryParticlePreset.Style.DUST,
            0xFF4B7CFF,
            0xFFA6C2FF,
            0xFFFFFFFF,
            5.0f,
            4.0f,
            -12.0f,
            12.0f,
            -22.0f,
            -8.0f,
            28.0f,
            0.948f,
            0.48f,
            0.88f,
            2.8f,
            4.8f,
            -0.6f,
            0.8f,
            0.32f,
            0.68f,
            1,
            1,
            1,
            126,
            34
    );

    private static final InventoryParticlePreset AMETHYST = new InventoryParticlePreset(
            InventoryParticlePreset.Style.VOID,
            0xFFC675FF,
            0xFFFFB5F6,
            0xFFFFFFFF,
            4.8f,
            3.8f,
            -14.0f,
            14.0f,
            -28.0f,
            -10.0f,
            22.0f,
            0.946f,
            0.54f,
            1.02f,
            3.4f,
            6.0f,
            -0.4f,
            1.5f,
            0.42f,
            0.84f,
            1,
            2,
            2,
            86,
            22
    );

    private static final InventoryParticlePreset NETHERITE = new InventoryParticlePreset(
            InventoryParticlePreset.Style.EMBER,
            0xFF6F5B58,
            0xFFFF8A52,
            0xFFFFFFFF,
            4.4f,
            3.4f,
            -12.0f,
            12.0f,
            -32.0f,
            -10.0f,
            42.0f,
            0.936f,
            0.40f,
            0.78f,
            2.8f,
            5.0f,
            -0.7f,
            0.9f,
            0.38f,
            0.80f,
            1,
            2,
            2,
            96,
            24
    );

    private static final InventoryParticlePreset ENCHANTED = new InventoryParticlePreset(
            InventoryParticlePreset.Style.VOID,
            0xFF9D6BFF,
            0xFFE4A8FF,
            0xFFFFFFFF,
            4.8f,
            3.8f,
            -14.0f,
            14.0f,
            -30.0f,
            -10.0f,
            20.0f,
            0.946f,
            0.56f,
            1.04f,
            3.6f,
            6.2f,
            -0.4f,
            1.7f,
            0.44f,
            0.88f,
            1,
            2,
            2,
            80,
            20
    );

    private InventoryParticlePresetRegistry() {
    }

    public static InventoryParticlePreset resolve(ItemStack stack) {
        Item item = stack.getItem();
        String path = Registries.ITEM.getId(item).getPath();

        if (item == Items.TOTEM_OF_UNDYING || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE || item == Items.NETHER_STAR) {
            return HOLY;
        }

        if (item == Items.ENDER_PEARL || item == Items.ENDER_EYE || item == Items.CHORUS_FRUIT || item == Items.CHORUS_FLOWER || item == Items.ECHO_SHARD) {
            return VOID;
        }

        if (item == Items.ENCHANTED_BOOK) {
            return ENCHANTED;
        }

        if (path.contains("diamond")) {
            return DIAMOND;
        }

        if (path.contains("emerald")) {
            return EMERALD;
        }

        if (path.contains("redstone")) {
            return REDSTONE;
        }

        if (path.contains("lapis")) {
            return LAPIS;
        }

        if (path.contains("amethyst")) {
            return AMETHYST;
        }

        if (path.contains("netherite") || path.contains("ancient_debris") || path.contains("netherite_scrap")) {
            return NETHERITE;
        }

        if (item == Items.LAVA_BUCKET || item == Items.BLAZE_ROD || item == Items.BLAZE_POWDER || item == Items.FIRE_CHARGE || path.contains("magma")) {
            return EMBER;
        }

        if (path.contains("potion") || path.contains("bottle") || item instanceof PotionItem || item instanceof BucketItem) {
            return POTION;
        }

        if (item instanceof SwordItem || item instanceof TridentItem || item instanceof BowItem || item instanceof CrossbowItem || path.contains("arrow")) {
            return WEAPON;
        }

        if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem || item instanceof ShearsItem) {
            return TOOL;
        }

        if (item instanceof ArmorItem || item instanceof ShieldItem) {
            return ARMOR;
        }

        if (isFood(path)) {
            return FOOD;
        }

        if (item instanceof BlockItem) {
            return BLOCK;
        }

        if (stack.hasGlint()) {
            return ENCHANTED;
        }

        return DEFAULT;
    }

    private static boolean isFood(String path) {
        return path.contains("apple")
                || path.contains("bread")
                || path.contains("beef")
                || path.contains("pork")
                || path.contains("chicken")
                || path.contains("mutton")
                || path.contains("rabbit")
                || path.contains("cod")
                || path.contains("salmon")
                || path.contains("fish")
                || path.contains("potato")
                || path.contains("carrot")
                || path.contains("beetroot")
                || path.contains("berry")
                || path.contains("melon")
                || path.contains("cookie")
                || path.contains("stew")
                || path.contains("soup")
                || path.contains("pie")
                || path.contains("cake")
                || path.contains("kelp")
                || path.contains("flesh")
                || path.contains("spider_eye")
                || path.contains("chorus_fruit")
                || path.contains("honey_bottle")
                || path.contains("golden_apple")
                || path.contains("dried_kelp")
                || path.contains("pumpkin_pie")
                || path.contains("glistering_melon_slice");
    }
}