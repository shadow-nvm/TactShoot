package com.tactshoot.game.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.tactshoot.game.Platform;
import com.tactshoot.game.TactShootGame;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class DesktopLauncher implements Platform {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("TactShoot");
        cfg.setWindowedMode(1280, 720);
        cfg.useVsync(true);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new TactShootGame(new DesktopLauncher()), cfg);
    }

    @Override
    public void shareText(String text) {
        try {
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            clip.setContents(new StringSelection(text), null);
            System.out.println("[TactShoot] Copied to clipboard: " + text);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void vibrate(int ms) {
        // no-op on desktop
    }

    @Override
    public boolean isAndroid() {
        return false;
    }
}
