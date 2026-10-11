package com.tactshoot.game;

/** Platform-specific services provided by the host (Android / Desktop). */
public interface Platform {
    /** Open the OS share sheet (Android) or copy to clipboard (desktop). */
    void shareText(String text);

    /** Vibrate for ms milliseconds (Android only). */
    void vibrate(int ms);

    /** True when running on Android. */
    boolean isAndroid();
}
