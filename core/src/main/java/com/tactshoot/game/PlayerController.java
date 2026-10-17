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
    private float jumpBuffer;
    private float coyote;
    private float recoilPitchAccum;
    private float recoilYawAccum;
    private float recoilHold;
    private float airControl = 2.4f;
    private float groundControl = 18f;

    private final Vector3 wish = new Vector3();
    private final Vector3 eye = new Vector3();
    private final Vector3 dir = new Vector3();
    private final Vector3 rightV = new Vector3();
    private final Vector3 upV = new Vector3();

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
        jumpBuffer = 0f;
        coyote = 0f;
        recoilPitchAccum = 0f;
        recoilYawAccum = 0f;
        recoilHold = 0f;
        e.aiming = false;
        e.crouching = false;
        e.crouchAmount = 0f;
        e.sprinting = false;
        e.firing = false;
        e.landImpact = 0f;
    }

    public void update(PlayerEntity e, InputState in, float dt, Array<Box> solids) {
        if (!e.alive) return;

        // ---------- recoil recovery ----------
        // Recoil moves the real aim point (so sprays kick), then eases back
        // toward the pre-recoil aim like modern shooters. Holding the trigger
        // keeps the kick applied.
        if (recoilHold > 0f) {
            recoilHold -= dt;
        } else {
            float rec = T.damp(9f, dt);
            float dp = recoilPitchAccum * rec;
            float dy = recoilYawAccum * rec;
            e.pitch -= dp;
            e.yaw -= dy;
            recoilPitchAccum -= dp;
            recoilYawAccum -= dy;
        }

        // ---------- look ----------
        float adsMul = e.aiming ? settings.adsSensitivity : 1f;
        float sens = settings.sensitivity * 0.0022f * adsMul;
        e.yaw += in.lookDX * sens;
        e.pitch += (settings.invertY ? 1f : -1f) * in.lookDY * sens;
        e.pitch = T.clamp(e.pitch, -1.48f, 1.48f);

        // ---------- crouch / aim ----------
        if (in.crouchPressed) wantCrouch = !wantCrouch;
        boolean headBlocked = !Collision.canStand(e, NetConfig.STAND_HEIGHT, solids);
        e.crouching = wantCrouch || headBlocked;
        e.crouchAmount += ((e.crouching ? 1f : 0f) - e.crouchAmount) * T.damp(16f, dt);

        boolean wantSprint = (in.sprint || (settings.autoSprint && in.moveY > 0.88f))
                && in.moveY > 0.35f && !e.crouching && !in.fire;
        e.sprinting = wantSprint && !in.ads;
        e.aiming = in.ads && e.reloadTimer <= 0f && !e.sprinting;

        // ---------- movement ----------
        Weapons.Spec spec = e.currentSpec();
        float mx = in.moveX, my = in.moveY;
        float mag = (float) Math.sqrt(mx * mx + my * my);
        if (mag > 1f) {
            mx /= mag;
            my /= mag;
        }

        float maxSpeed = 4.35f * spec.moveSpeedMul;
        if (e.crouching) maxSpeed *= 0.55f;
        if (e.aiming) maxSpeed *= 0.58f;
        if (e.sprinting) maxSpeed = 6.55f * spec.moveSpeedMul;

        // Camera right = forward x up = (-sin, 0, cos). The old code used the
        // opposite sign, which inverted A/D strafing.
        float fx = MathUtils.cos(e.yaw);
        float fz = MathUtils.sin(e.yaw);
        float rx = -MathUtils.sin(e.yaw);
        float rz = MathUtils.cos(e.yaw);
        wish.set(fx * my + rx * mx, 0f, fz * my + rz * mx).nor().scl(maxSpeed);
        if (mag < 0.001f) wish.setZero();

        float rate = e.onGround
                ? (wish.len2() > 0.0001f ? groundControl : groundControl * 0.75f)
                : airControl;
        float f = T.damp(rate, dt);
        e.vel.x += (wish.x - e.vel.x) * f;
        e.vel.z += (wish.z - e.vel.z) * f;
        e.moving = (Math.abs(e.vel.x) + Math.abs(e.vel.z)) > 0.6f && e.onGround;

        // Jump buffering + coyote time: forgiving, but no bunny-hop chain.
        if (in.jumpPressed) jumpBuffer = 0.14f;
        if (jumpBuffer > 0f) jumpBuffer -= dt;
        coyote = e.onGround ? 0.12f : coyote - dt;
        if (jumpBuffer > 0f && coyote > 0f) {
            e.vel.y = 6.5f;
            e.onGround = false;
            coyote = 0f;
            jumpBuffer = 0f;
        }

        e.vel.y -= 20.5f * dt;
        if (e.vel.y < -34f) e.vel.y = -34f;

        boolean wasAir = !e.onGround;
        float fallingSpeed = e.vel.y;
        Collision.moveEntity(e, dt, solids);
        if (wasAir && e.onGround && fallingSpeed < -4f) {
            e.landImpact = T.clamp(-fallingSpeed / 15f, 0.12f, 1f);
            cb.onStep(e);
        }

        if (e.moving) {
            stepTimer -= dt * (e.sprinting ? 1.9f : 1f);
            if (stepTimer <= 0f) {
                stepTimer = e.crouching ? 0.58f : 0.44f;
                cb.onStep(e);
            }
        } else {
            stepTimer = Math.min(stepTimer, 0.15f);
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
                * (e.onGround ? 1f : spec.spreadAirMul)
                * (e.crouching ? 0.8f : 1f);
        e.spread = Math.max(spreadTarget, e.spread - spec.spreadRecover * dt);

        // ---------- reload ----------
        if (in.reloadPressed) startReload(e);
        if (e.reloadTimer > 0f) {
            e.reloadTimer -= dt;
            if (e.reloadTimer <= 0f) {
                if (spec.shotgun) {
                    if (e.ammo() < spec.magSize && e.reserveAmmo() > 0) {
                        e.mag[e.weapon]++;
                        e.reserve[e.weapon]--;
                        assets.play(Assets.S_PUMP, 0.7f);
                        if (e.ammo() < spec.magSize && e.reserveAmmo() > 0) {
                            e.reloadTimer = spec.reloadTime;
                        }
                    }
                } else {
                    int need = spec.magSize - e.ammo();
                    int take = Math.min(need, e.reserveAmmo());
                    e.mag[e.weapon] += take;
                    e.reserve[e.weapon] -= take;
                }
            }
        }

        // ---------- weapon switch ----------
        int select = in.weaponSelect;
        if (select >= 0 && select != e.weapon && e.switchTimer <= 0f) {
            e.weapon = select;
            e.switchTimer = 0.45f;
            e.reloadTimer = 0f;
            assets.play(Assets.S_SWITCH, 0.8f);
        } else if (in.switchPressed && e.switchTimer <= 0f && e.reloadTimer <= 0f) {
            e.weapon = 1 - e.weapon;
            e.switchTimer = 0.45f;
            assets.play(Assets.S_SWITCH, 0.8f);
        }

        // ---------- fire ----------
        e.firing = false;
        boolean firing = in.fire || in.firePressed;
        // Automatic weapons fire while held. Semi-autos need a fresh trigger
        // pull, except when the touch auto-fire lock is engaged.
        boolean trigger = in.firePressed || (in.fire && (spec.auto || in.autoFire));
        if (firing && e.reloadTimer > 0f && spec.shotgun && e.ammo() > 0) {
            e.reloadTimer = 0f; // pump shotgun can interrupt the reload
        }
        if (trigger && e.fireTimer <= 0f && e.reloadTimer <= 0f && e.switchTimer <= 0f) {
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
            e.grenadeTimer = 1.2f;
            eye.set(e.pos.x, e.pos.y + e.eyeY(), e.pos.z);
            dirFromLook(e, dir, 0f);
            assets.play(Assets.S_PIN, 0.8f);
            cb.onGrenade(e, eye, dir);
        }

        e.landImpact *= Math.max(0f, 1f - 7f * dt);
        recoilVis = Math.max(0f, recoilVis - recoilVis * 12f * dt);
    }

    private void shoot(PlayerEntity e) {
        Weapons.Spec spec = e.currentSpec();
        e.mag[e.weapon]--;
        e.fireTimer = 1f / spec.rof;
        e.firing = true;

        // Recoil kick: vertical climbs, horizontal alternates around the
        // centre instead of always drifting the same way.
        float kick = spec.recoilPitch;
        float yawKick = MathUtils.random(-spec.recoilYaw, spec.recoilYaw);
        e.pitch += kick;
        e.yaw += yawKick;
        e.pitch = T.clamp(e.pitch, -1.48f, 1.48f);
        recoilPitchAccum += kick;
        recoilYawAccum += yawKick;
        recoilHold = 0.075f;
        recoilVis = Math.min(recoilVis + kick * 0.8f, 0.14f);

        e.spread = Math.min(spec.spreadMax, e.spread + spec.spreadPerShot);

        eye.set(e.pos.x, e.pos.y + e.eyeY(), e.pos.z);
        dirFromLook(e, dir, e.spread);
        assets.play(spec.soundKind == 2 ? Assets.S_SHOT_SG
                : spec.soundKind == 1 ? Assets.S_SHOT_SMG
                : spec.soundKind == 3 ? Assets.S_SHOT_PISTOL
                : Assets.S_SHOT_AR, 1f);
        cb.onShoot(e, eye, dir, spec);

        if (e.ammo() <= 0) {
            if (e.reserveAmmo() > 0) {
                startReload(e);
            } else if (e.weapon == 0 && e.mag[1] > 0) {
                // Out of primary ammo: fall back to the sidearm instead of
                // standing there clicking an empty gun.
                e.weapon = 1;
                e.switchTimer = 0.4f;
            }
        }
    }

    /** Builds a look direction from yaw/pitch plus random cone spread. */
    private void dirFromLook(PlayerEntity e, Vector3 out, float spread) {
        T.dirFromAngles(e.yaw, e.pitch, out);
        if (spread > 0.0001f) {
            rightV.set(-MathUtils.sin(e.yaw), 0f, MathUtils.cos(e.yaw));
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
            e.reloadTimer = 0f;
        }
    }
}
