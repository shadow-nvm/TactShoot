package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** First-person weapon rendering: bob, recoil, ADS alignment, reload/switch animation. */
public class WeaponView {

    /** Viewmodels are authored at life size; shrink them so they fit in front of the near plane. */
    private static final float SCALE = 0.72f;
    /** Height of the sight line in model space (front and rear sights agree). */
    private static final float SIGHT_Y = 0.096f;

    private final Assets assets;
    private final Settings settings;

    /** Local light so the viewmodel is always readable, independent of map lighting. */
    private final Environment gunEnv = new Environment();
    private final Matrix4 transform = new Matrix4();

    private ModelInstance inst;
    private int specIdx = -1;

    private float kick;
    private float recoilVis;
    private float reloadAmount;
    private float switchAmount;
    private float adsAmount;
    private float sprintAmount;
    private float bobX, bobY;
    private float swayX, swayY;
    private float lastYaw, lastPitch;
    private boolean hasLast;

    public WeaponView(Assets assets, Settings settings) {
        this.assets = assets;
        this.settings = settings;
        gunEnv.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.58f, 1f));
        gunEnv.add(new DirectionalLight().set(new Color(1f, 0.96f, 0.90f, 1f),
                new Vector3(-0.45f, -1f, 0.35f).nor()));
    }

    public void setWeapon(int spec) {
        if (spec == specIdx && inst != null) return;
        specIdx = spec;
        inst = new ModelInstance(assets.gunFor(spec));
        hasLast = false;
    }

    public void onFire() {
        Weapons.Spec s = Weapons.spec(specIdx);
        kick = s.kick;
        recoilVis = Math.min(recoilVis + s.recoilPitch * 0.7f, 0.12f);
    }

    public void update(float dt, PlayerEntity e, float speed) {
        Weapons.Spec s = Weapons.spec(specIdx);
        adsAmount += ((e.aiming ? 1f : 0f) - adsAmount) * T.damp(14f, dt);
        sprintAmount += ((e.sprinting ? 1f : 0f) - sprintAmount) * T.damp(9f, dt);
        kick = Math.max(0f, kick - kick * 9f * dt - 0.001f);
        recoilVis = Math.max(0f, recoilVis - recoilVis * 11f * dt - 0.0005f);
        reloadAmount += ((e.reloadTimer > 0f ? 1f : 0f) - reloadAmount) * T.damp(11f, dt);
        switchAmount += ((e.switchTimer > 0f ? 1f : 0f) - switchAmount) * T.damp(12f, dt);

        if (e.moving && e.onGround) {
            e.bobPhase += speed * dt * 1.7f;
            float amp = 1f - adsAmount * 0.75f;
            bobX = MathUtils.sin(e.bobPhase * 1.9f) * 0.010f * amp;
            bobY = -Math.abs(MathUtils.cos(e.bobPhase * 1.9f)) * 0.014f * amp;
        } else {
            bobX *= Math.max(0f, 1f - 8f * dt);
            bobY *= Math.max(0f, 1f - 8f * dt);
        }

        // Weapon sway lags the camera: quick flicks make the gun drag a little.
        if (hasLast) {
            float dyaw = T.wrapAngle(e.yaw - lastYaw);
            float dpitch = e.pitch - lastPitch;
            float targetX = T.clamp(-dyaw * 0.055f, -0.05f, 0.05f);
            float targetY = T.clamp(-dpitch * 0.045f, -0.045f, 0.045f);
            float rate = T.damp(9f, dt);
            swayX += (targetX - swayX) * rate;
            swayY += (targetY - swayY) * rate;
        }
        lastYaw = e.yaw;
        lastPitch = e.pitch;
        hasLast = true;
    }

    public void render(ModelBatch batch, PerspectiveCamera cam, float yaw, float pitch) {
        if (inst == null) return;

        boolean left = settings.leftHanded;
        float side = left ? -1f : 1f;
        float ads = adsAmount;

        // ADS values line the sights (model y ~= 0.096, scaled) up with the crosshair.
        float sx = side * T.lerp(0.155f, 0.0f, ads) + bobX + swayX;
        float sy = T.lerp(-0.115f, -SIGHT_Y * SCALE, ads) + bobY + swayY;
        float sz = T.lerp(0.42f, 0.40f, ads)
                + kick
                + reloadAmount * 0.16f
                + switchAmount * 0.28f
                + sprintAmount * 0.05f;

        // Camera-space placement: +X right, +Y up, -Z forward.
        // The gun models are authored with the muzzle along -Z, so no yaw
        // offset is needed (the old code rotated them 90 degrees sideways).
        transform.set(cam.view).inv();
        transform.translate(sx, sy, -sz);
        transform.scale(SCALE, SCALE, SCALE);

        float reloadTilt = reloadAmount * 42f;
        float switchTilt = switchAmount * 26f;
        float sprintTilt = sprintAmount * 16f;
        // The camera already carries yaw/pitch; only the local recoil snap is added.
        transform.rotate(Vector3.X, -reloadTilt + switchTilt - sprintTilt + recoilVis * 90f);
        transform.rotate(Vector3.Y, reloadTilt * 0.5f);
        transform.rotate(Vector3.Z, left ? -reloadTilt * 0.3f : reloadTilt * 0.3f);

        inst.transform.set(transform);

        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(cam);
        batch.render(inst, gunEnv);
        batch.end();
    }
}
