package fun.rich.features.impl.render;

public record InventoryParticlePreset(
        Style style,
        int primaryColor,
        int secondaryColor,
        int accentColor,
        float spreadX,
        float spreadY,
        float velocityXMin,
        float velocityXMax,
        float velocityYMin,
        float velocityYMax,
        float gravity,
        float drag,
        float minLifetime,
        float maxLifetime,
        float minSize,
        float maxSize,
        float minSizeVelocity,
        float maxSizeVelocity,
        float minAlpha,
        float maxAlpha,
        int minBurst,
        int maxBurst,
        int hoverBurstBonus,
        int baseIntervalMs,
        int hoverIntervalMs
) {
    public enum Style {
        SOFT,
        BUBBLE,
        SPARK,
        RING,
        DUST,
        EMBER,
        SHARD,
        HOLY,
        VOID
    }
}