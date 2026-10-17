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
    public static final String INVERT_Y = "invertY";
    public static final String ADS_SENS = "adsSens";
    public static final String AIM_TOGGLE = "aimToggle";
    public static final String AUTO_SPRINT = "autoSprint";
    public static final String AUTO_QUALITY = "autoQuality";
    public static final String SHOW_FPS = "showFps";

    public String playerName;
    public float sensitivity = 1.0f;      // hip-fire look multiplier
    public float adsSensitivity = 0.8f;   // look multiplier while aiming
    public int quality = 2;               // 0=low 1=medium 2=high
    public boolean soundOn = true;
    public int fov = 85;                  // horizontal degrees
    public String server = "";            // empty = default
    public boolean autoFire = true;       // tap-to-fire toggle (mobile)
    public boolean leftHanded = false;
    public boolean usernameSet = false;
    public boolean invertY = false;       // invert vertical look
    public boolean aimToggle = false;     // tap ADS to toggle instead of hold
    public boolean autoSprint = true;     // full joystick deflection sprints
    public boolean autoQuality = true;    // drop quality if the device struggles
    public boolean showFps = false;

    private com.badlogic.gdx.Preferences prefs;

    public Settings() {
        try {
            prefs = Gdx.app != null ? Gdx.app.getPreferences("tactshoot") : null;
        } catch (Throwable t) {
            if (Gdx.app != null) Gdx.app.error("TactShoot", "Preferences init failed: " + t.getMessage());
            prefs = null;
        }
    }

    public void load() {
        if (prefs != null) {
            try {
                playerName = prefs.getString(NAME, "");
                sensitivity = prefs.getFloat(SENS, 1.0f);
                quality = prefs.getInteger(QUALITY, 2);
                soundOn = prefs.getBoolean(SOUND, true);
                fov = prefs.getInteger(FOV, 85);
                server = prefs.getString(SERVER, "");
                autoFire = prefs.getBoolean(AUTO_FIRE, true);
                leftHanded = prefs.getBoolean(LEFT_HANDED, false);
                usernameSet = prefs.getBoolean(USERNAME_SET, false);
                invertY = prefs.getBoolean(INVERT_Y, false);
                adsSensitivity = prefs.getFloat(ADS_SENS, 0.8f);
                aimToggle = prefs.getBoolean(AIM_TOGGLE, false);
                autoSprint = prefs.getBoolean(AUTO_SPRINT, true);
                autoQuality = prefs.getBoolean(AUTO_QUALITY, true);
                showFps = prefs.getBoolean(SHOW_FPS, false);
            } catch (Throwable t) {
                if (Gdx.app != null) Gdx.app.error("TactShoot", "Preferences load error: " + t.getMessage());
            }
        }
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Ranger" + MathUtils.random(100, 999);
        }
        sanitize();
    }

    public void sanitize() {
        if (playerName == null || playerName.trim().isEmpty()) {
            playerName = "Ranger" + MathUtils.random(100, 999);
        }
        playerName = playerName.replaceAll("[^\\p{L}\\p{N} _\\-.]", "").trim();
        if (playerName.isEmpty()) playerName = "Ranger" + MathUtils.random(100, 999);
        if (playerName.length() > 16) playerName = playerName.substring(0, 16);
        sensitivity = MathUtils.clamp(sensitivity, 0.3f, 3.0f);
        adsSensitivity = MathUtils.clamp(adsSensitivity, 0.3f, 1.5f);
        quality = MathUtils.clamp(quality, 0, 2);
        fov = MathUtils.clamp(fov, 65, 105);
    }

    public void save() {
        sanitize();
        if (prefs == null) return;
        prefs.putString(NAME, playerName);
        prefs.putFloat(SENS, sensitivity);
        prefs.putInteger(QUALITY, quality);
        prefs.putBoolean(SOUND, soundOn);
        prefs.putInteger(FOV, fov);
        prefs.putString(SERVER, server == null ? "" : server);
        prefs.putBoolean(AUTO_FIRE, autoFire);
        prefs.putBoolean(LEFT_HANDED, leftHanded);
        prefs.putBoolean(USERNAME_SET, usernameSet);
        prefs.putBoolean(INVERT_Y, invertY);
        prefs.putFloat(ADS_SENS, adsSensitivity);
        prefs.putBoolean(AIM_TOGGLE, aimToggle);
        prefs.putBoolean(AUTO_SPRINT, autoSprint);
        prefs.putBoolean(AUTO_QUALITY, autoQuality);
        prefs.putBoolean(SHOW_FPS, showFps);
        prefs.flush();
    }

    /** The multiplayer server URL to use. */
    public String serverUrl() {
        if (server != null && !server.trim().isEmpty()) return server.trim();
        return NetConfig.DEFAULT_SERVER;
    }

    public String qualityName() {
        return quality == 0 ? "LOW" : quality == 1 ? "MEDIUM" : "HIGH";
    }
}
