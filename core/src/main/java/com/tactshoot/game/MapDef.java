package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

/** Static description of a multiplayer map: geometry, lighting and spawns. */
public class MapDef {

    public String id;
    public String displayName;
    public String description;

    // Sky / atmosphere
    public Vector3 sunDir = new Vector3();
    public Color sunColor = new Color();
    public Color skyTop = new Color();
    public Color skyHorizon = new Color();
    public Color groundCol = new Color();
    public Color fogCol = new Color();
    public Color ambient = new Color();
    public float night = 0f; // 0..1, drives stars / darker sky

    // World bounds (walkable area)
    public float minX, minZ, maxX, maxZ;

    public Array<Box> solids = new Array<>();
    /** Team spawns: index 0..1, or FFA uses all. */
    public Array<Vector3> spawnsA = new Array<>();
    public Array<Vector3> spawnsB = new Array<>();
    /** Extra roam points for bots. */
    public Array<Vector3> roam = new Array<>();

    /** Non-colliding visual props rendered on top of the world model. */
    public Array<Prop> props = new Array<>();

    public static class Prop {
        public String type; // "barrel", "pole", "antenna", "sandbag", "lamp", "crate"
        public float x, y, z;
        public float rot;   // yaw degrees
        public float scale = 1f;

        public Prop(String type, float x, float y, float z, float rot) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rot = rot;
        }
    }

    public void addSpawnA(float x, float z) {
        spawnsA.add(new Vector3(x, 0, z));
    }

    public void addSpawnB(float x, float z) {
        spawnsB.add(new Vector3(x, 0, z));
    }

    public void addRoam(float x, float z) {
        roam.add(new Vector3(x, 0, z));
    }

    public Vector3 pickSpawn(int team, int avoidHint) {
        Array<Vector3> list = (team == 1) ? spawnsB : spawnsA;
        if (list.size == 0) list = spawnsA;
        if (list.size == 0) return new Vector3(0, 0, 0);
        // Try a few random picks, prefer the one whose index differs from avoidHint.
        Vector3 best = list.first();
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 5; i++) {
            Vector3 s = list.get(T.randInt(0, list.size - 1));
            float score = Math.abs(list.indexOf(s, true) - avoidHint) * 10f - T.rand(0, 5);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return new Vector3(best);
    }
}
