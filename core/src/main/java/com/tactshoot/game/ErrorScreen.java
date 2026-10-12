package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** Fallback screen displayed if any unexpected fatal error occurs during initialization. */
public class ErrorScreen extends ScreenAdapter {

    private final String errorMsg;
    private SpriteBatch batch;
    private BitmapFont font;

    public ErrorScreen(String errorMsg) {
        this.errorMsg = errorMsg == null ? "Unknown boot error" : errorMsg;
    }

    @Override
    public void show() {
        try {
            batch = new SpriteBatch();
            font = new BitmapFont();
            font.getData().setScale(1.6f);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.12f, 0.05f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (batch != null && font != null) {
            try {
                batch.begin();
                font.draw(batch, "TACTSHOOT BOOT ERROR:\n" + errorMsg, 40, Gdx.graphics.getHeight() - 60);
                batch.end();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void dispose() {
        if (batch != null) try { batch.dispose(); } catch (Throwable ignored) {}
        if (font != null) try { font.dispose(); } catch (Throwable ignored) {}
    }
}
