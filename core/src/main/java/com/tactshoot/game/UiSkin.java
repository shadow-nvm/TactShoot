package com.tactshoot.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** Builds the entire Scene2D skin safely with lazy OpenGL texture creation. */
public final class UiSkin {

    public static class LazyDrawable extends BaseDrawable implements Disposable {
        private final Color fillColor;
        private final Color borderColor;
        private Texture texture;

        public LazyDrawable(Color fillColor, Color borderColor) {
            this(fillColor, borderColor, 48, 48);
        }

        public LazyDrawable(Color fillColor, Color borderColor, float minWidth, float minHeight) {
            this.fillColor = new Color(fillColor);
            this.borderColor = borderColor == null ? null : new Color(borderColor);
            setMinWidth(minWidth);
            setMinHeight(minHeight);
        }

        @Override
        public void draw(Batch batch, float x, float y, float width, float height) {
            if (texture == null) {
                Pixmap pixmap = null;
                try {
                    int w = 64;
                    int h = 64;
                    pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
                    pixmap.setColor(fillColor);
                    pixmap.fillRectangle(0, 0, w, h);
                    if (borderColor != null) {
                        pixmap.setColor(borderColor);
                        pixmap.drawRectangle(0, 0, w - 1, h - 1);
                    }
                    texture = new Texture(pixmap);
                } catch (Throwable t) {
                    return;
                } finally {
                    if (pixmap != null) pixmap.dispose();
                }
            }
            batch.draw(texture, x, y, width, height);
        }

        @Override
        public void dispose() {
            if (texture != null) {
                texture.dispose();
                texture = null;
            }
        }
    }

    public static Skin build(BitmapFont font, BitmapFont fontSmall) {
        if (font == null) font = new BitmapFont();
        if (fontSmall == null) fontSmall = new BitmapFont();
        Skin skin = new Skin();

        Drawable panelD = new LazyDrawable(new Color(0.06f, 0.075f, 0.11f, 0.93f), new Color(0.16f, 0.20f, 0.27f, 1f));
        Drawable panelSolidD = new LazyDrawable(new Color(0.07f, 0.085f, 0.125f, 1f), new Color(0.22f, 0.27f, 0.36f, 1f));
        addDrawable(skin, "panel", panelD);
        addDrawable(skin, "panelSolid", panelSolidD);

        Drawable btnUpD = new LazyDrawable(new Color(0.13f, 0.16f, 0.22f, 1f), new Color(0.30f, 0.37f, 0.48f, 1f));
        Drawable btnOverD = new LazyDrawable(new Color(0.20f, 0.25f, 0.33f, 1f), new Color(0.45f, 0.53f, 0.66f, 1f));
        Drawable btnDownD = new LazyDrawable(new Color(0.08f, 0.10f, 0.14f, 1f), new Color(0.22f, 0.27f, 0.36f, 1f));
        Drawable accentUpD = new LazyDrawable(new Color(0.85f, 0.47f, 0.08f, 1f), new Color(1.0f, 0.70f, 0.25f, 1f));
        Drawable accentOverD = new LazyDrawable(new Color(1.0f, 0.55f, 0.10f, 1f), new Color(1.0f, 0.82f, 0.40f, 1f));
        Drawable accentDownD = new LazyDrawable(new Color(0.62f, 0.33f, 0.05f, 1f), new Color(0.85f, 0.47f, 0.08f, 1f));
        Drawable fieldD = new LazyDrawable(new Color(0.045f, 0.055f, 0.08f, 1f), new Color(0.30f, 0.36f, 0.47f, 1f));
        Drawable sliderBgD = new LazyDrawable(new Color(0.06f, 0.07f, 0.10f, 1f), new Color(0.25f, 0.30f, 0.40f, 1f));
        Drawable knobD = new LazyDrawable(new Color(0.9f, 0.95f, 1f, 1f), new Color(0.4f, 0.46f, 0.58f, 1f));
        Drawable checkOnD = new LazyDrawable(new Color(0.85f, 0.47f, 0.08f, 1f), new Color(1.0f, 0.72f, 0.28f, 1f));
        Drawable checkOffD = new LazyDrawable(new Color(0.09f, 0.11f, 0.16f, 1f), new Color(0.30f, 0.36f, 0.47f, 1f));
        Drawable cursorD = new LazyDrawable(Palette.ACCENT, null, 2, 32);
        Drawable selectionD = new LazyDrawable(new Color(0.85f, 0.47f, 0.08f, 0.5f), null, 1, 1);
        addDrawable(skin, "buttonUp", btnUpD);
        addDrawable(skin, "buttonOver", btnOverD);
        addDrawable(skin, "buttonDown", btnDownD);
        addDrawable(skin, "accentUp", accentUpD);
        addDrawable(skin, "accentOver", accentOverD);
        addDrawable(skin, "accentDown", accentDownD);
        addDrawable(skin, "field", fieldD);
        addDrawable(skin, "sliderBackground", sliderBgD);
        addDrawable(skin, "sliderKnob", knobD);
        addDrawable(skin, "checkOn", checkOnD);
        addDrawable(skin, "checkOff", checkOffD);
        addDrawable(skin, "fieldCursor", cursorD);
        addDrawable(skin, "fieldSelection", selectionD);

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
        fieldStyle.cursor = cursorD;
        fieldStyle.selection = selectionD;
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

        if (!skin.has("panel", Drawable.class) || !skin.has("panelSolid", Drawable.class)) {
            throw new IllegalStateException("UI skin did not register its panel drawables");
        }

        return skin;
    }

    private static void addDrawable(Skin skin, String name, Drawable drawable) {
        // Skin.add(name, object) records the concrete runtime class. getDrawable()
        // queries Drawable.class, so custom BaseDrawable subclasses must be
        // registered using the Drawable type explicitly.
        skin.add(name, drawable, Drawable.class);
    }
}
