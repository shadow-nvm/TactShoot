package com.tactshoot.game;

/** Network configuration and protocol constants shared with the server. */
public final class NetConfig {

    /** Protocol version; server rejects mismatches with a clear error. */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * Default game server. Replace with your own deployed server URL
     * (see server/README.md and the deploy guide). The in-game
     * Settings -> Server field overrides this per device.
     */
    public static final String DEFAULT_SERVER = "wss://tactshoot.onrender.com";

    /** Fallback used by the queue screen when nothing is configured. */
    public static final String FALLBACK_DEV_SERVER = "ws://10.0.2.2:8080"; // Android emulator -> host PC

    public static final int TICK_RATE = 15;          // server state broadcast Hz
    public static final int INPUT_RATE = 15;         // client input send Hz
    public static final float INTERP_DELAY = 0.12f;  // remote entity render delay (s)

    public static final int MAX_PLAYERS = 12;
    public static final int FFA_PLAYERS = 8;

    public static final float RESPAWN_TIME = 3.0f;
    public static final float MATCH_TIME = 480.0f;
    public static final int TDM_KILL_LIMIT = 40;
    public static final int FFA_KILL_LIMIT = 25;

    public static final float PLAYER_RADIUS = 0.32f;
    public static final float STAND_HEIGHT = 1.78f;
    public static final float CROUCH_HEIGHT = 1.15f;

    private NetConfig() {
    }
}
