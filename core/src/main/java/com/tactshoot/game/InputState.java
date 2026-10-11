package com.tactshoot.game;

/** Per-frame input snapshot, filled by TouchControls or DesktopControls. */
public class InputState {

    // continuous
    public float moveX, moveY;      // -1..1, moveY = forward
    public float lookDX, lookDY;    // pointer delta (pixels)
    public boolean fire;            // hold fire
    public boolean ads;             // aim down sights
    public boolean sprint;

    // edge-triggered (consumed each frame)
    public boolean jumpPressed;
    public boolean crouchPressed;
    public boolean reloadPressed;
    public boolean switchPressed;
    public boolean grenadePressed;
    public boolean pausePressed;
    public boolean chatPressed;
    public boolean scoreboard;

    public void resetFrame() {
        lookDX = 0f;
        lookDY = 0f;
        jumpPressed = false;
        crouchPressed = false;
        reloadPressed = false;
        switchPressed = false;
        grenadePressed = false;
        pausePressed = false;
        chatPressed = false;
        scoreboard = false;
    }

    public void clearAll() {
        resetFrame();
        moveX = 0f;
        moveY = 0f;
        fire = false;
        sprint = false;
    }
}
