package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
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

    private static Model box(ModelBuilder mb, Material m, float w, float h, float d) {
        return mb.createBox(w, h, d, m, ATTRIBS);
    }

    private static Model cyl(ModelBuilder mb, Material m, float w, float h, float d, int seg) {
        return mb.createCylinder(w, h, d, seg, m, ATTRIBS);
    }

    private static void at(Model m, float x, float y, float z, float rx) {
        m.nodes.first().translation.set(x, y, z);
        if (rx != 0f) m.nodes.first().rotation.set(Vector3.X, rx);
    }

    private static void add(ModelBuilder mb, Model prim, float x, float y, float z, float rx) {
        at(prim, x, y, z, rx);
        mb.node().addChild(prim.nodes.first());
    }

    // ================================================================ guns

    public static Model gunAssault(ModelBuilder mb, ObjectMap<String, Texture> tex) {
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        Material accent = colorMat(Palette.ACCENT);
        add(mb, box(mb, metal, 0.085f, 0.10f, 0.42f), 0, 0.02f, -0.28f, 0);            // receiver
        add(mb, cyl(mb, metal, 0.034f, 0.30f, 0.034f, 12), 0, 0.02f, -0.62f, 0);       // barrel
        add(mb, box(mb, dark, 0.07f, 0.075f, 0.26f), 0, 0.005f, -0.40f, 0);           // handguard
        add(mb, cyl(mb, metal, 0.056f, 0.06f, 0.056f, 12), 0, 0.02f, -0.78f, 0);      // muzzle brake
        add(mb, box(mb, dark, 0.06f, 0.20f, 0.09f), 0, -0.13f, -0.26f, 0);            // magazine
        add(mb, box(mb, dark, 0.07f, 0.105f, 0.24f), 0, 0.0f, 0.18f, 0);              // stock
        add(mb, box(mb, dark, 0.055f, 0.15f, 0.07f), 0, -0.10f, -0.03f, 12);          // grip
        add(mb, box(mb, dark, 0.014f, 0.05f, 0.03f), 0, 0.095f, -0.50f, 0);           // front sight
        add(mb, box(mb, dark, 0.03f, 0.045f, 0.02f), 0, 0.098f, -0.12f, 0);           // rear sight
        add(mb, cyl(mb, metal, 0.044f, 0.05f, 0.044f, 14), 0, 0.115f, -0.22f, 0);     // red dot
        add(mb, box(mb, accent, 0.087f, 0.02f, 0.30f), 0, 0.058f, -0.28f, 0);         // accent stripe
        add(mb, box(mb, dark, 0.02f, 0.03f, 0.06f), 0, -0.03f, 0.02f, 0);             // trigger guard front
        return mb.end();
    }

    public static Model gunSmg(ModelBuilder mb, ObjectMap<String, Texture> tex) {
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        Material accent = colorMat(Palette.TEAL);
        add(mb, box(mb, metal, 0.08f, 0.095f, 0.30f), 0, 0.02f, -0.22f, 0);            // receiver
        add(mb, cyl(mb, metal, 0.03f, 0.16f, 0.03f, 12), 0, 0.02f, -0.42f, 0);        // barrel
        add(mb, cyl(mb, dark, 0.062f, 0.10f, 0.062f, 14), 0, 0.02f, -0.52f, 0);       // suppressor
        add(mb, box(mb, dark, 0.055f, 0.19f, 0.075f), 0, -0.125f, -0.26f, 6);         // magazine
        add(mb, box(mb, dark, 0.06f, 0.09f, 0.16f), 0, 0.0f, 0.16f, 0);               // stock
        add(mb, box(mb, dark, 0.05f, 0.14f, 0.065f), 0, -0.095f, -0.03f, 10);         // grip
        add(mb, box(mb, accent, 0.082f, 0.018f, 0.22f), 0, 0.055f, -0.22f, 0);        // stripe
        add(mb, box(mb, dark, 0.012f, 0.04f, 0.03f), 0, 0.09f, -0.34f, 0);            // sights
        return mb.end();
    }

    public static Model gunShotgun(ModelBuilder mb, ObjectMap<String, Texture> tex) {
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        Material wood = mat(tex, "wood_dark");
        add(mb, cyl(mb, metal, 0.048f, 0.55f, 0.048f, 12), 0, 0.02f, -0.55f, 0);      // barrel
        add(mb, cyl(mb, dark, 0.044f, 0.42f, 0.044f, 12), 0, -0.035f, -0.45f, 0);     // tube
        add(mb, box(mb, metal, 0.08f, 0.10f, 0.30f), 0, 0.01f, -0.18f, 0);            // receiver
        add(mb, box(mb, wood, 0.075f, 0.08f, 0.24f), 0, -0.04f, -0.42f, 0);           // pump
        add(mb, box(mb, wood, 0.07f, 0.11f, 0.26f), 0, 0.005f, 0.20f, 0);             // stock
        add(mb, box(mb, dark, 0.05f, 0.14f, 0.065f), 0, -0.095f, -0.02f, 10);         // grip
        add(mb, box(mb, dark, 0.012f, 0.035f, 0.02f), 0, 0.075f, -0.72f, 0);          // bead
        add(mb, box(mb, dark, 0.03f, 0.03f, 0.015f), 0, 0.075f, -0.10f, 0);           // rear
        return mb.end();
    }

    public static Model gunPistol(ModelBuilder mb, ObjectMap<String, Texture> tex) {
        Material metal = mat(tex, "metal2");
        Material dark = mat(tex, "metal_dark");
        add(mb, box(mb, metal, 0.062f, 0.055f, 0.24f), 0, 0.075f, -0.16f, 0);         // slide
        add(mb, box(mb, dark, 0.055f, 0.09f, 0.18f), 0, 0.02f, -0.13f, 0);            // frame
        add(mb, cyl(mb, metal, 0.028f, 0.03f, 0.028f, 10), 0, 0.075f, -0.28f, 0);     // muzzle
        add(mb, box(mb, dark, 0.05f, 0.13f, 0.065f), 0, -0.065f, -0.05f, 15);         // grip
        add(mb, box(mb, dark, 0.012f, 0.025f, 0.012f), 0, 0.112f, -0.26f, 0);         // front sight
        add(mb, box(mb, dark, 0.022f, 0.025f, 0.012f), 0, 0.114f, -0.09f, 0);         // rear sight
        add(mb, box(mb, dark, 0.006f, 0.035f, 0.05f), 0, -0.02f, -0.10f, 0);          // trigger guard
        return mb.end();
    }

    // ================================================================ soldier

    /** Builds a soldier model. Team-tintable material indices: TORSO_IDX and HELMET_IDX. */
    public static final int TORSO_IDX = 1;
    public static final int HELMET_IDX = 7;

    public static Model soldier(ModelBuilder mb, ObjectMap<String, Texture> tex) {
        Material dark = mat(tex, "metal_dark");
        Material cloth = colorMat(Color.WHITE);
        Material cloth2 = colorMat(Color.WHITE);
        Material skin = colorMat(new Color(0.80f, 0.66f, 0.52f, 1f));
        add(mb, box(mb, dark, 0.17f, 0.55f, 0.19f), -0.10f, 0.275f, 0, 0);            // leg L
        add(mb, box(mb, cloth, 0.46f, 0.52f, 0.28f), 0, 0.82f, 0, 0);                 // torso (TINT)
        add(mb, box(mb, dark, 0.47f, 0.08f, 0.29f), 0, 0.62f, 0, 0);                  // belt
        add(mb, box(mb, dark, 0.13f, 0.42f, 0.13f), -0.30f, 0.78f, 0.22f, 70);        // arm L
        add(mb, box(mb, dark, 0.13f, 0.42f, 0.13f), 0.30f, 0.78f, 0.22f, 70);         // arm R
        add(mb, box(mb, dark, 0.09f, 0.16f, 0.55f), 0.13f, 0.80f, 0.34f, 0);          // gun
        add(mb, cyl(mb, skin, 0.32f, 0.32f, 0.32f, 14), 0, 1.30f, 0, 0);              // head
        add(mb, cyl(mb, cloth2, 0.37f, 0.20f, 0.37f, 14), 0, 1.335f, 0, 0);           // helmet (TINT)
        add(mb, box(mb, dark, 0.34f, 0.36f, 0.14f), 0, 0.85f, -0.20f, 0);             // backpack
        add(mb, box(mb, dark, 0.17f, 0.55f, 0.19f), 0.10f, 0.275f, 0, 0);             // leg R
        return mb.end();
    }
}
