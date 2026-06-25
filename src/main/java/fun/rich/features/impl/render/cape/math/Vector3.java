package fun.rich.features.impl.render.cape.math;

import net.minecraft.util.math.MathHelper;

public class Vector3 {
    public float x;
    public float y;
    public float z;

    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Vector3 clone() {
        return new Vector3(this.x, this.y, this.z);
    }

    public void copy(Vector3 vec) {
        this.x = vec.x;
        this.y = vec.y;
        this.z = vec.z;
    }

    public Vector3 add(Vector3 vec) {
        this.x += vec.x;
        this.y += vec.y;
        this.z += vec.z;
        return this;
    }

    public Vector3 subtract(Vector3 vec) {
        this.x -= vec.x;
        this.y -= vec.y;
        this.z -= vec.z;
        return this;
    }

    public Vector3 div(float amount) {
        this.x /= amount;
        this.y /= amount;
        this.z /= amount;
        return this;
    }

    public Vector3 mul(float amount) {
        this.x *= amount;
        this.y *= amount;
        this.z *= amount;
        return this;
    }

    public Vector3 normalize() {
        float f = MathHelper.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        if (f < 1.0E-4f) {
            this.x = 0.0f;
            this.y = 0.0f;
            this.z = 0.0f;
        } else {
            this.x /= f;
            this.y /= f;
            this.z /= f;
        }
        return this;
    }

    public Vector3 rotateDegrees(float deg) {
        float ox = this.x;
        float oy = this.y;
        deg = (float) Math.toRadians(deg);
        this.x = MathHelper.cos(deg) * ox - MathHelper.sin(deg) * oy;
        this.y = MathHelper.sin(deg) * ox + MathHelper.cos(deg) * oy;
        return this;
    }

    public float sqrMagnitude() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    @Override
    public String toString() {
        return "Vector3[x=" + this.x + ", y=" + this.y + ", z=" + this.z + "]";
    }
}