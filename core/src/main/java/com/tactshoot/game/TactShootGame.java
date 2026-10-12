package com.tactshoot.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Array;

/** Application entry point. */
public class TactShootGame extends Game {

    public static final String VERSION = "1.0.0";

    public final Platform platform;
    public Assets assets;
    public Settings settings;
    private final Array<Screen> retiredScreens = new Array<Screen>(false, 4);

    public TactShootGame(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void create() {
        Gdx.app.log("TactShoot", "booting v" + VERSION);
        try {
            settings = new Settings();
            settings.load();
            assets = new Assets();
            assets.load(settings);
            setScreen(new MenuScreen(this));
        } catch (Throwable t) {
            Gdx.app.error("TactShoot", "Fatal boot error: " + t.getMessage(), t);
            setScreen(new ErrorScreen(t.getMessage()));
        }
    }

    @Override
    public void setScreen(Screen next) {
        Screen previous = getScreen();
        if (previous == next) return;
        boolean retainPrevious = next instanceof SettingsScreen
                && ((SettingsScreen) next).returnsTo(previous);
        retiredScreens.removeValue(next, true);
        super.setScreen(next);
        if (previous != null && !retainPrevious && !retiredScreens.contains(previous, true)) {
            retiredScreens.add(previous);
        }
    }

    @Override
    public void render() {
        super.render();
        disposeRetiredScreens();
    }

    private void disposeRetiredScreens() {
        Screen active = getScreen();
        for (int i = retiredScreens.size - 1; i >= 0; i--) {
            Screen retired = retiredScreens.get(i);
            if (retired != active) retired.dispose();
        }
        retiredScreens.clear();
    }

    @Override
    public void dispose() {
        super.dispose();
        disposeRetiredScreens();
        if (assets != null) assets.dispose();
    }
}
