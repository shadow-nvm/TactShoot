package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Main menu with a live 3D map backdrop. */
public class MenuScreen extends ScreenAdapter {

    private final TactShootGame game;
    private final Assets assets;
    private final Settings settings;
    private final Platform platform;

    private Stage stage;
    private PerspectiveCamera cam;
    private ModelBatch batch;
    private Environment env;
    private Model worldModel;
    private ModelInstance worldInst;
    private Atmosphere sky;
    private SpriteBatch uiBatch;
    private float t;

    public MenuScreen(TactShootGame game) {
        this.game = game;
        this.assets = game.assets;
        this.settings = game.settings;
        this.platform = game.platform;

        DefaultShader.Config sc = new DefaultShader.Config();
        sc.numDirectionalLights = 1;
        sc.numPointLights = 0;
        batch = new ModelBatch(new DefaultShaderProvider(sc));
        cam = new PerspectiveCamera(65, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 0.1f;
        cam.far = 250f;

        MapDef map = assets.maps[0];
        worldModel = assets.worldModels[0];
        worldInst = new ModelInstance(worldModel);
        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, map.ambient.r, map.ambient.g, map.ambient.b, 1f));
        env.add(new DirectionalLight().set(map.sunColor, new Vector3(map.sunDir).scl(-1f)));
        sky = new Atmosphere(assets);

        stage = new Stage(new ScreenViewport());
        uiBatch = new SpriteBatch();
        buildUi();
        Gdx.input.setInputProcessor(stage);
    }

    private void buildUi() {
        float us = assets.uiScale;
        Table root = new Table();
        root.setFillParent(true);
        root.left();
        Table panel = new Table();
        panel.setBackground(assets.skin.getDrawable("panel"));
        panel.pad(30 * us);

        Label title = new Label("TACT", assets.skin, "default");
        title.setFontScale(3.2f);
        title.setColor(Palette.WHITE);
        Label title2 = new Label("SHOOT", assets.skin, "default");
        title2.setFontScale(3.2f);
        title2.setColor(Palette.ACCENT);
        Table titleRow = new Table();
        titleRow.add(title).padRight(6);
        titleRow.add(title2);
        panel.add(titleRow).left().padBottom(4 * us).row();
        Label sub = new Label("MOBILE MULTIPLAYER FPS", assets.skin, "small");
        panel.add(sub).left().padBottom(22 * us).row();

        TextButton play = new TextButton("PLAY ONLINE", assets.skin, "accent");
        play.addListener(go(() -> new OnlineScreen(game)));
        TextButton practice = new TextButton("PRACTICE VS BOTS", assets.skin);
        practice.addListener(go(() -> new PracticeScreen(game)));
        TextButton settingsB = new TextButton("SETTINGS", assets.skin);
        settingsB.addListener(go(() -> new SettingsScreen(game)));
        TextButton quit = new TextButton("QUIT", assets.skin);
        quit.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                Gdx.app.exit();
            }
        });

        Table btns = new Table();
        btns.add(play).width(340 * us).height(74 * us).pad(6 * us).row();
        btns.add(practice).width(340 * us).height(64 * us).pad(6 * us).row();
        btns.add(settingsB).width(340 * us).height(64 * us).pad(6 * us).row();
        if (!platform.isAndroid()) {
            btns.add(quit).width(340 * us).height(64 * us).pad(6 * us).row();
        }
        panel.add(btns).left().row();

        Label ver = new Label("v" + TactShootGame.VERSION + "  -  free to play, no ads",
                assets.skin, "small");
        panel.add(ver).left().padTop(16 * us);
        root.add(panel).padLeft(60 * us).padTop(40 * us).top().left();
        stage.addActor(root);
    }

    private ClickListener go(java.util.function.Supplier<com.badlogic.gdx.Screen> s) {
        return new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                game.setScreen(s.get());
            }
        };
    }

    @Override
    public void render(float delta) {
        t += Math.min(delta, 0.1f);
        float ang = t * 0.05f;
        float rad = 38f;
        cam.position.set((float) Math.cos(ang) * rad, 22f, (float) Math.sin(ang) * rad);
        cam.direction.set(-cam.position.x, -12f, -cam.position.z).nor();
        cam.up.set(0f, 1f, 0f);
        cam.update();

        MapDef map = assets.maps[0];
        Gdx.gl.glClearColor(map.skyHorizon.r, map.skyHorizon.g, map.skyHorizon.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sky.render(cam, map);
        batch.begin(cam);
        batch.render(worldInst, env);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void dispose() {
        stage.dispose();
        uiBatch.dispose();
        batch.dispose();
        sky.dispose();
    }
}
