package com.tactshoot.game;

import com.badlogic.gdx.utils.JsonValue;

/** Configuration for a match: online or practice, mode, map, bots. */
public class MatchConfig {

    public boolean online;
    public String mode = "ffa";           // "tdm" | "ffa"
    public int mapIdx = 0;
    public int primaryWeapon = Weapons.AR;
    public int botCount = 7;
    public float botLevel = 0.5f;
    public String serverUrl = "";
    public String roomCode = "";          // private-room join code
    public boolean createRoom;            // create a private room instead of quick play

    /** Adopted connection from the queue screen. */
    public NetClient net;
    /** Match payload received from the server. */
    public JsonValue matchPayload;

    public boolean isTdm() {
        return "tdm".equals(mode);
    }
}
