package com.tactshoot.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/** Central asset cache: textures, fonts, sounds, models, shaders, maps. */
public class Assets implements Disposable {

    // sounds
    public static final int S_SHOT_AR = 0, S_SHOT_SMG = 1, S_SHOT_SG = 2, S_SHOT_PISTOL = 3,
            S_DRY = 4, S_RELOAD = 5, S_PUMP = 6, S_HIT = 7, S_KILL = 8, S_DAMAGE = 9,
            S_EXPLOSION = 10, S_CLICK = 11, S_PIN = 12, S_STEP = 13, S_SWITCH = 14;

    public final ObjectMap<String, Texture> textures = new ObjectMap<String, Texture>();
    public Texture dot, crosshairTex, hitmarkerTex, flashTex, logo;

    public BitmapFont fontTitle, fontBig, font, fontSmall;
    public Skin skin;

    public Sound[] snd = new Sound[15];

    public Model gunAR, gunSMG, gunSG, gunPistol;
    public Model soldierModel;

    public MapDef[] maps;
    public Model[] worldModels;
    public Array<ModelInstance>[] worldProps;
    public NavGrid[] navGrids;

    public ShaderProgram skyShader, postShader, particleShader;

    public float uiScale = 1f;
    private boolean soundOn = true;

    public void setSoundOn(boolean on) {
        soundOn = on;
    }

    // ---------------------------------------------------------------- load

    public void load(Settings settings) {
        soundOn = settings.soundOn;
        uiScale = MathUtils.clamp(Gdx.graphics.getHeight() / 720f, 0.8f, 1.6f);

        loadSprites();
        loadFonts();
        skin = UiSkin.build(font, fontSmall);
        loadSounds();
        loadShaders();
        loadTextures();
        loadModels();
        loadMaps();
    }

    private void loadSprites() {
        dot = new Texture(TexGen.softDot(64, Color.WHITE, 1f));
        dot.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        crosshairTex = new Texture(TexGen.crosshair(96, new Color(0.3f, 1f, 0.5f, 1f)));
        crosshairTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        hitmarkerTex = new Texture(TexGen.hitmarker(96, Palette.ACCENT));
        hitmarkerTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        flashTex = new Texture(TexGen.flash(128));
        flashTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        FileHandle logoF = Gdx.files.internal("ui/logo.png");
        if (logoF.exists()) {
            logo = new Texture(logoF);
            logo.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
    }

    private void loadFonts() {
        FileHandle orbitron = Gdx.files.internal("fonts/Orbitron-VF.ttf");
        FileHandle roboto = Gdx.files.internal("fonts/Roboto-VF.ttf");
        String extra = "\u2191\u2192\u2193\u2190\u2713\u00d7\u2022\u00b0\u2013\u2014\u2026\u2300\u25a0\u25cf";
        if (orbitron.exists()) {
            fontTitle = genFont(orbitron, (int) (48 * uiScale), extra);
            fontBig = genFont(orbitron, (int) (30 * uiScale), extra);
        } else {
            fontTitle = null;
            fontBig = null;
        }
        if (roboto.exists()) {
            font = genFont(roboto, (int) (23 * uiScale), extra);
            fontSmall = genFont(roboto, (int) (16 * uiScale), extra);
        } else {
            font = new BitmapFont();
            fontSmall = new BitmapFont();
            font.getData().setScale(uiScale);
            fontSmall.getData().setScale(uiScale * 0.7f);
        }
    }

    private BitmapFont genFont(FileHandle f, int size, String extra) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(f);
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + extra;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        BitmapFont font = gen.generateFont(p);
        gen.dispose();
        return font;
    }

    private void loadSounds() {
        String[] names = {"shot_ar", "shot_smg", "shot_sg", "shot_pistol", "dry", "reload", "pump",
                "hit", "kill", "damage", "explosion", "click", "pin", "step", "switch"};
        byte[][] data = {
                SoundSynth.gunshot(11, 0, 1f), SoundSynth.gunshot(21, 1, 1f), SoundSynth.gunshot(31, 2, 1f),
                SoundSynth.gunshot(41, 3, 1f), SoundSynth.dryFire(51), SoundSynth.reload(61),
                SoundSynth.pump(71), SoundSynth.hitmarker(81), SoundSynth.killConfirm(91),
                SoundSynth.damage(101), SoundSynth.explosion(111), SoundSynth.click(121),
                SoundSynth.pin(131), SoundSynth.step(141), SoundSynth.weaponSwitch(151)
        };
        for (int i = 0; i < names.length; i++) {
            FileHandle f = Gdx.files.local("tsnd/" + names[i] + ".wav");
            if (!f.exists()) {
                try {
                    if (f.parent() != null) f.parent().mkdirs();
                    f.writeBytes(data[i], false);
                } catch (Exception e) {
                    Gdx.app.error("TactShoot", "Failed to cache sound " + names[i] + ": " + e.getMessage());
                }
            }
            try {
                snd[i] = Gdx.audio.newSound(f);
            } catch (Exception e) {
                snd[i] = null;
            }
        }
    }

    private void loadShaders() {
        skyShader = new ShaderProgram(SKY_VERT, SKY_FRAG);
        postShader = new ShaderProgram(POST_VERT, POST_FRAG);
        particleShader = new ShaderProgram(PARTICLE_VERT, PARTICLE_FRAG);
        if (!skyShader.isCompiled()) log("sky", skyShader.getLog());
        if (!postShader.isCompiled()) log("post", postShader.getLog());
        if (!particleShader.isCompiled()) log("particle", particleShader.getLog());
    }

    private void log(String name, String log) {
        Gdx.app.error("TactShoot", name + " shader failed: " + log);
    }

    private void loadTextures() {
        String[] names = Maps.TEX_NAMES;
        for (int i = 0; i < names.length; i++) {
            textures.put(names[i], loadTex(names[i], fallback(i)));
        }
        textures.put("metal2", loadTex("metal2", metal2Fallback()));
    }

    private Texture loadTex(String name, Pixmap fallback) {
        FileHandle f = Gdx.files.internal("textures/" + name + ".jpg");
        if (f.exists()) {
            try {
                Texture t = new Texture(f, true);
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                return t;
            } catch (Exception e) {
                Gdx.app.error("TactShoot", "texture load failed " + name, e);
            }
        }
        Texture t = new Texture(fallback);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return t;
    }

    private Pixmap fallback(int i) {
        switch (i) {
            case 0: return TexGen.noiseTiled(256, 101, new Color(0.80f, 0.72f, 0.58f, 1f), 0.35f);
            case 1: return TexGen.noiseTiled(256, 202, new Color(0.55f, 0.55f, 0.56f, 1f), 0.20f);
            case 2: return TexGen.metalPanels(256, 303, new Color(0.48f, 0.51f, 0.55f, 1f));
            case 3: return TexGen.woodPlanks(256, 404, new Color(0.52f, 0.40f, 0.30f, 1f));
            case 4: return TexGen.bricks(256, 505, new Color(0.35f, 0.35f, 0.36f, 1f),
                    new Color(0.62f, 0.60f, 0.58f, 1f));
            case 5: return TexGen.bricks(256, 606, new Color(0.55f, 0.48f, 0.38f, 1f),
                    new Color(0.80f, 0.70f, 0.56f, 1f));
            case 6: return TexGen.bricks(256, 707, new Color(0.30f, 0.30f, 0.31f, 1f),
                    new Color(0.48f, 0.48f, 0.50f, 1f));
            case 7: return TexGen.noiseTiled(256, 808, new Color(0.45f, 0.38f, 0.28f, 1f), 0.35f);
            case 8: return TexGen.noiseTiled(256, 909, new Color(0.16f, 0.17f, 0.19f, 1f), 0.20f);
            case 9: return TexGen.woodPlanks(256, 1001, new Color(0.32f, 0.25f, 0.18f, 1f));
            default: return TexGen.noiseTiled(256, 1102, new Color(0.25f, 0.25f, 0.27f, 1f), 0.20f);
        }
    }

    private Pixmap metal2Fallback() {
        return TexGen.noiseTiled(256, 1203, new Color(0.13f, 0.14f, 0.14f, 1f), 0.18f);
    }

    private void loadModels() {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        gunAR = ModelFactory.gunAssault(mb, textures);
        mb = new ModelBuilder();
        mb.begin();
        gunSMG = ModelFactory.gunSmg(mb, textures);
        mb = new ModelBuilder();
        mb.begin();
        gunSG = ModelFactory.gunShotgun(mb, textures);
        mb = new ModelBuilder();
        mb.begin();
        gunPistol = ModelFactory.gunPistol(mb, textures);
        mb = new ModelBuilder();
        mb.begin();
        soldierModel = ModelFactory.soldier(mb, textures);
    }

    private void loadMaps() {
        maps = Maps.all();
        worldModels = new Model[maps.length];
        worldProps = new Array[maps.length];
        navGrids = new NavGrid[maps.length];
        for (int i = 0; i < maps.length; i++) {
            worldModels[i] = Maps.buildWorld(maps[i], textures);
            worldProps[i] = Maps.buildProps(maps[i], textures);
            navGrids[i] = new NavGrid(maps[i]);
        }
    }

    // ---------------------------------------------------------------- helpers

    public Model gunFor(int specIdx) {
        switch (specIdx) {
            case Weapons.SMG: return gunSMG;
            case Weapons.SHOTGUN: return gunSG;
            case Weapons.PISTOL: return gunPistol;
            default: return gunAR;
        }
    }

    public void play(int id) {
        play(id, 1f);
    }

    public void play(int id, float volume) {
        if (!soundOn) return;
        try {
            if (id >= 0 && id < snd.length && snd[id] != null) {
                snd[id].play(MathUtils.clamp(volume, 0f, 1f));
            }
        } catch (Exception ignored) {
        }
    }

    public void playDist(int id, float dist) {
        // simple distance attenuation: 1 at 0m, 0 at 45m
        float v = MathUtils.clamp(1f - dist / 45f, 0f, 1f);
        if (v > 0.03f) play(id, v);
    }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        if (dot != null) dot.dispose();
        if (crosshairTex != null) crosshairTex.dispose();
        if (hitmarkerTex != null) hitmarkerTex.dispose();
        if (flashTex != null) flashTex.dispose();
        if (logo != null) logo.dispose();
        if (fontTitle != null) fontTitle.dispose();
        if (fontBig != null) fontBig.dispose();
        if (font != null) font.dispose();
        if (fontSmall != null) fontSmall.dispose();
        if (skin != null) skin.dispose();
        for (Sound s : snd) if (s != null) s.dispose();
        if (gunAR != null) gunAR.dispose();
        if (gunSMG != null) gunSMG.dispose();
        if (gunSG != null) gunSG.dispose();
        if (gunPistol != null) gunPistol.dispose();
        if (soldierModel != null) soldierModel.dispose();
        if (worldModels != null) for (Model m : worldModels) if (m != null) m.dispose();
        if (skyShader != null) skyShader.dispose();
        if (postShader != null) postShader.dispose();
        if (particleShader != null) particleShader.dispose();
    }

    // ---------------------------------------------------------------- shaders

    static final String SKY_VERT =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_uv;\n" +
            "void main() {\n" +
            "  v_uv = a_position * 0.5 + 0.5;\n" +
            "  gl_Position = vec4(a_position, 0.999999, 1.0);\n" +
            "}\n";

    static final String SKY_FRAG =
            "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
            "varying vec2 v_uv;\n" +
            "uniform vec3 u_fwd, u_right, u_up;\n" +
            "uniform float u_aspectTan, u_tanHalf;\n" +
            "uniform vec3 u_sunDir;\n" +
            "uniform vec4 u_skyTop, u_skyHorizon, u_groundCol, u_sunCol;\n" +
            "uniform float u_night;\n" +
            "float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n" +
            "void main() {\n" +
            "  vec3 dir = normalize(u_fwd + (v_uv.x*2.0-1.0)*u_aspectTan*u_right + (v_uv.y*2.0-1.0)*u_tanHalf*u_up);\n" +
            "  float h = dir.y;\n" +
            "  vec3 col = mix(u_skyHorizon.rgb, u_skyTop.rgb, smoothstep(-0.12, 0.45, h));\n" +
            "  col = mix(u_groundCol.rgb, col, smoothstep(-0.45, -0.02, h));\n" +
            "  float s = max(dot(dir, u_sunDir), 0.0);\n" +
            "  col += u_sunCol.rgb * (pow(s, 400.0) * 1.7 + pow(s, 40.0) * 0.35 + pow(s, 6.0) * 0.10);\n" +
            "  float star = step(0.9970 + u_night * 0.0025, hash(floor(dir.xz * 150.0) + floor(dir.y * 30.0) * 0.37));\n" +
            "  col += vec3(star * step(0.05, dir.y) * u_night * (1.0 - s) * 1.6);\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    static final String POST_VERT =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_uv;\n" +
            "void main() {\n" +
            "  v_uv = a_position * 0.5 + 0.5;\n" +
            "  gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "}\n";

    static final String POST_FRAG =
            "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
            "varying vec2 v_uv;\n" +
            "uniform sampler2D u_tex;\n" +
            "uniform float u_damage, u_lowHp, u_time;\n" +
            "void main() {\n" +
            "  vec2 uv = v_uv;\n" +
            "  vec2 c = uv - 0.5;\n" +
            "  float d2 = dot(c, c);\n" +
            "  float aber = 0.0035 + d2 * 0.014;\n" +
            "  vec3 col;\n" +
            "  col.r = texture2D(u_tex, uv + c * aber).r;\n" +
            "  col.g = texture2D(u_tex, uv).g;\n" +
            "  col.b = texture2D(u_tex, uv - c * aber).b;\n" +
            "  col *= 1.0 - d2 * 0.85;\n" +
            "  float l = dot(col, vec3(0.299, 0.587, 0.114));\n" +
            "  col = mix(vec3(l), col, 1.10);\n" +
            "  col = (col - 0.5) * 1.07 + 0.5;\n" +
            "  float edge = smoothstep(0.0, 0.5, d2 * 2.0);\n" +
            "  col += vec3(0.50, 0.06, 0.04) * (1.0 - edge) * u_damage;\n" +
            "  col += vec3(0.30, 0.02, 0.01) * (1.0 - edge) * u_lowHp * (0.5 + 0.5 * sin(u_time * 0.006));\n" +
            "  float g = fract(sin(dot(uv * vec2(12.9898, 78.233) + u_time * 0.0001, vec2(4.1414, 2.351))) * 43758.5453);\n" +
            "  col += (g - 0.5) * 0.045;\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    static final String PARTICLE_VERT =
            "attribute vec3 a_position;\n" +
            "attribute vec4 a_color;\n" +
            "attribute vec2 a_texCoord0;\n" +
            "uniform mat4 u_projTrans;\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_uv;\n" +
            "void main() {\n" +
            "  v_color = a_color;\n" +
            "  v_uv = a_texCoord0;\n" +
            "  gl_Position = u_projTrans * vec4(a_position, 1.0);\n" +
            "}\n";

    static final String PARTICLE_FRAG =
            "#ifdef GL_ES\nprecision mediump float;\n#endif\n" +
            "varying vec4 v_color;\n" +
            "varying vec2 v_uv;\n" +
            "uniform sampler2D u_tex;\n" +
            "void main() {\n" +
            "  vec4 t = texture2D(u_tex, v_uv);\n" +
            "  gl_FragColor = v_color * t;\n" +
            "}\n";
}
