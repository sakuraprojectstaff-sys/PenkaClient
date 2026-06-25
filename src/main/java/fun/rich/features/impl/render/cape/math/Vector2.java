package fun.rich.features.impl.render.cape.math;

import net.minecraft.util.math.MathHelper;

public class Vector2 {
    public float x;
    public float y;

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public Vector2 clone() {
        return new Vector2(this.x, this.y);
    }

    public void copy(Vector2 vec) {
        this.x = vec.x;
        this.y = vec.y;
    }

    public Vector2 add(Vector2 vec) {
        this.x += vec.x;
        this.y += vec.y;
        return this;
    }

    public Vector2 subtract(Vector2 vec) {
        this.x -= vec.x;
        this.y -= vec.y;
        return this;
    }

    public Vector2 div(float amount) {
        this.x /= amount;
        this.y /= amount;
        return this;
    }

    public Vector2 mul(float amount) {
        this.x *= amount;
        this.y *= amount;
        return this;
    }

    public Vector2 normalize() {
        float f = MathHelper.sqrt(this.x * this.x + this.y * this.y);
        if (f < 1.0E-4f) {
            this.x = 0.0f;
            this.y = 0.0f;
        } else {
            this.x /= f;
            this.y /= f;
        }
        return this;
    }

    public Vector2 rotateDegrees(float deg) {
        float ox = this.x;
        float oy = this.y;
        deg = (float) Math.toRadians(deg);
        this.x = MathHelper.cos(deg) * ox - MathHelper.sin(deg) * oy;
        this.y = MathHelper.sin(deg) * ox + MathHelper.cos(deg) * oy;
        return this;
    }

    @Override
    public String toString() {
        return "Vector2[x=" + this.x + ", y=" + this.y + "]";
    }
}