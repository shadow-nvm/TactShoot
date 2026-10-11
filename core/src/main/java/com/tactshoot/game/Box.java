package com.tactshoot.game;

/** An axis-aligned solid box in the world. */
public class Box {
    public float minX, minY, minZ;
    public float maxX, maxY, maxZ;

    /** Material index into the map's material table (see Maps). */
    public int material;

    public Box() {
    }

    public Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int material) {
        set(minX, minY, minZ, maxX, maxY, maxZ, material);
    }

    public void set(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int material) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.material = material;
    }

    /** Convenience: centered box on the ground. */
    public static Box ground(float cx, float cz, float w, float d, float h, int material) {
        return new Box(cx - w / 2f, 0f, cz - d / 2f, cx + w / 2f, h, cz + d / 2f, material);
    }

    public float centerX() {
        return (minX + maxX) / 2f;
    }

    public float centerY() {
        return (minY + maxY) / 2f;
    }

    public float centerZ() {
        return (minZ + maxZ) / 2f;
    }
}
