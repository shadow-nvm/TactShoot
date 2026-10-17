package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/** Offscreen scene buffer + screen-space post pass (grade, vignette, chromatic aberration, grain). */
public class PostFx {

    private final Assets assets;
    private final QuadRender quad = new QuadRender();
    private FrameBuffer fbo;
    private int w, h;
    private float damage = 0f;
    private float lowHp = 0f;
    private boolean active;

    public PostFx(Assets assets) {
        this.assets = assets;
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (fbo != null) {
            try { fbo.dispose(); } catch (Exception ignored) {}
            fbo = null;
        }
        // A depth-less fallback would render the world without depth testing
        // (the "high settings looks broken" symptom), so fail the pass instead
        // and let the scene draw straight to the screen.
        try {
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        } catch (Throwable e1) {
            try {
                fbo = new FrameBuffer(Pixmap.Format.RGB888, width, height, true);
            } catch (Throwable e2) {
                Gdx.app.error("TactShoot", "Post FX disabled (no depth-capable FBO): " + e2.getMessage());
                fbo = null;
            }
        }
        w = width;
        h = height;
    }

    public void begin() {
        active = false;
        if (!canRender()) return;
        try {
            fbo.begin();
            active = true;
        } catch (Throwable t) {
            Gdx.app.error("TactShoot", "Post FX begin failed: " + t.getMessage());
        }
    }

    public void end() {
        if (!canRender() || !active) return;
        active = false;
        try {
            fbo.end();
        } catch (Throwable t) {
            Gdx.app.error("TactShoot", "Post FX end failed: " + t.getMessage());
            return;
        }
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        assets.postShader.bind();
        assets.postShader.setUniformi("u_tex", 0);
        assets.postShader.setUniformf("u_damage", damage);
        assets.postShader.setUniformf("u_lowHp", lowHp);
        assets.postShader.setUniformf("u_time", (float) (System.currentTimeMillis() % 1000000L));
        assets.postShader.setUniformf("u_texel", 1f / Math.max(1, w), 1f / Math.max(1, h));
        fbo.getColorBufferTexture().bind(0);
        quad.draw(assets.postShader);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private boolean canRender() {
        return fbo != null && w > 0 && h > 0
                && assets.postShader != null && assets.postShader.isCompiled();
    }

    public void setDamage(float d) {
        damage = d;
    }

    public void setLowHp(float d) {
        lowHp = d;
    }

    public void dispose() {
        quad.dispose();
        if (fbo != null) fbo.dispose();
    }
}
