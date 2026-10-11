package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;

/** Theme palette for the whole game. */
public final class Palette {

    public static final Color BG = new Color(0.043f, 0.051f, 0.075f, 1f);
    public static final Color BG2 = new Color(0.078f, 0.094f, 0.129f, 1f);
    public static final Color PANEL = new Color(0.055f, 0.066f, 0.094f, 0.86f);
    public static final Color ACCENT = new Color(1.0f, 0.549f, 0.102f, 1f);   // amber
    public static final Color ACCENT_DARK = new Color(0.72f, 0.38f, 0.05f, 1f);
    public static final Color TEAL = new Color(0.16f, 0.72f, 0.62f, 1f);
    public static final Color RED = new Color(0.92f, 0.22f, 0.19f, 1f);
    public static final Color RED_DARK = new Color(0.55f, 0.10f, 0.09f, 1f);
    public static final Color GREEN = new Color(0.35f, 0.78f, 0.38f, 1f);
    public static final Color TEXT = new Color(0.93f, 0.95f, 0.97f, 1f);
    public static final Color TEXT_DIM = new Color(0.62f, 0.68f, 0.75f, 1f);
    public static final Color WHITE = new Color(1f, 1f, 1f, 1f);
    public static final Color BLACK = new Color(0f, 0f, 0f, 1f);

    // team colors
    public static final Color TEAM_A = new Color(0.25f, 0.55f, 0.95f, 1f);    // blue
    public static final Color TEAM_B = new Color(0.93f, 0.30f, 0.22f, 1f);    // red
    public static final Color FFA_NEUTRAL = new Color(0.85f, 0.85f, 0.85f, 1f);

    public static Color teamColor(int team) {
        if (team == 0) return TEAM_A;
        if (team == 1) return TEAM_B;
        return FFA_NEUTRAL;
    }

    private Palette() {
    }
}
