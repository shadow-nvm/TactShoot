package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Builds the entire Scene2D skin from runtime-generated Pixmaps. */
public final class UiSkin {

    public static Skin build(BitmapFont font, BitmapFont fontSmall) {
        if (font == null) font = new BitmapFont();
        if (fontSmall == null) fontSmall = new BitmapFont();
        Skin skin = new Skin();

        // ---------- drawables ----------
        Pixmap panel = rounded(64, 64, 26, new Color(0.06f, 0.075f, 0.11f, 0.93f),
                new Color(0.16f, 0.20f, 0.27f, 1f), 2);
        Pixmap panelSolid = rounded(64, 64, 26, new Color(0.07f, 0.085f, 0.125f, 1f),
                new Color(0.22f, 0.27f, 0.36f, 1f), 2);

        Pixmap btnUp = rounded(64, 64, 22, new Color(0.13f, 0.16f, 0.22f, 1f),
                new Color(0.30f, 0.37f, 0.48f, 1f), 2);
        Pixmap btnOver = rounded(64, 64, 22, new Color(0.20f, 0.25f, 0.33f, 1f),
                new Color(0.45f, 0.53f, 0.66f, 1f), 2);
        Pixmap btnDown = rounded(64, 64, 22, new Color(0.08f, 0.10f, 0.14f, 1f),
                new Color(0.22f, 0.27f, 0.36f, 1f), 2);
        Pixmap btnAccentUp = rounded(64, 64, 22, new Color(0.85f, 0.47f, 0.08f, 1f),
                new Color(1.0f, 0.70f, 0.25f, 1f), 2);
        Pixmap btnAccentOver = rounded(64, 64, 22, new Color(1.0f, 0.55f, 0.10f, 1f),
                new Color(1.0f, 0.82f, 0.40f, 1f), 2);
        Pixmap btnAccentDown = rounded(64, 64, 22, new Color(0.62f, 0.33f, 0.05f, 1f),
                new Color(0.85f, 0.47f, 0.08f, 1f), 2);

        Pixmap field = rounded(64, 64, 16, new Color(0.045f, 0.055f, 0.08f, 1f),
                new Color(0.30f, 0.36f, 0.47f, 1f), 2);

        Pixmap sliderBg = rounded(64, 64, 16, new Color(0.06f, 0.07f, 0.10f, 1f),
                new Color(0.25f, 0.30f, 0.40f, 1f), 2);
        Pixmap sliderKnob = circle(48, 48, 24, new Color(0.9f, 0.95f, 1f, 1f),
                new Color(0.4f, 0.46f, 0.58f, 1f));

        Pixmap checkOn = rounded(48, 48, 14, new Color(0.85f, 0.47f, 0.08f, 1f),
                new Color(1.0f, 0.72f, 0.28f, 1f), 2);
        Pixmap checkOff = rounded(48, 48, 14, new Color(0.09f, 0.11f, 0.16f, 1f),
                new Color(0.30f, 0.36f, 0.47f, 1f), 2);

        Drawable panelD = np(panel);
        Drawable panelSolidD = np(panelSolid);
        skin.add("panel", panelD);
        skin.add("panelSolid", panelSolidD);
        Drawable btnUpD = np(btnUp);
        Drawable btnOverD = np(btnOver);
        Drawable btnDownD = np(btnDown);
        Drawable accentUpD = np(btnAccentUp);
        Drawable accentOverD = np(btnAccentOver);
        Drawable accentDownD = np(btnAccentDown);
        Drawable fieldD = np(field);
        Drawable sliderBgD = np(sliderBg);
        Drawable knobD = new TextureRegionDrawable(new TextureRegion(new Texture(sliderKnob)));
        Drawable checkOnD = new TextureRegionDrawable(new TextureRegion(new Texture(checkOn)));
        Drawable checkOffD = new TextureRegionDrawable(new TextureRegion(new Texture(checkOff)));

        // ---------- styles ----------
        TextButton.TextButtonStyle btn = new TextButton.TextButtonStyle();
        btn.font = font;
        btn.fontColor = Palette.TEXT;
        btn.up = btnUpD;
        btn.over = btnOverD;
        btn.down = btnDownD;
        skin.add("default", btn);

        TextButton.TextButtonStyle btnAccent = new TextButton.TextButtonStyle();
        btnAccent.font = font;
        btnAccent.fontColor = Palette.WHITE;
        btnAccent.up = accentUpD;
        btnAccent.over = accentOverD;
        btnAccent.down = accentDownD;
        skin.add("accent", btnAccent);

        Label.LabelStyle label = new Label.LabelStyle();
        label.font = font;
        label.fontColor = Palette.TEXT;
        skin.add("default", label);

        Label.LabelStyle labelDim = new Label.LabelStyle();
        labelDim.font = font;
        labelDim.fontColor = Palette.TEXT_DIM;
        skin.add("dim", labelDim);

        Label.LabelStyle labelSmall = new Label.LabelStyle();
        labelSmall.font = fontSmall;
        labelSmall.fontColor = Palette.TEXT_DIM;
        skin.add("small", labelSmall);

        Label.LabelStyle labelAccent = new Label.LabelStyle();
        labelAccent.font = font;
        labelAccent.fontColor = Palette.ACCENT;
        skin.add("accent", labelAccent);

        TextField.TextFieldStyle fieldStyle = new TextField.TextFieldStyle();
        fieldStyle.font = font;
        fieldStyle.fontColor = Palette.TEXT;
        fieldStyle.background = fieldD;
        fieldStyle.cursor = new TextureRegionDrawable(new TextureRegion(new Texture(
                solid(2, 32, Palette.ACCENT))));
        fieldStyle.selection = new TextureRegionDrawable(new TextureRegion(new Texture(
                solid(2, 32, new Color(0.85f, 0.47f, 0.08f, 0.5f)))));
        skin.add("default", fieldStyle);

        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = sliderBgD;
        sliderStyle.knob = knobD;
        skin.add("default-horizontal", sliderStyle);

        CheckBox.CheckBoxStyle cbStyle = new CheckBox.CheckBoxStyle();
        cbStyle.font = font;
        cbStyle.fontColor = Palette.TEXT;
        cbStyle.checkboxOn = checkOnD;
        cbStyle.checkboxOff = checkOffD;
        skin.add("default", cbStyle);

        ScrollPane.ScrollPaneStyle spStyle = new ScrollPane.ScrollPaneStyle();
        skin.add("default", spStyle);

        // dispose the raw pixmaps (drawables own textures now)
        dispose(panel, panelSolid, btnUp, btnOver, btnDown, btnAccentUp, btnAccentOver,
                btnAccentDown, field, sliderBg, sliderKnob, checkOn, checkOff);
        return skin;
    }

    private static NinePatchDrawable np(Pixmap p) {
        Texture t = new Texture(p);
        NinePatch patch = new NinePatch(new TextureRegion(t), 24, 24, 24, 24);
        return new NinePatchDrawable(patch);
    }

    private static Pixmap solid(int w, int h, Color c) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setColor(c);
        p.fill();
        return p;
    }

    private static Pixmap rounded(int w, int h, int r, Color fill, Color border, int borderW) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.SourceOver);
        p.setColor(fill);
        fillRoundedRect(p, 0, 0, w, h, r);
        if (borderW > 0 && border.a > 0) {
            p.setColor(border);
            strokeRoundedRect(p, 0, 0, w - 1, h - 1, r, borderW);
        }
        return p;
    }

    private static Pixmap circle(int w, int h, int r, Color fill, Color border) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.SourceOver);
        p.setColor(fill);
        p.fillCircle(w / 2, h / 2, r);
        p.setColor(border);
        p.drawCircle(w / 2, h / 2, r);
        return p;
    }

    private static void fillRoundedRect(Pixmap p, int x, int y, int w, int h, int r) {
        p.fillRectangle(x + r, y, w - 2 * r, h);
        p.fillRectangle(x, y + r, w, h - 2 * r);
        p.fillCircle(x + r, y + r, r);
        p.fillCircle(x + w - r - 1, y + r, r);
        p.fillCircle(x + r, y + h - r - 1, r);
        p.fillCircle(x + w - r - 1, y + h - r - 1, r);
    }

    private static void strokeRoundedRect(Pixmap p, int x, int y, int w, int h, int r, int t) {
        // crude stroke: draw border rects
        p.fillRectangle(x, y, w, t);
        p.fillRectangle(x, y + h - t, w, t);
        p.fillRectangle(x, y, t, h);
        p.fillRectangle(x + w - t, y, t, h);
    }

    private static void dispose(Pixmap... maps) {
        for (Pixmap m : maps) {
            if (m != null) m.dispose();
        }
    }
}
