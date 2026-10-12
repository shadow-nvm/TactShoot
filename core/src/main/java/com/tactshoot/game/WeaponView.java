package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** First-person weapon rendering: bob, recoil, ADS alignment, reload/switch animation. */
public class WeaponView {

    private final Assets assets;
    private final Settings settings;
    private final PerspectiveCamera gunCam = new PerspectiveCamera();
    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();
    private final Vector3 fwd = new Vector3();
    private final Vector3 pos = new Vector3();

    private ModelInstance inst;
    private int specIdx = -1;

    private float kick;
    private float recoilVis;
    private float reloadAmount;
    private float switchAmount;
    private float adsAmount;
    private float bobX, bobY;

    public WeaponView(Assets assets, Settings settings) {
        this.assets = assets;
        this.settings = settings;
    }

    public void setWeapon(int spec) {
        if (spec == specIdx && inst != null) return;
        specIdx = spec;
        inst = new ModelInstance(assets.gunFor(spec));
    }

    public void onFire() {
        Weapons.Spec s = Weapons.spec(specIdx);
        kick = s.kick;
        recoilVis = Math.min(recoilVis + s.recoilPitch * 0.7f, 0.12f);
    }

    public void update(float dt, PlayerEntity e, float speed) {
        Weapons.Spec s = Weapons.spec(specIdx);
        adsAmount += ((e.aiming ? 1f : 0f) - adsAmount) * T.damp(13f, dt);
        kick = Math.max(0f, kick - kick * 9f * dt - 0.001f);
        recoilVis = Math.max(0f, recoilVis - recoilVis * 11f * dt - 0.0005f);
        reloadAmount += ((e.reloadTimer > 0f ? 1f : 0f) - reloadAmount) * T.damp(11f, dt);
        switchAmount += ((e.switchTimer > 0f ? 1f : 0f) - switchAmount) * T.damp(12f, dt);
        if (e.moving && e.onGround) {
            e.bobPhase += speed * dt * 1.6f;
            bobX = MathUtils.sin(e.bobPhase * 1.9f) * 0.010f;
            bobY = -Math.abs(MathUtils.cos(e.bobPhase * 1.9f)) * 0.014f;
        } else {
            bobX *= Math.max(0f, 1f - 8f * dt);
            bobY *= Math.max(0f, 1f - 8f * dt);
        }
    }

    public void render(ModelBatch batch, PerspectiveCamera cam, float yaw, float pitch) {
        if (inst == null) return;
        gunCam.position.set(cam.position);
        gunCam.direction.set(cam.direction);
        gunCam.up.set(cam.up);
        gunCam.fieldOfView = cam.fieldOfView;
        gunCam.viewportWidth = cam.viewportWidth;
        gunCam.viewportHeight = cam.viewportHeight;
        gunCam.near = 0.04f;
        gunCam.far = 6f;
        gunCam.update();

        right.set(cam.direction).crs(cam.up).nor();
        up.set(right).crs(cam.direction).nor();
        fwd.set(cam.direction).scl(-1f);

        boolean left = settings.leftHanded;
        float sx = (left ? -1f : 1f) * T.lerp(0.25f, 0.015f, adsAmount) + bobX;
        float sy = T.lerp(-0.185f, -0.165f, adsAmount) + bobY;
        float sz = T.lerp(0.30f, 0.235f, adsAmount) + kick + reloadAmount * 0.16f + switchAmount * 0.22f;

        pos.set(cam.position)
                .mulAdd(right, sx)
                .mulAdd(up, sy)
                .mulAdd(fwd, sz);

        float pitchDeg = (pitch + recoilVis + reloadAmount * 1.05f + switchAmount * 0.55f)
                * MathUtils.radiansToDegrees;
        float yawDeg = yaw * MathUtils.radiansToDegrees;
        inst.transform.idt()
                .translate(pos)
                .rotate(Vector3.Y, yawDeg)
                .rotate(Vector3.X, pitchDeg);

        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(gunCam);
        batch.render(inst);
        batch.end();
    }
}
