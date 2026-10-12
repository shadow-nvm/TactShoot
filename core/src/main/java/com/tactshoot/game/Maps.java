package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.badlogic.gdx.utils.ObjectMap;

/** Map definitions + the world geometry builder. */
public final class Maps {

    // material table
    public static final int M_SAND = 0, M_CONCRETE = 1, M_METAL = 2, M_WOOD = 3, M_BRICK = 4,
            M_ADOBE = 5, M_PAVING = 6, M_GROUND = 7, M_METAL_DARK = 8, M_WOOD_DARK = 9,
            M_CONCRETE_DARK = 10;

    public static final String[] TEX_NAMES = {
            "sand", "concrete", "metal", "wood", "bricks", "adobe",
            "paving", "ground", "metal_dark", "wood_dark", "concrete_dark"
    };

    public static final float[] UV_SCALE = {
            3.0f, 2.0f, 1.6f, 1.5f, 1.4f, 1.4f, 2.2f, 3.0f, 1.5f, 1.4f, 2.0f
    };

    private Maps() {
    }

    // ================================================================ maps

    public static MapDef[] all() {
        return new MapDef[]{dustyard(), facility(), arena()};
    }

    private static void ground(MapDef m, float size, int mat) {
        float h = size / 2f;
        m.solids.add(new Box(-h, -0.8f, -h, h, 0f, h, mat));
        m.minX = -h;
        m.maxX = h;
        m.minZ = -h;
        m.maxZ = h;
    }

    private static void wallX(Array<Box> s, float z1, float z2, float x, float h, int mat, float thick) {
        s.add(new Box(x - thick / 2f, 0, z1, x + thick / 2f, h, z2, mat));
    }

    private static void wallZ(Array<Box> s, float x1, float x2, float z, float h, int mat, float thick) {
        s.add(new Box(x1, 0, z - thick / 2f, x2, h, z + thick / 2f, mat));
    }

    private static void crate(Array<Box> s, float cx, float cz, float size, int mat) {
        s.add(Box.ground(cx, cz, size, size, size, mat));
    }

    private static void longCrate(Array<Box> s, float cx, float cz, float w, float d, float h, int mat) {
        s.add(Box.ground(cx, cz, w, d, h, mat));
    }

    /** Stairs climbing in +x direction, starting at (x,z). */
    private static void stepsX(Array<Box> s, float x, float z, int n, int mat) {
        for (int i = 0; i < n; i++) {
            s.add(new Box(x + i * 0.6f, 0, z - 0.8f, x + (i + 1) * 0.6f, 0.3f * (i + 1), z + 0.8f, mat));
        }
    }

    /** Stairs climbing in +z direction, starting at (x,z). */
    private static void stepsZ(Array<Box> s, float x, float z, int n, int mat) {
        for (int i = 0; i < n; i++) {
            s.add(new Box(x - 0.8f, 0, z + i * 0.6f, x + 0.8f, 0.3f * (i + 1), z + (i + 1) * 0.6f, mat));
        }
    }

    // ---------------------------------------------------------------- DUSTYARD

    public static MapDef dustyard() {
        MapDef m = new MapDef();
        m.id = "dust";
        m.displayName = "DUSTYARD";
        m.description = "A sun-scorched desert outpost. Close quarters, long sightlines.";
        m.sunDir.set(0.45f, 0.62f, 0.30f).nor(); // toward the sun (for sky shader)
        m.sunColor.set(1f, 0.93f, 0.75f, 1f);
        m.skyTop.set(0.28f, 0.48f, 0.80f, 1f);
        m.skyHorizon.set(0.93f, 0.80f, 0.64f, 1f);
        m.groundCol.set(0.72f, 0.64f, 0.52f, 1f);
        m.fogCol.set(0.88f, 0.74f, 0.60f, 1f);
        m.ambient.set(0.52f, 0.48f, 0.42f, 1f);
        m.night = 0f;

        Array<Box> s = m.solids;
        ground(m, 64, M_SAND);

        // perimeter walls
        wallX(s, -32, 32, -31, 6, M_ADOBE, 1.2f);
        wallX(s, -32, 32, 31, 6, M_ADOBE, 1.2f);
        wallZ(s, -32, 32, -31, 6, M_ADOBE, 1.2f);
        wallZ(s, -32, 32, 31, 6, M_ADOBE, 1.2f);

        // central stronghold: hollow rectangle, 4m gate on the south
        float hx = 11f, hz = 8f;
        wallX(s, -hz, hz, -hx, 7, M_ADOBE, 1f);            // west wall
        wallX(s, -hz, hz, hx, 7, M_ADOBE, 1f);             // east wall
        wallZ(s, -hx, hx, -hz, 7, M_ADOBE, 1f);            // north wall
        wallZ(s, -hx, -2f, hz, 7, M_ADOBE, 1f);            // south wall, west of gate
        wallZ(s, 2f, hx, hz, 7, M_ADOBE, 1f);              // south wall, east of gate
        // roof (also the interior ceiling)
        s.add(new Box(-hx + 0.5f, 6.5f, -hz + 0.5f, hx - 0.5f, 7.2f, hz - 0.5f, M_CONCRETE));
        // interior crates + pillars
        crate(s, -6, 4, 1.3f, M_WOOD);
        crate(s, -3.2f, -4, 1.3f, M_WOOD);
        longCrate(s, 6.5f, -3, 1.6f, 3.2f, 1.1f, M_WOOD_DARK);
        s.add(Box.ground(-9, -4, 0.7f, 0.7f, 6.5f, M_CONCRETE)); // pillar
        s.add(Box.ground(9, 4, 0.7f, 0.7f, 6.5f, M_CONCRETE));

        // watchtower NW (landmark)
        s.add(Box.ground(-25, -25, 4, 4, 6, M_BRICK));
        s.add(new Box(-26, 6, -26, -24, 6.8f, -24, M_BRICK));
        s.add(new Box(-24, 6, -26, -22, 6.8f, -24, M_BRICK));
        s.add(new Box(-26, 6, -24, -24, 6.8f, -22, M_BRICK));
        s.add(new Box(-24, 6, -24, -22, 6.8f, -22, M_BRICK));

        // market street (two rows of stalls east of center)
        for (int i = 0; i < 5; i++) {
            float z = 8 + i * 3.6f;
            longCrate(s, 6, z, 3, 1.8f, 1.25f, M_WOOD_DARK);
            longCrate(s, -5, z + 1.4f, 3, 1.8f, 1.25f, M_WOOD_DARK);
            m.props.add(new MapDef.Prop("lamp", 0.5f, 0, z + 0.7f, 0));
        }

        // container yard SW
        longCrate(s, -20, -14, 6, 2.6f, 2.6f, M_METAL_DARK);
        longCrate(s, -20, -10, 6, 2.6f, 2.6f, M_METAL_DARK);
        longCrate(s, -13.2f, -12, 6, 2.6f, 2.6f, M_METAL);
        s.add(new Box(-21, 2.6f, -14.5f, -19, 5.2f, -9.5f, M_METAL_DARK)); // stacked container

        // platforms + stairs (east side, for verticality)
        s.add(Box.ground(18, 18, 5, 5, 1.2f, M_CONCRETE));
        stepsZ(s, 18, 13.1f, 4, M_CONCRETE);
        s.add(Box.ground(24, 18, 4, 4, 1.2f, M_CONCRETE));
        stepsX(s, 19.6f, 18, 4, M_CONCRETE);
        s.add(Box.ground(21.25f, 18, 1.5f, 1.5f, 1.2f, M_CONCRETE)); // bridge

        // scattered cover
        crate(s, 12, -6, 1.2f, M_WOOD);
        crate(s, 13.4f, -6, 1.2f, M_WOOD);
        crate(s, -12, 8, 1.2f, M_WOOD);
        crate(s, 8, -22, 1.2f, M_WOOD);
        longCrate(s, -6, 22, 1.4f, 4, 1.0f, M_ADOBE); // low wall
        longCrate(s, 15, 8, 4, 1.4f, 1.0f, M_ADOBE);
        longCrate(s, 2, -18, 4, 1.4f, 1.0f, M_ADOBE);
        longCrate(s, -15, 16, 1.4f, 4, 1.0f, M_ADOBE);
        // sandbag nests
        longCrate(s, -28.5f, 6, 1.2f, 4, 0.9f, M_SAND);
        longCrate(s, 28.5f, -6, 1.2f, 4, 0.9f, M_SAND);

        // props
        for (int i = 0; i < 6; i++) {
            m.props.add(new MapDef.Prop("barrel", -18 + i * 2.2f, 0, -2 + (i % 2) * 1.5f, i * 37));
        }
        m.props.add(new MapDef.Prop("antenna", 26, 0, -26, 0));
        m.props.add(new MapDef.Prop("barrel", 4, 0, -14, 90));
        m.props.add(new MapDef.Prop("barrel", 4.9f, 0, -14, 0));
        m.props.add(new MapDef.Prop("lamp", -16, 0, 22, 0));
        m.props.add(new MapDef.Prop("lamp", 16, 0, 22, 0));

        // spawns: A along east, B along west
        m.addSpawnA(26, 2);
        m.addSpawnA(18, -24);
        m.addSpawnA(2, 26);
        m.addSpawnA(28, 14);
        m.addSpawnB(-26, 2);
        m.addSpawnB(-18, 24);
        m.addSpawnB(-6, -24);
        m.addSpawnB(-28, -14);

        // roam points
        float[][] roam = {{0, 0}, {8, -8}, {-8, 8}, {16, -14}, {-16, 14}, {2, 24}, {-2, -24},
                {24, -4}, {-24, 4}, {10, 16}, {-10, -16}, {20, 4}, {-20, -4}, {0, 12}, {0, -12}};
        for (float[] r : roam) m.addRoam(r[0], r[1]);
        return m;
    }

    // ---------------------------------------------------------------- FACILITY

    public static MapDef facility() {
        MapDef m = new MapDef();
        m.id = "facility";
        m.displayName = "FACILITY";
        m.description = "Dusk raid on an industrial yard. Vertical, tight and moody.";
        m.sunDir.set(-0.25f, 0.22f, 0.55f).nor();
        m.sunColor.set(1f, 0.62f, 0.35f, 1f);
        m.skyTop.set(0.05f, 0.08f, 0.16f, 1f);
        m.skyHorizon.set(0.42f, 0.30f, 0.25f, 1f);
        m.groundCol.set(0.16f, 0.16f, 0.18f, 1f);
        m.fogCol.set(0.30f, 0.24f, 0.22f, 1f);
        m.ambient.set(0.36f, 0.36f, 0.44f, 1f);
        m.night = 0.55f;

        Array<Box> s = m.solids;
        ground(m, 60, M_PAVING);

        // perimeter
        wallX(s, -30, 30, -29, 6.5f, M_CONCRETE_DARK, 1.2f);
        wallX(s, -30, 30, 29, 6.5f, M_CONCRETE_DARK, 1.2f);
        wallZ(s, -30, 30, -29, 6.5f, M_CONCRETE_DARK, 1.2f);
        wallZ(s, -30, 30, 29, 6.5f, M_CONCRETE_DARK, 1.2f);

        // main warehouse (center, 3m door on the south)
        float wx1 = -6, wx2 = 10, wz1 = -6, wz2 = 10;
        wallZ(s, wx1, wx2, wz1, 8, M_METAL, 0.8f);            // north wall
        wallX(s, wz1, wz2, wx1, 8, M_METAL, 0.8f);            // west wall
        wallX(s, wz1, wz2, wx2, 8, M_METAL, 0.8f);            // east wall
        wallZ(s, wx1, 2f, wz2, 8, M_METAL, 0.8f);             // south wall, west of door
        wallZ(s, 5f, wx2, wz2, 8, M_METAL, 0.8f);             // south wall, east of door
        // roof
        s.add(new Box(wx1 + 0.5f, 7.5f, wz1 + 0.5f, wx2 - 0.5f, 8.2f, wz2 - 0.5f, M_CONCRETE_DARK));
        // interior pillars + shelves
        s.add(Box.ground(-2, 4, 0.9f, 0.9f, 7.4f, M_CONCRETE));
        s.add(Box.ground(6, -2, 0.9f, 0.9f, 7.4f, M_CONCRETE));
        s.add(Box.ground(6, 6, 0.9f, 0.9f, 7.4f, M_CONCRETE));
        longCrate(s, -2, -2, 4, 1.2f, 1.6f, M_METAL_DARK);  // shelf
        longCrate(s, -2, 0, 4, 1.2f, 1.6f, M_METAL_DARK);
        crate(s, 8, -4, 1.3f, M_WOOD);

        // container yard SW
        longCrate(s, -22, -20, 6.2f, 2.6f, 2.6f, M_METAL_DARK);
        longCrate(s, -22, -15.8f, 6.2f, 2.6f, 2.6f, M_METAL);
        longCrate(s, -15, -22, 6.2f, 2.6f, 2.6f, M_METAL_DARK);
        longCrate(s, -14.5f, -14, 6.2f, 2.6f, 2.6f, M_METAL);
        s.add(new Box(-23, 2.6f, -21, -21, 5.2f, -19, M_METAL_DARK));

        // office block NE (two rooms, open doors)
        wallZ(s, 12, 26, 12, 5.2f, M_CONCRETE, 0.8f);
        wallX(s, 12, 24, 12, 5.2f, M_CONCRETE, 0.8f);
        wallX(s, 12, 24, 26, 5.2f, M_CONCRETE, 0.8f);
        wallZ(s, 12, 26, 24, 5.2f, M_CONCRETE, 0.8f);
        s.removeIndex(s.size - 1);
        wallZ(s, 12, 16, 24, 5.2f, M_CONCRETE, 0.8f);
        wallZ(s, 19, 26, 24, 5.2f, M_CONCRETE, 0.8f); // door gap 16..19
        wallZ(s, 12, 26, 17.5f, 3.2f, M_CONCRETE, 0.4f); // interior partition, door gap x 16..20
        s.removeIndex(s.size - 1);
        wallZ(s, 12, 16, 17.5f, 3.2f, M_CONCRETE, 0.4f);
        wallZ(s, 20, 26, 17.5f, 3.2f, M_CONCRETE, 0.4f);
        s.add(new Box(12.4f, 4.6f, 12.4f, 25.6f, 5.2f, 23.6f, M_CONCRETE_DARK)); // roof
        longCrate(s, 14, 14, 2.4f, 1.2f, 1.1f, M_WOOD_DARK); // desk

        // fuel tanks north
        m.props.add(new MapDef.Prop("tank", -10, 0, -24, 0));
        m.props.add(new MapDef.Prop("tank", -5.5f, 0, -25, 0));
        // pipes east
        m.props.add(new MapDef.Prop("pipe", 22, 1.6f, -20, 90));
        m.props.add(new MapDef.Prop("pipe", 22, 1.6f, -14, 90));

        // scattered cover
        crate(s, 14, -14, 1.2f, M_WOOD);
        crate(s, 15.4f, -14, 1.2f, M_WOOD);
        crate(s, -4, 20, 1.2f, M_WOOD);
        crate(s, 16, 6, 1.2f, M_WOOD);
        longCrate(s, -14, 6, 1.4f, 4, 1.0f, M_CONCRETE);
        longCrate(s, 4, 20, 4, 1.4f, 1.0f, M_CONCRETE);
        longCrate(s, -8, -14, 4, 1.4f, 1.0f, M_CONCRETE);
        longCrate(s, 22, -6, 1.4f, 4, 1.0f, M_CONCRETE);

        for (int i = 0; i < 4; i++) {
            m.props.add(new MapDef.Prop("barrel", 8 + i * 1.2f, 0, 22 + (i % 2) * 1.4f, i * 55));
        }
        m.props.add(new MapDef.Prop("lamp", -26, 0, 26, 0));
        m.props.add(new MapDef.Prop("lamp", 26, 0, -26, 0));
        m.props.add(new MapDef.Prop("lamp", 0, 0, -4, 0));

        m.addSpawnA(0, 24);
        m.addSpawnA(-24, 24);
        m.addSpawnA(24, 24);
        m.addSpawnA(0, -28);
        m.addSpawnB(0, -24);
        m.addSpawnB(-24, -24);
        m.addSpawnB(24, -24);
        m.addSpawnB(0, 28);

        float[][] roam = {{0, 0}, {2, 4}, {-12, -4}, {8, 22}, {-20, 12}, {20, -12}, {-24, -6},
                {24, 6}, {-6, 22}, {6, -14}, {-26, 22}, {26, 22}};
        for (float[] r : roam) m.addRoam(r[0], r[1]);
        return m;
    }

    // ---------------------------------------------------------------- ARENA

    public static MapDef arena() {
        MapDef m = new MapDef();
        m.id = "arena";
        m.displayName = "ARENA";
        m.description = "Symmetric kill-box. Pure aim, zero camping.";
        m.sunDir.set(0.55f, 0.70f, 0.20f).nor();
        m.sunColor.set(1f, 0.95f, 0.82f, 1f);
        m.skyTop.set(0.24f, 0.42f, 0.72f, 1f);
        m.skyHorizon.set(0.82f, 0.86f, 0.88f, 1f);
        m.groundCol.set(0.55f, 0.56f, 0.58f, 1f);
        m.fogCol.set(0.78f, 0.82f, 0.84f, 1f);
        m.ambient.set(0.5f, 0.52f, 0.56f, 1f);
        m.night = 0f;

        Array<Box> s = m.solids;
        ground(m, 40, M_PAVING);

        // perimeter
        wallX(s, -20, 20, -19, 5, M_CONCRETE, 1.2f);
        wallX(s, -20, 20, 19, 5, M_CONCRETE, 1.2f);
        wallZ(s, -20, 20, -19, 5, M_CONCRETE, 1.2f);
        wallZ(s, -20, 20, 19, 5, M_CONCRETE, 1.2f);

        // center platform with 4 stair ramps climbing toward it
        s.add(Box.ground(0, 0, 6, 6, 1.2f, M_METAL));
        stepsX(s, -5.4f, 0, 4, M_CONCRETE);                                  // west stairs
        for (int i = 0; i < 4; i++) {                                        // east stairs (climb -x)
            s.add(new Box(5.4f - 0.6f * (i + 1), 0, -0.8f, 5.4f - 0.6f * i,
                    0.3f * (i + 1), 0.8f, M_CONCRETE));
        }
        stepsZ(s, 0, -5.4f, 4, M_CONCRETE);                                  // north stairs
        for (int i = 0; i < 4; i++) {                                        // south stairs (climb -z)
            s.add(new Box(-0.8f, 0, 5.4f - 0.6f * (i + 1), 0.8f,
                    0.3f * (i + 1), 5.4f - 0.6f * i, M_CONCRETE));
        }

        // corner bunkers with door gaps
        bunker(s, -15, -15, M_CONCRETE);
        bunker(s, 15, -15, M_CONCRETE);
        bunker(s, -15, 15, M_CONCRETE);
        bunker(s, 15, 15, M_CONCRETE);

        // X-shaped mid walls between center and corners
        longCrate(s, -11, -11, 5, 1.2f, 1.1f, M_CONCRETE);
        longCrate(s, 11, -11, 5, 1.2f, 1.1f, M_CONCRETE);
        longCrate(s, -11, 11, 5, 1.2f, 1.1f, M_CONCRETE);
        longCrate(s, 11, 11, 5, 1.2f, 1.1f, M_CONCRETE);
        longCrate(s, -11, 11, 1.2f, 5, 1.1f, M_CONCRETE);
        longCrate(s, 11, 11, 1.2f, 5, 1.1f, M_CONCRETE);
        longCrate(s, -11, -11, 1.2f, 5, 1.1f, M_CONCRETE);
        longCrate(s, 11, -11, 1.2f, 5, 1.1f, M_CONCRETE);

        // crates at cardinal points
        crate(s, 0, 14, 1.2f, M_WOOD);
        crate(s, 0, -14, 1.2f, M_WOOD);
        crate(s, 14, 0, 1.2f, M_WOOD);
        crate(s, -14, 0, 1.2f, M_WOOD);
        crate(s, 8, 8, 1.2f, M_WOOD);
        crate(s, -8, -8, 1.2f, M_WOOD);

        for (int i = 0; i < 4; i++) {
            m.props.add(new MapDef.Prop("barrel", -3 + i * 2f, 0, -12.5f, 0));
            m.props.add(new MapDef.Prop("lamp", -16, 0, 16, 0));
        }

        m.addSpawnA(16, 0);
        m.addSpawnA(-16, 0);
        m.addSpawnA(0, 16);
        m.addSpawnA(0, -16);
        m.addSpawnB(6, 13);
        m.addSpawnB(-6, -13);
        m.addSpawnB(-6, 13);
        m.addSpawnB(13, 6);
        m.addSpawnB(-13, -6);

        float[][] roam = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {10, 0}, {-10, 0}, {0, 10},
                {0, -10}, {16, 16}, {-16, -16}};
        for (float[] r : roam) m.addRoam(r[0], r[1]);
        return m;
    }

    private static void bunker(Array<Box> s, float cx, float cz, int mat) {
        // 3x3 bunker with door facing the center
        float dx = cx < 0 ? 1 : -1; // door side
        float dz = cz < 0 ? 1 : -1;
        // walls: two corner L shapes, leaving a 1.2m door on the center-facing corner
        s.add(new Box(cx - 1.5f, 0, cz - 1.5f, cx - 0.5f, 2.4f, cz + 1.5f, mat)); // left wall
        s.add(new Box(cx + 0.5f, 0, cz - 1.5f, cx + 1.5f, 2.4f, cz + 1.5f, mat)); // right wall
        s.add(new Box(cx - 0.5f, 0, cz - 1.5f, cx + 0.5f, 2.4f, cz + 1.5f * -1f, mat)); // back wall
        s.add(new Box(cx - 1.5f, 2.4f, cz - 1.5f, cx + 1.5f, 2.6f, cz + 1.5f, mat)); // roof
        s.add(Box.ground(cx + dx * 2.4f, cz + dz * 2.4f, 1.6f, 1.6f, 1.0f, M_WOOD_DARK));
    }

    // ================================================================ world model

    private static final long ATTRIBS = VertexAttributes.Usage.Position
            | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;

    /** Builds the merged static world model (one mesh, batched by material). */
    public static Model buildWorld(MapDef map, ObjectMap<String, Texture> textures) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Array<Material> materials = new Array<>();
        for (int i = 0; i < TEX_NAMES.length; i++) {
            Texture t = textures.get(TEX_NAMES[i]);
            Material mat = new Material(TextureAttribute.createDiffuse(t));
            materials.add(mat);
        }
        int counter = 0;
        for (Box b : map.solids) {
            MeshPartBuilder part = mb.part("p" + (counter++), GL20.GL_TRIANGLES, ATTRIBS,
                    materials.get(b.material));
            fillBox(part, b, UV_SCALE[b.material]);
        }
        Model model = mb.end();
        return model;
    }

    /** Emits one box as 24 vertices / 36 indices with per-face UV scaling. */
    public static void fillBox(MeshPartBuilder part, Box b, float uv) {
        FloatArray v = new FloatArray(24 * 8);
        ShortArray idx = new ShortArray(36);
        // faces: +Z, -Z, +X, -X, +Y, -Y
        emitFace(v, idx,
                b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ,
                0, 0, 1, uv, true, false);
        emitFace(v, idx,
                b.maxX, b.minY, b.minZ, b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ,
                0, 0, -1, uv, true, false);
        emitFace(v, idx,
                b.maxX, b.minY, b.maxZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ,
                1, 0, 0, uv, false, false);
        emitFace(v, idx,
                b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ,
                -1, 0, 0, uv, false, false);
        emitFace(v, idx,
                b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ,
                0, 1, 0, uv, false, true);
        emitFace(v, idx,
                b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ,
                0, -1, 0, uv, false, true);
        part.addMesh(v.toArray(), idx.toArray());
    }

    private static void emitFace(FloatArray v, ShortArray idx,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3,
                                 float nx, float ny, float nz, float uv, boolean faceXY, boolean faceXZ) {
        int base = v.size / 8;
        float[][] corners = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        for (float[] c : corners) {
            float u, vv;
            if (faceXY) { // faces perpendicular to Z
                u = (c[0] - Math.min(Math.min(x0, x1), Math.min(x2, x3))) / uv;
                vv = (c[1] - Math.min(Math.min(y0, y1), Math.min(y2, y3))) / uv;
            } else if (faceXZ) { // faces perpendicular to Y
                u = (c[0] - Math.min(Math.min(x0, x1), Math.min(x2, x3))) / uv;
                vv = (c[2] - Math.min(Math.min(z0, z1), Math.min(z2, z3))) / uv;
            } else { // faces perpendicular to X
                u = (c[2] - Math.min(Math.min(z0, z1), Math.min(z2, z3))) / uv;
                vv = (c[1] - Math.min(Math.min(y0, y1), Math.min(y2, y3))) / uv;
            }
            v.add(c[0]);
            v.add(c[1]);
            v.add(c[2]);
            v.add(nx);
            v.add(ny);
            v.add(nz);
            v.add(u);
            v.add(vv);
        }
        idx.add((short) base);
        idx.add((short) (base + 1));
        idx.add((short) (base + 2));
        idx.add((short) base);
        idx.add((short) (base + 2));
        idx.add((short) (base + 3));
    }

    /** Emits a cylinder (axis Y) into the mesh. */
    public static void fillCylinder(MeshPartBuilder part, float cx, float y0, float cz, float radius, float height,
                                    int segments, float uv) {
        FloatArray v = new FloatArray();
        ShortArray idx = new ShortArray();
        // side
        for (int i = 0; i <= segments; i++) {
            float a = (float) i / segments * (float) Math.PI * 2f;
            float x = cx + (float) Math.cos(a) * radius;
            float z = cz + (float) Math.sin(a) * radius;
            float nx = (float) Math.cos(a), nz = (float) Math.sin(a);
            float u = (i / (float) segments) * (2f * (float) Math.PI * radius) / uv;
            v.add(x); v.add(y0); v.add(z); v.add(nx); v.add(0); v.add(nz); v.add(u); v.add(0);
            v.add(x); v.add(y0 + height); v.add(z); v.add(nx); v.add(0); v.add(nz); v.add(u); v.add(height / uv);
        }
        int ringBase = 0;
        for (int i = 0; i < segments; i++) {
            int b = i * 2;
            idx.add((short) b);
            idx.add((short) (b + 1));
            idx.add((short) (b + 3));
            idx.add((short) b);
            idx.add((short) (b + 3));
            idx.add((short) (b + 2));
        }
        // caps
        int centerTop = v.size / 8;
        v.add(cx); v.add(y0 + height); v.add(cz); v.add(0); v.add(1); v.add(0); v.add(0.5f); v.add(0.5f);
        for (int i = 0; i <= segments; i++) {
            float a = (float) i / segments * (float) Math.PI * 2f;
            float x = cx + (float) Math.cos(a) * radius;
            float z = cz + (float) Math.sin(a) * radius;
            v.add(x); v.add(y0 + height); v.add(z); v.add(0); v.add(1); v.add(0);
            v.add(((float) Math.cos(a) + 1f) / 2f); v.add(((float) Math.sin(a) + 1f) / 2f);
        }
        for (int i = 0; i < segments; i++) {
            idx.add((short) centerTop);
            idx.add((short) (centerTop + 1 + i + 1));
            idx.add((short) (centerTop + 1 + i));
        }
        int centerBot = v.size / 8;
        v.add(cx); v.add(y0); v.add(cz); v.add(0); v.add(-1); v.add(0); v.add(0.5f); v.add(0.5f);
        for (int i = 0; i <= segments; i++) {
            float a = (float) i / segments * (float) Math.PI * 2f;
            float x = cx + (float) Math.cos(a) * radius;
            float z = cz + (float) Math.sin(a) * radius;
            v.add(x); v.add(y0); v.add(z); v.add(0); v.add(-1); v.add(0);
            v.add(((float) Math.cos(a) + 1f) / 2f); v.add(((float) Math.sin(a) + 1f) / 2f);
        }
        for (int i = 0; i < segments; i++) {
            idx.add((short) centerBot);
            idx.add((short) (centerBot + 1 + i));
            idx.add((short) (centerBot + 1 + i + 1));
        }
        part.addMesh(v.toArray(), idx.toArray());
    }

    // ================================================================ props

    private static int addPropPart(ModelBuilder modelBuilder, int index, Model primitive,
                                  float x, float y, float z, float rx,
                                  float sx, float sy, float sz) {
        Node node = modelBuilder.node("propPart" + index, primitive);
        node.translation.set(x, y, z);
        node.scale.set(sx, sy, sz);
        if (rx != 0f) node.rotation.set(Vector3.X, rx);
        return index + 1;
    }

    /** Small standalone prop models. */
    public static Model buildProp(String type, ObjectMap<String, Texture> textures) {
        Texture metal = textures.get("metal_dark");
        Texture metalL = textures.get("metal");
        Texture concrete = textures.get("concrete_dark");
        ModelBuilder modelBuilder = new ModelBuilder();
        ModelBuilder primitiveBuilder = new ModelBuilder();
        modelBuilder.begin();
        int partIndex = 0;
        if (type.equals("barrel")) {
            partIndex = addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(0.9f, 1.05f, 0.9f, 16,
                            new Material(TextureAttribute.createDiffuse(metalL)), ATTRIBS),
                    0, 0.525f, 0, 0, 1, 1, 1);
            partIndex = addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(0.82f, 0.14f, 0.82f, 16,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 0.98f, 0, 0, 1, 1, 1);
            addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(0.9f, 0.14f, 0.9f, 16,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 0.12f, 0, 0, 1, 1, 1);
        } else if (type.equals("lamp")) {
            partIndex = addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(0.14f, 4.6f, 0.14f, 10,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 2.3f, 0, 0, 1, 1, 1);
            addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(0.1f, 1.1f, 0.1f, 8,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 3.6f, 0.55f, 90, 1, 1, 1);
        } else if (type.equals("antenna")) {
            for (int i = 0; i < 3; i++) {
                float taper = 1f - i * 0.3f;
                partIndex = addPropPart(modelBuilder, partIndex,
                        primitiveBuilder.createCylinder(0.16f, 3.4f, 0.16f, 8,
                                new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                        0, 1.2f + i * 3.4f, 0, 0, taper, 1, taper);
            }
            addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createBox(3.4f, 0.1f, 0.1f,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 6.2f, 0, 0, 1, 1, 1);
        } else if (type.equals("tank")) {
            partIndex = addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(2.6f, 3.2f, 2.6f, 24,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 1.6f, 0, 0, 1, 1, 1);
            addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(1.0f, 3.3f, 1.0f, 16,
                            new Material(TextureAttribute.createDiffuse(concrete)), ATTRIBS),
                    0, 3.2f, 0, 0, 1, 1, 1);
        } else { // pipe
            addPropPart(modelBuilder, partIndex,
                    primitiveBuilder.createCylinder(0.5f, 7f, 0.5f, 12,
                            new Material(TextureAttribute.createDiffuse(metal)), ATTRIBS),
                    0, 0, 0, 0, 1, 1, 1);
        }
        return modelBuilder.end();
    }

    public static Array<ModelInstance> buildProps(MapDef map, ObjectMap<String, Texture> textures) {
        Array<ModelInstance> out = new Array<>();
        ObjectMap<String, Model> cache = new ObjectMap<String, Model>();
        for (MapDef.Prop p : map.props) {
            Model model = cache.get(p.type);
            if (model == null) {
                model = buildProp(p.type, textures);
                cache.put(p.type, model);
            }
            ModelInstance inst = new ModelInstance(model);
            inst.transform.setToTranslationAndScaling(p.x, p.y, p.z, p.scale, p.scale, p.scale);
            inst.transform.rotate(Vector3.Y, p.rot);
            out.add(inst);
        }
        return out;
    }
}
