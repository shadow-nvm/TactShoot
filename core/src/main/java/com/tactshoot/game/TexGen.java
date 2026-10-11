package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

import java.util.Random;

/**
 * Runtime Pixmap generation for UI sprites and fallback world textures.
 * Everything here is procedural so the game works with zero binary assets.
 */
public final class TexGen {

    private TexGen() {
    }

    // ---------------------------------------------------------------- noise

    public static float[][] fbm(int dim, long seed, int octaves) {
        float[][] out = new float[dim][dim];
        Random rng = new Random(seed);
        float amp = 1f;
        float total = 0f;
        for (int o = 0; o < octaves; o++) {
            int step = Math.max(1, dim >> o);
            for (int y = 0; y < dim; y++) {
                for (int x = 0; x < dim; x++) {
                    out[y][x] += valueNoise(rng, x / step, y / step, step, dim) * amp;
                }
            }
            total += amp;
            amp *= 0.5f;
        }
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                out[y][x] /= total;
            }
        }
        return out;
    }

    private static float valueNoise(Random rng, int gx, int gy, int step, int dim) {
        long h = ((long) gx * 73856093L) ^ ((long) gy * 19349663L);
        rng.setSeed(h);
        return rng.nextFloat();
    }

    /** Generates a tiling noise Pixmap tinted toward a base color. */
    public static Pixmap noiseTiled(int dim, long seed, Color base, float contrast) {
        float[][] n = fbm(dim, seed, 5);
        Pixmap p = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                float v = n[y][x];
                // make it tile: blend toward edges
                float fx = Math.min(x, dim - 1 - x) / (dim * 0.25f);
                float fy = Math.min(y, dim - 1 - y) / (dim * 0.25f);
                float edge = Math.min(1f, Math.min(fx, fy));
                v = v * edge + n[0][0] * (1f - edge);
                float r = T.clamp(base.r * (1f + (v - 0.5f) * 2f * contrast), 0f, 1f);
                float g = T.clamp(base.g * (1f + (v - 0.5f) * 2f * contrast), 0f, 1f);
                float b = T.clamp(base.b * (1f + (v - 0.5f) * 2f * contrast), 0f, 1f);
                p.setColor(r, g, b, 1f);
                p.drawPixel(x, y);
            }
        }
        return p;
    }

    // ---------------------------------------------------------------- sprites

    /** Soft radial dot used for particles, blips and muzzle flashes. */
    public static Pixmap softDot(int size, Color inner, float innerAlpha) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float c = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - c) / c;
                float dy = (y - c) / c;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = T.clamp(1f - d, 0f, 1f);
                a = a * a * innerAlpha;
                p.setColor(inner.r, inner.g, inner.b, a);
                p.drawPixel(x, y);
            }
        }
        return p;
    }

    /** Stretched spark: bright core fading along +Y (used for muzzle flash). */
    public static Pixmap flash(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float c = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - c) / c;
                float dy = (y - c) / c;
                float d = (float) Math.sqrt(dx * dx * 2.4f + dy * dy * 0.55f);
                float a = T.clamp(1.2f - d, 0f, 1f);
                a = Math.min(1f, a * 1.6f);
                p.setColor(1f, 0.85f + (y / (float) size) * 0.15f, 0.5f, a);
                p.drawPixel(x, y);
            }
        }
        return p;
    }

    public static Pixmap crosshair(int size, Color col) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        int c = size / 2;
        int len = size / 5;
        int thick = Math.max(2, size / 26);
        int gap = size / 16;
        p.setColor(0, 0, 0, 0.6f);
        for (int i = 0; i < 4; i++) {
            int dx = (i == 0 ? 1 : (i == 1 ? -1 : 0));
            int dy = (i == 2 ? 1 : (i == 3 ? -1 : 0));
            int sx = c + dx * gap;
            int sy = c + dy * gap;
            int ex = c + dx * (gap + len);
            int ey = c + dy * (gap + len);
            p.fillRectangle(Math.min(sx, ex) - thick / 2, Math.min(sy, ey) - thick / 2,
                    Math.abs(ex - sx) + thick, Math.abs(ey - sy) + thick);
        }
        p.setColor(col);
        for (int i = 0; i < 4; i++) {
            int dx = (i == 0 ? 1 : (i == 1 ? -1 : 0));
            int dy = (i == 2 ? 1 : (i == 3 ? -1 : 0));
            int sx = c + dx * gap;
            int sy = c + dy * gap;
            int ex = c + dx * (gap + len);
            int ey = c + dy * (gap + len);
            for (int x = Math.min(sx, ex) - 1; x <= Math.max(sx, ex) + 1; x++) {
                for (int y = Math.min(sy, ey) - 1; y <= Math.max(sy, ey) + 1; y++) {
                    if (x >= 0 && y >= 0 && x < size && y < size) p.drawPixel(x, y);
                }
            }
        }
        p.setColor(col);
        p.fillCircle(c, c, Math.max(1, size / 40));
        return p;
    }

    public static Pixmap hitmarker(int size, Color col) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        int c = size / 2;
        int len = size / 3;
        int thick = Math.max(2, size / 22);
        p.setColor(col);
        for (int i = 0; i < 4; i++) {
            int dx = (i < 2 ? 1 : -1);
            int dy = (i % 2 == 0 ? 1 : -1);
            for (int t = 0; t <= len; t++) {
                int x = c + dx * (thick / 2 + t) + (dx > 0 ? 0 : -thick);
                int y = c + dy * (thick / 2 + t) + (dy > 0 ? 0 : -thick);
                for (int ox = 0; ox < thick; ox++) {
                    for (int oy = 0; oy < thick; oy++) {
                        int px = x + ox, py = y + oy;
                        if (px >= 0 && py >= 0 && px < size && py < size) p.drawPixel(px, py);
                    }
                }
            }
        }
        return p;
    }

    // ---------------------------------------------------------------- world fallbacks

    public static Pixmap bricks(int dim, long seed, Color mortar, Color brick) {
        Pixmap p = new Pixmap(dim, dim, Pixmap.Format.RGBA8888);
        Random rng = new Random(seed);
        int rows = 8;
        int brickH = dim / rows;
        int brickW = dim / 4;
        p.setColor(mortar);
        p.fill();
        for (int row = 0; row < rows; row++) {
            int offset = (row % 2 == 0) ? 0 : brickW / 2;
            for (int x = -brickW + offset; x < dim; x += brickW) {
                Color c = brick.cpy().mul(0.85f + rng.nextFloat() * 0.3f);
                c.a = 1f;
                p.setColor(c);
                p.fillRectangle(x + 1, row * brickH + 1, brickW - 2, brickH - 2);
            }
        }
        return p;
    }

    public static Pixmap metalPanels(int dim, long seed, Color base) {
        Pixmap p = noiseTiled(dim, seed, base, 0.25f);
        Random rng = new Random(seed);
        int panels = 2;
        int step = dim / panels;
        p.setColor(0, 0, 0, 0.35f);
        for (int i = 0; i <= panels; i++) {
            p.fillRectangle(i * step - 1, 0, 2, dim);
        }
        for (int i = 0; i < 24; i++) {
            int x = rng.nextInt(dim - 4);
            int y = rng.nextInt(dim - 4);
            p.setColor(0.75f, 0.78f, 0.8f, 0.5f);
            p.drawPixel(x, y);
        }
        return p;
    }

    public static Pixmap woodPlanks(int dim, long seed, Color base) {
        Pixmap p = noiseTiled(dim, seed, base, 0.3f);
        p.setColor(0, 0, 0, 0.4f);
        for (int i = 1; i < 4; i++) {
            p.fillRectangle(0, i * (dim / 4) - 1, dim, 2);
        }
        // vertical grain streaks
        Random rng = new Random(seed);
        for (int i = 0; i < 60; i++) {
            int x = rng.nextInt(dim);
            int len = dim / 6 + rng.nextInt(dim / 3);
            p.setColor(0, 0, 0, 0.12f);
            p.fillRectangle(x, rng.nextInt(dim), 1, len);
        }
        return p;
    }
}
