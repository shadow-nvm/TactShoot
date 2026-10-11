package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Offline practice setup: mode, map, weapon, bot count and difficulty. */
public class PracticeScreen extends ScreenAdapter {

    private final TactShootGame game;
    private final Assets assets;

    private Stage stage;
    private String mode = "ffa";
    private int mapIdx;
    private int weapon = Weapons.AR;
    private int botCount = 7;
    private float botLevel = 0.5f;

    private TextButton tdmBtn, ffaBtn;
    private final TextButton[] mapBtns = new TextButton[3];
    private final TextButton[] weaponBtns = new TextButton[3];
    private Label botLabel, diffLabel;

    public PracticeScreen(TactShootGame game) {
        this.game = game;
        this.assets = game.assets;
        stage = new Stage(new ScreenViewport());
        buildUi();
    }

    private void buildUi() {
        float us = assets.uiScale;
        Table root = new Table();
        root.setFillParent(true);
        Table panel = new Table();
        panel.setBackground(assets.skin.getDrawable("panel"));
        panel.pad(30 * us);

        Label title = new Label("PRACTICE", assets.skin, "default");
        title.setFontScale(1.9f);
        panel.add(title).padBottom(14 * us).row();

        // mode
        Table modeRow = new Table();
        tdmBtn = new TextButton("TEAM DEATHMATCH", assets.skin);
        ffaBtn = new TextButton("FREE FOR ALL", assets.skin, "accent");
        tdmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                mode = "tdm";
                refresh();
            }
        });
        ffaBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                mode = "ffa";
                refresh();
            }
        });
        modeRow.add(tdmBtn).width(220 * us).height(52 * us).pad(4);
        modeRow.add(ffaBtn).width(220 * us).height(52 * us).pad(4);
        panel.add(modeRow).padBottom(8).row();

        // maps
        Label mapL = new Label("MAP", assets.skin, "small");
        panel.add(mapL).left().padLeft(10).row();
        Table mapRow = new Table();
        for (int i = 0; i < assets.maps.length; i++) {
            final int idx = i;
            mapBtns[i] = new TextButton(assets.maps[i].displayName, assets.skin);
            mapBtns[i].addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    assets.play(Assets.S_CLICK);
                    mapIdx = idx;
                    refresh();
                }
            });
            mapRow.add(mapBtns[i]).width(140 * us).height(48 * us).pad(4);
        }
        panel.add(mapRow).padBottom(8).row();

        // weapons
        Label wL = new Label("PRIMARY WEAPON", assets.skin, "small");
        panel.add(wL).left().padLeft(10).row();
        Table wRow = new Table();
        String[] wNames = {"ASSAULT RIFLE", "SMG", "SHOTGUN"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            weaponBtns[i] = new TextButton(wNames[i], assets.skin);
            weaponBtns[i].addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    assets.play(Assets.S_CLICK);
                    weapon = idx;
                    refresh();
                }
            });
            wRow.add(weaponBtns[i]).width(140 * us).height(48 * us).pad(4);
        }
        panel.add(wRow).padBottom(10).row();

        // bot count
        botLabel = new Label("BOTS: 7", assets.skin, "small");
        panel.add(botLabel).left().padLeft(10).row();
        Slider botSlider = new Slider(1f, 11f, 1f, false, assets.skin);
        botSlider.setValue(botCount);
        botSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                botCount = Math.round(botSlider.getValue());
                botLabel.setText("BOTS: " + botCount);
            }
        });
        panel.add(botSlider).width(430 * us).height(40 * us).padBottom(8).row();

        // difficulty
        diffLabel = new Label("DIFFICULTY: NORMAL", assets.skin, "small");
        panel.add(diffLabel).left().padLeft(10).row();
        Slider diffSlider = new Slider(0f, 1f, 0.1f, false, assets.skin);
        diffSlider.setValue(botLevel);
        diffSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                botLevel = diffSlider.getValue();
                diffLabel.setText("DIFFICULTY: " + diffName(botLevel));
            }
        });
        panel.add(diffSlider).width(430 * us).height(40 * us).padBottom(16).row();

        TextButton start = new TextButton("START MATCH", assets.skin, "accent");
        start.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                MatchConfig cfg = new MatchConfig();
                cfg.online = false;
                cfg.mode = mode;
                cfg.mapIdx = mapIdx;
                cfg.primaryWeapon = weapon;
                cfg.botCount = botCount;
                cfg.botLevel = botLevel;
                game.setScreen(new GameScreen(game, cfg));
            }
        });
        panel.add(start).width(430 * us).height(70 * us).pad(4).row();

        TextButton back = new TextButton("BACK", assets.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                game.setScreen(new MenuScreen(game));
            }
        });
        panel.add(back).width(430 * us).height(56 * us).pad(4);

        root.add(panel);
        stage.addActor(root);
        refresh();
    }

    private String diffName(float d) {
        if (d < 0.34f) return "EASY";
        if (d < 0.67f) return "NORMAL";
        return "HARD";
    }

    private void refresh() {
        tdmBtn.setStyle(assets.skin.get(mode.equals("tdm") ? "accent" : "default",
                TextButton.TextButtonStyle.class));
        ffaBtn.setStyle(assets.skin.get(mode.equals("ffa") ? "accent" : "default",
                TextButton.TextButtonStyle.class));
        for (int i = 0; i < mapBtns.length; i++) {
            mapBtns[i].setStyle(assets.skin.get(i == mapIdx ? "accent" : "default",
                    TextButton.TextButtonStyle.class));
        }
        for (int i = 0; i < weaponBtns.length; i++) {
            weaponBtns[i].setStyle(assets.skin.get(i == weapon ? "accent" : "default",
                    TextButton.TextButtonStyle.class));
        }
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
        stage.dispose();
    }
}
