package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Online play menu: quick play, private rooms and server selection. */
public class OnlineScreen extends ScreenAdapter {

    private final TactShootGame game;
    private final Assets assets;
    private final Settings settings;

    private Stage stage;
    private String mode = "tdm";
    private TextButton tdmBtn, ffaBtn;
    private TextField codeField, serverField;
    private Label modeLabel;

    public OnlineScreen(TactShootGame game) {
        this.game = game;
        this.assets = game.assets;
        this.settings = game.settings;
        stage = new Stage(new ScreenViewport());
        buildUi();
    }

    private void buildUi() {
        float us = assets.uiScale;
        Table root = new Table();
        root.setFillParent(true);
        Table panel = new Table();
        panel.setBackground(assets.skin.getDrawable("panel"));
        panel.pad(34 * us);

        Label title = new Label("ONLINE", assets.skin, "default");
        title.setFontScale(1.9f);
        panel.add(title).padBottom(14 * us).row();

        // mode toggle
        Table modeRow = new Table();
        tdmBtn = new TextButton("TEAM DEATHMATCH", assets.skin, "accent");
        ffaBtn = new TextButton("FREE FOR ALL", assets.skin);
        tdmBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                mode = "tdm";
                refreshModeButtons();
            }
        });
        ffaBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                mode = "ffa";
                refreshModeButtons();
            }
        });
        modeRow.add(tdmBtn).width(240 * us).height(56 * us).pad(4);
        modeRow.add(ffaBtn).width(240 * us).height(56 * us).pad(4);
        panel.add(modeRow).padBottom(14 * us).row();

        TextButton quick = new TextButton("QUICK PLAY", assets.skin, "accent");
        quick.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                startQueue(false, "");
            }
        });
        panel.add(quick).width(400 * us).height(70 * us).pad(5).row();

        TextButton create = new TextButton("CREATE PRIVATE ROOM", assets.skin);
        create.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                startQueue(true, "");
            }
        });
        panel.add(create).width(400 * us).height(60 * us).pad(5).row();

        // join room
        Table joinRow = new Table();
        codeField = new TextField("", assets.skin);
        codeField.setMessageText("ROOM CODE");
        codeField.setTextFieldFilter((field, c) -> Character.isLetterOrDigit(c) && field.getText().length() < 4);
        TextButton join = new TextButton("JOIN", assets.skin);
        join.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                startQueue(false, codeField.getText().trim().toUpperCase());
            }
        });
        joinRow.add(codeField).width(280 * us).height(56 * us).padRight(8);
        joinRow.add(join).width(112 * us).height(56 * us);
        panel.add(joinRow).pad(8).row();

        // server
        Label serverLabel = new Label("GAME SERVER (wss://...)", assets.skin, "small");
        panel.add(serverLabel).padTop(10).row();
        serverField = new TextField(settings.serverUrl(), assets.skin);
        serverField.setMessageText(NetConfig.DEFAULT_SERVER);
        serverField.setTextFieldListener((field, c) -> {
            settings.server = field.getText().trim();
            settings.save();
        });
        panel.add(serverField).width(400 * us).height(56 * us).padBottom(10).row();

        TextButton back = new TextButton("BACK", assets.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                game.setScreen(new MenuScreen(game));
            }
        });
        panel.add(back).width(400 * us).height(60 * us).pad(6);

        root.add(panel);
        stage.addActor(root);
    }

    private void refreshModeButtons() {
        tdmBtn.setStyle(mode.equals("tdm") ? assets.skin.get("accent", TextButton.TextButtonStyle.class)
                : assets.skin.get("default", TextButton.TextButtonStyle.class));
        ffaBtn.setStyle(mode.equals("ffa") ? assets.skin.get("accent", TextButton.TextButtonStyle.class)
                : assets.skin.get("default", TextButton.TextButtonStyle.class));
    }

    private void startQueue(boolean createRoom, String code) {
        MatchConfig cfg = new MatchConfig();
        cfg.online = true;
        cfg.mode = mode;
        cfg.createRoom = createRoom;
        cfg.roomCode = code;
        cfg.serverUrl = serverField.getText().trim();
        game.setScreen(new QueueScreen(game, cfg));
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        refreshModeButtons();
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
