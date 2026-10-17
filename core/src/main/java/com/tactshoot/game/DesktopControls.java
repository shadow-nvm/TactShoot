package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Keyboard + mouse controls for the desktop build. */
public class DesktopControls extends InputAdapter {

    public final InputState state = new InputState();

    /** Mirror of the user preference: tap-to-toggle ADS instead of hold. */
    public boolean aimToggle;
    private boolean adsToggle;
    private boolean crouchToggle;
    private boolean scoreToggle;
    private boolean lookEnabled = true;
    private boolean enabled = true;
    private float sens = 1f;

    public void setSensitivity(float s) {
        sens = s;
    }

    public void setLookEnabled(boolean enabled) {
        lookEnabled = enabled;
        if (!enabled) {
            state.lookDX = 0f;
            state.lookDY = 0f;
        }
    }

    /** Disabled while typing in the chat field so keys reach the text field. */
    public void setEnabled(boolean e) {
        enabled = e;
    }

    public void poll() {
        if (!enabled) {
            state.moveX = 0f;
            state.moveY = 0f;
            state.fire = false;
            state.ads = false;
            state.sprint = false;
            return;
        }
        state.moveX = 0f;
        state.moveY = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) state.moveY += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) state.moveY -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) state.moveX += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) state.moveX -= 1f;
        float len = (float) Math.sqrt(state.moveX * state.moveX + state.moveY * state.moveY);
        if (len > 1f) {
            state.moveX /= len;
            state.moveY /= len;
        }
        state.sprint = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) && state.moveY > 0f;
        state.fire = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        state.ads = aimToggle ? adsToggle : Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        state.scoreboard = scoreToggle;

        // Mouse look only while the cursor is captured (in-match, not in menus).
        if (lookEnabled && Gdx.input.isCursorCatched()) {
            state.lookDX += Gdx.input.getDeltaX() * sens;
            state.lookDY += Gdx.input.getDeltaY() * sens;
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (!enabled) return false;
        switch (keycode) {
            case Input.Keys.SPACE:
                state.jumpPressed = true;
                return true;
            case Input.Keys.R:
                state.reloadPressed = true;
                return true;
            case Input.Keys.Q:
                state.switchPressed = true;
                return true;
            case Input.Keys.NUM_1:
                state.weaponSelect = 0;
                return true;
            case Input.Keys.NUM_2:
                state.weaponSelect = 1;
                return true;
            case Input.Keys.G:
                state.grenadePressed = true;
                return true;
            case Input.Keys.C:
            case Input.Keys.CONTROL_LEFT:
                crouchToggle = !crouchToggle;
                state.crouchPressed = true;
                return true;
            case Input.Keys.T:
                state.chatPressed = true;
                return true;
            case Input.Keys.ESCAPE:
                state.pausePressed = true;
                return true;
            case Input.Keys.TAB:
                scoreToggle = true;
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        if (keycode == Input.Keys.TAB) {
            scoreToggle = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        // Only swallow clicks while the mouse is captured. Menus and the pause
        // screen need these events to reach the Scene2D stage.
        if (!Gdx.input.isCursorCatched()) return false;
        if (button == Input.Buttons.LEFT) {
            state.firePressed = true;
            state.fire = true;
            return true;
        }
        if (button == Input.Buttons.RIGHT && aimToggle) {
            adsToggle = !adsToggle;
            state.ads = adsToggle;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.LEFT) {
            state.fire = false;
        }
        return false;
    }

    public void releaseFireLock() {
    }
}
