package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** In-match HUD: radar, health, ammo, crosshair, kill feed, chat, scoreboard, menus. */
public class Hud {

    public interface Listener {
        void onResume();

        void onLeave();

        void onShare();

        void onSettings();

        void onSendChat(String text);

        void onRematch();
    }

    public interface Context {
        PlayerEntity local();

        Array<PlayerEntity> players();

        boolean tdm();

        float matchTime();

        int scoreA();

        int scoreB();

        float ping();

        String modeText();

        String mapName();

        boolean online();

        String roomCode();
    }

    private final Assets assets;
    private final Settings settings;
    private final Listener listener;
    private final Stage stage;
    private final GlyphLayout gl = new GlyphLayout();

    private Label hpNum, ammoLabel, reserveLabel, weaponLabel, grenadeLabel, timerLabel, scoreLabel, pingLabel;    private Label bannerLabel, bannerSub;
    private Label toastLabel;
    private Label respawnLabel;
    private Label killConfirmLabel;
    private Table feedTable, chatTable;
    private Table scoreboardRoot;
    private Table pauseRoot, endRoot;
    private Label endTitle;
    private TextField chatField;

    private final Array<FeedEntry> feed = new Array<>();
    private final Array<ChatEntry> chat = new Array<>();
    private float hitmarkerTimer;
    private boolean hitHead;
    private float damageTimer;
    private float damageDir;
    private float bannerTimer;
    private float toastTimer;
    private float respawnTimer = -1f;
    private float killConfirmTimer;
    private String killConfirmText = "";
    private boolean scoreboardShown;
    private boolean paused;
    private boolean ended;
    private boolean chatMode;
    private float crosshairSpread;
    private Table sbTable;
    private Table endTable;
    private TextButton shareRoomBtn;
    private TextButton rematchBtn;

    private static class FeedEntry {
        String a, v, w;
        boolean head;
        float t;
        Color ca, cv;
    }

    private static class ChatEntry {
        String name, msg;
        float t;
        Color c;
    }

    public Hud(Assets assets, Settings settings, Listener listener) {
        this.assets = assets;
        this.settings = settings;
        this.listener = listener;
        stage = new Stage(new ScreenViewport());
        float us = assets.uiScale;

        // top bar
        Table top = new Table();
        top.setFillParent(true);
        top.top();
        scoreLabel = new Label("0 - 0", assets.skin, "default");
        timerLabel = new Label("8:00", assets.skin, "default");
        pingLabel = new Label("", assets.skin, "small");
        Table topRow = new Table();
        topRow.add(scoreLabel).padRight(16);
        topRow.add(timerLabel).padRight(16);
        topRow.add(pingLabel);
        top.add(topRow).padTop(10 * us).row();
        bannerLabel = new Label("", assets.skin, "default");
        bannerSub = new Label("", assets.skin, "small");
        bannerLabel.setFontScale(1.2f);
        top.add(bannerLabel).padTop(46 * us).row();
        top.add(bannerSub);
        stage.addActor(top);

        // bottom-left: hp (number drawn over the bar in renderOverlay)
        Table hpT = new Table();
        hpT.setFillParent(true);
        hpT.bottom().left();
        stage.addActor(hpT);

        // bottom-right: ammo
        Table ammoT = new Table();
        ammoT.setFillParent(true);
        ammoT.bottom().right();
        ammoLabel = new Label("30", assets.skin, "default");
        ammoLabel.setFontScale(1.6f);
        reserveLabel = new Label("/ 120", assets.skin, "dim");
        weaponLabel = new Label("ASSAULT RIFLE", assets.skin, "small");
        grenadeLabel = new Label("G 2", assets.skin, "default");
        Table ammoRow = new Table();
        ammoRow.add(ammoLabel);
        ammoRow.add(reserveLabel).padLeft(6).padBottom(6);
        Table ammoCol = new Table();
        ammoCol.add(weaponLabel).padRight(20 * us).padBottom(4).row();
        ammoCol.add(ammoRow).padRight(20 * us).row();
        ammoCol.add(grenadeLabel).padRight(20 * us).padTop(4).left();
        ammoT.add(ammoCol).padRight(30 * us).padBottom(24 * us);
        stage.addActor(ammoT);

        // kill feed (top right)
        feedTable = new Table();
        feedTable.top().right();
        feedTable.setFillParent(true);
        feedTable.padTop(64 * us).padRight(18 * us);
        stage.addActor(feedTable);

        // chat (bottom left, above hp)
        chatTable = new Table();
        chatTable.bottom().left();
        chatTable.setFillParent(true);
        chatTable.padLeft(18 * us).padBottom(110 * us);
        stage.addActor(chatTable);

        // chat input (desktop)
        chatField = new TextField("", assets.skin);
        chatField.setMessageText("Type a message, Enter to send...");
        chatField.setVisible(false);
        chatField.setTextFieldListener((field, c) -> {
            String text = field.getText().trim();
            if (!text.isEmpty()) {
                listener.onSendChat(text);
            }
            field.setText("");
            hideChat();
        });
        Table chatInputT = new Table();
        chatInputT.setFillParent(true);
        chatInputT.bottom().left();
        chatInputT.add(chatField).width(420 * us).padLeft(18 * us).padBottom(160 * us);
        stage.addActor(chatInputT);

        // toast
        toastLabel = new Label("", assets.skin, "small");
        Table toastT = new Table();
        toastT.setFillParent(true);
        toastT.bottom();
        toastT.add(toastLabel).padBottom(64 * us);
        stage.addActor(toastT);

        // respawn
        respawnLabel = new Label("", assets.skin, "default");
        respawnLabel.setFontScale(2.2f);
        Table respT = new Table();
        respT.setFillParent(true);
        respT.add(respawnLabel).padTop(120 * us);
        stage.addActor(respT);

        // kill confirm
        killConfirmLabel = new Label("", assets.skin, "accent");
        killConfirmLabel.setFontScale(1.3f);
        Table kcT = new Table();
        kcT.setFillParent(true);
        kcT.top();
        kcT.add(killConfirmLabel).padTop(150 * us);
        stage.addActor(kcT);

        // scoreboard
        scoreboardRoot = new Table();
        scoreboardRoot.setFillParent(true);
        sbTable = new Table();
        sbTable.setBackground(assets.skin.getDrawable("panel"));
        scoreboardRoot.add(sbTable).width(560 * us);
        scoreboardRoot.setVisible(false);
        stage.addActor(scoreboardRoot);

        // pause
        pauseRoot = buildPausePanel(us);
        pauseRoot.setVisible(false);
        stage.addActor(pauseRoot);

        // end
        endRoot = new Table();
        endRoot.setFillParent(true);
        Table endInner = new Table();
        endInner.setBackground(assets.skin.getDrawable("panel"));
        endTitle = new Label("VICTORY", assets.skin, "default");
        endTitle.setFontScale(2f);
        endInner.add(endTitle).pad(20 * us).row();
        endTable = new Table();
        endInner.add(endTable).growX().pad(8 * us).row();
        TextButton shareBtn = new TextButton("SHARE RESULT", assets.skin, "accent");
        shareBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onShare();
            }
        });
        TextButton rematch = new TextButton("REMATCH", assets.skin);
        rematch.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onRematch();
            }
        });
        rematchBtn = rematch;
        TextButton leaveBtn = new TextButton("BACK TO MENU", assets.skin);
        leaveBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onLeave();
            }
        });
        Table endBtns = new Table();
        endBtns.add(rematch).width(200 * us).height(64 * us).pad(8);
        endBtns.add(shareBtn).width(220 * us).height(64 * us).pad(8);
        endBtns.add(leaveBtn).width(220 * us).height(64 * us).pad(8);
        endInner.add(endBtns).padBottom(16 * us);
        endRoot.add(endInner).width(640 * us);
        endRoot.setVisible(false);
        stage.addActor(endRoot);
    }

    private Table buildPausePanel(float us) {
        Table root = new Table();
        root.setFillParent(true);
        Table inner = new Table();
        inner.setBackground(assets.skin.getDrawable("panel"));
        Label title = new Label("PAUSED", assets.skin, "default");
        title.setFontScale(1.6f);
        inner.add(title).pad(24 * us).row();
        TextButton resume = new TextButton("RESUME", assets.skin, "accent");
        resume.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onResume();
            }
        });
        TextButton settingsB = new TextButton("SETTINGS", assets.skin);
        settingsB.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onSettings();
            }
        });
        TextButton shareB = new TextButton("SHARE ROOM CODE", assets.skin);
        shareB.setName("shareBtn");
        shareB.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onShare();
            }
        });
        shareRoomBtn = shareB;
        TextButton leave = new TextButton("LEAVE MATCH", assets.skin);
        leave.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                assets.play(Assets.S_CLICK);
                listener.onLeave();
            }
        });
        Table btns = new Table();
        btns.add(resume).width(300 * us).height(60 * us).pad(6).row();
        btns.add(settingsB).width(300 * us).height(60 * us).pad(6).row();
        btns.add(shareB).width(300 * us).height(60 * us).pad(6).row();
        btns.add(leave).width(300 * us).height(60 * us).pad(6);
        inner.add(btns).padBottom(20 * us);
        root.add(inner).width(380 * us);
        return root;
    }

    // ---------------------------------------------------------------- setters

    public void setHp(float hp) {
        if (hpNum != null) hpNum.setText(String.valueOf(Math.max(0, Math.round(hp))));
    }

    public void setAmmo(int mag, int reserve, int grenades, String weaponName) {
        ammoLabel.setText(String.valueOf(mag));
        reserveLabel.setText("/ " + reserve);
        weaponLabel.setText(weaponName);
        grenadeLabel.setText("G " + grenades);
    }

    public void setTimer(String t) {
        timerLabel.setText(t);
    }

    public void setScore(String s) {
        scoreLabel.setText(s);
    }

    public void setPing(float ms) {
        if (ms < 0) pingLabel.setText("");
        else pingLabel.setText((int) ms + " ms");
    }

    public void setMode(String s) {
        bannerSub.setText(s);
    }

    public void banner(String text, float seconds) {
        bannerLabel.setText(text);
        bannerTimer = seconds;
    }

    public void toast(String text) {
        toastLabel.setText(text);
        toastTimer = 3.5f;
    }

    public void showHitmarker(boolean head) {
        hitmarkerTimer = 0.16f;
        hitHead = head;
    }

    public void showKillConfirm(String text, boolean head) {
        killConfirmText = text;
        killConfirmTimer = 1.6f;
        killConfirmLabel.setText(text);
        killConfirmLabel.setColor(head ? Palette.RED : Palette.ACCENT);
    }

    public void showDamage(float fromYawWorld) {
        damageTimer = 0.8f;
        damageDir = fromYawWorld;
    }

    public void addFeed(String a, String v, String weapon, boolean head, Color ca, Color cv) {
        FeedEntry e = new FeedEntry();
        e.a = a;
        e.v = v;
        e.w = weapon;
        e.head = head;
        e.t = 5f;
        e.ca = ca;
        e.cv = cv;
        feed.add(e);
        if (feed.size > 6) feed.removeIndex(0);
        rebuildFeed();
    }

    private void rebuildFeed() {
        feedTable.clearChildren();
        for (int i = feed.size - 1; i >= 0; i--) {
            FeedEntry e = feed.get(i);
            Label la = new Label(e.a, assets.skin, "default");
            la.setColor(e.ca);
            Label lw = new Label(" " + e.w + " ", assets.skin, "small");
            Label lv = new Label(e.v, assets.skin, "default");
            lv.setColor(e.cv);
            float alpha = T.clamp(e.t / 1.5f, 0f, 1f);
            la.getColor().a = alpha;
            lw.getColor().a = alpha;
            lv.getColor().a = alpha;
            feedTable.add(la).padBottom(3).row();
            feedTable.add(lw).padBottom(3).row();
            feedTable.add(lv).padBottom(3).row();
        }
    }

    public void addChat(String name, String msg, Color c) {
        ChatEntry e = new ChatEntry();
        e.name = name;
        e.msg = msg;
        e.t = 9f;
        e.c = c;
        chat.add(e);
        if (chat.size > 6) chat.removeIndex(0);
        rebuildChat();
    }

    private void rebuildChat() {
        chatTable.clearChildren();
        for (ChatEntry e : chat) {
            float alpha = T.clamp(e.t / 2f, 0f, 1f);
            Label ln = new Label(e.name + ": ", assets.skin, "small");
            ln.setColor(e.c.r, e.c.g, e.c.b, alpha);
            Label lm = new Label(e.msg, assets.skin, "small");
            lm.setColor(1f, 1f, 1f, alpha);
            Table row = new Table();
            row.add(ln);
            row.add(lm).left();
            chatTable.add(row).left().padBottom(2).row();
        }
    }

    public void showRespawn(float t) {
        if (t < 0f) {
            respawnTimer = -1f;
            respawnLabel.setText("");
            return;
        }
        respawnTimer = t;
    }

    public void showPause(boolean online, boolean hasRoomCode) {
        paused = true;
        pauseRoot.setVisible(true);
        if (shareRoomBtn != null) {
            shareRoomBtn.setVisible(hasRoomCode);
            Table btns = (Table) shareRoomBtn.getParent();
            btns.getCell(shareRoomBtn).height(hasRoomCode ? 60 * assets.uiScale : 0f);
        }
    }

    public void hidePause() {
        paused = false;
        pauseRoot.setVisible(false);
    }

    public boolean isPaused() {
        return paused;
    }

    public void showScoreboard(Array<PlayerEntity> players, PlayerEntity local, boolean tdm) {
        scoreboardShown = true;
        scoreboardRoot.setVisible(true);
        sbTable.clearChildren();
        Label head = new Label("SCOREBOARD", assets.skin, "default");
        sbTable.add(head).colspan(4).pad(10).row();
        // sort by score
        Array<PlayerEntity> sorted = new Array<>(players);
        sorted.sort((a, b) -> b.score - a.score);
        for (PlayerEntity p : sorted) {
            Color c = p == local ? Palette.ACCENT
                    : (tdm ? Palette.teamColor(p.team) : Palette.WHITE);
            Label ln = new Label(p.name, assets.skin, "small");
            ln.setColor(c);
            Label lk = new Label("K " + p.kills, assets.skin, "small");
            Label ld = new Label("D " + p.deaths, assets.skin, "small");
            Label ls = new Label(String.valueOf(p.score), assets.skin, "small");
            sbTable.add(ln).left().width(220).pad(3);
            sbTable.add(lk).width(80).pad(3);
            sbTable.add(ld).width(80).pad(3);
            sbTable.add(ls).width(90).right().pad(3).row();
        }
    }

    public void hideScoreboard() {
        scoreboardShown = false;
        scoreboardRoot.setVisible(false);
    }

    public void showEnd(boolean victory, Array<PlayerEntity> players, PlayerEntity local,
                        boolean tdm, boolean practice) {
        ended = true;
        endRoot.setVisible(true);
        if (rematchBtn != null) {
            rematchBtn.setVisible(practice);
            Table btns = (Table) rematchBtn.getParent();
            btns.getCell(rematchBtn).width(practice ? 200 * assets.uiScale : 0f);
            btns.getCell(rematchBtn).padLeft(practice ? 8 : 0f);
            btns.getCell(rematchBtn).padRight(practice ? 8 : 0f);
        }
        endTitle.setText(victory ? "VICTORY" : "DEFEAT");
        endTitle.setColor(victory ? Palette.GREEN : Palette.RED);
        endTable.clearChildren();
        Label head = new Label("FINAL SCORE", assets.skin, "default");
        endTable.add(head).colspan(4).pad(8).row();
        Array<PlayerEntity> sorted = new Array<>(players);
        sorted.sort((a, b) -> b.score - a.score);
        for (PlayerEntity p : sorted) {
            Color c = p == local ? Palette.ACCENT
                    : (tdm ? Palette.teamColor(p.team) : Palette.WHITE);
            Label ln = new Label(p.name, assets.skin, "small");
            ln.setColor(c);
            Label lk = new Label("K " + p.kills, assets.skin, "small");
            Label ld = new Label("D " + p.deaths, assets.skin, "small");
            Label ls = new Label(String.valueOf(p.score), assets.skin, "small");
            endTable.add(ln).left().width(220).pad(3);
            endTable.add(lk).width(80).pad(3);
            endTable.add(ld).width(80).pad(3);
            endTable.add(ls).width(90).right().pad(3).row();
        }
    }

    public boolean isEnded() {
        return ended;
    }

    public void showChat() {
        chatMode = true;
        chatField.setVisible(true);
        stage.setKeyboardFocus(chatField);
    }

    public void hideChat() {
        chatMode = false;
        chatField.setVisible(false);
        stage.setKeyboardFocus(null);
    }

    public boolean isChatMode() {
        return chatMode;
    }

    public void setCrosshairSpread(float pixels) {
        crosshairSpread = pixels;
    }

    public Stage stage() {
        return stage;
    }

    // ---------------------------------------------------------------- update / render

    public void update(float dt) {
        stage.act(dt);
        for (int i = feed.size - 1; i >= 0; i--) {
            FeedEntry e = feed.get(i);
            e.t -= dt;
            if (e.t <= 0f) {
                feed.removeIndex(i);
                rebuildFeed();
            }
        }
        for (int i = chat.size - 1; i >= 0; i--) {
            ChatEntry e = chat.get(i);
            e.t -= dt;
            if (e.t <= 0f) {
                chat.removeIndex(i);
                rebuildChat();
            }
        }
        if (hitmarkerTimer > 0f) hitmarkerTimer -= dt;
        if (damageTimer > 0f) damageTimer -= dt;
        if (bannerTimer > 0f) {
            bannerTimer -= dt;
            if (bannerTimer <= 0f) bannerLabel.setText("");
        }
        if (toastTimer > 0f) {
            toastTimer -= dt;
            if (toastTimer <= 0f) toastLabel.setText("");
        }
        if (killConfirmTimer > 0f) {
            killConfirmTimer -= dt;
            if (killConfirmTimer <= 0f) killConfirmLabel.setText("");
        }
        if (respawnTimer >= 0f) {
            respawnTimer -= dt;
            if (respawnTimer <= 0f) {
                respawnLabel.setText("");
                respawnTimer = -1f;
            } else {
                respawnLabel.setText(String.valueOf((int) Math.ceil(respawnTimer)));
            }
        }
    }

    public float damageFlash() {
        return T.clamp(damageTimer / 0.8f, 0f, 1f);
    }

    public void renderOverlay(SpriteBatch batch, ShapeRenderer sr, Context ctx, boolean localAlive,
                              boolean localAiming) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        float us = assets.uiScale;
        PlayerEntity local = ctx.local();
        sr.setProjectionMatrix(stage.getCamera().combined);
        batch.setProjectionMatrix(stage.getCamera().combined);

        Gdx.gl.glDisable(Gdx.gl20.GL_CULL_FACE);
        Gdx.gl.glEnable(Gdx.gl20.GL_BLEND);
        Gdx.gl.glBlendFunc(Gdx.gl20.GL_SRC_ALPHA, Gdx.gl20.GL_ONE_MINUS_SRC_ALPHA);

        // ---------- radar ----------
        float cx = 96 * us, cy = h - 96 * us, R = 78 * us;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.5f);
        sr.circle(cx, cy, R);
        float scale = R / 32f;
        for (PlayerEntity p : ctx.players()) {
            if (p == local || !p.alive) continue;
            float dx = p.pos.x - local.pos.x;
            float dz = p.pos.z - local.pos.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist > 32f) continue;
            float cosY = MathUtils.cos(local.yaw);
            float sinY = MathUtils.sin(local.yaw);
            float rx = dx * cosY - dz * sinY;
            float rz = dx * sinY + dz * cosY;
            float sx = cx + rz * scale;
            float sy = cy + rx * scale;
            boolean friendly = ctx.tdm() && p.team == local.team;
            if (friendly) sr.setColor(Palette.TEAL);
            else sr.setColor(1f, 0.25f, 0.2f, 1f);
            sr.circle(sx, sy, 5 * us);
        }
        // player arrow
        sr.setColor(1f, 1f, 1f, 0.9f);
        sr.triangle(cx, cy + R * 0.42f, cx - R * 0.16f, cy + R * 0.1f, cx + R * 0.16f, cy + R * 0.1f);
        sr.setColor(0.05f, 0.07f, 0.1f, 0.7f);
        sr.rect(cx - R * 0.16f, cy - R * 0.06f, R * 0.32f, R * 0.12f); // hide dot area
        sr.setColor(1f, 1f, 1f, 0.9f);
        sr.circle(cx, cy, 4 * us);
        sr.end();
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(1f, 1f, 1f, 0.3f);
        sr.circle(cx, cy, R);
        sr.end();

        // ---------- hp bar ----------
        float hp = T.clamp(local.hp / 100f, 0f, 1f);
        float bw = 260 * us, bh = 24 * us, bx = 20 * us, by = 22 * us;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.55f);
        sr.rect(bx - 2, by - 2, bw + 4, bh + 4);
        Color hpCol = hp > 0.5f ? Palette.GREEN
                : hp > 0.25f ? Palette.ACCENT : Palette.RED;
        sr.setColor(hpCol.r, hpCol.g, hpCol.b, 0.95f);
        sr.rect(bx, by, bw * hp, bh);
        sr.end();
        batch.begin();
        assets.font.setColor(1f, 1f, 1f, 1f);
        gl.setText(assets.font, String.valueOf(Math.max(0, Math.round(local.hp))));
        assets.font.draw(batch, String.valueOf(Math.max(0, Math.round(local.hp))),
                bx + bw / 2f - gl.width / 2f, by + bh / 2f + gl.height / 2f);

        // ---------- crosshair ----------
        if (localAlive && !localAiming) {
            float gap = T.clamp(7 * us + crosshairSpread, 8 * us, 52 * us);
            float len = 13 * us;
            float th = 3 * us;
            float ccx = w / 2f, ccy = h / 2f;
            sr.setProjectionMatrix(stage.getCamera().combined);
            sr.begin(ShapeRenderer.ShapeType.Filled);
            // dark outline pass
            sr.setColor(0f, 0f, 0f, 0.6f);
            for (int i = 0; i < 4; i++) {
                crossRect(sr, i, gap - 1.5f * us, len + 3 * us, th + 3 * us, ccx, ccy);
            }
            // bright pass
            sr.setColor(0.4f, 1f, 0.6f, 0.95f);
            for (int i = 0; i < 4; i++) {
                crossRect(sr, i, gap, len, th, ccx, ccy);
            }
            sr.setColor(0.4f, 1f, 0.6f, 0.95f);
            sr.circle(ccx, ccy, 2.4f * us);
            sr.end();
        }

        // hitmarker
        if (hitmarkerTimer > 0f) {
            float a = hitmarkerTimer / 0.16f;
            float size = 56 * us;
            batch.setColor(hitHead ? Palette.RED : Color.WHITE);
            batch.setColor(batch.getColor().r, batch.getColor().g, batch.getColor().b, a);
            batch.draw(assets.hitmarkerTex, w / 2f - size / 2f, h / 2f - size / 2f, size, size);
            batch.setColor(Color.WHITE);
        }

        // damage direction arc
        if (damageTimer > 0f && localAlive) {
            float a = damageTimer / 0.8f;
            float ang = -damageDir + local.yaw + MathUtils.PI / 2f;
            float rr = Math.min(w, h) * 0.36f;
            float arcCx = w / 2f, arcCy = h / 2f;
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(0.9f, 0.1f, 0.05f, a * 0.75f);
            for (int i = -3; i <= 3; i++) {
                float aa = ang + i * MathUtils.PI / 24f;
                sr.rect(arcCx + MathUtils.cos(aa) * rr - 5 * us,
                        arcCy + MathUtils.sin(aa) * rr - 5 * us,
                        10 * us, 10 * us);
            }
            sr.end();
        }

        batch.end();
    }

    public void draw(SpriteBatch batch) {
        Gdx.gl.glDisable(Gdx.gl20.GL_CULL_FACE);
        stage.draw();
    }

    private void crossRect(ShapeRenderer sr, int i, float gap, float len, float th,
                           float ccx, float ccy) {
        if (i == 0) sr.rect(ccx + gap, ccy - th / 2f, len, th);
        else if (i == 1) sr.rect(ccx - gap - len, ccy - th / 2f, len, th);
        else if (i == 2) sr.rect(ccx - th / 2f, ccy + gap, th, len);
        else sr.rect(ccx - th / 2f, ccy - gap - len, th, len);
    }

    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
    }

    public void dispose() {
        stage.dispose();
    }
}
