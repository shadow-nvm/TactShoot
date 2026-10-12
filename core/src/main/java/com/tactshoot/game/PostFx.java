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

    public PostFx(Assets assets) {
        this.assets = assets;
    }

    public void resize(int width, int height) {
        if (fbo != null) {
            try { fbo.dispose(); } catch (Exception ignored) {}
            fbo = null;
        }
        try {
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
        } catch (Exception e1) {
            try {
                // Fallback without depth buffer if hardware attachment failed
                fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
            } catch (Exception e2) {
                Gdx.app.error("TactShoot", "FrameBuffer creation failed: " + e2.getMessage());
            }
        }
        w = width;
        h = height;
    }

    public void begin() {
        if (fbo != null) fbo.begin();
    }

    public void end() {
        if (fbo == null) return;
        fbo.end();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        assets.postShader.bind();
        assets.postShader.setUniformi("u_tex", 0);
        assets.postShader.setUniformf("u_damage", damage);
        assets.postShader.setUniformf("u_lowHp", lowHp);
        assets.postShader.setUniformf("u_time", (float) (System.currentTimeMillis() % 1000000L));
        fbo.getColorBufferTexture().bind(0);
        quad.draw(assets.postShader);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
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
