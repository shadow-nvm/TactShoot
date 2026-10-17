package com.tactshoot.game;

/** Weapon definitions and loadout helpers. */
public final class Weapons {

    public static final int AR = 0;
    public static final int SMG = 1;
    public static final int SHOTGUN = 2;
    public static final int PISTOL = 3;

    public static class Spec {
        public String name;
        public int magSize;
        public int reserveTotal;
        public float damage;
        public float headMult;
        public float range;          // meters, full damage distance
        public float falloffMin;     // damage multiplier at 2x range
        public float rof;            // rounds per second
        public boolean auto;
        public boolean shotgun;
        public int pellets;
        public float spreadBase;     // radians (hip, standing)
        public float spreadMax;
        public float spreadPerShot;
        public float spreadRecover;  // per second
        public float spreadAdsMul;
        public float spreadMoveMul;
        public float spreadAirMul;
        public float recoilPitch;    // radians per shot
        public float recoilYaw;
        public float reloadTime;
        public float adsFov;         // horizontal FOV when aiming
        public float moveSpeedMul;
        public float kick;           // viewmodel kick distance
        public int soundKind;        // SoundSynth gunshot kind

        Spec(String name, int magSize, int reserveTotal, float damage, float headMult, float range,
             float falloffMin, float rof, boolean auto, boolean shotgun, int pellets, float spreadBase,
             float spreadMax, float spreadPerShot, float spreadRecover, float spreadAdsMul,
             float spreadMoveMul, float spreadAirMul, float recoilPitch, float recoilYaw,
             float reloadTime, float adsFov, float moveSpeedMul, float kick, int soundKind) {
            this.name = name;
            this.magSize = magSize;
            this.reserveTotal = reserveTotal;
            this.damage = damage;
            this.headMult = headMult;
            this.range = range;
            this.falloffMin = falloffMin;
            this.rof = rof;
            this.auto = auto;
            this.shotgun = shotgun;
            this.pellets = pellets;
            this.spreadBase = spreadBase;
            this.spreadMax = spreadMax;
            this.spreadPerShot = spreadPerShot;
            this.spreadRecover = spreadRecover;
            this.spreadAdsMul = spreadAdsMul;
            this.spreadMoveMul = spreadMoveMul;
            this.spreadAirMul = spreadAirMul;
            this.recoilPitch = recoilPitch;
            this.recoilYaw = recoilYaw;
            this.reloadTime = reloadTime;
            this.adsFov = adsFov;
            this.moveSpeedMul = moveSpeedMul;
            this.kick = kick;
            this.soundKind = soundKind;
        }
    }

    public static final Spec[] SPECS = {
            new Spec("ASSAULT RIFLE", 30, 120, 25f, 2.0f, 55f, 0.35f, 10.5f, true, false, 1,
                    0.011f, 0.085f, 0.0060f, 3.2f, 0.32f, 1.7f, 2.6f,
                    0.017f, 0.006f, 2.1f, 55f, 0.92f, 0.11f, 0),
            new Spec("SMG", 40, 160, 19f, 1.9f, 32f, 0.40f, 15.0f, true, false, 1,
                    0.016f, 0.075f, 0.0045f, 3.6f, 0.35f, 1.5f, 2.4f,
                    0.012f, 0.008f, 1.9f, 58f, 0.98f, 0.08f, 1),
            new Spec("SHOTGUN", 8, 32, 9.5f, 1.6f, 18f, 0.20f, 1.15f, false, true, 9,
                    0.050f, 0.16f, 0.045f, 5.0f, 0.5f, 1.25f, 1.9f,
                    0.045f, 0.012f, 0.55f, 60f, 0.90f, 0.16f, 2),
            new Spec("PISTOL", 12, 60, 34f, 2.0f, 35f, 0.40f, 5.5f, false, false, 1,
                    0.010f, 0.075f, 0.011f, 3.4f, 0.30f, 1.6f, 2.4f,
                    0.021f, 0.007f, 1.6f, 62f, 1.00f, 0.09f, 3),
    };

    // grenade
    public static final float GRENADE_DAMAGE = 100f;
    public static final float GRENADE_RADIUS = 6.5f;
    public static final float GRENADE_MIN_DAMAGE = 25f;
    public static final float GRENADE_FUSE = 1.5f;
    public static final float GRENADE_THROW_SPEED = 16f;
    public static final float GRENADE_UP_ANGLE = 0.16f; // slight lob

    public static Spec spec(int idx) {
        return SPECS[Math.max(0, Math.min(idx, SPECS.length - 1))];
    }

    private Weapons() {
    }
}
