package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/** Keyboard + mouse controls for the desktop build. */
public class DesktopControls extends InputAdapter {

    public final InputState state = new InputState();

    private boolean adsToggle;
    private boolean crouchToggle;
    private boolean scoreToggle;
    private float sens = 1f;

    public void setSensitivity(float s) {
        sens = s;
    }

    public void poll() {
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
        state.ads = adsToggle;
        state.lookDX += Gdx.input.getDeltaX() * sens;
        state.lookDY += Gdx.input.getDeltaY() * sens;
        state.scoreboard = scoreToggle;
        state.crouchPressed = false;
    }

    @Override
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.SPACE:
                state.jumpPressed = true;
                return true;
            case Input.Keys.R:
                state.reloadPressed = true;
                return true;
            case Input.Keys.Q:
            case Input.Keys.NUM_1:
            case Input.Keys.NUM_2:
                state.switchPressed = true;
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
        if (button == Input.Buttons.RIGHT) {
            adsToggle = !adsToggle;
            state.ads = adsToggle;
            return true;
        }
        return false;
    }

    public void releaseFireLock() {
    }
}
