package com.tactshoot.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;

/** Application entry point. */
public class TactShootGame extends Game {

    public static final String VERSION = "1.0.0";

    public final Platform platform;
    public Assets assets;
    public Settings settings;

    public TactShootGame(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void create() {
        Gdx.app.log("TactShoot", "booting v" + VERSION);
        settings = new Settings();
        settings.load();
        assets = new Assets();
        assets.load(settings);
        setScreen(new MenuScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        if (assets != null) assets.dispose();
    }
}
