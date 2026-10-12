package com.tactshoot.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Offline bot brain: nav-mesh roaming, line-of-sight targeting, burst fire. */
public class BotAI {

    public interface ShotCallback {
        void onBotShot(PlayerEntity bot, Vector3 origin, Vector3 dir);
    }

    private final NavGrid nav;
    private final Array<Box> solids;
    private final Assets assets;
    private final boolean tdm;
    private final ShotCallback shotCb;

    private final Vector3 eye = new Vector3();
    private final Vector3 targetEye = new Vector3();
    private final Vector3 aim = new Vector3();
    private final Vector3 dir = new Vector3();
    private final Vector3 wish = new Vector3();
    private final Vector3 rightV = new Vector3();
    private final Vector3 upV = new Vector3();

    public BotAI(NavGrid nav, Array<Box> solids, Assets assets, boolean tdm, ShotCallback shotCb) {
        this.nav = nav;
        this.solids = solids;
        this.assets = assets;
        this.tdm = tdm;
        this.shotCb = shotCb;
    }

    public void update(PlayerEntity b, Array<PlayerEntity> enemies, float dt) {
        if (!b.alive) return;

        b.fireTimer -= dt;
        b.switchTimer -= dt;
        b.botRepathTimer -= dt;
        b.botStrafeTimer -= dt;
        b.botReact -= dt;
        b.botFireDelay -= dt;

        Weapons.Spec spec = b.currentSpec();
        b.eyePos(eye);

        // ---------- perception ----------
        PlayerEntity target = null;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < enemies.size; i++) {
            PlayerEntity t = enemies.get(i);
            if (t == b || !t.alive) continue;
            if (b.friendlyTo(t, tdm)) continue;
            t.eyePos(targetEye);
            float dist = eye.dst(targetEye);
            if (dist > 75f) continue;
            // FOV check
            Vector3 to = T.tmp1.set(targetEye).sub(eye);
            float toYaw = (float) Math.atan2(to.z, to.x);
            float yawDiff = Math.abs(T.wrapAngle(toYaw - b.yaw));
            boolean seen = yawDiff < 1.75f && Collision.lineOfSight(eye, targetEye, solids);
            float score = dist + (seen ? 0f : 400f);
            if (score < bestScore) {
                bestScore = score;
                target = t;
                b.botAim.set(targetEye);
            }
        }

        // spread recovery
        float spreadTarget = spec.spreadBase * (b.moving ? spec.spreadMoveMul : 1f);
        b.spread = Math.max(spreadTarget, b.spread - spec.spreadRecover * dt);

        // ---------- reload management ----------
        if (b.reloadTimer > 0f) {
            b.reloadTimer -= dt;
            if (b.reloadTimer <= 0f) {
                int need = spec.magSize - b.ammo();
                int take = Math.min(need, b.reserveAmmo());
                b.mag[b.weapon] += take;
                b.reserve[b.weapon] -= take;
            }
        } else if (b.ammo() <= 0) {
            if (b.reserveAmmo() > 0) {
                b.reloadTimer = spec.reloadTime;
                assets.playDist(Assets.S_RELOAD, 12f);
            } else if (b.weapon == 0 && b.mag[1] > 0) {
                b.weapon = 1;
                b.switchTimer = 0.4f;
            }
        }

        boolean engaged = target != null && b.botReact <= 0f
                && Collision.lineOfSight(eye, b.botAim, solids)
                && eye.dst(b.botAim) < 65f;

        // ---------- locomotion ----------
        wish.setZero();
        if (engaged) {
            float dist = eye.dst(b.botAim);
            // face the target
            float[] ang = T.anglesTo(eye, b.botAim);
            float turnRate = 6f + b.botLevel * 8f;
            b.yaw = T.lerpAngle(b.yaw, ang[0], T.damp(turnRate, dt));
            b.pitch = T.lerpAngle(b.pitch, ang[1], T.damp(turnRate, dt));
            // strafe
            b.botStrafeTimer -= dt;
            if (b.botStrafeTimer <= 0f) {
                b.botStrafeTimer = MathUtils.random(0.7f, 1.6f);
                b.botStrafeDir = MathUtils.randomBoolean() ? 1f : -1f;
            }
            wish.set(MathUtils.sin(b.yaw), 0f, -MathUtils.cos(b.yaw)).scl(b.botStrafeDir * 2.6f);
            if (dist > 24f) {
                wish.add(MathUtils.cos(b.yaw) * 3.4f, 0f, MathUtils.sin(b.yaw) * 3.4f);
            } else if (dist < 7f && b.botLevel > 0.35f) {
                wish.add(MathUtils.cos(b.yaw) * -2.6f, 0f, MathUtils.sin(b.yaw) * -2.6f);
            }
            // shooting
            float aimErr = (1f - b.botLevel) * 0.16f + 0.02f;
            float yawErr = Math.abs(T.wrapAngle(T.anglesTo(eye, b.botAim)[0] - b.yaw));
            if (b.botFireDelay <= 0f && yawErr < aimErr * 1.5f && dist < spec.range * 1.1f) {
                if (b.botBurst <= 0f) {
                    b.botBurst = MathUtils.random(3, 6) + (int) (b.botLevel * 4f);
                    b.botFireDelay = MathUtils.random(0.25f, 0.7f) * (1.4f - b.botLevel);
                }
                if (b.fireTimer <= 0f && b.botBurst > 0 && b.ammo() > 0) {
                    b.botBurst--;
                    shoot(b, aimErr);
                }
            }
        } else {
            b.pitch = T.lerpAngle(b.pitch, 0f, T.damp(3f, dt));
            // move toward last aim / roam point
            if (b.pathIdx < b.path.size) {
                Vector3 wp = b.path.get(b.pathIdx);
                if (b.pos.dst(wp.x, b.pos.y, wp.z) < 0.8f) {
                    b.pathIdx++;
                } else {
                    Vector3 to = T.tmp1.set(wp).sub(b.pos);
                    wish.set(to.x, 0f, to.z).nor().scl(4.0f);
                    b.yaw = T.lerpAngle(b.yaw, (float) Math.atan2(to.z, to.x), T.damp(8f, dt));
                }
            }
            if (b.pathIdx >= b.path.size && b.botRepathTimer <= 0f) {
                b.botRepathTimer = MathUtils.random(3f, 7f);
                Vector3 dest = nav.randomOpen(b.pos.x, b.pos.z);
                if (dest != null) {
                    Array<Vector3> p = nav.findPath(b.pos.x, b.pos.z, dest.x, dest.z);
                    if (p != null && p.size > 0) {
                        b.path.clear();
                        b.path.addAll(p);
                        b.pathIdx = 0;
                    }
                }
            }
        }

        // apply movement
        b.vel.x += (wish.x - b.vel.x) * T.damp(10f, dt);
        b.vel.z += (wish.z - b.vel.z) * T.damp(10f, dt);
        b.vel.y -= 22f * dt;
        if (b.vel.y < -32f) b.vel.y = -32f;
        b.moving = (Math.abs(b.vel.x) + Math.abs(b.vel.z)) > 0.6f && b.onGround;
        Collision.moveEntity(b, dt, solids);
    }

    private void shoot(PlayerEntity b, float aimErr) {
        Weapons.Spec spec = b.currentSpec();
        b.mag[b.weapon]--;
        b.fireTimer = 1f / spec.rof;
        b.firing = true;
        eye.set(b.pos.x, b.pos.y + b.eyeY(), b.pos.z);
        dir.set(b.botAim).sub(eye).nor();
        if (aimErr > 0f) {
            rightV.set(MathUtils.sin(b.yaw), 0f, -MathUtils.cos(b.yaw));
            upV.set(rightV).crs(dir).nor();
            float a = MathUtils.random(0f, MathUtils.PI2);
            float r = aimErr * (float) Math.sqrt(MathUtils.random());
            dir.mulAdd(rightV, MathUtils.cos(a) * r)
                    .mulAdd(upV, MathUtils.sin(a) * r)
                    .nor();
        }
        b.spread = Math.min(spec.spreadMax, b.spread + spec.spreadPerShot);
        b.pitch += spec.recoilPitch * 0.6f;
        // fire the actual hitscan through the game
        eye.set(b.pos.x, b.pos.y + b.eyeY(), b.pos.z);
        shotCb.onBotShot(b, eye, dir);
    }

    public Vector3 shotOrigin(PlayerEntity b) {
        return eye.set(b.pos.x, b.pos.y + b.eyeY(), b.pos.z);
    }

    public Vector3 shotDir(PlayerEntity b) {
        dir.set(b.botAim).sub(shotOrigin(b)).nor();
        return dir;
    }
}
