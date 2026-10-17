package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Dual-stick touch controls modelled on modern mobile shooters.
 *
 * <p>Look input is accumulated from the drag of the look finger. The previous
 * implementation used {@code Gdx.input.getDeltaX()} which only tracks pointer
 * 0, so the camera either ignored the right thumb or rotated while the left
 * thumb was moving the stick.</p>
 */
public class TouchControls implements InputProcessor {

    public final InputState state = new InputState();
    private final Settings settings;

    private boolean enabled = true;
    private boolean autoFireEnabled = true;

    private int w, h;

    private final Vector2 stickOrigin = new Vector2();
    private final Vector2 stickCur = new Vector2();
    private int movePointer = -1;
    private int lookPointer = -1;
    private int firePointer = -1;
    private int adsPointer = -1;
    private float lookScreenX, lookScreenY;
    private boolean fireHeld;
    private boolean fireLock;
    private float lastTapTime = -10f;
    private float fireTapX = -1f, fireTapY = -1f;
    private boolean adsToggle = false;
    private boolean crouchToggle = false;
    private boolean scoreToggle = false;

    /** Pixel-to-look gain. Mouse input is 1:1; thumb swipes need more travel. */
    private static final float LOOK_SCALE = 2.3f;

    private static final Color FILL = new Color(0.02f, 0.04f, 0.07f, 0.34f);
    private static final Color BORDER = new Color(1f, 1f, 1f, 0.34f);
    private final GlyphLayout iconLayout = new GlyphLayout();

    // Layout as fractions of the screen (mirrored for left-handed mode).
    private static final float FIRE_X = 0.855f, FIRE_Y = 0.245f, FIRE_R = 0.125f;
    private static final float ADS_X = 0.735f, ADS_Y = 0.465f, ADS_R = 0.075f;
    private static final float RELOAD_X = 0.745f, RELOAD_Y = 0.155f, RELOAD_R = 0.058f;
    private static final float SWITCH_X = 0.845f, SWITCH_Y = 0.600f, SWITCH_R = 0.060f;
    private static final float GRENADE_X = 0.680f, GRENADE_Y = 0.690f, GRENADE_R = 0.053f;
    private static final float JUMP_X = 0.115f, JUMP_Y = 0.395f, JUMP_R = 0.072f;
    private static final float CROUCH_X = 0.100f, CROUCH_Y = 0.175f, CROUCH_R = 0.058f;
    private static final float PAUSE_X = 0.955f, PAUSE_Y = 0.930f, PAUSE_R = 0.043f;
    private static final float CHAT_X = 0.040f, CHAT_Y = 0.930f, CHAT_R = 0.040f;

    private float bx(float f) { return (settings.leftHanded ? 1f - f : f) * w; }
    private float by(float f) { return f * h; }
    private float br(float f) { return f * Math.min(w, h); }

    private float fx() { return bx(FIRE_X); }
    private float fy() { return by(FIRE_Y); }
    private float fr() { return br(FIRE_R); }

    private float ax() { return bx(ADS_X); }
    private float ay() { return by(ADS_Y); }
    private float ar() { return br(ADS_R); }

    private float rx() { return bx(RELOAD_X); }
    private float ry() { return by(RELOAD_Y); }
    private float rr() { return br(RELOAD_R); }

    private float sx() { return bx(SWITCH_X); }
    private float sy() { return by(SWITCH_Y); }
    private float sr() { return br(SWITCH_R); }

    private float gx() { return bx(GRENADE_X); }
    private float gy() { return by(GRENADE_Y); }
    private float gr() { return br(GRENADE_R); }

    private float jx() { return bx(JUMP_X); }
    private float jy() { return by(JUMP_Y); }
    private float jr() { return br(JUMP_R); }

    private float cx() { return bx(CROUCH_X); }
    private float cy() { return by(CROUCH_Y); }
    private float cr() { return br(CROUCH_R); }

    private float px() { return bx(PAUSE_X); }
    private float py() { return by(PAUSE_Y); }
    private float pr() { return br(PAUSE_R); }

    private float hx() { return bx(CHAT_X); }
    private float hy() { return by(CHAT_Y); }
    private float hr() { return br(CHAT_R); }

    private float stickBaseX() { return bx(0.145f); }
    private float stickBaseY() { return by(0.235f); }

    public TouchControls(Settings settings) {
        this.settings = settings;
    }

    public void resize(int width, int height) {
        w = width;
        h = height;
    }

    // ---------------------------------------------------------------- layout access
    // Used by DebugProbe so the automated input check swipes the real controls
    // instead of duplicating this layout math in the test driver.

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public float stickCenterX() {
        return stickBaseX();
    }

    public float stickCenterY() {
        return stickBaseY();
    }

    public float stickRadius() {
        return Math.min(w, h) * 0.14f;
    }

    public void setEnabled(boolean e) {
        enabled = e;
        if (!e) {
            state.clearAll();
            movePointer = -1;
            lookPointer = -1;
            adsPointer = -1;
            fireHeld = false;
            fireLock = false;
            firePointer = -1;
            adsToggle = false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSensitivity(float s) {
        // Look sensitivity is applied centrally in PlayerController.
    }

    public void setAutoFireEnabled(boolean enabled) {
        autoFireEnabled = enabled;
        if (!enabled) {
            fireLock = false;
            lastTapTime = -10f;
        }
    }

    public void releaseFireLock() {
        fireLock = false;
    }

    public float fireX() {
        return fx();
    }

    public float fireY() {
        return fy();
    }

    public float fireRadius() {
        return fr();
    }

    public boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!enabled) return false;
        float x = screenX;
        float y = h - screenY;
        DebugProbe.touch(x, y);

        // Buttons are tested first so dragging over them never steals the look.
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
            if (settings.aimToggle) {
                adsToggle = !adsToggle;
                state.ads = adsToggle;
            } else {
                adsPointer = pointer;
                state.ads = true;
            }
            return true;
        }
        if (inCircle(x, y, jx(), jy(), jr())) {
            state.jumpPressed = true;
            return true;
        }
        if (inCircle(x, y, cx(), cy(), cr())) {
            crouchToggle = !crouchToggle;
            state.crouchPressed = true;
            return true;
        }
        if (inCircle(x, y, fx(), fy(), fr())) {
            fireHeld = true;
            state.firePressed = true;
            firePointer = pointer;
            if (autoFireEnabled) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 320 && Math.abs(x - fireTapX) < fr()
                        && Math.abs(y - fireTapY) < fr()) {
                    fireLock = !fireLock;
                    lastTapTime = -10f;
                } else {
                    lastTapTime = now;
                    fireTapX = x;
                    fireTapY = y;
                }
            }
            return true;
        }

        boolean movementSide = settings.leftHanded ? x > w * 0.55f : x < w * 0.45f;
        if (movementSide) {
            if (movePointer == -1) {
                movePointer = pointer;
                stickOrigin.set(stickBaseX(), stickBaseY());
                stickCur.set(x, y);
                // If the player grabs outside the visual stick, pull the knob toward it.
                float dx = x - stickOrigin.x, dy = y - stickOrigin.y;
                float maxR = Math.min(w, h) * 0.14f;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > maxR) {
                    stickCur.set(stickOrigin.x + dx / len * maxR, stickOrigin.y + dy / len * maxR);
                }
                return true;
            }
        } else if (lookPointer == -1) {
            lookPointer = pointer;
            lookScreenX = screenX;
            lookScreenY = screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!enabled) return false;
        if (pointer == movePointer) {
            stickCur.set(screenX, h - screenY);
            return true;
        }
        if (pointer == lookPointer) {
            // Screen-space deltas: +x turns right, +y looks down (controller maps this).
            state.lookDX += (screenX - lookScreenX) * LOOK_SCALE;
            state.lookDY += (screenY - lookScreenY) * LOOK_SCALE;
            lookScreenX = screenX;
            lookScreenY = screenY;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pointer == movePointer) {
            movePointer = -1;
            state.moveX = 0f;
            state.moveY = 0f;
            state.sprint = false;
            return true;
        }
        if (pointer == lookPointer) {
            lookPointer = -1;
            return true;
        }
        if (pointer == adsPointer) {
            adsPointer = -1;
            if (!settings.aimToggle) state.ads = false;
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
        // Look deltas are accumulated by touchDragged; see class comment.
    }

    public void updateState() {
        if (movePointer != -1) {
            float dx = stickCur.x - stickOrigin.x;
            float dy = stickCur.y - stickOrigin.y;
            float maxR = Math.min(w, h) * 0.14f;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > maxR) {
                dx = dx / len * maxR;
                dy = dy / len * maxR;
                len = maxR;
            }
            float dead = maxR * 0.16f;
            if (len < dead) {
                state.moveX = 0f;
                state.moveY = 0f;
                state.sprint = false;
            } else {
                float norm = (len - dead) / (maxR - dead);
                state.moveX = dx / maxR;
                state.moveY = dy / maxR;
                state.sprint = settings.autoSprint && norm > 0.90f;
            }
        } else {
            state.moveX = 0f;
            state.moveY = 0f;
            state.sprint = false;
        }
        state.fire = fireHeld || fireLock;
        state.autoFire = fireLock;
        if (!settings.aimToggle) state.ads = adsPointer != -1;
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
        Gdx.gl.glEnable(com.badlogic.gdx.Gdx.gl20.GL_BLEND);
        Gdx.gl.glBlendFunc(com.badlogic.gdx.Gdx.gl20.GL_SRC_ALPHA,
                com.badlogic.gdx.Gdx.gl20.GL_ONE_MINUS_SRC_ALPHA);
        Color active = Palette.ACCENT;

        sr.begin(ShapeRenderer.ShapeType.Filled);
        // Always-visible movement base + active knob.
        float baseR = Math.min(w, h) * 0.14f;
        sr.setColor(0.02f, 0.04f, 0.07f, 0.30f);
        sr.circle(stickBaseX(), stickBaseY(), baseR);
        sr.setColor(1f, 1f, 1f, 0.10f);
        sr.circle(stickBaseX(), stickBaseY(), baseR * 0.34f);
        if (movePointer != -1) {
            sr.setColor(active.r, active.g, active.b, 0.24f);
            sr.circle(stickBaseX(), stickBaseY(), baseR * 0.94f);
            sr.setColor(active.r, active.g, active.b, 0.85f);
            sr.circle(stickCur.x, stickCur.y, Math.min(w, h) * 0.050f);
        }

        fillButton(sr, fx(), fy(), fr(), fireHeld || fireLock, active);
        fillButton(sr, ax(), ay(), ar(), state.ads, active);
        fillButton(sr, rx(), ry(), rr(), false, active);
        fillButton(sr, sx(), sy(), sr(), false, active);
        fillButton(sr, gx(), gy(), gr(), false, active);
        fillButton(sr, jx(), jy(), jr(), false, active);
        fillButton(sr, cx(), cy(), cr(), crouchToggle, active);
        fillButton(sr, px(), py(), pr(), false, active);
        fillButton(sr, hx(), hy(), hr(), false, active);
        sr.end();

        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(BORDER);
        sr.circle(fx(), fy(), fr());
        sr.circle(ax(), ay(), ar());
        sr.circle(rx(), ry(), rr());
        sr.circle(sx(), sy(), sr());
        sr.circle(gx(), gy(), gr());
        sr.circle(jx(), jy(), jr());
        sr.circle(cx(), cy(), cr());
        sr.circle(px(), py(), pr());
        sr.circle(hx(), hy(), hr());
        sr.end();

        batch.begin();
        f.setColor(Color.WHITE);
        drawIcon(batch, f, "R", rx(), ry(), rr());
        drawIcon(batch, f, "SW", sx(), sy(), sr());
        drawIcon(batch, f, "G", gx(), gy(), gr());
        drawIcon(batch, f, "\u2191", jx(), jy(), jr());
        drawIcon(batch, f, "\u2193", cx(), cy(), cr());
        drawIcon(batch, f, "II", px(), py(), pr());
        drawIcon(batch, f, "CH", hx(), hy(), hr());
        drawIcon(batch, f, "AIM", ax(), ay(), ar());
        if (assets.crosshairTex != null) {
            batch.setColor(1f, 1f, 1f, 0.9f);
            batch.draw(assets.crosshairTex, fx() - fr() * 0.48f, fy() - fr() * 0.48f,
                    fr() * 0.96f, fr() * 0.96f);
            batch.setColor(Color.WHITE);
        }
        batch.end();
    }

    private void fillButton(ShapeRenderer sr, float x, float y, float r, boolean on, Color active) {
        if (on) sr.setColor(active.r, active.g, active.b, 0.52f);
        else sr.setColor(FILL);
        sr.circle(x, y, r);
    }

    private void drawIcon(SpriteBatch batch, BitmapFont f, String text,
                          float x, float y, float r) {
        iconLayout.setText(f, text);
        f.setColor(1f, 1f, 1f, 0.86f);
        f.draw(batch, text, x - iconLayout.width / 2f, y + iconLayout.height / 2f - r * 0.1f);
    }
}
