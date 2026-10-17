package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonValue;

/**
 * The gameplay screen: world rendering with shadows and post FX, local
 * player control, bots, and (optionally) networked multiplayer.
 */
public class GameScreen extends ScreenAdapter implements
        PlayerController.Callbacks, Hud.Listener, Hud.Context, NetClient.Listener {

    private final TactShootGame game;
    private final Assets assets;
    private final Settings settings;
    private final Platform platform;
    private final MatchConfig cfg;

    // world
    private MapDef map;
    private Model world;
    private ModelInstance worldInst;
    private Array<ModelInstance> props;
    private NavGrid nav;
    private int mapIdx;

    // rendering
    private Environment env;
    private DirectionalShadowLight shadowLight;
    private PointLight muzzleLight;
    private PerspectiveCamera cam;
    private ModelBatch batch;
    private boolean shadows, postOn;
    private boolean ready;

    // entities
    private final Array<PlayerEntity> players = new Array<>();
    private PlayerEntity local;
    private final IntMap<PlayerEntity> byId = new IntMap<>();
    private final IntMap<ModelInstance> soldierInst = new IntMap<>();
    private PlayerController controller;
    private BotAI botAi;
    private WeaponView weaponView;
    private int lastWeapon = -1;
    private ModelInstance grenadeInst;

    // fx / ui
    private Fx fx;
    private Atmosphere sky;
    private PostFx post;
    private Hud hud;
    private TouchControls touch;
    private DesktopControls desk;
    private SpriteBatch uiBatch;
    private ShapeRenderer sr;
    private InputMultiplexer multiplexer;

    // net
    private NetClient net;
    private boolean online;
    private float sendTimer;
    private int myNetId = -1;
    private String roomCode = "";
    private boolean gotMatch;

    // match state
    private float matchTime;
    private int scoreA, scoreB;
    private boolean ended;
    private boolean paused;
    private float afk;
    private float muzzleLightIntensity;
    private final Vector3 muzzleLightPos = new Vector3();
    private float botRespawnSkip;
    private boolean scoreboardOpen;

    private static class Grenade {
        float x, y, z, vx, vy, vz, fuse;
        int ownerId;
        float trail;
    }

    private final Array<Grenade> grenades = new Array<>();

    private final Vector3 tmpV = new Vector3();
    private final Vector3 tmpV2 = new Vector3();
    private final Vector3 tmpV3 = new Vector3();
    private final Vector3 lightDir = new Vector3();
    private final Vector3 muzzleWorld = new Vector3();
    private final Vector3 pelletDir = new Vector3();
    private final Vector3 pelletRight = new Vector3();
    private final Vector3 pelletUp = new Vector3();
    private final Vector3 hitPoint = new Vector3();
    private final Vector3 camRight = new Vector3();

    private float camEye = 1.62f;
    private float fps = 60f;
    private float slowTimer;
    private float qualityTimer;
    private int appliedQuality = -1;

    public GameScreen(TactShootGame game, MatchConfig cfg) {
        this.game = game;
        this.assets = game.assets;
        this.settings = game.settings;
        this.platform = game.platform;
        this.cfg = cfg;
        this.online = cfg.online;

        DefaultShader.Config sc = new DefaultShader.Config();
        sc.numDirectionalLights = 1;
        sc.numPointLights = 1;
        sc.numSpotLights = 0;
        batch = new ModelBatch(new DefaultShaderProvider(sc));

        int w = Gdx.graphics != null && Gdx.graphics.getWidth() > 0 ? Gdx.graphics.getWidth() : 1280;
        int h = Gdx.graphics != null && Gdx.graphics.getHeight() > 0 ? Gdx.graphics.getHeight() : 720;
        cam = new PerspectiveCamera(settings.fov, w, h);
        cam.near = 0.08f;
        cam.far = 220f;

        env = new Environment();

        local = new PlayerEntity();
        local.isLocal = true;
        local.name = settings.playerName;
        local.primaryChoice = cfg.primaryWeapon;
        local.id = 0;
        local.alive = !online;
        players.add(local);
        byId.put(local.id, local);

        controller = new PlayerController(settings, assets, this);
        weaponView = new WeaponView(assets, settings);
        grenadeInst = new ModelInstance(ModelFactory.grenade(assets.textures));
        fx = new Fx(assets);
        sky = new Atmosphere(assets);
        post = new PostFx(assets);
        hud = new Hud(assets, settings, this);
        uiBatch = new SpriteBatch();
        sr = new ShapeRenderer();

        if (platform.isAndroid()) {
            touch = new TouchControls(settings);
            touch.setSensitivity(1f);
            touch.setAutoFireEnabled(settings.autoFire);
        } else {
            desk = new DesktopControls();
            desk.setSensitivity(1f);
            desk.aimToggle = settings.aimToggle;
        }
        multiplexer = new InputMultiplexer();
        if (touch != null) multiplexer.addProcessor(touch);
        if (desk != null) multiplexer.addProcessor(desk);
        multiplexer.addProcessor(hud.stage());
        Gdx.input.setInputProcessor(multiplexer);

        // net
        if (cfg.net != null) {
            net = cfg.net; // adopted from the queue screen
            net.setListener(this);
            online = true;
        } else if (online) {
            net = new NetClient(this);
            net.connect(cfg.serverUrl.isEmpty() ? settings.serverUrl() : cfg.serverUrl);
        }
        if (online && cfg.matchPayload != null) {
            handleMatch(cfg.matchPayload);
        } else if (!online) {
            startPracticeMatch();
        } else {
            hud.banner("CONNECTING", 1.5f);
            hud.toast("Waiting for a match...");
        }

        post.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (touch != null) touch.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        shadows = settings.quality >= 1;
        postOn = settings.quality >= 1;
        camEye = local.eyeY();
        applyQuality();
    }

    // ================================================================ setup

    private void applyQuality() {
        appliedQuality = settings.quality;
        if (shadowLight != null) {
            try { shadowLight.dispose(); } catch (Throwable ignored) {}
            shadowLight = null;
        }
        shadows = settings.quality >= 1;
        postOn = settings.quality >= 1;
        env.clear();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, map == null ? 0.42f : map.ambient.r,
                map == null ? 0.42f : map.ambient.g, map == null ? 0.42f : map.ambient.b, 1f));
        if (map != null) {
            lightDir.set(map.sunDir).scl(-1f).nor();
            env.add(new DirectionalLight().set(map.sunColor, lightDir));
        }
        if (shadows && map != null) {
            try {
                int res = settings.quality >= 2 ? 2048 : 1024;
                float extent = settings.quality >= 2 ? 80f : 70f;
                shadowLight = new DirectionalShadowLight(res, res, extent, extent, 0.5f, 190f);
                shadowLight.set(map.sunColor, lightDir);
                env.add(shadowLight);
                env.shadowMap = shadowLight;
            } catch (Throwable t) {
                // A device that cannot allocate the shadow FBO must still render.
                Gdx.app.error("TactShoot", "ShadowLight init failed, disabling shadows: " + t.getMessage());
                shadowLight = null;
                env.shadowMap = null;
                shadows = false;
            }
        } else {
            shadowLight = null;
            env.shadowMap = null;
        }
        muzzleLight = new PointLight().set(new Color(1f, 0.85f, 0.6f, 1f), muzzleLightPos, 0f);
        env.add(muzzleLight);
    }

    private void setupMap(int idx) {
        mapIdx = MathUtils.clamp(idx, 0, assets.maps.length - 1);
        if (worldInst != null) worldInst = null;
        map = assets.maps[mapIdx];
        world = assets.worldModels[mapIdx];
        worldInst = new ModelInstance(world);
        props = assets.worldProps[mapIdx];
        nav = assets.navGrids[mapIdx];
        botAi = new BotAI(nav, map.solids, assets, cfg.isTdm(),
                (bot, o, d) -> onShoot(bot, o, d, bot.currentSpec()));
        ready = true;
        applyQuality();
    }

    private void startPracticeMatch() {
        setupMap(cfg.mapIdx);
        // bots
        int maxBots = cfg.isTdm() ? NetConfig.MAX_PLAYERS - 1 : NetConfig.FFA_PLAYERS - 1;
        int botCount = MathUtils.clamp(cfg.botCount, 1, maxBots);
        for (int i = 0; i < botCount; i++) {
            PlayerEntity bot = new PlayerEntity();
            bot.isBot = true;
            bot.name = Names.random();
            bot.id = 1 + i;
            bot.primaryChoice = new int[]{Weapons.AR, Weapons.SMG, Weapons.SHOTGUN}[MathUtils.random(2)];
            bot.botLevel = MathUtils.clamp(cfg.botLevel + MathUtils.random(-0.08f, 0.08f), 0.1f, 1f);
            // The player is on team 0, so the first bot must balance the
            // opposing team before alternating teammates and opponents.
            bot.team = cfg.isTdm() ? (i % 2 == 0 ? 1 : 0) : 100 + i;
            players.add(bot);
            byId.put(bot.id, bot);
        }
        matchTime = NetConfig.MATCH_TIME;
        scoreA = 0;
        scoreB = 0;
        ended = false;
        for (int i = 0; i < players.size; i++) {
            spawnEntity(players.get(i), true);
        }
        controller.reset(local);
        weaponView.setWeapon(cfg.primaryWeapon);
        lastWeapon = local.weapon;
        hud.setMode(cfg.isTdm() ? "TEAM DEATHMATCH - " + map.displayName : "FREE FOR ALL - " + map.displayName);
        hud.banner(cfg.isTdm() ? "TEAM DEATHMATCH" : "FREE FOR ALL", 2.2f);
        hud.toast(map.displayName + " - " + map.description);
    }

    private void spawnEntity(PlayerEntity e, boolean initial) {
        if (initial) e.fullReset();
        else e.resetLoadout();
        e.alive = true;
        e.hp = 100f;
        e.respawnTimer = 0f;
        Vector3 sp = pickSpawn(e);
        e.pos.set(sp.x, 0.05f, sp.z);
        float cx = (map.minX + map.maxX) / 2f;
        float cz = (map.minZ + map.maxZ) / 2f;
        e.yaw = (float) Math.atan2(cz - sp.z, cx - sp.x);
        e.pitch = 0f;
        e.vel.setZero();
        if (e.isLocal) {
            controller.reset(e);
            lastWeapon = -1;
            camEye = e.eyeY();
            if (touch != null) touch.setEnabled(true);
        }
    }

    private Vector3 pickSpawn(PlayerEntity e) {
        for (int attempt = 0; attempt < 6; attempt++) {
            Vector3 sp = map.pickSpawn(e.team, e.id + attempt);
            sp.add(MathUtils.random(-1.2f, 1.2f), 0f, MathUtils.random(-1.2f, 1.2f));
            e.pos.set(sp.x, 0.05f, sp.z);
            boolean blocked = false;
            for (Box b : map.solids) {
                if (Collision.entityOverlaps(e, b)) {
                    blocked = true;
                    break;
                }
            }
            if (!blocked) return sp;
        }
        Vector3 fallback = nav.randomOpen(map.minX + (map.maxX - map.minX) / 2f,
                map.minZ + (map.maxZ - map.minZ) / 2f);
        return fallback == null ? new Vector3(0, 0.05f, 0) : fallback.set(fallback.x, 0.05f, fallback.z);
    }

    // ================================================================ net handling

    private void handleMatch(JsonValue data) {
        gotMatch = true;
        myNetId = data.getInt("you", 0);
        roomCode = data.getString("code", "");
        String mode = data.getString("mode", "ffa");
        cfg.mode = mode;
        String mapName = data.getString("map", "dust");
        int mi = 0;
        for (int i = 0; i < assets.maps.length; i++) {
            if (assets.maps[i].id.equals(mapName)) {
                mi = i;
                break;
            }
        }
        setupMap(mi);
        // clear bots/extra entities
        players.clear();
        byId.clear();
        local.team = data.getInt("team", 0);
        local.id = myNetId;
        local.name = settings.playerName;
        local.primaryChoice = cfg.primaryWeapon;
        local.fullReset();
        players.add(local);
        byId.put(myNetId, local);
        JsonValue list = data.get("players");
        if (list != null) {
            for (JsonValue p = list.child; p != null; p = p.next) {
                int id = p.getInt("id");
                if (id == myNetId) continue;
                PlayerEntity r = new PlayerEntity();
                r.isRemote = true;
                r.id = id;
                r.name = p.getString("name", "?");
                r.team = p.getInt("team", 0);
                r.primaryChoice = cfg.primaryWeapon;
                r.alive = false;
                players.add(r);
                byId.put(id, r);
            }
        }
        matchTime = NetConfig.MATCH_TIME;
        scoreA = 0;
        scoreB = 0;
        ended = false;
        local.alive = false;
        hud.setMode(cfg.isTdm() ? "TEAM DEATHMATCH - " + map.displayName : "FREE FOR ALL - " + map.displayName);
        hud.banner(cfg.isTdm() ? "TEAM DEATHMATCH" : "FREE FOR ALL", 2.2f);
        if (!roomCode.isEmpty()) {
            hud.toast("Room code: " + roomCode);
        }
    }

    private void sendInput() {
        if (net == null || !net.isOpen() || !gotMatch) return;
        int flags = 0;
        if (local.aiming) flags |= 1;
        if (local.crouching) flags |= 2;
        if (local.sprinting) flags |= 4;
        if (local.reloadTimer > 0f) flags |= 8;
        if (local.firing) flags |= 16;
        net.send(NetClient.obj("t", "input",
                "x", MathUtils.round(local.pos.x * 100f) / 100f,
                "y", MathUtils.round(local.pos.y * 100f) / 100f,
                "z", MathUtils.round(local.pos.z * 100f) / 100f,
                "yaw", MathUtils.round(local.yaw * 1000f) / 1000f,
                "pitch", MathUtils.round(local.pitch * 1000f) / 1000f,
                "f", flags, "w", local.weapon));
    }

    // ---- NetClient.Listener ----

    @Override
    public void onOpen() {
        hud.toast("Connected to server");
    }

    @Override
    public void onClose(String reason) {
        if (!ended && online && !paused) {
            hud.toast("Connection lost: " + reason);
        }
    }

    @Override
    public void onError(String msg) {
        hud.toast("Network error: " + msg);
    }

    @Override
    public void onMessage(String type, JsonValue data) {
        switch (type) {
            case "match":
                handleMatch(data);
                break;
            case "state": {
                JsonValue list = data.get("players");
                if (list == null) break;
                long now = System.currentTimeMillis();
                float t = now / 1000f;
                for (JsonValue p = list.child; p != null; p = p.next) {
                    int id = p.getInt("id");
                    PlayerEntity e = byId.get(id);
                    if (e == null) {
                        e = new PlayerEntity();
                        e.isRemote = true;
                        e.id = id;
                        e.name = p.getString("name", "?");
                        e.team = p.getInt("team", 0);
                        e.primaryChoice = Weapons.AR;
                        e.alive = false;
                        players.add(e);
                        byId.put(id, e);
                    }
                    float x = p.getFloat("x"), y = p.getFloat("y"), z = p.getFloat("z");
                    float yaw = p.getFloat("yaw"), pitch = p.getFloat("pitch");
                    int flags = p.getInt("f", 0);
                    e.aiming = (flags & 1) != 0;
                    e.crouching = (flags & 2) != 0;
                    e.sprinting = (flags & 4) != 0;
                    e.reloadTimer = (flags & 8) != 0 ? Math.max(e.reloadTimer, 0.1f) : Math.min(e.reloadTimer, 0f);
                    if (id == myNetId) {
                        // soft reconciliation
                        float d = Vector3.dst(x, y, z, local.pos.x, local.pos.y, local.pos.z);
                        if (d > 2.5f) {
                            local.pos.set(x, y, z);
                        } else if (d > 0.4f) {
                            local.pos.lerp(tmpV.set(x, y, z), 0.12f);
                        }
                    } else {
                        e.pushNetState(new Vector3(x, y, z), yaw, pitch, t, p.getInt("seq", 0));
                    }
                }
                break;
            }
            case "ev": {
                String e = data.getString("e");
                handleEvent(e, data);
                break;
            }
            case "chat": {
                hud.addChat(data.getString("name", "?"), data.getString("m", ""),
                        new Color(0.75f, 0.85f, 1f, 1f));
                break;
            }
            case "roomCode": {
                roomCode = data.getString("code", "");
                hud.toast("Room code: " + roomCode);
                break;
            }
            case "error":
            case "roomError":
                hud.toast(data.getString("m", "error"));
                break;
            case "pong": {
                if (net != null) net.handlePong(data.getLong("tm"));
                break;
            }
            default:
                break;
        }
    }

    private void handleEvent(String e, JsonValue data) {
        switch (e) {
            case "spawn": {
                int id = data.getInt("id");
                PlayerEntity ent = byId.get(id);
                if (ent == null) {
                    ent = new PlayerEntity();
                    ent.isRemote = true;
                    ent.id = id;
                    ent.name = data.getString("name", "?");
                    ent.team = data.getInt("team", 0);
                    ent.primaryChoice = Weapons.AR;
                    players.add(ent);
                    byId.put(id, ent);
                }
                ent.pos.set(data.getFloat("x"), data.getFloat("y"), data.getFloat("z"));
                ent.hp = data.getFloat("hp", 100f);
                ent.alive = true;
                ent.respawnTimer = 0f;
                ent.resetLoadout();
                if (id == myNetId) {
                    local.alive = true;
                    local.hp = ent.hp;
                    local.pos.set(ent.pos);
                    controller.reset(local);
                    hud.showRespawn(-1f);
                }
                break;
            }
            case "hit": {
                int f = data.getInt("f");
                int v = data.getInt("v");
                float dmg = data.getFloat("dmg");
                float hp = data.getFloat("hp");
                boolean head = data.getBoolean("head", false);
                PlayerEntity victim = byId.get(v);
                if (victim != null) {
                    victim.hp = hp;
                    victim.lastDamageTime = gameTime();
                    if (v == myNetId) {
                        PlayerEntity shooter = byId.get(f);
                        if (shooter != null) hud.showDamage(damageYawFrom(shooter));
                        assets.play(Assets.S_DAMAGE);
                        platform.vibrate(head ? 40 : 20);
                    }
                    if (f == myNetId && v != myNetId) {
                        hud.showHitmarker(head);
                        assets.play(Assets.S_HIT);
                    }
                }
                break;
            }
            case "die": {
                int id = data.getInt("id");
                int by = data.getInt("by");
                boolean head = data.getBoolean("head", false);
                String w = data.getString("w", "");
                float rt = data.getFloat("rt", NetConfig.RESPAWN_TIME);
                PlayerEntity victim = byId.get(id);
                PlayerEntity shooter = byId.get(by);
                if (victim != null) {
                    victim.alive = false;
                    victim.hp = 0f;
                    victim.deaths++;
                }
                if (shooter != null) {
                    shooter.kills++;
                    shooter.score += head ? 150 : 100;
                }
                String an = shooter != null ? shooter.name : "?";
                String vn = victim != null ? victim.name : "?";
                Color ca = shooter != null ? feedColor(shooter) : Palette.WHITE;
                Color cv = victim != null ? feedColor(victim) : Palette.WHITE;
                hud.addFeed(an, vn, w, head, ca, cv);
                if (id == myNetId) {
                    hud.banner("YOU WERE ELIMINATED", 1.6f);
                    local.respawnTimer = rt;
                    hud.showRespawn(rt);
                    assets.play(Assets.S_DAMAGE);
                    platform.vibrate(60);
                }
                if (by == myNetId && id != myNetId) {
                    hud.showKillConfirm(head ? "HEADSHOT +150" : "ELIMINATED +100", head);
                    assets.play(Assets.S_KILL);
                    platform.vibrate(30);
                }
                break;
            }
            case "shot": {
                int id = data.getInt("id");
                if (id == myNetId) break;
                PlayerEntity shooter = byId.get(id);
                float ox = data.getFloat("ox"), oy = data.getFloat("oy"), oz = data.getFloat("oz");
                float dx = data.getFloat("dx"), dy = data.getFloat("dy"), dz = data.getFloat("dz");
                int w = data.getInt("w", 0);
                fx.muzzleFlash(ox, oy, oz, dx, dy, dz);
                fx.tracer(ox, oy, oz, ox + dx * 9f, oy + dy * 9f, oz + dz * 9f, 1f, 0.85f, 0.5f);
                int kind = shooter != null ? shooter.currentSpec().soundKind : 0;
                assets.playDist(kind == 2 ? Assets.S_SHOT_SG : kind == 1 ? Assets.S_SHOT_SMG
                        : kind == 3 ? Assets.S_SHOT_PISTOL : Assets.S_SHOT_AR, distToLocal(ox, oy, oz));
                break;
            }
            case "gren": {
                int id = data.getInt("id");
                if (id == myNetId) break;
                float ox = data.getFloat("ox"), oy = data.getFloat("oy"), oz = data.getFloat("oz");
                float dx = data.getFloat("dx"), dy = data.getFloat("dy"), dz = data.getFloat("dz");
                Grenade g = new Grenade();
                g.x = ox + dx * 0.4f;
                g.y = oy + dy * 0.4f;
                g.z = oz + dz * 0.4f;
                g.vx = dx * Weapons.GRENADE_THROW_SPEED;
                g.vy = dy * Weapons.GRENADE_THROW_SPEED;
                g.vz = dz * Weapons.GRENADE_THROW_SPEED;
                g.fuse = Weapons.GRENADE_FUSE;
                g.ownerId = id;
                grenades.add(g);
                assets.play(Assets.S_PIN, 0.5f);
                break;
            }
            case "boom": {
                float x = data.getFloat("x"), y = data.getFloat("y"), z = data.getFloat("z");
                explodeFx(x, y, z);
                break;
            }
            case "score": {
                scoreA = data.getInt("a", 0);
                scoreB = data.getInt("b", 0);
                break;
            }
            case "end": {
                ended = true;
                boolean win = data.getBoolean("win", false);
                hud.showEnd(win, players, local, cfg.isTdm(), false);
                if (touch != null) touch.setEnabled(false);
                break;
            }
            default:
                break;
        }
    }

    private float gameTime() {
        return System.currentTimeMillis() / 1000f;
    }

    private Color feedColor(PlayerEntity e) {
        if (cfg.isTdm()) return Palette.teamColor(e.team);
        return Palette.WHITE;
    }

    private float distToLocal(float x, float y, float z) {
        return Vector3.dst(x, y, z, local.pos.x, local.pos.y + 1.6f, local.pos.z);
    }

    /** World-space yaw pointing from the local player toward an attacker. */
    private float damageYawFrom(PlayerEntity attacker) {
        return (float) Math.atan2(attacker.pos.z - local.pos.z, attacker.pos.x - local.pos.x);
    }

    // ================================================================ shooting

    @Override
    public void onShoot(PlayerEntity shooter, Vector3 origin, Vector3 dir, Weapons.Spec spec) {
        // Muzzle position. The old implementation stored this in T.tmp1 and
        // then reused T.tmp1 for the spread basis, so every tracer started at
        // the world origin (0, 1, 0).
        muzzleWorld.set(origin).mulAdd(dir, 0.48f);
        fx.muzzleFlash(muzzleWorld.x, muzzleWorld.y, muzzleWorld.z, dir.x, dir.y, dir.z);

        pelletRight.set(-MathUtils.sin(shooter.yaw), 0f, MathUtils.cos(shooter.yaw));
        float eject = settings.leftHanded ? -1f : 1f;
        fx.shell(muzzleWorld.x + pelletRight.x * 0.22f * eject,
                muzzleWorld.y - 0.1f,
                muzzleWorld.z + pelletRight.z * 0.22f * eject,
                pelletRight.x * eject, pelletRight.z * eject);

        if (shooter == local) {
            weaponView.onFire();
            muzzleLightPos.set(muzzleWorld).add(dir.x * 0.4f, dir.y * 0.4f, dir.z * 0.4f);
            muzzleLightIntensity = 14f;
            if (online) {
                net.send(NetClient.obj("t", "shoot",
                        "w", local.weapon,
                        "ox", MathUtils.round(origin.x * 100f) / 100f,
                        "oy", MathUtils.round(origin.y * 100f) / 100f,
                        "oz", MathUtils.round(origin.z * 100f) / 100f,
                        "dx", MathUtils.round(dir.x * 1000f) / 1000f,
                        "dy", MathUtils.round(dir.y * 1000f) / 1000f,
                        "dz", MathUtils.round(dir.z * 1000f) / 1000f));
            }
        } else {
            assets.playDist(spec.soundKind == 2 ? Assets.S_SHOT_SG
                    : spec.soundKind == 1 ? Assets.S_SHOT_SMG
                    : spec.soundKind == 3 ? Assets.S_SHOT_PISTOL
                    : Assets.S_SHOT_AR, distToLocal(origin.x, origin.y, origin.z));
        }

        // Hitscan per pellet.
        for (int p = 0; p < spec.pellets; p++) {
            pelletDir.set(dir);
            if (spec.pellets > 1) {
                float s = 0.055f;
                pelletDir.mulAdd(pelletRight, MathUtils.random(-s, s))
                        .mulAdd(pelletUp.set(0f, 1f, 0f), MathUtils.random(-s, s)).nor();
            }
            float range = spec.range * 2.2f;
            PlayerEntity victim = null;
            float victimDist = Float.MAX_VALUE;
            for (int i = 0; i < players.size; i++) {
                PlayerEntity cand = players.get(i);
                if (!cand.alive || cand == shooter) continue;
                if (shooter.friendlyTo(cand, cfg.isTdm())) continue;
                float t = Collision.rayVsPlayer(origin, pelletDir, cand);
                if (t >= 0f && t < victimDist) {
                    victimDist = t;
                    victim = cand;
                }
            }
            Collision.Hit wallHit = Collision.rayWorld(origin, pelletDir, range, map.solids);
            if (victim != null && victimDist < wallHit.dist) {
                hitPoint.set(pelletDir).scl(victimDist).add(origin);
                fx.tracer(muzzleWorld.x, muzzleWorld.y, muzzleWorld.z,
                        hitPoint.x, hitPoint.y, hitPoint.z, 1f, 0.9f, 0.55f);
                fx.blood(hitPoint.x, hitPoint.y, hitPoint.z, pelletDir.x, pelletDir.y, pelletDir.z);
                boolean head = hitPoint.y > victim.pos.y + victim.height() * 0.86f;
                float falloff = victimDist <= spec.range ? 1f
                        : Math.max(spec.falloffMin, 1f - (victimDist - spec.range) / spec.range * (1f - spec.falloffMin));
                float dmg = spec.damage * falloff * (head ? spec.headMult : 1f);
                if (!online) {
                    applyDamage(shooter, victim, dmg, head);
                }
            } else if (wallHit.hit) {
                hitPoint.set(wallHit.point);
                fx.tracer(muzzleWorld.x, muzzleWorld.y, muzzleWorld.z,
                        hitPoint.x, hitPoint.y, hitPoint.z, 0.9f, 0.75f, 0.45f);
                fx.impactSparks(hitPoint.x, hitPoint.y, hitPoint.z,
                        wallHit.normal.x, wallHit.normal.y, wallHit.normal.z, true);
            } else {
                float endD = Math.min(range, 70f);
                fx.tracer(muzzleWorld.x, muzzleWorld.y, muzzleWorld.z,
                        muzzleWorld.x + pelletDir.x * endD,
                        muzzleWorld.y + pelletDir.y * endD,
                        muzzleWorld.z + pelletDir.z * endD, 1f, 0.85f, 0.5f);
            }
        }
    }

    private void applyDamage(PlayerEntity shooter, PlayerEntity victim, float dmg, boolean head) {
        if (!victim.alive || ended) return;
        victim.takeDamage(dmg, gameTime());
        if (victim == local) {
            hud.showDamage(damageYawFrom(shooter));
            assets.play(Assets.S_DAMAGE);
            platform.vibrate(head ? 40 : 20);
        }
        if (shooter == local) {
            hud.showHitmarker(head);
            assets.play(Assets.S_HIT);
        }
        if (victim.hp <= 0f) {
            onKill(shooter, victim, head);
        }
    }

    private void onKill(PlayerEntity shooter, PlayerEntity victim, boolean head) {
        victim.hp = 0f;
        victim.alive = false;
        victim.deaths++;
        victim.respawnTimer = NetConfig.RESPAWN_TIME;
        shooter.kills++;
        shooter.score += head ? 150 : 100;
        if (shooter == local) {
            hud.showKillConfirm(head ? "HEADSHOT +150" : "ELIMINATED +100", head);
            assets.play(Assets.S_KILL);
            platform.vibrate(30);
        }
        if (victim == local) {
            hud.banner("YOU WERE ELIMINATED", 1.6f);
        }
        hud.addFeed(shooter.name, victim.name, shooter.currentSpec().name,
                head, feedColor(shooter), feedColor(victim));
        if (cfg.isTdm()) {
            // scoreA belongs to team 1, scoreB to team 0. Killing a team 0
            // player therefore scores for team 1.
            if (victim.team == 0) scoreA++;
            else scoreB++;
        }
        checkWin();
    }

    private void checkWin() {
        if (ended) return;
        if (cfg.isTdm()) {
            if (scoreA >= NetConfig.TDM_KILL_LIMIT || scoreB >= NetConfig.TDM_KILL_LIMIT) {
                // scoreA belongs to team 1 and scoreB belongs to team 0.
                boolean win = (scoreA >= NetConfig.TDM_KILL_LIMIT && local.team == 1)
                        || (scoreB >= NetConfig.TDM_KILL_LIMIT && local.team == 0);
                endMatch(win, "TEAM DEATHMATCH");
            }
        } else {
            for (PlayerEntity player : players) {
                if (player.kills >= NetConfig.FFA_KILL_LIMIT) {
                    endMatch(player == local, "FREE FOR ALL");
                    break;
                }
            }
        }
    }

    private void endMatch(boolean win, String modeName) {
        if (ended) return;
        ended = true;
        hud.showEnd(win, players, local, cfg.isTdm(), !online);
        if (touch != null) touch.setEnabled(false);
        if (platform.isAndroid()) {
            // nothing extra
        }
    }

    private void explodeFx(float x, float y, float z) {
        fx.explosion(x, y, z);
        assets.playDist(Assets.S_EXPLOSION, distToLocal(x, y, z));
        platform.vibrate(35);
    }

    private void explodeGrenade(Grenade g) {
        explodeFx(g.x, g.y, g.z);
        if (!online) {
            PlayerEntity owner = byId.get(g.ownerId);
            for (int i = 0; i < players.size; i++) {
                PlayerEntity victim = players.get(i);
                if (!victim.alive) continue;
                if (owner != null && owner.friendlyTo(victim, cfg.isTdm())) continue;
                float d = Vector3.dst(g.x, g.y, g.z, victim.pos.x, victim.pos.y + 1f, victim.pos.z);
                if (d > Weapons.GRENADE_RADIUS) continue;
                // Walls stop blast damage; otherwise grenades kill through cover.
                tmpV2.set(victim.pos.x, victim.pos.y + 1f, victim.pos.z);
                if (!Collision.lineOfSight(tmpV.set(g.x, g.y + 0.1f, g.z), tmpV2, map.solids)) continue;
                float t = d / Weapons.GRENADE_RADIUS;
                float dmg = Weapons.GRENADE_MIN_DAMAGE
                        + (Weapons.GRENADE_DAMAGE - Weapons.GRENADE_MIN_DAMAGE) * (1f - t);
                applyDamage(owner != null ? owner : victim, victim, dmg, false);
            }
        }
    }

    @Override
    public void onGrenade(PlayerEntity e, Vector3 origin, Vector3 dir) {
        Grenade g = new Grenade();
        g.x = origin.x + dir.x * 0.4f;
        g.y = origin.y + dir.y * 0.4f;
        g.z = origin.z + dir.z * 0.4f;
        g.vx = dir.x * Weapons.GRENADE_THROW_SPEED;
        g.vy = dir.y * Weapons.GRENADE_THROW_SPEED + 1.35f;
        g.vz = dir.z * Weapons.GRENADE_THROW_SPEED;
        g.fuse = Weapons.GRENADE_FUSE;
        g.ownerId = e.id;
        grenades.add(g);
        if (online && e == local) {
            net.send(NetClient.obj("t", "grenade",
                    "ox", MathUtils.round(origin.x * 100f) / 100f,
                    "oy", MathUtils.round(origin.y * 100f) / 100f,
                    "oz", MathUtils.round(origin.z * 100f) / 100f,
                    "dx", MathUtils.round(dir.x * 1000f) / 1000f,
                    "dy", MathUtils.round(dir.y * 1000f) / 1000f,
                    "dz", MathUtils.round(dir.z * 1000f) / 1000f));
        }
    }

    @Override
    public void onStep(PlayerEntity e) {
        if (e == local && e.onGround) {
            assets.play(Assets.S_STEP, 0.22f);
        }
    }

    // ================================================================ hud callbacks

    @Override
    public void onResume() {
        hud.hidePause();
        paused = false;
        if (touch != null) touch.setEnabled(true);
        Gdx.input.setCursorCatched(!platform.isAndroid());
    }

    @Override
    public void onLeave() {
        leaveMatch();
    }

    @Override
    public void onShare() {
        String text;
        if (online && !roomCode.isEmpty()) {
            text = "Join my TactShoot room! Code: " + roomCode
                    + " - TactShoot, the mobile multiplayer FPS";
        } else if (!ended) {
            text = "I'm playing TactShoot " + (cfg.isTdm() ? "TDM" : "FFA") + " on " + map.displayName
                    + " - kills: " + local.kills + "!";
        } else {
            text = "I just finished a TactShoot match with " + local.kills + " kills on "
                    + map.displayName + "!";
        }
        platform.shareText(text);
    }

    @Override
    public void onSettings() {
        game.setScreen(new SettingsScreen(game, this));
    }

    @Override
    public void onSendChat(String text) {
        if (online && net != null && net.isOpen()) {
            net.send(NetClient.obj("t", "chat", "m", text));
            hud.addChat(local.name, text, Palette.ACCENT);
        }
    }

    @Override
    public void onRematch() {
        if (!online) {
            game.setScreen(new GameScreen(game, cfg));
        }
    }

    private void leaveMatch() {
        if (net != null) {
            try {
                net.send(NetClient.obj("t", "leave"));
            } catch (Exception ignored) {
            }
            net.close();
            net = null;
        }
        game.setScreen(new MenuScreen(game));
    }

    // ================================================================ hud context

    @Override
    public PlayerEntity local() {
        return local;
    }

    @Override
    public Array<PlayerEntity> players() {
        return players;
    }

    @Override
    public boolean tdm() {
        return cfg.isTdm();
    }

    @Override
    public float matchTime() {
        return matchTime;
    }

    @Override
    public int scoreA() {
        return scoreA;
    }

    @Override
    public int scoreB() {
        return scoreB;
    }

    @Override
    public float ping() {
        return net != null ? net.rtt() : -1f;
    }

    @Override
    public String modeText() {
        return cfg.isTdm() ? "TEAM DEATHMATCH" : "FREE FOR ALL";
    }

    @Override
    public String mapName() {
        return map != null ? map.displayName : "";
    }

    @Override
    public boolean online() {
        return online;
    }

    @Override
    public String roomCode() {
        return roomCode;
    }

    // ================================================================ lifecycle

    private void doPause() {
        paused = true;
        hud.showPause(online, online && !roomCode.isEmpty());
        if (touch != null) touch.setEnabled(false);
        Gdx.input.setCursorCatched(false);
    }

    @Override
    public void pause() {
        if (!paused && !ended) doPause();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(multiplexer);
        Gdx.input.setCursorCatched(!platform.isAndroid() && !paused);
        if (desk != null) desk.aimToggle = settings.aimToggle;
        if (touch != null) touch.setAutoFireEnabled(settings.autoFire);
        if (appliedQuality != settings.quality) applyQuality();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        post.resize(width, height);
        hud.resize(width, height);
        if (touch != null) touch.resize(width, height);
    }

    @Override
    public void dispose() {
        if (net != null) {
            try {
                net.send(NetClient.obj("t", "leave"));
            } catch (Exception ignored) {
            }
            net.close();
            net = null;
        }
        for (ModelInstance mi : soldierInst.values()) {
            // instances share models; only models are disposed via assets
        }
        soldierInst.clear();
        if (fx != null) fx.dispose();
        if (sky != null) sky.dispose();
        if (post != null) post.dispose();
        if (hud != null) hud.dispose();
        if (uiBatch != null) uiBatch.dispose();
        if (sr != null) sr.dispose();
        if (batch != null) batch.dispose();
        if (shadowLight != null) {
            shadowLight.dispose();
            shadowLight = null;
        }
    }

    // ================================================================ update

    private void update(float dt) {
        if (net != null) net.update();
        hud.update(dt);

        float raw = Math.max(0.0001f, Gdx.graphics.getDeltaTime());
        fps += (1f / raw - fps) * 0.06f;

        InputState in;
        if (touch != null) {
            touch.updateState();
            in = touch.state;
        } else {
            if (hud.isChatMode() && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                hud.hideChat();
            }
            if (desk != null) {
                desk.setEnabled(!hud.isChatMode());
                desk.setLookEnabled(!hud.isChatMode());
            }
            desk.poll();
            in = desk.state;
        }

        // Pause / resume. The old order cleared pausePressed before testing it,
        // so Escape and the Android back button could never resume a match.
        if (in.pausePressed) {
            if (paused) onResume();
            else doPause();
            in.resetFrame();
            return;
        }
        if (paused) {
            in.resetFrame();
            return;
        }
        if (!ready || map == null) {
            in.resetFrame();
            return;
        }
        if (ended) {
            in.resetFrame();
            return;
        }

        // chat
        if (in.chatPressed) {
            if (online) {
                if (platform.isAndroid()) {
                    Gdx.input.getTextInput(new Input.TextInputListener() {
                        @Override
                        public void input(String text) {
                            if (text != null && !text.trim().isEmpty()) {
                                onSendChat(text.trim());
                            }
                        }

                        @Override
                        public void canceled() {
                        }
                    }, "Chat", "", "Send a message...");
                } else {
                    hud.showChat();
                }
            }
        }

        // scoreboard
        if (in.scoreboard) {
            if (!scoreboardOpen) {
                hud.showScoreboard(players, local, cfg.isTdm());
                scoreboardOpen = true;
            }
        } else {
            if (scoreboardOpen) {
                hud.hideScoreboard();
                scoreboardOpen = false;
            }
        }

        // afk
        if (Math.abs(in.moveX) + Math.abs(in.moveY) + Math.abs(in.lookDX) + Math.abs(in.lookDY) > 0.01f
                || in.fire) {
            afk = 0f;
        } else {
            afk += dt;
        }
        if (online && afk > 240f) {
            hud.toast("Kicked for inactivity");
            leaveMatch();
            return;
        }

        // match timer
        if (matchTime > 0f) {
            matchTime -= dt;
            if (matchTime <= 0f) {
                matchTime = 0f;
                if (!online) {
                    boolean win;
                    if (cfg.isTdm()) {
                        win = (scoreA > scoreB && local.team == 1)
                                || (scoreB > scoreA && local.team == 0);
                    } else {
                        win = true;
                        for (PlayerEntity p : players) {
                            if (p.kills > local.kills) win = false;
                        }
                    }
                    endMatch(win, cfg.isTdm() ? "TEAM DEATHMATCH" : "FREE FOR ALL");
                }
            }
        }

        // local control
        if (local.alive) {
            controller.update(local, in, dt, map.solids);
            if (local.weapon != lastWeapon) {
                lastWeapon = local.weapon;
                weaponView.setWeapon(local.weapon == 0 ? local.primaryChoice : Weapons.PISTOL);
            }
        } else if (!online) {
            local.respawnTimer -= dt;
            if (local.respawnTimer <= 0f) {
                spawnEntity(local, false);
            } else {
                hud.showRespawn(local.respawnTimer);
            }
        } else {
            if (local.respawnTimer > 0f) {
                local.respawnTimer -= dt;
                hud.showRespawn(local.respawnTimer);
            }
        }

        // bots
        for (int i = 0; i < players.size; i++) {
            PlayerEntity b = players.get(i);
            if (!b.isBot) continue;
            if (b.alive) {
                botAi.update(b, players, dt);
            } else {
                b.respawnTimer -= dt;
                if (b.respawnTimer <= 0f) {
                    spawnEntity(b, false);
                }
            }
        }

        // Combatants should not share the same body space.
        Collision.separatePlayers(players);
        for (int i = 0; i < players.size; i++) {
            PlayerEntity p = players.get(i);
            if (p.isLocal || !p.alive || !p.moving) continue;
            float sp = (float) Math.sqrt(p.vel.x * p.vel.x + p.vel.z * p.vel.z);
            p.bobPhase += sp * dt * 1.4f;
        }

        // remote interpolation
        float renderTime = gameTime() - NetConfig.INTERP_DELAY;
        for (int i = 0; i < players.size; i++) {
            PlayerEntity r = players.get(i);
            if (r.isRemote) {
                r.applyInterpolation(renderTime);
            }
        }

        // grenades: axis-separated bounce, fuse detonation (never on impact)
        for (int i = grenades.size - 1; i >= 0; i--) {
            Grenade g = grenades.get(i);
            float step = Math.min(dt, 0.033f);
            g.vy -= 9.8f * step;
            g.fuse -= dt;

            float nx = g.x + g.vx * step;
            if (grenadeHitsWorld(nx, g.y, g.z)) {
                g.vx = -g.vx * 0.45f;
            } else {
                g.x = nx;
            }
            float ny = g.y + g.vy * step;
            if (grenadeHitsWorld(g.x, ny, g.z)) {
                g.vy = -g.vy * 0.40f;
                if (Math.abs(g.vy) < 0.7f) g.vy = 0f;
            } else {
                g.y = ny;
            }
            float nz = g.z + g.vz * step;
            if (grenadeHitsWorld(g.x, g.y, nz)) {
                g.vz = -g.vz * 0.45f;
            } else {
                g.z = nz;
            }
            if (g.y < 0.07f) {
                g.y = 0.07f;
                if (g.vy < -0.6f) g.vy = -g.vy * 0.40f;
                else g.vy = 0f;
                g.vx *= 0.74f;
                g.vz *= 0.74f;
            }

            g.trail -= dt;
            if (g.trail <= 0f) {
                g.trail = 0.04f;
                fx.grenadeTrail(g.x, g.y, g.z);
            }
            if (g.fuse <= 0f) {
                explodeGrenade(g);
                grenades.removeIndex(i);
            }
        }

        // fx + weapon + camera
        fx.update(dt);
        float speed = (float) Math.sqrt(local.vel.x * local.vel.x + local.vel.z * local.vel.z);
        weaponView.update(dt, local, speed);

        // Smooth crouch height, walking head-bob and landing dip.
        float targetEye = local.crouching ? 1.02f : 1.62f;
        camEye += (targetEye - camEye) * T.damp(13f, dt);
        float bobScale = local.aiming ? 0.22f : (local.sprinting ? 1.35f : 1f);
        float bobX = MathUtils.cos(local.bobPhase * 0.95f) * 0.014f * bobScale;
        float bobY = MathUtils.sin(local.bobPhase * 1.9f) * 0.019f * bobScale;
        camRight.set(-MathUtils.sin(local.yaw), 0f, MathUtils.cos(local.yaw));
        tmpV.set(local.pos.x + camRight.x * bobX,
                local.pos.y + camEye + bobY - local.landImpact * 0.22f,
                local.pos.z + camRight.z * bobX);
        cam.position.set(tmpV);
        T.dirFromAngles(local.yaw, local.pitch, cam.direction);
        cam.up.set(0f, 1f, 0f);
        float hFov = local.aiming && local.alive
                ? local.currentSpec().adsFov
                : settings.fov + (local.sprinting ? 4f : 0f);
        float targetFov = hFovToV(hFov, cam.viewportWidth / Math.max(1f, cam.viewportHeight));
        cam.fieldOfView += (targetFov - cam.fieldOfView) * T.damp(13f, dt);
        cam.update();

        hud.setCrosshairSpread(local.spread * 2100f);

        // muzzle light decay
        muzzleLightIntensity = Math.max(0f, muzzleLightIntensity - muzzleLightIntensity * 8f * dt - 0.2f);
        muzzleLight.intensity = muzzleLightIntensity;
        muzzleLight.position.set(muzzleLightPos);

        // HUD state
        hud.setHp(local.hp);
        hud.setAmmo(local.ammo(), local.reserveAmmo(), local.grenades,
                local.weapon == 0 ? local.currentSpec().name : "PISTOL");
        hud.setTimer(T.fmtTime(matchTime));
        if (cfg.isTdm()) {
            // scoreA = team 1 kills, scoreB = team 0 kills. Show own team first.
            int own = local.team == 0 ? scoreB : scoreA;
            int enemy = local.team == 0 ? scoreA : scoreB;
            hud.setScore(own + "  -  " + enemy);
        } else {
            hud.setScore(local.kills + " / " + NetConfig.FFA_KILL_LIMIT);
        }
        hud.setPing(online ? (net != null ? net.rtt() : -1f) : -1f);
        hud.setFps(settings.showFps ? fps : -1f);

        // Adaptive quality: high-end effects must never make the game unplayable.
        qualityTimer += dt;
        if (settings.autoQuality && settings.quality > 0 && qualityTimer > 5f && !ended) {
            if (fps < 42f) slowTimer += dt;
            else slowTimer = Math.max(0f, slowTimer - dt * 1.5f);
            if (slowTimer > 3.5f) {
                settings.quality--;
                settings.save();
                applyQuality();
                slowTimer = 0f;
                qualityTimer = 0f;
                hud.toast("Auto graphics: " + settings.qualityName());
            }
        }

        // net send
        if (online) {
            sendTimer -= dt;
            if (sendTimer <= 0f) {
                sendTimer = 1f / NetConfig.INPUT_RATE;
                sendInput();
            }
        }

        // post fx
        post.setDamage(hud.damageFlash());
        post.setLowHp(local.alive && local.hp <= 30f && !ended ? 1f : 0f);

        in.resetFrame();
    }

    // ================================================================ render

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);
        update(dt);

        if (!ready || map == null) {
            Gdx.gl.glClearColor(0.035f, 0.045f, 0.065f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            hud.draw(uiBatch);
            return;
        }

        // Shadow pass MUST run before the post buffer is bound. Frame buffers
        // nest badly: DirectionalShadowLight.end() rebinds the default
        // framebuffer, so running it inside the post pass sent the world to the
        // screen, which the post pass then covered with its own (sky-only) buffer.
        if (shadows && shadowLight != null) {
            T.dirFromAngles(local.yaw, 0f, tmpV2);
            tmpV.set(local.pos.x + tmpV2.x * 12f, 2f, local.pos.z + tmpV2.z * 12f);
            shadowLight.begin(tmpV, lightDir);
            batch.begin(shadowLight.getCamera());
            renderWorld();
            renderPlayers();
            batch.end();
            shadowLight.end();
        }

        if (postOn && !paused) {
            post.begin();
        }
        Gdx.gl.glClearColor(map.fogCol.r, map.fogCol.g, map.fogCol.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        sky.render(cam, map);

        // main pass
        batch.begin(cam);
        renderWorld();
        renderPlayers();
        renderGrenades();
        batch.end();

        fx.render(cam, true);
        fx.render(cam, false);

        if (local.alive && !ended) {
            weaponView.render(batch, cam, local.yaw, local.pitch);
        }

        if (postOn && !paused) {
            post.end();
        }

        // HUD
        hud.renderOverlay(uiBatch, sr, this, local.alive, local.aiming);
        hud.draw(uiBatch);
        if (touch != null) {
            sr.setProjectionMatrix(hud.stage().getCamera().combined);
            touch.render(sr, uiBatch, assets.fontSmall, assets);
        }
    }

    private void renderWorld() {
        // Passing the environment is essential: without it DefaultShader has
        // no lights and draws every world surface black.
        batch.render(worldInst, env);
        if (props != null) {
            for (int i = 0; i < props.size; i++) {
                batch.render(props.get(i), env);
            }
        }
    }

    private void renderPlayers() {
        for (int i = 0; i < players.size; i++) {
            PlayerEntity e = players.get(i);
            if (e.isLocal || !e.alive) continue;
            ModelInstance mi = soldierInst.get(e.id);
            if (mi == null) {
                mi = new ModelInstance(assets.soldierModel);
                Color teamCol = cfg.isTdm() ? Palette.teamColor(e.team) : Palette.FFA_NEUTRAL;
                if (mi.materials.size > ModelFactory.TORSO_IDX) {
                    mi.materials.get(ModelFactory.TORSO_IDX).set(ColorAttribute.createDiffuse(teamCol));
                }
                if (mi.materials.size > ModelFactory.HELMET_IDX) {
                    mi.materials.get(ModelFactory.HELMET_IDX).set(ColorAttribute.createDiffuse(teamCol));
                }
                soldierInst.put(e.id, mi);
            }
            // The soldier model faces +Z. Rotating by (90 - yaw) maps +Z onto
            // the yaw direction; the old (+90) rotated every enemy backwards.
            float walk = e.moving ? MathUtils.sin(e.bobPhase * 5f) * 2.4f : 0f;
            float crouch = T.clamp(e.crouchAmount, 0f, 1f);
            mi.transform.idt()
                    .translate(e.pos.x, e.pos.y, e.pos.z)
                    .rotate(Vector3.Y, 90f - e.yaw * MathUtils.radiansToDegrees)
                    .rotate(Vector3.X, walk)
                    .scale(1f, 1f - crouch * 0.36f, 1f);
            batch.render(mi, env);
        }
    }

    private void renderGrenades() {
        if (grenadeInst == null) return;
        for (int i = 0; i < grenades.size; i++) {
            Grenade g = grenades.get(i);
            grenadeInst.transform.idt()
                    .translate(g.x, g.y, g.z)
                    .rotate(Vector3.Y, (g.fuse * 480f) % 360f);
            batch.render(grenadeInst, env);
        }
    }

    private boolean grenadeHitsWorld(float x, float y, float z) {
        for (int i = 0; i < map.solids.size; i++) {
            Box b = map.solids.get(i);
            if (x > b.minX - 0.05f && x < b.maxX + 0.05f
                    && y > b.minY - 0.05f && y < b.maxY + 0.05f
                    && z > b.minZ - 0.05f && z < b.maxZ + 0.05f) {
                return true;
            }
        }
        return false;
    }

    /** Converts a horizontal FOV in degrees to libGDX's vertical FOV. */
    private static float hFovToV(float hDeg, float aspect) {
        float h = hDeg * MathUtils.degreesToRadians * 0.5f;
        return (float) (2.0 * Math.atan(Math.tan(h) / aspect) * MathUtils.radiansToDegrees);
    }
}
