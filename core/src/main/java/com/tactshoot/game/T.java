package com.tactshoot.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Small math / random helpers. */
public final class T {

    private T() {
    }

    public static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Frame-rate independent exponential smoothing factor. */
    public static float damp(float rate, float dt) {
        return 1f - (float) Math.exp(-rate * dt);
    }

    /** Ease out cubic. */
    public static float easeOut(float t) {
        t = clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static float rand(float min, float max) {
        return MathUtils.random(min, max);
    }

    public static int randInt(int min, int max) {
        return MathUtils.random(min, max);
    }

    /** Wrap an angle to [-PI, PI]. */
    public static float wrapAngle(float a) {
        while (a > MathUtils.PI) a -= MathUtils.PI2;
        while (a < -MathUtils.PI) a += MathUtils.PI2;
        return a;
    }

    /** Smoothly interpolate an angle taking the shortest path. */
    public static float lerpAngle(float a, float b, float t) {
        float d = wrapAngle(b - a);
        return a + d * t;
    }

    public static final Vector3 tmp1 = new Vector3();
    public static final Vector3 tmp2 = new Vector3();
    public static final Vector3 tmp3 = new Vector3();
    public static final Vector3 tmp4 = new Vector3();

    /** Direction (normalized) from yaw/pitch (radians), pitch up positive. */
    public static void dirFromAngles(float yaw, float pitch, Vector3 out) {
        float cp = MathUtils.cos(pitch);
        out.set(MathUtils.cos(yaw) * cp, MathUtils.sin(pitch), MathUtils.sin(yaw) * cp);
    }

    /** Compute yaw/pitch (radians) looking from 'from' to 'to'. */
    public static float[] anglesTo(Vector3 from, Vector3 to) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float dz = to.z - from.z;
        float yaw = (float) Math.atan2(dz, dx);
        float horiz = (float) Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) Math.atan2(dy, horiz);
        return new float[]{yaw, pitch};
    }

    public static String fmtTime(float seconds) {
        int s = Math.max(0, (int) Math.ceil(seconds));
        int m = s / 60;
        s = s % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
