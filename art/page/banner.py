"""Title banner of the Rustling Leaves page: an in-game frame (darkened and out of focus behind the title), the name in
Fraunces (OFL, fonts/) and the mod's own falling leaf sprites drifting down over it in a seamless loop.

Usage (from art/page): python banner.py <background frame> <out base> [focus_y 0..1]
Writes <out base>.png (still, 1600x520) and <out base>.gif (animated, 1000 wide).
"""
import math
import os
import random
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1600, 520
GIF_W = 1000
FRAMES, FPS = 120, 25
LEAF_DIR = "../../src/main/resources/assets/rustling_leaves/textures/block/leaf"
# LeafPalette.AUTUMN plus two greens of fresh leaves
COLORS = [0xE8B53A, 0xDB8A2C, 0xCF6A2E, 0xBC4E34, 0xA87842, 0xD4AE4A, 0x9C7448, 0x7FA040, 0x9AAE3C]


def sprite(name):
    """The 16x16 sprite is a 5x5 leaf scaled x3: take one pixel of each 3x3 block back."""
    src = Image.open(os.path.join(LEAF_DIR, name + ".png")).convert("RGBA")
    out = Image.new("RGBA", (5, 5))
    for y in range(5):
        for x in range(5):
            out.putpixel((x, y), src.getpixel((x * 3 + 1, y * 3 + 1)))
    return out


SPRITES = [sprite(n) for n in ("leaf_light", "leaf_mid", "leaf_dark")]


def tinted(base, color, shade):
    a = np.array(base, np.float32)
    rgb = np.array([(color >> 16) & 255, (color >> 8) & 255, color & 255], np.float32) / 255.0
    a[..., :3] = np.clip(a[..., :3] * rgb * shade, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


class Leaf:
    def __init__(self, rnd, front):
        self.front = front
        self.px = rnd.choice((9, 10, 11, 12)) if front else rnd.choice((4, 5, 5, 6, 6, 7))
        self.passes = 2 if self.px >= 9 else 1  # near leaves fall faster: parallax
        self.phase = rnd.random()
        self.x0 = rnd.uniform(-40, W + 40)
        self.sway = rnd.uniform(30, 90)
        self.sway_k = rnd.choice((1, 2))
        self.sway_p = rnd.random() * math.tau
        self.flip_k = rnd.choice((2, 3, 4))
        self.flip_p = rnd.random() * math.tau
        self.rot0 = rnd.uniform(0, 360)
        self.rot_k = rnd.choice((1, 2))
        self.rot_p = rnd.random() * math.tau
        shade = 0.85 + 0.25 * rnd.random() if front else 0.65 + 0.25 * rnd.random()
        self.image = tinted(rnd.choice(SPRITES), rnd.choice(COLORS), shade)

    def draw(self, img, t):
        u = (self.phase + self.passes * t) % 1.0
        y = -80 + (H + 160) * u
        a = math.tau * t
        x = self.x0 + self.sway * math.sin(self.sway_k * a + self.sway_p)
        flip = math.cos(self.flip_k * a + self.flip_p)
        size = 5 * self.px
        leaf = self.image.resize((size, size), Image.NEAREST)
        width = max(2, int(round(size * max(0.18, abs(flip)))))
        leaf = leaf.resize((width, size), Image.NEAREST)
        if flip < 0:
            leaf = leaf.transpose(Image.FLIP_LEFT_RIGHT)
        angle = self.rot0 + 35 * math.sin(self.rot_k * a + self.rot_p) + 15 * math.sin(a * self.passes)
        leaf = leaf.rotate(angle, resample=Image.NEAREST, expand=True)
        shadow = Image.new("RGBA", leaf.size, (0, 0, 0, 0))
        shadow.putalpha(leaf.getchannel("A").point(lambda v: int(v * 0.35)))
        ox, oy = int(x - leaf.width / 2), int(y - leaf.height / 2)
        if self.front:
            paste(img, shadow.filter(ImageFilter.GaussianBlur(3)), (ox + 5, oy + 8))
        paste(img, leaf, (ox, oy))


def paste(img, layer, pos):
    """alpha_composite that accepts positions outside the image."""
    x, y = pos
    box = (max(0, -x), max(0, -y), min(layer.width, W - x), min(layer.height, H - y))
    if box[2] <= box[0] or box[3] <= box[1]:
        return
    img.alpha_composite(layer.crop(box), (max(0, x), max(0, y)))



def scene(bg_path, focus):
    src = Image.open(bg_path).convert("RGB")
    src = src.resize((W, round(src.height * W / src.width)), Image.LANCZOS)
    top = max(0, min(src.height - H, int(src.height * focus - H / 2)))
    crop = src.crop((0, top, W, top + H))
    sharp = np.array(crop, np.float32)
    soft = np.array(crop.filter(ImageFilter.GaussianBlur(7)), np.float32)
    xs = np.clip((np.linspace(0, 1, W) - 0.42) / 0.35, 0, 1)[None, :, None]
    bg = soft * (1 - xs) + sharp * xs  # sharp on the right, softly out of focus behind the title
    x = np.linspace(0, 1, W)[None, :, None]
    y = np.linspace(0, 1, H)[:, None, None]
    shade = np.clip(1.0 - 0.78 * np.clip((0.8 - x) / 0.6, 0, 1) ** 1.1, 0.2, 1.0)
    shade = shade * (1.0 - 0.3 * np.clip((y - 0.72) / 0.28, 0, 1))
    tint = np.array([34, 20, 10], np.float32)  # warm dusk brown, not black
    bg = bg * shade + tint * (1 - shade)
    return Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def title_layer():
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    font = ImageFont.truetype("fonts/Fraunces.ttf", 132)
    font.set_variation_by_axes([144, 700, 100, 1])
    small = ImageFont.truetype("fonts/Fraunces.ttf", 31)
    small.set_variation_by_axes([24, 500, 50, 0])
    tx, ty = 90, 120
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).text((tx, ty), "Rustling Leaves", font=font, fill=255)
    glow = Image.new("RGBA", (W, H), (255, 170, 70, 0))
    glow.putalpha(mask.filter(ImageFilter.GaussianBlur(22)).point(lambda a: int(a * 0.45)))
    layer.alpha_composite(glow)
    shadow = Image.new("RGBA", (W, H), (20, 8, 0, 0))
    shadow.putalpha(mask.filter(ImageFilter.GaussianBlur(3)).point(lambda a: int(a * 0.85)))
    layer.alpha_composite(shadow, (4, 6))
    grad = np.zeros((H, W, 4), np.uint8)
    gy = np.clip((np.arange(H)[:, None] - ty - 25) / 140.0, 0, 1)
    top_c, bot_c = np.array([255, 238, 196]), np.array([232, 140, 58])
    grad[..., :3] = (top_c * (1 - gy[..., None]) + bot_c * gy[..., None]).astype(np.uint8)
    grad[..., 3] = np.array(mask)
    layer.alpha_composite(Image.fromarray(grad, "RGBA"))
    d = ImageDraw.Draw(layer)
    line_y = ty + 192
    d.line([(tx + 6, line_y), (tx + 700, line_y)], fill=(226, 150, 70, 210), width=2)
    d.text((tx + 6, line_y + 20), "FALLING LEAVES  ·  LEAF PILES  ·  WIND  ·  CLIENT-SIDE", font=small,
           fill=(255, 226, 180, 255))
    return layer


def frame(base, title, back, front, t):
    img = base.copy()
    for leaf in back:
        leaf.draw(img, t)
    img.alpha_composite(title)
    for leaf in front:
        leaf.draw(img, t)
    return img


if __name__ == "__main__":
    bg_path, out_base = sys.argv[1:3]
    focus = float(sys.argv[3]) if len(sys.argv) > 3 else 0.5
    rnd = random.Random(7)
    back = [Leaf(rnd, False) for _ in range(26)]
    front = [Leaf(rnd, True) for _ in range(5)]
    base, title = scene(bg_path, focus), title_layer()
    frame(base, title, back, front, 0.37).convert("RGB").save(out_base + ".png")
    tmp = tempfile.mkdtemp()
    gif_h = round(H * GIF_W / W)
    for k in range(FRAMES):
        frame(base, title, back, front, k / FRAMES).convert("RGB").resize((GIF_W, gif_h), Image.LANCZOS).save(
            os.path.join(tmp, "%04d.png" % k))
    import imageio_ffmpeg
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-framerate", str(FPS), "-i",
                    os.path.join(tmp, "%04d.png"), "-vf",
                    "split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle",
                    "-loop", "0", out_base + ".gif"], check=True)
    shutil.rmtree(tmp)
    print(out_base, FRAMES, "frames", round(os.path.getsize(out_base + ".gif") / 1e6, 2), "MB")
