package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * CPU-billboard particle system: muzzle flashes, sparks, smoke, tracers,
 * explosions, shell casings. Two render passes (additive / alpha) sharing
 * one dynamic mesh and one soft-dot texture.
 */
public class Fx {

    public static final int MAX = 720;
    public static final int K_SMOKE = 0, K_SPARK = 1, K_FLASH = 2, K_TRACER = 3, K_FIRE = 4, K_SHELL = 5;

    private static class P {
        float x, y, z;
        float vx, vy, vz;
        float life, maxLife;
        float size;
        float r, g, b, a;
        float grav;
        float grow;
        int kind;
        boolean additive;
        float x2, y2, z2; // tracer end / shell spin
        float spin;
    }

    private final Array<P> ps = new Array<>(false, 256);
    private final float[] verts = new float[MAX * 4 * 9];
    private final short[] indices = new short[MAX * 6];
    private final Mesh mesh;
    private final Assets assets;

    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();
    private final Vector3 dir = new Vector3();
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmp2 = new Vector3();

    private int drawCount;

    public Fx(Assets assets) {
        this.assets = assets;
        for (int i = 0; i < MAX; i++) {
            int v = i * 4;
            indices[i * 6 + 0] = (short) v;
            indices[i * 6 + 1] = (short) (v + 1);
            indices[i * 6 + 2] = (short) (v + 2);
            indices[i * 6 + 3] = (short) v;
            indices[i * 6 + 4] = (short) (v + 2);
            indices[i * 6 + 5] = (short) (v + 3);
        }
        mesh = new Mesh(true, MAX * 4, MAX * 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
        mesh.setIndices(indices);
    }

    // ---------------------------------------------------------------- spawns

    private P obtain() {
        if (ps.size >= MAX) ps.removeIndex(0);
        P p = new P();
        ps.add(p);
        return p;
    }

    public void muzzleFlash(float x, float y, float z, float dx, float dy, float dz) {
        P f = obtain();
        f.kind = K_FLASH;
        f.x = x + dx * 0.25f;
        f.y = y + dy * 0.25f;
        f.z = z + dz * 0.25f;
        f.vx = dx * 2.2f;
        f.vy = dy * 2.2f;
        f.vz = dz * 2.2f;
        f.life = f.maxLife = 0.055f;
        f.size = 0.55f;
        f.r = 1f; f.g = 0.85f; f.b = 0.5f; f.a = 1f;
        f.additive = true;
        for (int i = 0; i < 3; i++) {
            P s = obtain();
            s.kind = K_SPARK;
            s.x = x + dx * 0.3f;
            s.y = y + dy * 0.3f;
            s.z = z + dz * 0.3f;
            s.vx = dx * (6f + MathUtils.random(4f)) + MathUtils.random(-1.5f, 1.5f);
            s.vy = dy * (6f + MathUtils.random(4f)) + MathUtils.random(-1f, 1f);
            s.vz = dz * (6f + MathUtils.random(4f)) + MathUtils.random(-1.5f, 1.5f);
            s.life = s.maxLife = MathUtils.random(0.06f, 0.16f);
            s.size = 0.10f;
            s.r = 1f; s.g = 0.75f; s.b = 0.35f; s.a = 1f;
            s.grav = 14f;
            s.additive = true;
        }
        P sm = obtain();
        sm.kind = K_SMOKE;
        sm.x = x + dx * 0.35f;
        sm.y = y + dy * 0.35f;
        sm.z = z + dz * 0.35f;
        sm.vx = dx * 1.4f + MathUtils.random(-0.3f, 0.3f);
        sm.vy = dy * 1.4f + 0.35f;
        sm.vz = dz * 1.4f + MathUtils.random(-0.3f, 0.3f);
        sm.life = sm.maxLife = MathUtils.random(0.5f, 0.8f);
        sm.size = 0.22f;
        sm.r = 0.55f; sm.g = 0.53f; sm.b = 0.5f; sm.a = 0.32f;
        sm.grow = 1.1f;
        sm.additive = false;
    }

    public void tracer(float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
        P t = obtain();
        t.kind = K_TRACER;
        t.x = x1; t.y = y1; t.z = z1;
        t.x2 = x2; t.y2 = y2; t.z2 = z2;
        t.life = t.maxLife = 0.07f;
        t.size = 0.024f;
        t.r = r; t.g = g; t.b = b; t.a = 1f;
        t.additive = true;
    }

    public void impactSparks(float x, float y, float z, float nx, float ny, float nz, boolean hot) {
        int n = hot ? 6 : 4;
        for (int i = 0; i < n; i++) {
            P s = obtain();
            s.kind = K_SPARK;
            s.x = x; s.y = y; s.z = z;
            s.vx = nx * MathUtils.random(1f, 3f) + MathUtils.random(-2f, 2f);
            s.vy = ny * MathUtils.random(1f, 3f) + MathUtils.random(1f, 3f);
            s.vz = nz * MathUtils.random(1f, 3f) + MathUtils.random(-2f, 2f);
            s.life = s.maxLife = MathUtils.random(0.08f, 0.22f);
            s.size = 0.06f;
            s.r = hot ? 1f : 0.9f;
            s.g = hot ? 0.7f : 0.75f;
            s.b = hot ? 0.3f : 0.5f;
            s.a = 1f;
            s.grav = 12f;
            s.additive = true;
        }
        P sm = obtain();
        sm.kind = K_SMOKE;
        sm.x = x + nx * 0.04f;
        sm.y = y + ny * 0.04f;
        sm.z = z + nz * 0.04f;
        sm.vx = nx * 0.9f;
        sm.vy = ny * 0.9f + 0.3f;
        sm.vz = nz * 0.9f;
        sm.life = sm.maxLife = MathUtils.random(0.5f, 0.9f);
        sm.size = 0.18f;
        sm.r = 0.7f; sm.g = 0.66f; sm.b = 0.6f; sm.a = 0.4f;
        sm.grow = 1.2f;
        sm.additive = false;
    }

    public void blood(float x, float y, float z, float dx, float dy, float dz) {
        for (int i = 0; i < 5; i++) {
            P s = obtain();
            s.kind = K_SPARK;
            s.x = x; s.y = y; s.z = z;
            s.vx = -dx * MathUtils.random(0.5f, 2f) + MathUtils.random(-1f, 1f);
            s.vy = -dy * MathUtils.random(0.5f, 2f) + MathUtils.random(0.5f, 2f);
            s.vz = -dz * MathUtils.random(0.5f, 2f) + MathUtils.random(-1f, 1f);
            s.life = s.maxLife = MathUtils.random(0.15f, 0.35f);
            s.size = 0.08f;
            s.r = 0.55f; s.g = 0.05f; s.b = 0.04f; s.a = 0.9f;
            s.grav = 15f;
            s.additive = false;
        }
    }

    public void shell(float x, float y, float z, float dx, float dz) {
        P s = obtain();
        s.kind = K_SHELL;
        s.x = x; s.y = y; s.z = z;
        s.vx = dx * MathUtils.random(0.8f, 1.6f);
        s.vy = MathUtils.random(1.6f, 2.4f);
        s.vz = dz * MathUtils.random(0.8f, 1.6f);
        s.life = s.maxLife = MathUtils.random(0.5f, 0.8f);
        s.size = 0.03f;
        s.r = 0.95f; s.g = 0.8f; s.b = 0.35f; s.a = 1f;
        s.grav = 9.8f;
        s.spin = MathUtils.random(-20f, 20f);
        s.additive = false;
    }

    public void explosion(float x, float y, float z) {
        P f = obtain();
        f.kind = K_FLASH;
        f.x = x; f.y = y; f.z = z;
        f.life = f.maxLife = 0.12f;
        f.size = 3.4f;
        f.r = 1f; f.g = 0.75f; f.b = 0.35f; f.a = 1f;
        f.additive = true;
        for (int i = 0; i < 22; i++) {
            P s = obtain();
            s.kind = K_SPARK;
            s.x = x; s.y = y; s.z = z;
            float a = MathUtils.random(0f, MathUtils.PI2);
            float b = MathUtils.random(-0.4f, 1.2f);
            float sp = MathUtils.random(4f, 14f);
            s.vx = MathUtils.cos(a) * MathUtils.cos(b) * sp;
            s.vy = MathUtils.sin(b) * sp;
            s.vz = MathUtils.sin(a) * MathUtils.cos(b) * sp;
            s.life = s.maxLife = MathUtils.random(0.2f, 0.6f);
            s.size = 0.12f;
            s.r = 1f; s.g = MathUtils.random(0.4f, 0.8f); s.b = 0.25f; s.a = 1f;
            s.grav = 9f;
            s.additive = true;
        }
        for (int i = 0; i < 8; i++) {
            P sm = obtain();
            sm.kind = K_SMOKE;
            sm.x = x + MathUtils.random(-0.6f, 0.6f);
            sm.y = y + MathUtils.random(0f, 0.4f);
            sm.z = z + MathUtils.random(-0.6f, 0.6f);
            sm.vx = MathUtils.random(-2f, 2f);
            sm.vy = MathUtils.random(1f, 3.4f);
            sm.vz = MathUtils.random(-2f, 2f);
            sm.life = sm.maxLife = MathUtils.random(0.8f, 1.6f);
            sm.size = MathUtils.random(0.5f, 0.9f);
            sm.r = 0.22f; sm.g = 0.2f; sm.b = 0.18f; sm.a = 0.55f;
            sm.grow = 1.6f;
            sm.additive = false;
        }
        for (int i = 0; i < 6; i++) {
            P fr = obtain();
            fr.kind = K_FIRE;
            fr.x = x + MathUtils.random(-0.5f, 0.5f);
            fr.y = y;
            fr.z = z + MathUtils.random(-0.5f, 0.5f);
            fr.vx = MathUtils.random(-0.6f, 0.6f);
            fr.vy = MathUtils.random(1.5f, 4f);
            fr.vz = MathUtils.random(-0.6f, 0.6f);
            fr.life = fr.maxLife = MathUtils.random(0.25f, 0.6f);
            fr.size = MathUtils.random(0.5f, 0.9f);
            fr.r = 1f; fr.g = 0.55f; fr.b = 0.18f; fr.a = 0.9f;
            fr.additive = true;
        }
    }

    public void muzzleSmoke(float x, float y, float z) {
        P sm = obtain();
        sm.kind = K_SMOKE;
        sm.x = x; sm.y = y; sm.z = z;
        sm.vx = MathUtils.random(-0.2f, 0.2f);
        sm.vy = 0.5f;
        sm.vz = MathUtils.random(-0.2f, 0.2f);
        sm.life = sm.maxLife = 0.9f;
        sm.size = 0.14f;
        sm.r = 0.6f; sm.g = 0.58f; sm.b = 0.55f; sm.a = 0.25f;
        sm.grow = 1.4f;
        sm.additive = false;
    }

    public void grenadeTrail(float x, float y, float z) {
        P sm = obtain();
        sm.kind = K_SMOKE;
        sm.x = x; sm.y = y; sm.z = z;
        sm.vx = MathUtils.random(-0.15f, 0.15f);
        sm.vy = 0.1f;
        sm.vz = MathUtils.random(-0.15f, 0.15f);
        sm.life = sm.maxLife = 0.35f;
        sm.size = 0.08f;
        sm.r = 0.8f; sm.g = 0.8f; sm.b = 0.75f; sm.a = 0.4f;
        sm.grow = 0.8f;
        sm.additive = false;
    }

    // ---------------------------------------------------------------- update / render

    public void update(float dt) {
        for (int i = ps.size - 1; i >= 0; i--) {
            P p = ps.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                ps.removeIndex(i);
                continue;
            }
            if (p.kind == K_TRACER) continue;
            p.vy -= p.grav * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            if (p.kind == K_SMOKE) {
                p.size += p.grow * dt;
            }
        }
    }

    public void render(Camera cam, boolean additivePass) {
        if (assets.particleShader == null || !assets.particleShader.isCompiled() || assets.dot == null) return;
        drawCount = 0;
        Vector3 cRight = tmp.set(cam.direction).crs(cam.up).nor();
        Vector3 cUp = tmp2.set(cRight).crs(cam.direction).nor();
        for (int i = 0; i < ps.size; i++) {
            P p = ps.get(i);
            if (p.additive != additivePass) continue;
            if (drawCount >= MAX) break;
            float t = p.life / p.maxLife;
            int o = drawCount * 36;
            drawCount++;
            float size = p.size;
            float r, g, b, a;
            r = p.r; g = p.g; b = p.b; a = p.a * t;
            if (p.kind == K_FLASH) {
                size = p.size * (1.4f - t * 0.5f);
            }
            if (p.kind == K_TRACER) {
                a = p.a * t;
                if (!emitTracer(o, p, cRight, cUp, size, r, g, b, a)) {
                    // Degenerate tracer: emit a zero-area quad so the unused
                    // slot never renders stale vertex data.
                    vert(o + 0, p.x, p.y, p.z, r, g, b, 0f, 0, 0);
                    vert(o + 1, p.x, p.y, p.z, r, g, b, 0f, 1, 0);
                    vert(o + 2, p.x, p.y, p.z, r, g, b, 0f, 1, 1);
                    vert(o + 3, p.x, p.y, p.z, r, g, b, 0f, 0, 1);
                }
                continue;
            }
            if (p.kind == K_FIRE) {
                a = p.a * t * t;
                size = p.size * (1f + (1f - t) * 0.6f);
            }
            // billboard quad
            float hx = cRight.x * size / 2f, hy = cRight.y * size / 2f, hz = cRight.z * size / 2f;
            float ux = cUp.x * size / 2f, uy = cUp.y * size / 2f, uz = cUp.z * size / 2f;
            // spin for shells (rotate around view axis)
            if (p.kind == K_SHELL) {
                float ca = MathUtils.cos(p.spin * p.life);
                float sa = MathUtils.sin(p.spin * p.life);
                float rx = cRight.x * ca + cUp.x * sa;
                float ry = cRight.y * ca + cUp.y * sa;
                float rz = cRight.z * ca + cUp.z * sa;
                float ux2 = -cRight.x * sa + cUp.x * ca;
                float uy2 = -cRight.y * sa + cUp.y * ca;
                float uz2 = -cRight.z * sa + cUp.z * ca;
                hx = rx * size / 2f; hy = ry * size / 2f; hz = rz * size / 2f;
                ux = ux2 * size / 2f; uy = uy2 * size / 2f; uz = uz2 * size / 2f;
            }
            vert(o + 0, p.x - hx - ux, p.y - hy - uy, p.z - hz - uz, r, g, b, a, 0, 0);
            vert(o + 1, p.x + hx - ux, p.y + hy - uy, p.z + hz - uz, r, g, b, a, 1, 0);
            vert(o + 2, p.x + hx + ux, p.y + hy + uy, p.z + hz + uz, r, g, b, a, 1, 1);
            vert(o + 3, p.x - hx + ux, p.y - hy + uy, p.z - hz + uz, r, g, b, a, 0, 1);
        }
        if (drawCount == 0) return;
        // The vertex data was previously written to the CPU array but never
        // uploaded, so particles, tracers and muzzle flashes were invisible.
        mesh.setVertices(verts, 0, drawCount * 36);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        if (additivePass) {
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        } else {
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        Gdx.gl.glDepthMask(false);
        assets.particleShader.bind();
        assets.particleShader.setUniformMatrix("u_projTrans", cam.combined);
        assets.dot.bind(0);
        assets.particleShader.setUniformi("u_tex", 0);
        mesh.bind(assets.particleShader);
        mesh.render(assets.particleShader, GL20.GL_TRIANGLES, 0, drawCount * 6);
        mesh.unbind(assets.particleShader);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private boolean emitTracer(int o, P p, Vector3 cRight, Vector3 cUp, float w, float r, float g, float b, float a) {
        dir.set(p.x2 - p.x, p.y2 - p.y, p.z2 - p.z);
        float len = dir.len();
        if (len < 0.01f) return false;
        dir.scl(1f / len);
        // perpendicular toward camera for width
        right.set(dir).crs(cUp).nor().scl(w / 2f);
        if (right.len2() < 1e-10f) right.set(cRight).scl(w / 2f);
        vert(o + 0, p.x - right.x, p.y - right.y, p.z - right.z, r, g, b, a, 0, 0);
        vert(o + 1, p.x + right.x, p.y + right.y, p.z + right.z, r, g, b, a, 1, 0);
        vert(o + 2, p.x2 + right.x, p.y2 + right.y, p.z2 + right.z, r, g, b, a, 1, 1);
        vert(o + 3, p.x2 - right.x, p.y2 - right.y, p.z2 - right.z, r, g, b, a, 0, 1);
        return true;
    }

    private void vert(int o, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        verts[o] = x;
        verts[o + 1] = y;
        verts[o + 2] = z;
        verts[o + 3] = r;
        verts[o + 4] = g;
        verts[o + 5] = b;
        verts[o + 6] = a;
        verts[o + 7] = u;
        verts[o + 8] = v;
    }

    public void dispose() {
        mesh.dispose();
    }
}
