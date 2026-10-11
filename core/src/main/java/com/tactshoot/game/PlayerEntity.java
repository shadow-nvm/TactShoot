package com.tactshoot.game;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/**
 * One combatant: local player, remote network player or bot.
 * Positions are feet-center. Team: 0/1 for TDM; for FFA 'team' is the
 * player's slot and friendliness is decided by id.
 */
public class PlayerEntity {

    public int id;
    public String name;
    public int team;
    public boolean isLocal;
    public boolean isRemote;
    public boolean isBot;

    public final Vector3 pos = new Vector3();   // feet center
    public final Vector3 vel = new Vector3();
    public float yaw, pitch;                     // radians

    public float hp = 100f;
    public boolean alive = true;
    public float respawnTimer;
    public int kills, deaths, score;

    public int weapon = 0;                       // 0 primary, 1 pistol
    public int primaryChoice = Weapons.AR;       // Weapons.* index
    public final int[] mag = new int[2];
    public final int[] reserve = new int[2];
    public int grenades = 2;

    public float reloadTimer;                    // >0 = reloading
    public float fireTimer;                      // cooldown until next shot
    public float switchTimer;                    // >0 = switching weapon
    public float grenadeTimer;                   // throw cooldown
    public float spread;                         // current spread (radians)

    public boolean aiming;
    public boolean crouching;
    public boolean moving;
    public boolean firing;
    public boolean sprinting;
    public boolean onGround;

    public float bobPhase;
    public float lastDamageTime = -99f;
    public float afkTime;

    // ------- bot brain state -------
    public float botLevel = 0.5f;
    public final Array<Vector3> path = new Array<>();
    public int pathIdx;
    public final Vector3 botAim = new Vector3();
    public float botReact;                       // seconds until bot "notices"
    public float botStrafeDir = 1f;
    public float botStrafeTimer;
    public float botBurst;
    public float botRepathTimer;
    public float botFireDelay;

    // ------- remote interpolation -------
    public final Vector3 netPrev = new Vector3();
    public final Vector3 netNext = new Vector3();
    public float netPrevYaw, netNextYaw;
    public float netPrevPitch, netNextPitch;
    public float netPrevTime, netNextTime;       // local clock
    public int netSeq;

    public PlayerEntity() {
    }

    public void resetLoadout() {
        Weapons.Spec primary = Weapons.spec(primaryChoice);
        Weapons.Spec pistol = Weapons.spec(Weapons.PISTOL);
        mag[0] = primary.magSize;
        reserve[0] = primary.reserveTotal;
        mag[1] = pistol.magSize;
        reserve[1] = pistol.reserveTotal;
        grenades = 2;
        reloadTimer = 0f;
        fireTimer = 0f;
        switchTimer = 0f;
        spread = 0f;
        weapon = 0;
        aiming = false;
        crouching = false;
        firing = false;
        sprinting = false;
        vel.setZero();
        hp = 100f;
        alive = true;
        respawnTimer = 0f;
    }

    public Weapons.Spec currentSpec() {
        return Weapons.spec(weapon == 0 ? primaryChoice : Weapons.PISTOL);
    }

    public int ammo() {
        return mag[weapon];
    }

    public int reserveAmmo() {
        return reserve[weapon];
    }

    public float height() {
        return crouching ? NetConfig.CROUCH_HEIGHT : NetConfig.STAND_HEIGHT;
    }

    public float eyeY() {
        return crouching ? 1.02f : 1.62f;
    }

    public Vector3 eyePos(Vector3 out) {
        return out.set(pos.x, pos.y + eyeY(), pos.z);
    }

    public boolean friendlyTo(PlayerEntity other, boolean tdm) {
        if (tdm) return team == other.team;
        return id == other.id;
    }

    public void takeDamage(float dmg, float now) {
        hp -= dmg;
        lastDamageTime = now;
    }

    /** Push a remote snapshot into the interpolation buffers. */
    public void pushNetState(Vector3 p, float y, float pi, float now, int seq) {
        if (netNextTime > 0) {
            netPrev.set(netNext);
            netPrevYaw = netNextYaw;
            netPrevPitch = netNextPitch;
            netPrevTime = netNextTime;
        } else {
            netPrev.set(p);
            netPrevYaw = y;
            netPrevPitch = pi;
            netPrevTime = now;
        }
        netNext.set(p);
        netNextYaw = y;
        netNextPitch = pi;
        netNextTime = now;
        netSeq = seq;
    }

    public void applyInterpolation(float renderTime) {
        if (netNextTime <= 0) return;
        float span = netNextTime - netPrevTime;
        float t = span <= 0.0001f ? 1f : T.clamp((renderTime - netPrevTime) / span, 0f, 1f);
        pos.set(netPrev).lerp(netNext, t);
        yaw = T.lerpAngle(netPrevYaw, netNextYaw, t);
        pitch = T.lerpAngle(netPrevPitch, netNextPitch, t);
    }

    public void fullReset() {
        resetLoadout();
        kills = 0;
        deaths = 0;
        score = 0;
        path.clear();
        pathIdx = 0;
        netPrevTime = 0;
        netNextTime = 0;
        lastDamageTime = -99f;
        bobPhase = 0f;
    }
}
