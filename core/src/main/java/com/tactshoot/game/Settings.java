package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;

/** Persistent user preferences. */
public final class Settings {

    public static final String NAME = "name";
    public static final String SENS = "sens";
    public static final String QUALITY = "quality";
    public static final String SOUND = "sound";
    public static final String FOV = "fov";
    public static final String SERVER = "server";
    public static final String AUTO_FIRE = "autoFire";
    public static final String LEFT_HANDED = "leftHanded";
    public static final String USERNAME_SET = "usernameSet";

    public String playerName;
    public float sensitivity = 1.0f;      // look multiplier
    public int quality = 1;               // 0=low 1=medium 2=high
    public boolean soundOn = true;
    public int fov = 75;                  // horizontal degrees
    public String server = "";            // empty = default
    public boolean autoFire = true;       // tap-to-fire toggle (mobile)
    public boolean leftHanded = false;
    public boolean usernameSet = false;

    private com.badlogic.gdx.Preferences prefs;

    public Settings() {
        prefs = Gdx.app.getPreferences("tactshoot");
    }

    public void load() {
        playerName = prefs.getString(NAME, "");
        if (playerName.isEmpty()) {
            playerName = "Ranger" + MathUtils.random(100, 999);
        }
        sensitivity = prefs.getFloat(SENS, 1.0f);
        quality = prefs.getInteger(QUALITY, 1);
        soundOn = prefs.getBoolean(SOUND, true);
        fov = prefs.getInteger(FOV, 75);
        server = prefs.getString(SERVER, "");
        autoFire = prefs.getBoolean(AUTO_FIRE, true);
        leftHanded = prefs.getBoolean(LEFT_HANDED, false);
        usernameSet = prefs.getBoolean(USERNAME_SET, false);
        sanitize();
    }

    public void sanitize() {
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Ranger" + MathUtils.random(100, 999);
        }
        playerName = playerName.replaceAll("[^\\p{L}\\p{N} _\\-.]", "").trim();
        if (playerName.length() > 16) playerName = playerName.substring(0, 16);
        sensitivity = MathUtils.clamp(sensitivity, 0.3f, 3.0f);
        quality = MathUtils.clamp(quality, 0, 2);
        fov = MathUtils.clamp(fov, 55, 100);
    }

    public void save() {
        sanitize();
        prefs.putString(NAME, playerName);
        prefs.putFloat(SENS, sensitivity);
        prefs.putInteger(QUALITY, quality);
        prefs.putBoolean(SOUND, soundOn);
        prefs.putInteger(FOV, fov);
        prefs.putString(SERVER, server == null ? "" : server);
        prefs.putBoolean(AUTO_FIRE, autoFire);
        prefs.putBoolean(LEFT_HANDED, leftHanded);
        prefs.putBoolean(USERNAME_SET, usernameSet);
        prefs.flush();
    }

    /** The multiplayer server URL to use. */
    public String serverUrl() {
        if (server != null && !server.trim().isEmpty()) return server.trim();
        return NetConfig.DEFAULT_SERVER;
    }
}
