package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;

/** Procedural sky dome: gradient, sun disc, ground haze and stars. */
public class Atmosphere {

    private final Assets assets;
    private final QuadRender quad = new QuadRender();

    public Atmosphere(Assets assets) {
        this.assets = assets;
    }

    public void render(PerspectiveCamera cam, MapDef map) {
        if (assets.skyShader == null || !assets.skyShader.isCompiled()) return;
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        assets.skyShader.bind();
        assets.skyShader.setUniformf("u_fwd", cam.direction);
        assets.skyShader.setUniformf("u_right", right(cam));
        assets.skyShader.setUniformf("u_up", cam.up);
        float tanHalf = (float) Math.tan(Math.toRadians(cam.fieldOfView) / 2.0);
        float aspect = cam.viewportWidth / cam.viewportHeight;
        assets.skyShader.setUniformf("u_tanHalf", tanHalf);
        assets.skyShader.setUniformf("u_aspectTan", tanHalf * aspect);
        assets.skyShader.setUniformf("u_sunDir", map.sunDir);
        assets.skyShader.setUniformf("u_skyTop", map.skyTop);
        assets.skyShader.setUniformf("u_skyHorizon", map.skyHorizon);
        assets.skyShader.setUniformf("u_groundCol", map.groundCol);
        assets.skyShader.setUniformf("u_sunCol", map.sunColor);
        assets.skyShader.setUniformf("u_night", map.night);
        quad.draw(assets.skyShader);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private com.badlogic.gdx.math.Vector3 right(PerspectiveCamera cam) {
        return T.tmp1.set(cam.direction).crs(cam.up).nor();
    }

    public void dispose() {
        quad.dispose();
    }
}
