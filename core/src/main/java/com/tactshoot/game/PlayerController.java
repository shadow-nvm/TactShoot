package com.tactshoot.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Local player simulation: look, movement, weapons, reload, grenades. */
public class PlayerController {

    public interface Callbacks {
        /** A shot was fired from origin toward dir with the given spec. */
        void onShoot(PlayerEntity e, Vector3 origin, Vector3 dir, Weapons.Spec spec);

        /** A grenade was thrown. */
        void onGrenade(PlayerEntity e, Vector3 origin, Vector3 dir);

        /** Footstep event (for sounds). */
        void onStep(PlayerEntity e);
    }

    private final Settings settings;
    private final Assets assets;
    private final Callbacks cb;

    public float recoilVis;
    private boolean wantCrouch;
    private float dryTimer;
    private float stepTimer;
    private float airControl = 3.5f;
    private float groundControl = 13f;

    private final Vector3 wish = new Vector3();
    private final Vector3 eye = new Vector3();
    private final Vector3 dir = new Vector3();
    private final Vector3 rightV = new Vector3();
    private final Vector3 upV = new Vector3();
    private final Vector3 tmp = new Vector3();

    public PlayerController(Settings settings, Assets assets, Callbacks cb) {
        this.settings = settings;
        this.assets = assets;
        this.cb = cb;
    }

    public boolean wantsCrouch() {
        return wantCrouch;
    }

    public void reset(PlayerEntity e) {
        wantCrouch = false;
        recoilVis = 0f;
        dryTimer = 0f;
        stepTimer = 0f;
        e.aiming = false;
        e.crouching = false;
        e.sprinting = false;
        e.firing = false;
    }

    public void update(PlayerEntity e, InputState in, float dt, Array<Box> solids) {
        if (!e.alive) return;

        // ---------- look ----------
        float sens = settings.sensitivity * 0.0022f;
        e.yaw += in.lookDX * sens;
        e.pitch -= in.lookDY * sens;
        e.pitch = T.clamp(e.pitch, -1.45f, 1.45f);

        // ---------- crouch ----------
        if (in.crouchPressed) wantCrouch = !wantCrouch;
        boolean headBlocked = !Collision.canStand(e, NetConfig.STAND_HEIGHT, solids);
        e.crouching = wantCrouch || headBlocked;

        // ---------- aiming ----------
        e.aiming = in.ads && !e.crouching && e.reloadTimer <= 0f;

        // ---------- movement ----------
        Weapons.Spec spec = e.currentSpec();
        float maxSpeed = 4.3f;
        if (e.sprinting) maxSpeed = 6.4f;
        if (e.crouching) maxSpeed = 2.5f;
        maxSpeed *= spec.moveSpeedMul;
        if (e.aiming) maxSpeed *= 0.60f;

        float mx = in.moveX, my = in.moveY;
        e.sprinting = in.sprint && my > 0.35f && !e.aiming && !e.crouching;
        if (e.sprinting) maxSpeed = 6.4f * spec.moveSpeedMul;

        wish.setZero();
        if (mx != 0f || my != 0f) {
            wish.set(MathUtils.cos(e.yaw) * my + MathUtils.sin(e.yaw) * mx,
                    0f,
                    MathUtils.sin(e.yaw) * my - MathUtils.cos(e.yaw) * mx)
                    .nor()
                    .scl(maxSpeed);
        }
        float control = e.onGround ? groundControl : airControl;
        float rate = T.damp(control, dt);
        e.vel.x += (wish.x - e.vel.x) * rate;
        e.vel.z += (wish.z - e.vel.z) * rate;
        e.moving = (Math.abs(e.vel.x) + Math.abs(e.vel.z)) > 0.6f && e.onGround;

        if (in.jumpPressed && e.onGround) {
            e.vel.y = 7.2f;
            e.onGround = false;
        }
        e.vel.y -= 22f * dt;
        if (e.vel.y < -32f) e.vel.y = -32f;
        Collision.moveEntity(e, dt, solids);

        // footsteps
        if (e.moving) {
            stepTimer -= dt * (e.sprinting ? 1.9f : 1f);
            if (stepTimer <= 0f) {
                stepTimer = 0.42f;
                cb.onStep(e);
            }
        }

        // ---------- timers ----------
        e.fireTimer -= dt;
        e.switchTimer -= dt;
        e.grenadeTimer -= dt;
        dryTimer -= dt;

        // spread recovery
        float spreadTarget = spec.spreadBase
                * (e.aiming ? spec.spreadAdsMul : 1f)
                * (e.moving ? spec.spreadMoveMul : 1f)
                * (e.onGround ? 1f : spec.spreadAirMul);
        e.spread = Math.max(spreadTarget, e.spread - spec.spreadRecover * dt);

        // ---------- reload ----------
        if (in.reloadPressed) startReload(e);
        if (spec.shotgun) {
            if (e.reloadTimer > 0f) {
                e.reloadTimer -= dt;
                if (e.reloadTimer <= 0f) {
                    if (e.ammo() < spec.magSize && e.reserveAmmo() > 0) {
                        e.mag[e.weapon]++;
                        e.reserve[e.weapon]--;
                        e.reloadTimer = spec.reloadTime;
                        assets.play(Assets.S_PUMP, 0.7f);
                    }
                }
            }
        } else if (e.reloadTimer > 0f) {
            e.reloadTimer -= dt;
            if (e.reloadTimer <= 0f) {
                int need = spec.magSize - e.ammo();
                int take = Math.min(need, e.reserveAmmo());
                e.mag[e.weapon] += take;
                e.reserve[e.weapon] -= take;
            }
        }

        // ---------- weapon switch ----------
        if (in.switchPressed && e.switchTimer <= 0f && e.reloadTimer <= 0f) {
            e.weapon = 1 - e.weapon;
            e.switchTimer = 0.45f;
            assets.play(Assets.S_SWITCH, 0.8f);
        }

        // ---------- fire ----------
        e.firing = false;
        boolean firing = in.fire && e.switchTimer <= 0f;
        if (firing && e.reloadTimer > 0f && spec.shotgun && e.ammo() > 0) {
            e.reloadTimer = 0f; // pump shotgun can interrupt the reload
        }
        if (firing && e.fireTimer <= 0f && e.reloadTimer <= 0f) {
            if (e.ammo() <= 0) {
                if (dryTimer <= 0f) {
                    dryTimer = 0.35f;
                    assets.play(Assets.S_DRY, 0.9f);
                    startReload(e);
                }
            } else {
                shoot(e);
            }
        }

        // ---------- grenade ----------
        if (in.grenadePressed && e.grenades > 0 && e.grenadeTimer <= 0f) {
            e.grenades--;
            e.grenadeTimer = 3.0f;
            eye.set(e.pos.x, e.pos.y + e.eyeY(), e.pos.z);
            dirFromLook(e, eye, dir, 0f);
            assets.play(Assets.S_PIN, 0.8f);
            cb.onGrenade(e, eye, dir);
        }

        recoilVis = Math.max(0f, recoilVis - recoilVis * 12f * dt);
    }

    private void shoot(PlayerEntity e) {
        Weapons.Spec spec = e.currentSpec();
        e.mag[e.weapon]--;
        e.fireTimer = 1f / spec.rof;
        e.firing = true;

        // recoil kick
        e.pitch += spec.recoilPitch;
        e.yaw += MathUtils.random(-spec.recoilYaw, spec.recoilYaw);
        e.pitch = T.clamp(e.pitch, -1.45f, 1.45f);
        recoilVis = Math.min(recoilVis + spec.recoilPitch * 0.8f, 0.14f);

        // spread growth
        e.spread = Math.min(spec.spreadMax, e.spread + spec.spreadPerShot);

        eye.set(e.pos.x, e.pos.y + e.eyeY(), e.pos.z);
        dirFromLook(e, eye, dir, e.spread);
        assets.play(spec.soundKind == 2 ? Assets.S_SHOT_SG
                : spec.soundKind == 1 ? Assets.S_SHOT_SMG
                : spec.soundKind == 3 ? Assets.S_SHOT_PISTOL
                : Assets.S_SHOT_AR, 1f);
        cb.onShoot(e, eye, dir, spec);

        // auto reload when empty
        if (e.ammo() <= 0 && e.reserveAmmo() > 0) {
            startReload(e);
        }
    }

    /** Builds a look direction from yaw/pitch plus random cone spread. */
    private void dirFromLook(PlayerEntity e, Vector3 origin, Vector3 out, float spread) {
        T.dirFromAngles(e.yaw, e.pitch, out);
        if (spread > 0.0001f) {
            rightV.set(MathUtils.sin(e.yaw), 0f, -MathUtils.cos(e.yaw));
            upV.set(rightV).crs(out).nor();
            float a = MathUtils.random(0f, MathUtils.PI2);
            float r = spread * (float) Math.sqrt(MathUtils.random());
            out.mulAdd(rightV, MathUtils.cos(a) * r)
                    .mulAdd(upV, MathUtils.sin(a) * r)
                    .nor();
        }
    }

    public void startReload(PlayerEntity e) {
        Weapons.Spec spec = e.currentSpec();
        if (e.reloadTimer > 0f) return;
        if (e.ammo() >= spec.magSize) return;
        if (e.reserveAmmo() <= 0) return;
        e.reloadTimer = spec.reloadTime;
        assets.play(Assets.S_RELOAD, 0.9f);
    }

    public void cancelReload(PlayerEntity e) {
        if (!e.currentSpec().shotgun) {
            // keep partial reload progress but stop
            e.reloadTimer = Math.min(e.reloadTimer, 0f);
        }
    }
}
