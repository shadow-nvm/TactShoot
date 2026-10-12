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

    // entities
    private final Array<PlayerEntity> players = new Array<>();
    private PlayerEntity local;
    private final IntMap<PlayerEntity> byId = new IntMap<>();
    private final IntMap<ModelInstance> soldierInst = new IntMap<>();
    private PlayerController controller;
    private BotAI botAi;
    private WeaponView weaponView;
    private int lastWeapon = -1;

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
        players.add(local);
        byId.put(local.id, local);

        controller = new PlayerController(settings, assets, this);
        weaponView = new WeaponView(assets, settings);
        fx = new Fx(assets);
        sky = new Atmosphere(assets);
        post = new PostFx(assets);
        hud = new Hud(assets, settings, this);
        uiBatch = new SpriteBatch();
        sr = new ShapeRenderer();

        if (platform.isAndroid()) {
            touch = new TouchControls();
            touch.setSensitivity(1f);
        } else {
            desk = new DesktopControls();
            desk.setSensitivity(1f);
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
            setupMap(cfg.mapIdx);
            startPracticeMatch();
        }

        post.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (touch != null) touch.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        int q = settings.quality;
        shadows = q >= 1;
        postOn = q >= 1;
        applyQuality();
    }

    // ================================================================ setup

    private void applyQuality() {
        env.clear();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, map == null ? 0.4f : map.ambient.r,
                map == null ? 0.4f : map.ambient.g, map == null ? 0.4f : map.ambient.b, 1f));
        if (map != null) {
            lightDir.set(map.sunDir).scl(-1f);
            env.add(new DirectionalLight().set(map.sunColor, lightDir));
        }
        if (shadows && map != null) {
            try {
                shadowLight = new DirectionalShadowLight(1024, 1024, 90f, 90f, 1f, 160f);
                shadowLight.set(map.sunColor, lightDir);
                env.add(shadowLight);
                env.shadowMap = shadowLight;
            } catch (Throwable t) {
                Gdx.app.error("TactShoot", "ShadowLight init failed, disabling shadows: " + t.getMessage());
                shadowLight = null;
                env.shadowMap = null;
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
        applyQuality();
    }

    private void startPracticeMatch() {
        setupMap(cfg.mapIdx);
        // bots
        for (int i = 0; i < cfg.botCount; i++) {
            PlayerEntity bot = new PlayerEntity();
            bot.isBot = true;
            bot.name = Names.random();
            bot.id = 1 + i;
            bot.primaryChoice = new int[]{Weapons.AR, Weapons.SMG, Weapons.SHOTGUN}[MathUtils.random(2)];
            bot.botLevel = MathUtils.clamp(cfg.botLevel + MathUtils.random(-0.08f, 0.08f), 0.1f, 1f);
            bot.team = cfg.isTdm() ? (i % 2) : 100 + i;
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
                        if (shooter != null) hud.showDamage(shooter.yaw);
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

    // ================================================================ shooting

    @Override
    public void onShoot(PlayerEntity shooter, Vector3 origin, Vector3 dir, Weapons.Spec spec) {
        // muzzle world pos
        Vector3 muzzle = T.tmp1.set(origin).mulAdd(dir, 0.55f);
        fx.muzzleFlash(muzzle.x, muzzle.y, muzzle.z, dir.x, dir.y, dir.z);
        // eject shell
        Vector3 right = T.tmp2.set(MathUtils.sin(shooter.yaw), 0f, -MathUtils.cos(shooter.yaw));
        fx.shell(muzzle.x + right.x * 0.22f, muzzle.y - 0.1f, muzzle.z + right.z * 0.22f,
                settings.leftHanded ? -right.x : right.x, settings.leftHanded ? -right.z : right.z);

        if (shooter == local) {
            weaponView.onFire();
            muzzleLightPos.set(muzzle).add(dir.x * 0.4f, dir.y * 0.4f, dir.z * 0.4f);
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

        // hitscan per pellet
        Vector3 pelletDir = T.tmp3;
        Vector3 rightV = T.tmp4.set(MathUtils.sin(shooter.yaw), 0f, -MathUtils.cos(shooter.yaw));
        Vector3 upV = T.tmp1.set(0, 1, 0);
        for (int p = 0; p < spec.pellets; p++) {
            pelletDir.set(dir);
            if (spec.pellets > 1) {
                float s = 0.055f;
                pelletDir.mulAdd(rightV, MathUtils.random(-s, s))
                        .mulAdd(upV, MathUtils.random(-s, s)).nor();
            }
            float range = spec.range * 2.2f;
            // player hit
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
            Vector3 hitPoint;
            if (victim != null && victimDist < wallHit.dist) {
                hitPoint = T.tmp2.set(pelletDir).scl(victimDist).add(origin);
                fx.tracer(muzzle.x, muzzle.y, muzzle.z, hitPoint.x, hitPoint.y, hitPoint.z,
                        1f, 0.9f, 0.55f);
                fx.blood(hitPoint.x, hitPoint.y, hitPoint.z, pelletDir.x, pelletDir.y, pelletDir.z);
                boolean head = hitPoint.y > victim.pos.y + 1.42f;
                float falloff = victimDist <= spec.range ? 1f
                        : Math.max(spec.falloffMin, 1f - (victimDist - spec.range) / spec.range * (1f - spec.falloffMin));
                float dmg = spec.damage * falloff * (head ? spec.headMult : 1f);
                if (!online) {
                    applyDamage(shooter, victim, dmg, head);
                }
            } else if (wallHit.hit) {
                hitPoint = wallHit.point;
                fx.tracer(muzzle.x, muzzle.y, muzzle.z, hitPoint.x, hitPoint.y, hitPoint.z,
                        0.9f, 0.75f, 0.45f);
                fx.impactSparks(hitPoint.x, hitPoint.y, hitPoint.z,
                        wallHit.normal.x, wallHit.normal.y, wallHit.normal.z, true);
            } else {
                // long miss: fade the tracer out
                float endD = Math.min(range, 55f);
                fx.tracer(muzzle.x, muzzle.y, muzzle.z,
                        muzzle.x + pelletDir.x * endD, muzzle.y + pelletDir.y * endD,
                        muzzle.z + pelletDir.z * endD, 1f, 0.85f, 0.5f);
            }
        }
    }

    private void applyDamage(PlayerEntity shooter, PlayerEntity victim, float dmg, boolean head) {
        if (!victim.alive || ended) return;
        victim.takeDamage(dmg, gameTime());
        if (victim == local) {
            hud.showDamage(shooter.yaw);
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
            if (victim.team == 0) scoreB++;
            else scoreA++;
        }
        checkWin();
    }

    private void checkWin() {
        if (ended) return;
        if (cfg.isTdm()) {
            if (scoreA >= NetConfig.TDM_KILL_LIMIT || scoreB >= NetConfig.TDM_KILL_LIMIT) {
                boolean win = (scoreA >= NetConfig.TDM_KILL_LIMIT && local.team == 0)
                        || (scoreB >= NetConfig.TDM_KILL_LIMIT && local.team == 1);
                endMatch(win, "TEAM DEATHMATCH");
            }
        } else {
            if (local.kills >= NetConfig.FFA_KILL_LIMIT) {
                endMatch(true, "FREE FOR ALL");
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
        g.vy = dir.y * Weapons.GRENADE_THROW_SPEED + 1.4f;
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
        // open settings overlay later; for now simple toast
        hud.toast("Settings are available from the main menu");
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
        Gdx.input.setCursorCatched(!platform.isAndroid());
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
    }

    // ================================================================ update

    private void update(float dt) {
        if (net != null) net.update();
        hud.update(dt);

        InputState in;
        if (touch != null) {
            touch.pollLook();
            touch.updateState();
            in = touch.state;
        } else {
            desk.poll();
            in = desk.state;
        }

        if (paused) {
            in.resetFrame();
            if (in.pausePressed) {
                onResume();
            }
            return;
        }
        if (ended) {
            in.resetFrame();
            return;
        }

        if (in.pausePressed) {
            doPause();
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
                        win = (scoreA > scoreB && local.team == 0) || (scoreB > scoreA && local.team == 1);
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

        // remote interpolation
        float renderTime = gameTime() - NetConfig.INTERP_DELAY;
        for (int i = 0; i < players.size; i++) {
            PlayerEntity r = players.get(i);
            if (r.isRemote) {
                r.applyInterpolation(renderTime);
            }
        }

        // grenades
        for (int i = grenades.size - 1; i >= 0; i--) {
            Grenade g = grenades.get(i);
            g.x += g.vx * dt;
            g.y += g.vy * dt;
            g.z += g.vz * dt;
            g.vy -= 9.8f * dt;
            g.trail -= dt;
            if (g.trail <= 0f) {
                g.trail = 0.03f;
                fx.grenadeTrail(g.x, g.y, g.z);
            }
            g.fuse -= dt;
            boolean hitWall = false;
            for (Box b : map.solids) {
                if (g.x > b.minX && g.x < b.maxX && g.y > b.minY && g.y < b.maxY
                        && g.z > b.minZ && g.z < b.maxZ) {
                    hitWall = true;
                    break;
                }
            }
            if (hitWall || g.y <= 0.03f || g.fuse <= 0f) {
                explodeGrenade(g);
                grenades.removeIndex(i);
            }
        }

        // fx + weapon + camera
        fx.update(dt);
        float speed = (float) Math.sqrt(local.vel.x * local.vel.x + local.vel.z * local.vel.z);
        weaponView.update(dt, local, speed);

        tmpV.set(local.pos.x, local.pos.y + local.eyeY(), local.pos.z);
        cam.position.set(tmpV);
        T.dirFromAngles(local.yaw, local.pitch, cam.direction);
        cam.up.set(0f, 1f, 0f);
        float targetFov = local.aiming && local.alive
                ? local.currentSpec().adsFov : settings.fov;
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
            hud.setScore(scoreA + "  -  " + scoreB);
        } else {
            hud.setScore(local.kills + " / " + NetConfig.FFA_KILL_LIMIT);
        }
        hud.setPing(online ? (net != null ? net.rtt() : -1f) : -1f);

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

        if (postOn && !paused) {
            post.begin();
        }
        Gdx.gl.glClearColor(map.fogCol.r, map.fogCol.g, map.fogCol.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        sky.render(cam, map);

        // shadow pass
        if (shadows && shadowLight != null) {
            tmpV.set(local.pos).add(0f, 2f, 0f);
            tmpV2.set(map.sunDir).scl(60f).add(tmpV);
            shadowLight.begin(tmpV, lightDir);
            batch.begin(shadowLight.getCamera());
            renderWorld();
            renderPlayers();
            batch.end();
            shadowLight.end();
        }

        // main pass
        batch.begin(cam);
        renderWorld();
        renderPlayers();
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
        batch.render(worldInst);
        if (props != null) {
            for (int i = 0; i < props.size; i++) {
                batch.render(props.get(i));
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
            mi.transform.idt()
                    .translate(e.pos.x, e.pos.y, e.pos.z)
                    .rotate(Vector3.Y, e.yaw * MathUtils.radiansToDegrees + 90f)
                    .scale(1f, e.crouching ? 0.80f : 1f, 1f);
            batch.render(mi, env);
        }
    }
}
