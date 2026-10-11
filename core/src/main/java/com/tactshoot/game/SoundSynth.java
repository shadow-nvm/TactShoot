package com.tactshoot.game;

import com.badlogic.gdx.math.MathUtils;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Procedurally synthesizes every sound effect as a 16-bit mono WAV,
 * so the game needs zero audio files. Deterministic per sound id.
 */
public final class SoundSynth {

    public static final int RATE = 22050;

    private SoundSynth() {
    }

    // ---------------------------------------------------------------- encoding

    public static byte[] wav(float[] s) {
        int n = s.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + n * 2);
        try {
            putString(out, "RIFF");
            putInt(out, 36 + n * 2);
            putString(out, "WAVE");
            putString(out, "fmt ");
            putInt(out, 16);
            putShort(out, 1);              // PCM
            putShort(out, 1);              // mono
            putInt(out, RATE);
            putInt(out, RATE * 2);         // byte rate
            putShort(out, 2);              // block align
            putShort(out, 16);             // bits
            putString(out, "data");
            putInt(out, n * 2);
            for (float v : s) {
                putShort(out, (int) (MathUtils.clamp(v, -1f, 1f) * 32767f));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    private static void putString(ByteArrayOutputStream o, String s) {
        for (int i = 0; i < s.length(); i++) o.write(s.charAt(i));
    }

    private static void putInt(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void putShort(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    // ---------------------------------------------------------------- dsp helpers

    private static float[] noise(Random rng, float durSec, float lpCut, float attack) {
        int n = Math.max(1, (int) (durSec * RATE));
        float[] out = new float[n];
        float lp = 0f;
        float k = MathUtils.clamp(lpCut / (float) RATE * 6.283f, 0.001f, 1f);
        float a = Math.max(1, (int) (attack * RATE));
        for (int i = 0; i < n; i++) {
            float white = rng.nextFloat() * 2f - 1f;
            lp += (white - lp) * k;
            float env = 1f;
            if (i < a) env = i / a;
            out[i] = lp * env;
        }
        return out;
    }

    private static float[] sine(float durSec, float freq, float freqEnd) {
        int n = Math.max(1, (int) (durSec * RATE));
        float[] out = new float[n];
        float phase = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float f = freq + (freqEnd - freq) * t;
            phase += 2f * MathUtils.PI * f / RATE;
            out[i] = MathUtils.sin(phase);
        }
        return out;
    }

    private static void mul(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) a[i] *= b[i];
    }

    private static void decay(float[] s, float rate) {
        for (int i = 0; i < s.length; i++) {
            s[i] *= Math.exp(-rate * i / (float) RATE);
        }
    }

    private static void add(float[] dst, float[] src, int offset, float gain) {
        for (int i = 0; i < src.length && i + offset < dst.length; i++) {
            dst[i + offset] += src[i] * gain;
        }
    }

    private static float[] mix(float[] a, float[] b) {
        int n = Math.max(a.length, b.length);
        float[] out = new float[n];
        add(out, a, 0, 1f);
        add(out, b, 0, 1f);
        return out;
    }

    private static float[] sil(float dur) {
        return new float[Math.max(1, (int) (dur * RATE))];
    }

    // ---------------------------------------------------------------- gun shots

    /** kind: 0 rifle, 1 smg, 2 shotgun, 3 pistol */
    public static byte[] gunshot(int seed, int kind, float distanceGain) {
        Random rng = new Random(seed);
        float dur, boomDur, boomF, crackF, crackLp, click;
        switch (kind) {
            case 1: // smg
                dur = 0.14f; boomDur = 0.10f; boomF = 150f; crackF = 0.028f; crackLp = 3000f; click = 0.35f;
                break;
            case 2: // shotgun
                dur = 0.34f; boomDur = 0.30f; boomF = 95f; crackF = 0.05f; crackLp = 2400f; click = 0.5f;
                break;
            case 3: // pistol
                dur = 0.18f; boomDur = 0.12f; boomF = 130f; crackF = 0.022f; crackLp = 3400f; click = 0.45f;
                break;
            default: // rifle
                dur = 0.22f; boomDur = 0.17f; boomF = 120f; crackF = 0.032f; crackLp = 2800f; click = 0.42f;
                break;
        }
        float[] boom = noise(rng, boomDur, 400f, 0.004f);
        decay(boom, 34f);
        float[] thump = sine(boomDur, boomF, boomF * 0.7f);
        mul(thump, boom);
        float[] crack = noise(rng, crackF, crackLp, 0.0005f);
        float[] body = mix(boom, crack);
        // mechanical click tail
        float[] clickS = noise(rng, 0.03f, 5000f, 0.001f);
        add(body, clickS, (int) (0.02f * RATE), click * 0.8f);
        // distance attenuation
        for (int i = 0; i < body.length; i++) body[i] *= distanceGain;
        // normalize soft
        return wav(body);
    }

    public static byte[] dryFire(int seed) {
        Random rng = new Random(seed);
        float[] c = noise(rng, 0.025f, 6000f, 0.0004f);
        decay(c, 160f);
        return wav(c);
    }

    public static byte[] reload(int seed) {
        Random rng = new Random(seed);
        float[] out = sil(0.62f);
        // mag out click
        float[] c1 = noise(rng, 0.03f, 3500f, 0.0005f);
        decay(c1, 120f);
        add(out, c1, (int) (0.05f * RATE), 0.9f);
        // mag in clack
        float[] c2 = noise(rng, 0.05f, 2200f, 0.0005f);
        decay(c2, 70f);
        add(out, c2, (int) (0.30f * RATE), 1f);
        // slide release
        float[] c3 = noise(rng, 0.07f, 4200f, 0.001f);
        decay(c3, 90f);
        add(out, c3, (int) (0.47f * RATE), 0.85f);
        return wav(out);
    }

    public static byte[] pump(int seed) {
        Random rng = new Random(seed);
        float[] out = sil(0.4f);
        float[] c1 = noise(rng, 0.05f, 1800f, 0.001f);
        decay(c1, 90f);
        add(out, c1, (int) (0.02f * RATE), 0.9f);
        float[] c2 = noise(rng, 0.05f, 2600f, 0.001f);
        decay(c2, 90f);
        add(out, c2, (int) (0.22f * RATE), 0.9f);
        return wav(out);
    }

    // ---------------------------------------------------------------- feedback

    public static byte[] hitmarker(int seed) {
        float[] out = sil(0.06f);
        float[] t = sine(0.022f, 1150f, 1150f);
        decay(t, 10f);
        add(out, t, (int) (0.002f * RATE), 0.9f);
        float[] n = noise(new Random(seed), 0.015f, 6000f, 0.0004f);
        decay(n, 200f);
        add(out, n, 0, 0.5f);
        return wav(out);
    }

    public static byte[] killConfirm(int seed) {
        float[] out = sil(0.30f);
        float[] a = sine(0.09f, 660f, 660f);
        decay(a, 6f);
        float[] b = sine(0.14f, 990f, 990f);
        decay(b, 5f);
        add(out, a, 0, 0.8f);
        add(out, b, (int) (0.10f * RATE), 0.8f);
        return wav(out);
    }

    public static byte[] damage(int seed) {
        float[] out = sil(0.22f);
        float[] t = sine(0.16f, 105f, 62f);
        decay(t, 16f);
        add(out, t, 0, 1f);
        float[] n = noise(new Random(seed), 0.12f, 900f, 0.003f);
        decay(n, 40f);
        add(out, n, 0, 0.55f);
        return wav(out);
    }

    public static byte[] explosion(int seed) {
        Random rng = new Random(seed);
        float[] out = sil(1.05f);
        float[] boom = noise(rng, 0.85f, 500f, 0.006f);
        decay(boom, 6.5f);
        float[] rumble = sine(0.8f, 55f, 30f);
        decay(rumble, 4f);
        add(out, boom, 0, 1f);
        add(out, rumble, 0, 0.7f);
        // debris crackle
        float[] crackle = noise(rng, 0.4f, 2600f, 0.002f);
        decay(crackle, 26f);
        add(out, crackle, (int) (0.03f * RATE), 0.35f);
        return wav(out);
    }

    // ---------------------------------------------------------------- ui / misc

    public static byte[] click(int seed) {
        float[] out = sil(0.05f);
        float[] t = sine(0.016f, 1750f, 1400f);
        decay(t, 60f);
        add(out, t, 0, 0.7f);
        float[] n = noise(new Random(seed), 0.012f, 7000f, 0.0004f);
        decay(n, 260f);
        add(out, n, 0, 0.35f);
        return wav(out);
    }

    public static byte[] pin(int seed) {
        Random rng = new Random(seed);
        float[] out = sil(0.18f);
        for (int i = 0; i < 3; i++) {
            float[] t = sine(0.02f, 2400f - i * 300f, 1800f);
            decay(t, 160f);
            add(out, t, (int) ((0.02f + i * 0.055f) * RATE), 0.55f);
        }
        return wav(out);
    }

    public static byte[] step(int seed) {
        float[] n = noise(new Random(seed), 0.055f, 700f, 0.002f);
        decay(n, 55f);
        return wav(n);
    }

    public static byte[] weaponSwitch(int seed) {
        Random rng = new Random(seed);
        float[] out = sil(0.16f);
        float[] c1 = noise(rng, 0.03f, 3000f, 0.001f);
        decay(c1, 130f);
        add(out, c1, 0, 0.8f);
        float[] c2 = noise(rng, 0.04f, 4200f, 0.001f);
        decay(c2, 110f);
        add(out, c2, (int) (0.09f * RATE), 0.7f);
        return wav(out);
    }
}
