package fun.rich.features.impl.render;

final class InventoryParticle {
    float x;
    float y;
    float velocityX;
    float velocityY;
    float gravity;
    float drag;
    float age;
    float lifetime;
    float size;
    float sizeVelocity;
    float alpha;
    int color;
    int accentColor;
    InventoryParticlePreset.Style style;

    InventoryParticle(
            float x,
            float y,
            float velocityX,
            float velocityY,
            float gravity,
            float drag,
            float lifetime,
            float size,
            float sizeVelocity,
            float alpha,
            int color,
            int accentColor,
            InventoryParticlePreset.Style style
    ) {
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.gravity = gravity;
        this.drag = drag;
        this.lifetime = lifetime;
        this.size = size;
        this.sizeVelocity = sizeVelocity;
        this.alpha = alpha;
        this.color = color;
        this.accentColor = accentColor;
        this.style = style;
    }
}