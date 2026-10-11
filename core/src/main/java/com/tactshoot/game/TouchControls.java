package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** Dual-stick touch controls: left joystick, right look, action buttons. */
public class TouchControls implements InputProcessor {

    public final InputState state = new InputState();
    private boolean enabled = true;

    private int w, h;
    private float sens = 1f;

    private final Vector2 stickOrigin = new Vector2();
    private final Vector2 stickCur = new Vector2();
    private int movePointer = -1;
    private int lookPointer = -1;
    private int firePointer = -1;
    private boolean fireHeld;
    private boolean fireLock;
    private float lastTapTime = -10f;
    private float fireTapX = -1f, fireTapY = -1f;
    private boolean adsToggle = false;
    private boolean crouchToggle = false;
    private boolean scoreToggle = false;

    // button layout (fractions of screen)
    private static final float FIRE_X = 0.875f, FIRE_Y = 0.26f, FIRE_R = 0.145f;
    private static final float ADS_X = 0.725f, ADS_Y = 0.45f, ADS_R = 0.082f;
    private static final float RELOAD_X = 0.73f, RELOAD_Y = 0.16f, RELOAD_R = 0.07f;
    private static final float SWITCH_X = 0.875f, SWITCH_Y = 0.62f, SWITCH_R = 0.075f;
    private static final float GRENADE_X = 0.70f, GRENADE_Y = 0.68f, GRENADE_R = 0.06f;
    private static final float JUMP_X = 0.105f, JUMP_Y = 0.40f, JUMP_R = 0.078f;
    private static final float CROUCH_X = 0.105f, CROUCH_Y = 0.18f, CROUCH_R = 0.064f;
    private static final float PAUSE_X = 0.96f, PAUSE_Y = 0.94f, PAUSE_R = 0.05f;
    private static final float CHAT_X = 0.045f, CHAT_Y = 0.86f, CHAT_R = 0.05f;

    private float fx() { return FIRE_X * w; }
    private float fy() { return FIRE_Y * h; }
    private float fr() { return FIRE_R * Math.min(w, h); }

    private float ax() { return ADS_X * w; }
    private float ay() { return ADS_Y * h; }
    private float ar() { return ADS_R * Math.min(w, h); }

    private float rx() { return RELOAD_X * w; }
    private float ry() { return RELOAD_Y * h; }
    private float rr() { return RELOAD_R * Math.min(w, h); }

    private float sx() { return SWITCH_X * w; }
    private float sy() { return SWITCH_Y * h; }
    private float sr() { return SWITCH_R * Math.min(w, h); }

    private float gx() { return GRENADE_X * w; }
    private float gy() { return GRENADE_Y * h; }
    private float gr() { return GRENADE_R * Math.min(w, h); }

    private float jx() { return JUMP_X * w; }
    private float jy() { return JUMP_Y * h; }
    private float jr() { return JUMP_R * Math.min(w, h); }

    private float cx() { return CROUCH_X * w; }
    private float cy() { return CROUCH_Y * h; }
    private float cr() { return CROUCH_R * Math.min(w, h); }

    private float px() { return PAUSE_X * w; }
    private float py() { return PAUSE_Y * h; }
    private float pr() { return PAUSE_R * Math.min(w, h); }

    private float hx() { return CHAT_X * w; }
    private float hy() { return CHAT_Y * h; }
    private float hr() { return CHAT_R * Math.min(w, h); }

    public void resize(int width, int height) {
        w = width;
        h = height;
    }

    public void setEnabled(boolean e) {
        enabled = e;
        if (!e) {
            state.clearAll();
            movePointer = -1;
            lookPointer = -1;
            fireHeld = false;
            fireLock = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSensitivity(float s) {
        sens = s;
    }

    public void releaseFireLock() {
        fireLock = false;
    }

    public boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!enabled) return false;
        float x = screenX, y = h - screenY;
        // buttons first (small, precise)
        if (inCircle(x, y, px(), py(), pr())) {
            state.pausePressed = true;
            return true;
        }
        if (inCircle(x, y, hx(), hy(), hr())) {
            state.chatPressed = true;
            return true;
        }
        if (inCircle(x, y, rx(), ry(), rr())) {
            state.reloadPressed = true;
            return true;
        }
        if (inCircle(x, y, sx(), sy(), sr())) {
            state.switchPressed = true;
            return true;
        }
        if (inCircle(x, y, gx(), gy(), gr())) {
            state.grenadePressed = true;
            return true;
        }
        if (inCircle(x, y, ax(), ay(), ar())) {
            adsToggle = !adsToggle;
            state.ads = adsToggle;
            return true;
        }
        if (inCircle(x, y, jx(), jy(), jr())) {
            state.jumpPressed = true;
            return true;
        }
        if (inCircle(x, y, cx(), cy(), cr())) {
            crouchToggle = !crouchToggle;
            state.crouchPressed = true; // toggle handled by controller
            return true;
        }
        if (inCircle(x, y, fx(), fy(), fr())) {
            fireHeld = true;
            firePointer = pointer;
            // double-tap detection for auto-fire lock
            long now = System.currentTimeMillis();
            if (now - lastTapTime < 320f && Math.abs(x - fireTapX) < fr()
                    && Math.abs(y - fireTapY) < fr()) {
                fireLock = !fireLock;
                lastTapTime = -10f;
            } else {
                lastTapTime = now;
                fireTapX = x;
                fireTapY = y;
            }
            return true;
        }
        // left half: movement joystick
        if (x < w * 0.45f && movePointer == -1) {
            movePointer = pointer;
            stickOrigin.set(x, y);
            stickCur.set(x, y);
            return true;
        }
        // right: look
        if (lookPointer == -1) {
            lookPointer = pointer;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!enabled) return false;
        float x = screenX, y = h - screenY;
        if (pointer == movePointer) {
            stickCur.set(x, y);
            return true;
        }
        return pointer == lookPointer;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pointer == movePointer) {
            movePointer = -1;
            state.moveX = 0f;
            state.moveY = 0f;
            return true;
        }
        if (pointer == lookPointer) {
            lookPointer = -1;
            return true;
        }
        if (pointer == firePointer) {
            firePointer = -1;
            fireHeld = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }

    public void pollLook() {
        // look deltas polled from global input on Android too (works with raw touch deltas)
        state.lookDX += Gdx.input.getDeltaX() * sens;
        state.lookDY += Gdx.input.getDeltaY() * sens;
    }

    public void updateState() {
        // joystick
        if (movePointer != -1) {
            float dx = stickCur.x - stickOrigin.x;
            float dy = stickCur.y - stickOrigin.y;
            float maxR = Math.min(w, h) * 0.16f;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > maxR) {
                dx = dx / len * maxR;
                dy = dy / len * maxR;
                len = maxR;
            }
            float dead = maxR * 0.14f;
            if (len < dead) {
                state.moveX = 0f;
                state.moveY = 0f;
                state.sprint = false;
            } else {
                float norm = (len - dead) / (maxR - dead);
                state.moveX = dx / maxR;
                state.moveY = dy / maxR;
                state.sprint = norm > 0.92f;
            }
        } else {
            state.moveX = 0f;
            state.moveY = 0f;
            state.sprint = false;
        }
        state.fire = fireHeld || fireLock;
        state.ads = adsToggle;
        state.scoreboard = scoreToggle;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.BACK) {
            state.pausePressed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    // ---------------------------------------------------------------- draw

    public void render(ShapeRenderer sr, SpriteBatch batch, BitmapFont f, Assets assets) {
        if (!enabled) return;
        Gdx.gl20.glEnable(com.badlogic.gdx.Gdx.gl20.GL_BLEND);
        Gdx.gl20.glBlendFunc(com.badlogic.gdx.Gdx.gl20.GL_SRC_ALPHA,
                com.badlogic.gdx.Gdx.gl20.GL_ONE_MINUS_SRC_ALPHA);
        Color fill = new Color(1f, 1f, 1f, 0.09f);
        Color border = new Color(1f, 1f, 1f, 0.30f);
        Color active = Palette.ACCENT;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        // move joystick
        if (movePointer != -1) {
            sr.setColor(fill);
            sr.circle(stickOrigin.x, stickOrigin.y, Math.min(w, h) * 0.16f);
            sr.setColor(active.r, active.g, active.b, 0.35f);
            sr.circle(stickCur.x, stickCur.y, Math.min(w, h) * 0.055f);
        }
        // buttons
        drawButton(sr, fx(), fy(), fr(), fireHeld || fireLock, fill, border, active);
        drawButton(sr, ax(), ay(), ar(), adsToggle, fill, border, active);
        drawButton(sr, rx(), ry(), rr(), false, fill, border, active);
        drawButton(sr, sx(), sy(), sr(), false, fill, border, active);
        drawButton(sr, gx(), gy(), gr(), false, fill, border, active);
        drawButton(sr, jx(), jy(), jr(), false, fill, border, active);
        drawButton(sr, cx(), cy(), cr(), crouchToggle, fill, border, active);
        drawButton(sr, px(), py(), pr(), false, fill, border, active);
        drawButton(sr, hx(), hy(), hr(), false, fill, border, active);
        sr.end();

        batch.begin();
        f.setColor(Color.WHITE);
        drawIcon(batch, f, assets, "R", rx(), ry(), rr());
        drawIcon(batch, f, assets, "SW", sx(), sy(), sr());
        drawIcon(batch, f, assets, "G", gx(), gy(), gr());
        drawIcon(batch, f, assets, "\u2191", jx(), jy(), jr());
        drawIcon(batch, f, assets, "\u2193", cx(), cy(), cr());
        drawIcon(batch, f, assets, "II", px(), py(), pr());
        drawIcon(batch, f, assets, "CH", hx(), hy(), hr());
        // fire + ads glyphs
        batch.draw(assets.crosshairTex, fx() - fr() * 0.55f, fy() - fr() * 0.55f, fr() * 1.1f, fr() * 1.1f);
        batch.draw(assets.crosshairTex, ax() - ar() * 0.6f, ay() - ar() * 0.6f, ar() * 1.2f, ar() * 1.2f);
        batch.end();
    }

    private void drawButton(ShapeRenderer sr, float x, float y, float r, boolean on,
                            Color fill, Color border, Color active) {
        sr.setColor(fill);
        sr.circle(x, y, r);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl20.glLineWidth(2f);
        sr.setColor(on ? active : border);
        sr.circle(x, y, r);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Filled);
    }

    private void drawIcon(SpriteBatch batch, BitmapFont f, Assets assets, String text,
                          float x, float y, float r) {
        com.badlogic.gdx.graphics.g2d.GlyphLayout gl = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        gl.setText(f, text);
        f.setColor(1f, 1f, 1f, 0.75f);
        f.draw(batch, text, x - gl.width / 2f, y + gl.height / 2f - r * 0.12f);
    }
}
