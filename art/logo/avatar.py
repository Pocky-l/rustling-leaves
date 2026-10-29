"""Animated CurseForge avatar of Rustling Leaves: a leaf pile in front, leaves swinging down onto it, and one big leaf
tumbling past the camera in the foreground (it fills the first frame, which shows while the GIF loads).

The pieces are rendered in Blockbench (desktop, debugging port, like tools/blockbench/render_logo.py): the pile of
logo.bbmodel without its falling leaves, and the mod's falling leaf sprite (5x5, leaf_mid.png) as a voxel leaf with
some thickness, in a range of orientations. The frames are composed here: sky, out-of-focus leaves far away, sharp
leaves in the middle, the big leaf in front.

Run from art/logo:
    python avatar.py render      Blockbench -> avatar_parts/pile.png, avatar_parts/leaf_XX.png
    python avatar.py compose     avatar_parts/ -> avatar_400.gif (CurseForge avatar) + avatar_512.png (first frame)
"""
import base64
import glob
import io
import json
import math
import os
import pathlib
import random
import shutil
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[4] / "tools" / "blockbench"))
from render_logo import ensure_blockbench, evaluate  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
PARTS = HERE / "avatar_parts"
SPRITE = HERE.parents[1] / "src/main/resources/assets/rustling_leaves/textures/block/leaf/leaf_mid.png"
ORIENTATIONS = 24
FRAMES, FPS = 72, 18
# LeafPalette.AUTUMN, the mod's own leaf colours
COLORS = [0xF0BE45, 0xE69A32, 0xDD782B, 0xCB5530, 0xB73C2F, 0xDCB851, 0xB87239]


def leaf_texture():
    """The 5x5 leaf back out of the x3 sprite (one pixel of every 3x3 block)."""
    src = Image.open(SPRITE).convert("RGBA")
    out = Image.new("RGBA", (5, 5))
    for y in range(5):
        for x in range(5):
            out.putpixel((x, y), src.getpixel((x * 3 + 1, y * 3 + 1)))
    return out


def render():
    ensure_blockbench()
    PARTS.mkdir(exist_ok=True)
    pile = (HERE / "logo.bbmodel").read_text(encoding="utf-8")
    evaluate(f"""(async () => {{
  newProject(Formats.bedrock);
  Codecs.project.load({pile}, {{path: ''}});
  await new Promise(r => setTimeout(r, 1500));
  Canvas.updateAll();
  three_grid.visible = false;
  Cube.all.filter(c => c.name.startsWith('stream') || c.name.startsWith('falling')).forEach(c => c.mesh.visible = false);
  return 'ok';
}})()""")
    evaluate("""(async () => {
  const yaw = 30 * Math.PI / 180, pitch = 22 * Math.PI / 180, dist = 300, target = [0, 6, 0];
  const position = [dist * Math.sin(yaw) * Math.cos(pitch), target[1] + dist * Math.sin(pitch), dist * Math.cos(yaw) * Math.cos(pitch)];
  Preview.selected.loadAnglePreset({projection: 'orthographic', position, target, zoom: 0.35});
  await new Promise(r => setTimeout(r, 400));
  return await new Promise(resolve => Preview.selected.screenshot({crop: true}, resolve));
})()""", PARTS / "pile.png")

    buf = io.BytesIO()
    leaf_texture().save(buf, "PNG")
    data = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    pixels = [(x, y) for y in range(5) for x in range(5) if leaf_texture().getpixel((x, y))[3] > 0]
    evaluate(f"""(async () => {{
  newProject(Formats.free);
  Project.texture_width = 5; Project.texture_height = 5;
  const tex = new Texture({{name: 'leaf'}}).fromDataURL({json.dumps(data)}).add(false);
  await new Promise(r => setTimeout(r, 400));
  const group = new Group({{name: 'leaf', origin: [0, 0, 0]}}).init();
  {json.dumps(pixels)}.forEach(([x, y]) => {{
    const c = new Cube({{name: 'p', from: [x - 2.5, 2.5 - y - 1, -0.3], to: [x - 1.5, 2.5 - y, 0.3]}}).addTo(group).init();
    for (const f in c.faces) {{ c.faces[f].texture = tex.uuid; c.faces[f].uv = [x, y, x + 1, y + 1]; }}
  }});
  Canvas.updateAll();
  three_grid.visible = false;
  Preview.selected.loadAnglePreset({{projection: 'orthographic', position: [0, 0, 300], target: [0, 0, 0], zoom: 2.5}});
  window.__leaf = group;
  return 'ok';
}})()""")
    for k in range(ORIENTATIONS):
        a = k / ORIENTATIONS * math.tau
        evaluate(f"""(async () => {{
  const g = window.__leaf.mesh;
  // a tumble: a full turn about a tilted axis, with a gentle wobble, so the leaf shows its face, its edge and its back
  const axis = new THREE.Vector3(0.85, 0.35, 0.4).normalize();
  g.quaternion.setFromAxisAngle(axis, {a}).multiply(new THREE.Quaternion().setFromEuler(new THREE.Euler({0.25 * math.sin(2 * a)}, 0, {0.2 * math.cos(a)})));
  await new Promise(r => setTimeout(r, 150));
  return await new Promise(resolve => Preview.selected.screenshot({{crop: false}}, resolve));
}})()""", PARTS / f"leaf_{k:02d}.png")
        print("leaf", k, flush=True)


def tint(img, color, shade=1.0):
    a = np.array(img, np.float32)
    rgb = np.array([(color >> 16) & 255, (color >> 8) & 255, color & 255], np.float32) / 255.0
    # the render is grey (leaf_mid x Blockbench light): normalise to the sprite's bright tone, then multiply
    a[..., :3] = np.clip(a[..., :3] / 240.0 * 255.0 * rgb * shade, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def load_leaves():
    shots = [Image.open(f).convert("RGBA") for f in sorted(glob.glob(str(PARTS / "leaf_*.png")))]
    # one square crop box for all orientations, so a turning leaf does not jump
    boxes = [s.getbbox() for s in shots]
    side = max(max(b[2] - b[0], b[3] - b[1]) for b in boxes)
    cx = sum((b[0] + b[2]) / 2 for b in boxes) / len(boxes)
    cy = sum((b[1] + b[3]) / 2 for b in boxes) / len(boxes)
    return [s.crop((int(cx - side / 2), int(cy - side / 2), int(cx + side / 2), int(cy + side / 2))) for s in shots]


def sky(size):
    y = np.linspace(0, 1, size)[:, None, None]
    x = np.linspace(0, 1, size)[None, :, None]
    top, bottom = np.array([88, 150, 196], np.float32), np.array([186, 220, 236], np.float32)
    bg = top * (1 - y) + bottom * y
    sun = np.exp(-(((x - 0.22) ** 2 + (y - 0.18) ** 2) / 0.09))
    bg = bg + sun * np.array([120, 96, 40], np.float32)
    vign = np.clip(np.hypot(x - 0.5, y - 0.5) / 0.75, 0, 1) ** 2
    bg = bg * (1 - 0.35 * vign)
    return Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def paste(img, layer, cx, cy):
    """Alpha-composites layer centred at (cx, cy), clipped to the image."""
    x, y = int(round(cx - layer.width / 2)), int(round(cy - layer.height / 2))
    box = (max(0, -x), max(0, -y), min(layer.width, img.width - x), min(layer.height, img.height - y))
    if box[2] > box[0] and box[3] > box[1]:
        img.alpha_composite(layer.crop(box), (max(0, x), max(0, y)))


def leaf_at(leaves, orientation, size, color, shade=1.0, blur=0.0, alpha=1.0):
    leaf = tint(leaves[int(orientation) % len(leaves)], color, shade)
    leaf = leaf.resize((size, size), Image.LANCZOS if blur else Image.BILINEAR)
    if blur:
        pad = int(blur * 3)
        big = Image.new("RGBA", (size + 2 * pad, size + 2 * pad), (0, 0, 0, 0))
        big.alpha_composite(leaf, (pad, pad))
        leaf = big.filter(ImageFilter.GaussianBlur(blur))
    if alpha < 1.0:
        leaf.putalpha(leaf.getchannel("A").point(lambda v: int(v * alpha)))
    return leaf


def drop_shadow(img, leaf, x, y, offset, blur, strength):
    ds = Image.new("RGBA", leaf.size, (20, 30, 40, 0))
    ds.putalpha(leaf.getchannel("A").point(lambda v: int(v * strength)))
    paste(img, ds.filter(ImageFilter.GaussianBlur(blur)), x + offset[0], y + offset[1])


def compose():
    S = 512
    leaves = load_leaves()
    pile = Image.open(PARTS / "pile.png").convert("RGBA")
    pile = pile.crop(pile.getbbox())
    pw = int(S * 0.92)
    pile = pile.resize((pw, int(pile.height * pw / pile.width)), Image.LANCZOS)
    pile_cx, pile_cy = S * 0.5, S - pile.height / 2 - S * 0.03
    pile_top = pile_cy - pile.height * 0.25
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse([S * 0.08, S * 0.86, S * 0.92, S * 1.04], fill=(30, 40, 50, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(S * 0.03))

    rnd = random.Random(3)
    far = [dict(x=rnd.uniform(0, S), phase=rnd.random(), size=rnd.randint(26, 44), color=rnd.choice(COLORS),
                sway=rnd.uniform(10, 30), k=rnd.choice((1, 2)), rot=rnd.random() * 24) for _ in range(0)]
    # Leaf fall: big near leaves and smaller ones further back (drawn first, a little paler), phases spread evenly.
    # Every leaf moves its own way: a pendulum swing, a glide carried sideways by the wind, a spiral (it comes closer
    # and goes away again, growing and shrinking) or a quick flutter.
    count = 22
    mid = []
    for j in range(count):
        kind = ("swing", "glide", "spiral", "flutter")[j % 4]
        x1 = pile_cx + rnd.uniform(-S * 0.34, S * 0.34)
        drift = rnd.choice((-1, 1)) * rnd.uniform(S * 0.3, S * 0.5) if kind == "glide" else rnd.uniform(-S * 0.25, S * 0.25)
        mid.append(dict(kind=kind, x0=x1 - drift, x1=x1, phase=(j * 7 % count) / count,
                        size=rnd.choice((40, 46, 52, 60, 66, 72)), color=COLORS[j % len(COLORS)],
                        sway=rnd.uniform(12, 16) if kind == "flutter" else rnd.uniform(18, 36),
                        swings={"swing": rnd.choice((2, 3)), "glide": 1, "spiral": rnd.choice((1, 2)), "flutter": 6}[kind],
                        radius=rnd.uniform(S * 0.07, S * 0.11), ease=rnd.uniform(0.8, 1.3), rot=rnd.random() * 6.28))
    mid.sort(key=lambda m: m["size"])
    # Big leaves right in front of the camera, out of focus, each crossing in its own time window while the big
    # leaf of the first frame is away: (start, length, from, to, size, colour).
    # They cross the sky part of the picture, where they stand out.
    near = [(0.27, 0.22, (-0.18, 0.02), (1.18, 0.52), 160, 0xDCB851), (0.40, 0.22, (1.18, -0.05), (-0.18, 0.40), 140, 0xE69A32),
            (0.52, 0.17, (-0.18, 0.30), (1.18, 0.20), 180, 0xB73C2F), (0.04, 0.18, (0.55, -0.18), (1.18, 0.45), 120, 0xF0BE45)]

    def frame(t):
        img = sky(S)
        for f in far:  # far away, out of focus, slow
            u = (f["phase"] + t) % 1.0
            y = -60 + (S * 0.75 + 60) * u
            x = f["x"] + f["sway"] * math.sin(math.tau * (f["k"] * t + f["phase"]))
            paste(img, leaf_at(leaves, f["rot"] + 24 * t * f["k"], f["size"], f["color"], 1.05, blur=2.5, alpha=0.45), x, y)
        img.alpha_composite(shadow)
        paste(img, pile, pile_cx, pile_cy)
        for m in mid:  # sharp leaves coming down onto the pile
            u = (m["phase"] + t) % 1.0
            fall = u ** m["ease"]
            y = -50 + (pile_top + 60) * fall
            a = u * m["swings"] * math.tau
            size = m["size"]
            if m["kind"] == "glide":
                x = m["x0"] + (m["x1"] - m["x0"]) * (1 - (1 - u) ** 2) + 10 * math.sin(a * 3)
            elif m["kind"] == "spiral":
                x = m["x0"] + (m["x1"] - m["x0"]) * u + m["radius"] * math.sin(a + m["rot"])
                size = int(size * (1 + 0.28 * math.cos(a + m["rot"])))
            else:
                x = m["x0"] + (m["x1"] - m["x0"]) * u + m["sway"] * math.sin(a)
            leaf = leaf_at(leaves, 4.5 * math.sin(a + m["rot"]), size, m["color"],
                           shade=0.88 + size / 600, alpha=min(1.0, (1.0 - u) / 0.12))
            drop_shadow(img, leaf, x, y, (6, 10), 4, 0.25)
            paste(img, leaf, x, y)
        # The big leaf in front crosses the picture in the first 60% of the loop (off screen the rest of it). At t = 0
        # it is face-on in the upper right over the pile, so the first frame shows it.
        v0 = 0.55
        v = ((t + v0 * 0.6) % 1.0) / 0.6
        if v < 1.0:
            hx = S * (-0.2 + 1.55 * v) + S * 0.05 * math.sin((v - v0) * math.tau * 1.5)
            hy = S * (-0.42 + 1.15 * v) + S * 0.04 * math.cos((v - v0) * math.tau * 1.5)
            big = leaf_at(leaves, 4.5 * math.sin((v - v0) * math.tau * 0.8), int(S * 0.4), 0xCB5530)
            drop_shadow(img, big, hx, hy, (14, 22), 10, 0.3)
            paste(img, big, hx, hy)
        for start, length, p0, p1, size, color in near:
            w = (t - start) % 1.0 / length
            if w < 1.0:
                x = S * (p0[0] + (p1[0] - p0[0]) * w) + S * 0.06 * math.sin(w * math.tau)
                y = S * (p0[1] + (p1[1] - p0[1]) * w) + S * 0.04 * math.sin(w * math.tau * 2)
                leaf = leaf_at(leaves, 4.5 * math.sin(w * math.tau * 1.3 + size), size, color, blur=1.4)
                drop_shadow(img, leaf, x, y, (16, 26), 12, 0.22)
                paste(img, leaf, x, y)
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=S // 8, fill=255)
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        out.paste(img, (0, 0), mask)
        return out

    frame(0.0).save(HERE / "avatar_512.png")
    tmp = tempfile.mkdtemp()
    for k in range(FRAMES):
        frame(k / FRAMES).convert("RGB").resize((400, 400), Image.LANCZOS).save(os.path.join(tmp, "%04d.png" % k))
    import imageio_ffmpeg
    gif = HERE / "avatar_400.gif"
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-framerate", str(FPS), "-i",
                    os.path.join(tmp, "%04d.png"), "-vf",
                    "split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle",
                    "-loop", "0", str(gif)], check=True)
    shutil.rmtree(tmp)
    gifsicle = HERE.parents[1] / "tools" / "shots" / "bin" / "gifsicle.exe"
    if gifsicle.exists():
        subprocess.run([str(gifsicle), "-O3", "--lossy=60", "--batch", str(gif)], check=True)
    print(gif, FRAMES, "frames", round(os.path.getsize(gif) / 1e6, 2), "MB")


if __name__ == "__main__":
    {"render": render, "compose": compose}[sys.argv[1] if len(sys.argv) > 1 else "compose"]()
