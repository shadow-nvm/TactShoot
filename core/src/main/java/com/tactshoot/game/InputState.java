package com.tactshoot.game;

/** Per-frame input snapshot, filled by TouchControls or DesktopControls. */
public class InputState {

    // continuous
    public float moveX, moveY;      // -1..1, moveY = forward
    public float lookDX, lookDY;    // pointer delta (pixels, screen space)
    public boolean fire;            // hold fire (automatic weapons)
    public boolean autoFire;        // touch auto-fire lock is active
    public boolean ads;             // aim down sights (held / toggled)
    public boolean sprint;

    // edge-triggered (consumed each frame)
    public boolean firePressed;     // trigger pulled this frame (semi-auto)
    public boolean jumpPressed;
    public boolean crouchPressed;
    public boolean reloadPressed;
    public boolean switchPressed;
    public boolean grenadePressed;
    public boolean pausePressed;
    public boolean chatPressed;
    public boolean scoreboard;
    public int weaponSelect = -1;   // 0 = primary, 1 = sidearm, -1 = none

    public void resetFrame() {
        lookDX = 0f;
        lookDY = 0f;
        firePressed = false;
        jumpPressed = false;
        crouchPressed = false;
        reloadPressed = false;
        switchPressed = false;
        grenadePressed = false;
        pausePressed = false;
        chatPressed = false;
        scoreboard = false;
        weaponSelect = -1;
    }

    public void clearAll() {
        resetFrame();
        moveX = 0f;
        moveY = 0f;
        fire = false;
        autoFire = false;
        ads = false;
        sprint = false;
    }
}
