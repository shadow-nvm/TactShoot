package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Settings screen: name, sensitivity, quality, FOV, sound and server. */
public class SettingsScreen extends ScreenAdapter {

    private final TactShootGame game;
    private final Assets assets;
    private final Settings settings;
    private final Screen returnScreen;
    private boolean returningToMatch;

    private Stage stage;
    private TextField nameField, serverField;
    private Label sensLabel, adsLabel, fovLabel, qualityLabel;
    private CheckBox soundBox, autoFireBox, leftBox, invertBox, aimToggleBox,
            autoSprintBox, autoQualityBox, fpsBox;
    private float sens, adsSens, fov;
    private int quality;

    public SettingsScreen(TactShootGame game) {
        this(game, null);
    }

    public SettingsScreen(TactShootGame game, Screen returnScreen) {
        this.game = game;
        this.assets = game.assets;
        this.settings = game.settings;
        this.returnScreen = returnScreen;
        sens = settings.sensitivity;
        adsSens = settings.adsSensitivity;
        fov = settings.fov;
        quality = settings.quality;
        stage = new Stage(new ScreenViewport());
        buildUi();
    }

    private void buildUi() {
        float us = assets.uiScale;
        Table root = new Table();
        root.setFillParent(true);
        Table panel = new Table();
        panel.setBackground(assets.skin.getDrawable("panel"));
        panel.pad(28 * us);

        Label title = new Label("SETTINGS", assets.skin, "default");
        title.setFontScale(1.8f);
        panel.add(title).colspan(2).padBottom(12).row();

        Label nameL = new Label("CALLSIGN", assets.skin, "small");
        nameField = new TextField(settings.playerName, assets.skin);
        nameField.setTextFieldFilter((field, c) -> (Character.isLetterOrDigit(c) || c == ' '
                || c == '_' || c == '-' || c == '.') && field.getText().length() < 16);
        panel.add(nameL).right().padRight(14).pad(6);
        panel.add(nameField).width(340 * us).height(52 * us).pad(6).row();

        sensLabel = new Label("SENSITIVITY " + String.format("%.1f", sens), assets.skin, "small");
        panel.add(sensLabel).right().padRight(14).pad(4);
        Slider sensSlider = new Slider(0.3f, 3.0f, 0.1f, false, assets.skin);
        sensSlider.setValue(sens);
        sensSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                sens = Math.round(sensSlider.getValue() * 10f) / 10f;
                sensLabel.setText("SENSITIVITY " + String.format("%.1f", sens));
            }
        });
        panel.add(sensSlider).width(340 * us).height(40 * us).pad(4).row();

        fovLabel = new Label("FIELD OF VIEW " + fov, assets.skin, "small");
        panel.add(fovLabel).right().padRight(14).pad(4);
        Slider fovSlider = new Slider(65f, 105f, 1f, false, assets.skin);
        fovSlider.setValue(fov);
        fovSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                fov = Math.round(fovSlider.getValue());
                fovLabel.setText("FIELD OF VIEW " + fov);
            }
        });
        panel.add(fovSlider).width(340 * us).height(40 * us).pad(4).row();

        adsLabel = new Label("ADS SENSITIVITY " + String.format("%.1f", adsSens), assets.skin, "small");
        panel.add(adsLabel).right().padRight(14).pad(4);
        Slider adsSlider = new Slider(0.3f, 1.5f, 0.1f, false, assets.skin);
        adsSlider.setValue(adsSens);
        adsSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                adsSens = Math.round(adsSlider.getValue() * 10f) / 10f;
                adsLabel.setText("ADS SENSITIVITY " + String.format("%.1f", adsSens));
            }
        });
        panel.add(adsSlider).width(340 * us).height(40 * us).pad(4).row();

        Label invertL = new Label("INVERT VERTICAL LOOK", assets.skin, "small");
        invertBox = new CheckBox("", assets.skin);
        invertBox.setChecked(settings.invertY);
        panel.add(invertL).right().padRight(14).pad(6);
        panel.add(invertBox).width(340 * us).pad(6).left().row();

        Label aimToggleL = new Label("AIM TOGGLE (TAP ADS)", assets.skin, "small");
        aimToggleBox = new CheckBox("", assets.skin);
        aimToggleBox.setChecked(settings.aimToggle);
        panel.add(aimToggleL).right().padRight(14).pad(6);
        panel.add(aimToggleBox).width(340 * us).pad(6).left().row();

        Label sprintL = new Label("AUTO SPRINT", assets.skin, "small");
        autoSprintBox = new CheckBox("", assets.skin);
        autoSprintBox.setChecked(settings.autoSprint);
        panel.add(sprintL).right().padRight(14).pad(6);
        panel.add(autoSprintBox).width(340 * us).pad(6).left().row();

        Label autoQualityL = new Label("ADAPTIVE GRAPHICS", assets.skin, "small");
        autoQualityBox = new CheckBox("", assets.skin);
        autoQualityBox.setChecked(settings.autoQuality);
        panel.add(autoQualityL).right().padRight(14).pad(6);
        panel.add(autoQualityBox).width(340 * us).pad(6).left().row();

        Label fpsL = new Label("SHOW FPS", assets.skin, "small");
        fpsBox = new CheckBox("", assets.skin);
        fpsBox.setChecked(settings.showFps);
        panel.add(fpsL).right().padRight(14).pad(6);
        panel.add(fpsBox).width(340 * us).pad(6).left().row();

        qualityLabel = new Label("GRAPHICS " + qualityName(), assets.skin, "small");
        panel.add(qualityLabel).right().padRight(14).pad(6);
        TextButton qualityBtn = new TextButton("CHANGE", assets.skin);
        qualityBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                quality = (quality + 1) % 3;
                qualityLabel.setText("GRAPHICS " + qualityName());
            }
        });
        panel.add(qualityBtn).width(340 * us).height(52 * us).pad(6).row();

        Label soundL = new Label("SOUND", assets.skin, "small");
        soundBox = new CheckBox("", assets.skin);
        soundBox.setChecked(settings.soundOn);
        panel.add(soundL).right().padRight(14).pad(6);
        panel.add(soundBox).width(340 * us).pad(6).left().row();

        Label autoL = new Label("AUTO-FIRE (DOUBLE-TAP)", assets.skin, "small");
        autoFireBox = new CheckBox("", assets.skin);
        autoFireBox.setChecked(settings.autoFire);
        panel.add(autoL).right().padRight(14).pad(6);
        panel.add(autoFireBox).width(340 * us).pad(6).left().row();

        Label leftL = new Label("LEFT-HANDED WEAPON", assets.skin, "small");
        leftBox = new CheckBox("", assets.skin);
        leftBox.setChecked(settings.leftHanded);
        panel.add(leftL).right().padRight(14).pad(6);
        panel.add(leftBox).width(340 * us).pad(6).left().row();

        Label serverL = new Label("GAME SERVER", assets.skin, "small");
        serverField = new TextField(settings.serverUrl(), assets.skin);
        serverField.setMessageText(NetConfig.DEFAULT_SERVER);
        panel.add(serverL).right().padRight(14).pad(6);
        panel.add(serverField).width(340 * us).height(52 * us).pad(6).row();

        TextButton save = new TextButton("SAVE & BACK", assets.skin, "accent");
        save.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                save();
                returnToPreviousScreen();
            }
        });
        panel.add(save).colspan(2).width(420 * us).height(66 * us).padTop(10).row();

        TextButton back = new TextButton("BACK", assets.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                returnToPreviousScreen();
            }
        });
        panel.add(back).colspan(2).width(420 * us).height(56 * us).pad(6);

        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll =
                new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(panel, assets.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).expand().fill();
        stage.addActor(root);
    }

    private String qualityName() {
        return quality == 0 ? "LOW" : quality == 1 ? "MEDIUM" : "HIGH";
    }

    private void save() {
        settings.playerName = nameField.getText().trim();
        settings.sensitivity = sens;
        settings.adsSensitivity = adsSens;
        settings.fov = Math.round(fov);
        settings.quality = quality;
        settings.soundOn = soundBox.isChecked();
        settings.autoFire = autoFireBox.isChecked();
        settings.leftHanded = leftBox.isChecked();
        settings.invertY = invertBox.isChecked();
        settings.aimToggle = aimToggleBox.isChecked();
        settings.autoSprint = autoSprintBox.isChecked();
        settings.autoQuality = autoQualityBox.isChecked();
        settings.showFps = fpsBox.isChecked();
        settings.server = serverField.getText().trim();
        settings.usernameSet = true;
        settings.save();
        assets.setSoundOn(settings.soundOn);
    }

    private void returnToPreviousScreen() {
        if (returnScreen == null) {
            game.setScreen(new MenuScreen(game));
        } else {
            returningToMatch = true;
            game.setScreen(returnScreen);
        }
    }

    boolean returnsTo(Screen screen) {
        return returnScreen != null && returnScreen == screen;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) stage.dispose();
        if (returnScreen != null && !returningToMatch) returnScreen.dispose();
    }
}
