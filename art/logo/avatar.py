"""Animated CurseForge avatar of Rustling Leaves: a small Minecraft island (3x3 grass blocks) with an autumn oak in the
back corner and the leaf pile of logo.bbmodel in front; leaves let go of the crown one by one, swing down like real
leaves and settle on the pile, in a seamless loop. Rendered in Blockbench (desktop, debugging port, like
tools/blockbench/render_logo.py).

Run from art/logo:
    python avatar.py build           writes avatar.bbmodel (island + tree + pile, from logo.bbmodel and diorama_texture.png)
    python avatar.py render [N]      renders N frames (default 72) into avatar_frames/
    python avatar.py compose         avatar_frames/ -> avatar_400.gif (CurseForge avatar) + avatar_512.png (still)
"""
import base64
import copy
import glob
import json
import math
import os
import pathlib
import random
import shutil
import subprocess
import sys
import tempfile
import uuid

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[4] / "tools" / "blockbench"))
from render_logo import ensure_blockbench, evaluate  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
SOURCE = HERE / "logo.bbmodel"
MODEL = HERE / "avatar.bbmodel"
ATLAS = HERE / "diorama_texture.png"
FRAMES = HERE / "avatar_frames"
PILE_SCALE = 0.8
PILE_AT = (4.0, 0.0, 6.0)
YAW, PITCH, TARGET_Y, ZOOM = 45, 28, 30, 0.19

# Atlas cells (128x128 atlas like the project resolution, 16 px each): u, v of the top left corner.
GRASS_TOP, GRASS_SIDE, DIRT, LOG_SIDE = (0, 0), (16, 0), (32, 0), (48, 0)
LEAVES = [(0, 16), (16, 16), (32, 16)]  # amber, red-orange, golden
LOG_TOP = (48, 16)


def face(cell, tex=1):
    u, v = cell
    return {"uv": [u, v, u + 16, v + 16], "texture": tex}


def cube(name, frm, to, faces):
    return {"name": name, "box_uv": False, "render_order": "default", "locked": False, "export": True, "scope": 0,
            "allow_mirror_modeling": True, "from": list(frm), "to": list(to), "autouv": 0, "color": 0,
            "origin": [0, 0, 0], "faces": faces, "type": "cube", "uuid": str(uuid.uuid4())}


def block(name, x, y, z, kind, shade=0):
    """One block at block coordinates (x, y, z) of the island."""
    frm, to = (x * 16, y * 16, z * 16), (x * 16 + 16, y * 16 + 16, z * 16 + 16)
    if kind == "grass":
        faces = {d: face(GRASS_SIDE) for d in ("north", "east", "south", "west")}
        faces.update(up=face(GRASS_TOP), down=face(DIRT))
    elif kind == "log":
        faces = {d: face(LOG_SIDE) for d in ("north", "east", "south", "west")}
        faces.update(up=face(LOG_TOP), down=face(LOG_TOP))
    else:
        faces = {d: face(LEAVES[shade]) for d in ("north", "east", "south", "west", "up", "down")}
    return cube(name, frm, to, faces)


def build():
    d = json.loads(SOURCE.read_text(encoding="utf-8"))
    s = PILE_SCALE

    def move(p):
        return [p[0] * s + PILE_AT[0], p[1] * s + PILE_AT[1], p[2] * s + PILE_AT[2]]

    for e in d["elements"]:
        if e["name"].startswith(("stream", "falling")):
            continue  # the falling leaves are placed by the renderer every frame
        e["from"], e["to"], e["origin"] = move(e["from"]), move(e["to"]), move(e["origin"])
    for g in d.get("groups", []):
        g["origin"] = move(g["origin"])
    blocks = []
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            blocks.append(block(f"grass_{x}_{z}", x, -1, z, "grass"))
    # An oak in the back corner: three logs, a crown of leaves blocks in three autumn colours.
    for y in (0, 1):
        blocks.append(block(f"log_{y}", -1, y, -1, "log"))
    rnd = random.Random(5)
    crown = [(x, 2, z) for x in (-2, -1, 0) for z in (-2, -1, 0) if (x, z) != (0, 0)]
    crown += [(-1, 3, -1), (-2, 3, -1), (-1, 3, -2), (0, 3, -1), (-1, 3, 0)]
    for i, (x, y, z) in enumerate(crown):
        blocks.append(block(f"crown_{i}", x, y, z, "leaves", rnd.choice((0, 1, 1, 2))))
    d["elements"] += blocks
    d["outliner"] += [b["uuid"] for b in blocks]
    tex = copy.deepcopy(d["textures"][0])
    tex.update(name="diorama_texture.png", relative_path="diorama_texture.png", id="1", uuid=str(uuid.uuid4()),
               width=128, height=128, uv_width=128, uv_height=128,
               source="data:image/png;base64," + base64.b64encode(ATLAS.read_bytes()).decode())
    d["textures"].append(tex)
    d["name"] = "rustling_leaves_avatar"
    d["meta"]["model_format"] = "free"  # bedrock models have a single texture
    MODEL.write_text(json.dumps(d), encoding="utf-8")
    print("wrote", MODEL, len(d["elements"]), "elements")


LOAD = """(async () => {
  const data = %s;
  newProject(Formats.free);
  Codecs.project.load(data, {path: ''});
  await new Promise(r => setTimeout(r, 1800));
  Canvas.updateAll();
  three_grid.visible = false;
  const flying = Cube.all.filter(c => c.name.startsWith('stream') || c.name.startsWith('falling'));
  window.__av = {items: flying.map(c => ({cube: c, name: c.name, quat: c.mesh.quaternion.clone()}))};
  return flying.length;
})()"""


def camera_js():
    return f"""
  const yaw = {YAW} * Math.PI / 180, pitch = {PITCH} * Math.PI / 180, dist = 400, target = [0, {TARGET_Y}, 0];
  const position = [dist * Math.sin(yaw) * Math.cos(pitch), target[1] + dist * Math.sin(pitch), dist * Math.cos(yaw) * Math.cos(pitch)];
  Preview.selected.loadAnglePreset({{projection: 'orthographic', position, target, zoom: {ZOOM}}});
"""


def leaf_paths(count):
    """Start under the crown, end on the pile; every leaf swings along its own direction."""
    rnd = random.Random(11)
    paths = []
    for j in range(count):
        # The camera looks from +x +z: x - z runs across the picture, x + z towards the viewer. Leaves start at the left
        # or right edge of the crown, in front of the trunk, so they glide against the sky into the pile, swinging
        # across the picture.
        side = 1 if j % 2 else -1
        across, depth = side * rnd.uniform(22, 34), rnd.uniform(-4, 10)
        sx, sz = (depth + across) / 2, (depth - across) / 2
        ex, ez = PILE_AT[0] + rnd.uniform(-6, 6), PILE_AT[2] + rnd.uniform(-6, 6)
        paths.append({"s": [sx, rnd.uniform(29, 33), sz], "e": [ex, 8.5, ez], "dir": [0.707, -0.707],
                      "amp": rnd.uniform(6, 9), "swings": rnd.choice((2, 3)), "phase": j / count,
                      "spin": rnd.uniform(-1, 1), "size": rnd.uniform(0.95, 1.2)})
    return paths


def render(n):
    ensure_blockbench()
    count = int(json.loads(evaluate(LOAD % MODEL.read_text(encoding="utf-8"))))
    paths = leaf_paths(count)
    if FRAMES.exists():
        shutil.rmtree(FRAMES)
    FRAMES.mkdir()
    for k in range(n):
        evaluate(f"""(async () => {{
  const av = window.__av, t = {k / n}, paths = {json.dumps(paths)};
  av.items.forEach((it, j) => {{
    const p = paths[j];
    const u = (p.phase + t) % 1;
    const fall = u * u * (3 - 2 * u) * 0.25 + u * 0.75;      // a soft start as the leaf lets go
    // Pendulum glide: the leaf swings sideways, dipping at the middle of each swing and tilting with it.
    const swing = Math.sin(u * p.swings * Math.PI * 2);
    const x = p.s[0] + (p.e[0] - p.s[0]) * fall + p.dir[0] * p.amp * swing;
    const z = p.s[2] + (p.e[2] - p.s[2]) * fall + p.dir[1] * p.amp * swing;
    const y = p.s[1] + (p.e[1] - p.s[1]) * fall - 1.5 * Math.abs(Math.cos(u * p.swings * Math.PI * 2));
    const world = new THREE.Vector3(x, y, z);
    const inv = new THREE.Matrix4().copy(it.cube.mesh.parent.matrixWorld).invert();
    it.cube.mesh.position.copy(world.applyMatrix4(inv));
    const tilt = 0.7 * Math.cos(u * p.swings * Math.PI * 2);
    const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(tilt * p.dir[1], u * Math.PI * 2 * p.spin, -tilt * p.dir[0]));
    it.cube.mesh.quaternion.copy(it.quat).premultiply(q);
    const s = Math.min(1, u / 0.06, (1 - u) / 0.08) * p.size;
    it.cube.mesh.scale.setScalar(Math.max(0.001, s));
  }});
  {camera_js()}
  await new Promise(r => setTimeout(r, 120));
  return await new Promise(resolve => Preview.selected.screenshot({{crop: false}}, resolve));
}})()""", FRAMES / f"f{k:03d}.png")
        print("frame", k, flush=True)


def background(size):
    top, bottom = (150, 200, 232), (246, 222, 184)  # clear autumn sky into warm haze
    bg = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(bg)
    for y in range(size):
        k = (y / (size - 1)) ** 1.3
        draw.line([(0, y), (size, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3)) + (255,))
    sun = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(sun).ellipse([size * 0.5, size * 0.02, size * 1.1, size * 0.62], fill=(255, 244, 214, 150))
    bg.alpha_composite(sun.filter(ImageFilter.GaussianBlur(size * 0.09)))
    return bg


def compose():
    files = sorted(glob.glob(str(FRAMES / "f*.png")))
    shots = [Image.open(f).convert("RGBA") for f in files]
    boxes = [s.getbbox() for s in shots]
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))

    def frame(shot, size):
        model = shot.crop(box)
        bg = background(size)
        scale = min(size * 0.86 / model.width, size * 0.86 / model.height)
        model = model.resize((int(model.width * scale), int(model.height * scale)), Image.LANCZOS)
        x, y = (size - model.width) // 2, (size - model.height) // 2 + size // 40
        shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).ellipse([size * 0.2, y + model.height - size * 0.07, size * 0.8, y + model.height + size * 0.03],
                                       fill=(60, 40, 20, 90))
        bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(size * 0.03)))
        bg.alpha_composite(model, (x, y))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=size // 8, fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(bg, (0, 0), mask)
        return out

    frame(shots[0], 512).save(HERE / "avatar_512.png")
    tmp = tempfile.mkdtemp()
    for k, s in enumerate(shots):
        frame(s, 400).convert("RGB").save(os.path.join(tmp, "%04d.png" % k))
    import imageio_ffmpeg
    gif = HERE / "avatar_400.gif"
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-framerate", "18", "-i",
                    os.path.join(tmp, "%04d.png"), "-vf",
                    "split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle",
                    "-loop", "0", str(gif)], check=True)
    shutil.rmtree(tmp)
    gifsicle = HERE.parents[1] / "tools" / "shots" / "bin" / "gifsicle.exe"
    if gifsicle.exists():
        subprocess.run([str(gifsicle), "-O3", "--lossy=40", "--batch", str(gif)], check=True)
    print(gif, len(shots), "frames", round(os.path.getsize(gif) / 1e6, 2), "MB")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "build"
    if cmd == "build":
        build()
    elif cmd == "render":
        render(int(sys.argv[2]) if len(sys.argv) > 2 else 72)
    else:
        compose()
