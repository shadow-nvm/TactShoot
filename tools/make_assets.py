"""TactShoot asset pipeline:
1. Sanity-check downloaded ambientCG textures (mean color).
2. Bake Ambient Occlusion into Color maps, save optimized JPGs to assets/textures.
3. Create tinted material variants (adobe, dark metal, dark wood, sand...).
4. Generate the game logo + Android launcher icons.
"""
import os
from PIL import Image, ImageChops, ImageEnhance, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "dl", "tex")
DST = os.path.join(ROOT, "assets", "textures")
RES = os.path.join(ROOT, "android", "src", "main", "res")
os.makedirs(DST, exist_ok=True)

PACKS = {
    "ground": "Ground054_1K-JPG",    # dirt / gravel
    "concrete": "Concrete026_1K-JPG",
    "metal": "Metal032_1K-JPG",
    "metal2": "Metal029_1K-JPG",
    "wood": "Wood049_1K-JPG",
    "paving": "PavingStones064_1K-JPG",
    "bricks": "Bricks064_1K-JPG",
}


def color_file(pack):
    d = os.path.join(SRC, pack)
    if not os.path.isdir(d):
        return None
    for f in os.listdir(d):
        if f.endswith("_Color.jpg"):
            return os.path.join(d, f)
    return None


def ao_file(pack):
    d = os.path.join(SRC, pack)
    if not os.path.isdir(d):
        return None
    for f in os.listdir(d):
        if "AmbientOcclusion" in f:
            return os.path.join(d, f)
    return None


def load_baked(pack):
    c = Image.open(color_file(pack)).convert("RGB")
    a = ao_file(pack)
    if a:
        ao = Image.open(a).convert("RGB").resize(c.size, Image.LANCZOS)
        # soften AO so it reads as shading, not dirt
        ao = ImageChops.blend(Image.new("RGB", c.size, (255, 255, 255)), ao, 0.85)
        c = ImageChops.multiply(c, ao)
    return c


def save(img, name, quality=85):
    p = os.path.join(DST, name + ".jpg")
    img.save(p, "JPEG", quality=quality, optimize=True)
    print("saved", os.path.relpath(p, ROOT), img.size)


def mean(img):
    px = list(img.resize((32, 32)).getdata())
    n = len(px)
    return tuple(round(sum(p[i] for p in px) / n) for i in range(3))


def tint(img, rgb):
    return ImageChops.multiply(img, Image.new("RGB", img.size, rgb))


print("== sanity ==")
loaded = {}
for name, pack in PACKS.items():
    if color_file(pack) is None:
        print("MISSING PACK", name, pack)
        continue
    img = load_baked(pack)
    loaded[name] = img
    print(f"{name:10s} mean RGB {mean(img)}")

print("== bake & save ==")
for name, img in loaded.items():
    save(img, name)

print("== variants ==")
if "bricks" in loaded:
    adobe = ImageEnhance.Color(loaded["bricks"]).enhance(0.45)
    adobe = ImageChops.multiply(adobe, Image.new("RGB", adobe.size, (224, 202, 172)))
    adobe = ImageEnhance.Brightness(adobe).enhance(1.06)
    save(adobe, "adobe")
if "concrete" in loaded:
    save(tint(loaded["concrete"], (120, 120, 122)), "concrete_dark")
    save(tint(loaded["concrete"], (196, 200, 204)), "concrete_light")
if "metal" in loaded:
    save(tint(loaded["metal"], (105, 108, 116)), "metal_dark")
if "wood" in loaded:
    save(tint(loaded["wood"], (140, 128, 108)), "wood_dark")
if "ground" in loaded:
    sand = ImageEnhance.Color(loaded["ground"]).enhance(0.75)
    sand = ImageChops.multiply(sand, Image.new("RGB", sand.size, (255, 243, 222)))
    sand = ImageEnhance.Brightness(sand).enhance(1.10)
    save(sand, "sand")

print("== logo & icons ==")
ORANGE = (255, 140, 26)
DARK = (14, 17, 22)
STEEL = (52, 62, 76)


def draw_logo(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 512.0
    cx = cy = size / 2
    # outer glow ring
    d.ellipse([cx - 218 * s, cy - 218 * s, cx + 218 * s, cy + 218 * s], outline=(255, 140, 26, 90), width=int(10 * s))
    # main ring
    d.ellipse([cx - 178 * s, cy - 178 * s, cx + 178 * s, cy + 178 * s], outline=ORANGE, width=int(26 * s))
    # ticks
    tw, tl = 26 * s, 62 * s
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        x0 = cx + dx * 208 * s - (tw / 2 if dy else 0)
        y0 = cy + dy * 208 * s - (tw / 2 if dx else 0)
        x1 = cx + dx * (208 + tl) * s + (tw / 2 if dy else 0)
        y1 = cy + dy * (208 + tl) * s + (tw / 2 if dx else 0)
        d.rectangle([min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)], fill=ORANGE)
    # center dot
    d.ellipse([cx - 34 * s, cy - 34 * s, cx + 34 * s, cy + 34 * s], fill=ORANGE)
    # inner steel ring detail
    d.ellipse([cx - 120 * s, cy - 120 * s, cx + 120 * s, cy + 120 * s], outline=STEEL, width=int(8 * s))
    # diagonal brackets
    for rot in (45, -45):
        pass
    return img


logo = draw_logo(512)
logo_path = os.path.join(ROOT, "assets", "ui", "logo.png")
os.makedirs(os.path.dirname(logo_path), exist_ok=True)
logo.save(logo_path)
print("saved", os.path.relpath(logo_path, ROOT))

# launcher icons (legacy mipmaps + adaptive foreground)
densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, px in densities.items():
    icon = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    d = ImageDraw.Draw(icon)
    r = int(px * 0.22)
    d.rounded_rectangle([0, 0, px - 1, px - 1], radius=r, fill=DARK)
    inner = draw_logo(512).resize((int(px * 0.88), int(px * 0.88)), Image.LANCZOS)
    icon.alpha_composite(inner, (int(px * 0.06), int(px * 0.06)))
    out = os.path.join(RES, f"mipmap-{dpi}", "ic_launcher.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    icon.save(out)
    print("saved", os.path.relpath(out, ROOT))

# adaptive icon foreground (API 26+)
fg = draw_logo(432)
out_fg = os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png")
fg.save(out_fg)
print("saved", os.path.relpath(out_fg, ROOT))
print("DONE")
