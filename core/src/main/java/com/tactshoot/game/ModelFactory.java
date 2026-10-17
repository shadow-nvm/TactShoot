package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Procedural first-person weapon models and third-person soldier bodies.
 * Gun space: origin at the grip, -Z toward the muzzle, +Y up.
 */
public final class ModelFactory {

    private static final long ATTRIBS = VertexAttributes.Usage.Position
            | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;

    private ModelFactory() {
    }

    private static Material mat(ObjectMap<String, Texture> tex, String name) {
        return new Material(TextureAttribute.createDiffuse(tex.get(name)));
    }

    private static Material colorMat(Color c) {
        return new Material(ColorAttribute.createDiffuse(c));
    }

    /**
     * Builds each convenience primitive with its own completed builder, then transfers its
     * mesh and node into a single parent model. LibGDX's createBox/createCylinder methods
     * call begin/end internally, so they cannot run on the builder currently assembling the
     * parent model.
     */
    private static final class Assembly {
        private final ModelBuilder modelBuilder = new ModelBuilder();
        private final ModelBuilder primitiveBuilder = new ModelBuilder();
        private int nextNodeId;

        Assembly() {
            modelBuilder.begin();
        }

        void box(Material material, float w, float h, float d,
                 float x, float y, float z, float rx) {
            add(primitiveBuilder.createBox(w, h, d, material, ATTRIBS), x, y, z, rx);
        }

        void cylinder(Material material, float w, float h, float d, int segments,
                      float x, float y, float z, float rx) {
            add(primitiveBuilder.createCylinder(w, h, d, segments, material, ATTRIBS), x, y, z, rx);
        }

        private void add(Model primitive, float x, float y, float z, float rx) {
            Node node = modelBuilder.node("part" + nextNodeId++, primitive);
            node.translation.set(x, y, z);
            if (rx != 0f) node.rotation.set(Vector3.X, rx);
        }

        Model finish() {
            return modelBuilder.end();
        }
    }

    // ================================================================ guns

    public static Model gunAssault(ObjectMap<String, Texture> tex) {
        Assembly parts = new Assembly();
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        Material accent = colorMat(Palette.ACCENT);
        parts.box(metal, 0.085f, 0.10f, 0.42f, 0, 0.02f, -0.28f, 0);             // receiver
        parts.cylinder(metal, 0.034f, 0.30f, 0.034f, 12, 0, 0.02f, -0.62f, 0);   // barrel
        parts.box(dark, 0.07f, 0.075f, 0.26f, 0, 0.005f, -0.40f, 0);             // handguard
        parts.cylinder(metal, 0.056f, 0.06f, 0.056f, 12, 0, 0.02f, -0.78f, 0);  // muzzle brake
        parts.box(dark, 0.06f, 0.20f, 0.09f, 0, -0.13f, -0.26f, 0);             // magazine
        parts.box(dark, 0.07f, 0.105f, 0.24f, 0, 0.0f, 0.18f, 0);                // stock
        parts.box(dark, 0.055f, 0.15f, 0.07f, 0, -0.10f, -0.03f, 12);            // grip
        parts.box(dark, 0.014f, 0.05f, 0.03f, 0, 0.095f, -0.50f, 0);             // front sight
        parts.box(dark, 0.03f, 0.045f, 0.02f, 0, 0.098f, -0.12f, 0);             // rear sight
        parts.cylinder(metal, 0.044f, 0.05f, 0.044f, 14, 0, 0.115f, -0.22f, 0);  // red dot
        parts.box(accent, 0.087f, 0.02f, 0.30f, 0, 0.058f, -0.28f, 0);           // accent stripe
        parts.box(dark, 0.02f, 0.03f, 0.06f, 0, -0.03f, 0.02f, 0);               // trigger guard front
        return parts.finish();
    }

    public static Model gunSmg(ObjectMap<String, Texture> tex) {
        Assembly parts = new Assembly();
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        Material accent = colorMat(Palette.TEAL);
        parts.box(metal, 0.08f, 0.095f, 0.30f, 0, 0.02f, -0.22f, 0);            // receiver
        parts.cylinder(metal, 0.03f, 0.16f, 0.03f, 12, 0, 0.02f, -0.42f, 0);    // barrel
        parts.cylinder(dark, 0.062f, 0.10f, 0.062f, 14, 0, 0.02f, -0.52f, 0);  // suppressor
        parts.box(dark, 0.055f, 0.19f, 0.075f, 0, -0.125f, -0.26f, 6);         // magazine
        parts.box(dark, 0.06f, 0.09f, 0.16f, 0, 0.0f, 0.16f, 0);               // stock
        parts.box(dark, 0.05f, 0.14f, 0.065f, 0, -0.095f, -0.03f, 10);         // grip
        parts.box(accent, 0.082f, 0.018f, 0.22f, 0, 0.055f, -0.22f, 0);         // stripe
        parts.box(dark, 0.012f, 0.04f, 0.03f, 0, 0.09f, -0.34f, 0);             // sights
        return parts.finish();
    }

    public static Model gunShotgun(ObjectMap<String, Texture> tex) {
        Assembly parts = new Assembly();
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        Material wood = mat(tex, "wood_dark");
        parts.cylinder(metal, 0.048f, 0.55f, 0.048f, 12, 0, 0.02f, -0.55f, 0);  // barrel
        parts.cylinder(dark, 0.044f, 0.42f, 0.044f, 12, 0, -0.035f, -0.45f, 0); // tube
        parts.box(metal, 0.08f, 0.10f, 0.30f, 0, 0.01f, -0.18f, 0);             // receiver
        parts.box(wood, 0.075f, 0.08f, 0.24f, 0, -0.04f, -0.42f, 0);            // pump
        parts.box(wood, 0.07f, 0.11f, 0.26f, 0, 0.005f, 0.20f, 0);               // stock
        parts.box(dark, 0.05f, 0.14f, 0.065f, 0, -0.095f, -0.02f, 10);           // grip
        parts.box(dark, 0.012f, 0.035f, 0.02f, 0, 0.075f, -0.72f, 0);            // bead
        parts.box(dark, 0.03f, 0.03f, 0.015f, 0, 0.075f, -0.10f, 0);             // rear
        return parts.finish();
    }

    public static Model gunPistol(ObjectMap<String, Texture> tex) {
        Assembly parts = new Assembly();
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        parts.box(metal, 0.062f, 0.055f, 0.24f, 0, 0.075f, -0.16f, 0);           // slide
        parts.box(dark, 0.055f, 0.09f, 0.18f, 0, 0.02f, -0.13f, 0);              // frame
        parts.cylinder(metal, 0.028f, 0.03f, 0.028f, 10, 0, 0.075f, -0.28f, 0); // muzzle
        parts.box(dark, 0.05f, 0.13f, 0.065f, 0, -0.065f, -0.05f, 15);          // grip
        parts.box(dark, 0.012f, 0.025f, 0.012f, 0, 0.112f, -0.26f, 0);          // front sight
        parts.box(dark, 0.022f, 0.025f, 0.012f, 0, 0.114f, -0.09f, 0);          // rear sight
        parts.box(dark, 0.006f, 0.035f, 0.05f, 0, -0.02f, -0.10f, 0);           // trigger guard
        return parts.finish();
    }

    // ================================================================ grenade

    /** Small frag grenade thrown by the player. Origin at its centre. */
    public static Model grenade(ObjectMap<String, Texture> tex) {
        Assembly parts = new Assembly();
        Material metal = mat(tex, "metal_dark");
        Material accent = colorMat(Palette.ACCENT);
        parts.cylinder(metal, 0.10f, 0.16f, 0.10f, 12, 0, 0f, 0, 0);
        parts.cylinder(accent, 0.045f, 0.045f, 0.045f, 10, 0, 0.08f, 0, 0);
        parts.box(metal, 0.02f, 0.035f, 0.035f, 0.045f, 0.10f, 0, 0);
        return parts.finish();
    }

    // ================================================================ soldier

    /** Builds a soldier model. Team-tintable material indices: TORSO_IDX and HELMET_IDX. */
    public static final int TORSO_IDX = 1;
    public static final int HELMET_IDX = 3;

    public static Model soldier(ObjectMap<String, Texture> tex) {
        Assembly parts = new Assembly();
        Material dark = mat(tex, "metal_dark");
        Material cloth = colorMat(Color.WHITE);
        Material cloth2 = colorMat(Color.WHITE);
        Material skin = colorMat(new Color(0.80f, 0.66f, 0.52f, 1f));
        parts.box(dark, 0.17f, 0.55f, 0.19f, -0.10f, 0.275f, 0, 0);             // leg L
        parts.box(cloth, 0.46f, 0.52f, 0.28f, 0, 0.82f, 0, 0);                   // torso (TINT)
        parts.box(dark, 0.47f, 0.08f, 0.29f, 0, 0.62f, 0, 0);                   // belt
        parts.box(dark, 0.13f, 0.42f, 0.13f, -0.30f, 0.78f, 0.22f, 70);         // arm L
        parts.box(dark, 0.13f, 0.42f, 0.13f, 0.30f, 0.78f, 0.22f, 70);          // arm R
        parts.box(dark, 0.09f, 0.16f, 0.55f, 0.13f, 0.80f, 0.34f, 0);           // gun
        parts.cylinder(skin, 0.32f, 0.32f, 0.32f, 14, 0, 1.30f, 0, 0);          // head
        parts.cylinder(cloth2, 0.37f, 0.20f, 0.37f, 14, 0, 1.335f, 0, 0);       // helmet (TINT)
        parts.box(dark, 0.34f, 0.36f, 0.14f, 0, 0.85f, -0.20f, 0);              // backpack
        parts.box(dark, 0.17f, 0.55f, 0.19f, 0.10f, 0.275f, 0, 0);              // leg R
        return parts.finish();
    }
}
