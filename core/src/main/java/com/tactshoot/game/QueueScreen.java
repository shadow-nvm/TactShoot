package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Matchmaking / room creation screen. Owns the connection until a match starts. */
public class QueueScreen extends ScreenAdapter implements NetClient.Listener {

    private final TactShootGame game;
    private final Assets assets;
    private final Settings settings;
    private final Platform platform;
    private final MatchConfig cfg;

    private NetClient net;
    private Stage stage;
    private Label status;
    private TextButton shareBtn;
    private String roomCode = "";
    private boolean done;
    private float pulse;

    public QueueScreen(TactShootGame game, MatchConfig cfg) {
        this.game = game;
        this.assets = game.assets;
        this.settings = game.settings;
        this.platform = game.platform;
        this.cfg = cfg;
        stage = new Stage(new ScreenViewport());
        buildUi();
    }

    private void buildUi() {
        float us = assets.uiScale;
        Table root = new Table();
        root.setFillParent(true);
        Table panel = new Table();
        panel.setBackground(assets.skin.getDrawable("panel"));
        panel.pad(36 * us);

        Label title = new Label(cfg.createRoom ? "CREATE ROOM"
                : (cfg.roomCode != null && !cfg.roomCode.isEmpty() ? "JOIN ROOM" : "MATCHMAKING"),
                assets.skin, "default");
        title.setFontScale(1.8f);
        panel.add(title).padBottom(16 * us).row();

        status = new Label("Connecting...", assets.skin, "default");
        panel.add(status).padBottom(24 * us).row();

        shareBtn = new TextButton("SHARE ROOM CODE", assets.skin, "accent");
        shareBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                platform.shareText("Join my TactShoot room! Code: " + roomCode
                        + " - TactShoot, the mobile multiplayer FPS");
            }
        });
        shareBtn.setVisible(false);
        panel.add(shareBtn).width(300 * us).height(64 * us).pad(6).row();

        TextButton cancel = new TextButton("CANCEL", assets.skin);
        cancel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                if (net != null) net.close();
                net = null;
                game.setScreen(new OnlineScreen(game));
            }
        });
        panel.add(cancel).width(300 * us).height(60 * us).pad(6);
        root.add(panel);
        stage.addActor(root);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        net = new NetClient(this);
        String url = cfg.serverUrl != null && !cfg.serverUrl.isEmpty()
                ? cfg.serverUrl : settings.serverUrl();
        net.connect(url);
    }

    @Override
    public void onOpen() {
        status.setText("Connected - joining...");
        net.send(NetClient.obj("t", "hello", "name", settings.playerName,
                "v", NetConfig.PROTOCOL_VERSION));
    }

    @Override
    public void onClose(String reason) {
        if (!done) {
            status.setText("Disconnected: " + reason);
        }
    }

    @Override
    public void onError(String msg) {
        if (!done) {
            status.setText("Connection failed: " + msg);
        }
    }

    @Override
    public void onMessage(String type, JsonValue data) {
        switch (type) {
            case "welcome":
                if (cfg.createRoom) {
                    net.send(NetClient.obj("t", "roomCreate", "mode", cfg.mode));
                } else if (cfg.roomCode != null && !cfg.roomCode.isEmpty()) {
                    net.send(NetClient.obj("t", "roomJoin", "code", cfg.roomCode));
                } else {
                    net.send(NetClient.obj("t", "queue", "mode", cfg.mode));
                }
                break;
            case "queued":
                status.setText("Searching for " + (cfg.isTdm() ? "TDM" : "FFA") + " players...");
                break;
            case "roomCode":
                roomCode = data.getString("code", "");
                status.setText("Room " + roomCode + " created!\nShare the code and wait for players.");
                shareBtn.setVisible(true);
                break;
            case "match":
                done = true;
                cfg.net = net;
                cfg.matchPayload = data;
                net = null; // GameScreen takes ownership
                game.setScreen(new GameScreen(game, cfg));
                break;
            case "error":
            case "roomError":
                status.setText(data.getString("m", "Server error"));
                break;
            default:
                break;
        }
    }

    @Override
    public void render(float delta) {
        pulse += delta;
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
        if (net != null) {
            net.close();
            net = null;
        }
    }
}
