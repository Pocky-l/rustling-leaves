"""Animated Rustling Leaves logo: the leaf pile of art/logo/logo.bbmodel rendered in Blockbench, with its stream of
falling leaves flying along their own spiral down into the pile in a seamless loop (each leaf flutters, fades out as
it lands and comes back in at the top).

Run from art/logo (Blockbench desktop is started with a debugging port by tools/blockbench/render_logo.py):
    python animate.py probe          prints the stream leaves
    python animate.py render [N]     renders N frames (default 72) into frames/
    python animate.py compose        frames/ -> logo_anim_400.gif (CurseForge avatar) + logo_anim_512.png (still)
"""
import glob
import json
import math
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[4] / "tools" / "blockbench"))
from render_logo import ensure_blockbench, evaluate  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
BB = HERE / "logo.bbmodel"
FRAMES = HERE / "frames"
# Camera of the published logo (yaw 30, pitch 24, orthographic), pulled back so the whole stream stays in view.
YAW, PITCH, TARGET_Y, ZOOM = 30, 24, 30, 0.22

LOAD = """(async () => {
  const data = %s;
  newProject(Formats.bedrock);
  Codecs.project.load(data, {path: ''});
  await new Promise(r => setTimeout(r, 1500));
  Canvas.updateAll();
  three_grid.visible = false;
  const flying = Cube.all.filter(c => c.name.startsWith('stream') || c.name.startsWith('falling'));
  const items = flying.map(c => {
    c.mesh.updateMatrixWorld(true);
    const world = new THREE.Vector3();
    c.mesh.getWorldPosition(world);
    return {cube: c, name: c.name, pos: c.mesh.position.clone(), quat: c.mesh.quaternion.clone(), world};
  });
  window.__rl = {items};
  return JSON.stringify(items.map(i => [i.name, +i.world.x.toFixed(2), +i.world.y.toFixed(2), +i.world.z.toFixed(2)]));
})()"""


def camera_js():
    return f"""
  const yaw = {YAW} * Math.PI / 180, pitch = {PITCH} * Math.PI / 180, dist = 300, target = [0, {TARGET_Y}, 0];
  const position = [dist * Math.sin(yaw) * Math.cos(pitch), target[1] + dist * Math.sin(pitch), dist * Math.cos(yaw) * Math.cos(pitch)];
  Preview.selected.loadAnglePreset({{projection: 'orthographic', position, target, zoom: {ZOOM}}});
"""


def load():
    ensure_blockbench()
    raw = json.loads(evaluate(LOAD % BB.read_text(encoding="utf-8")))
    return json.loads(raw) if isinstance(raw, str) else raw


def render(n):
    items = load()
    # The stream is numbered from the pile (stream_0) up its spiral. Its far end (11-13) lies behind the arc and would
    # bunch the leaves up on screen, so the path runs from the top of the arc (stream_10) down into the pile; every
    # stream leaf flies along it.
    order = sorted((i[0] for i in items if i[0].startswith("stream")), key=lambda n: -int(n.split("_")[1]))
    path = [n for n in order if int(n.split("_")[1]) <= 10]
    print("stream order:", order)
    if FRAMES.exists():
        shutil.rmtree(FRAMES)
    FRAMES.mkdir()
    for k in range(n):
        t = k / n
        out = FRAMES / f"f{k:03d}.png"
        evaluate(f"""(async () => {{
  const rl = window.__rl, t = {t}, order = {json.dumps(order)}, path = {json.dumps(path)};
  const byName = Object.fromEntries(rl.items.map(i => [i.name, i]));
  const pts = path.map(n => byName[n].world.clone());
  // extend the path a little above the first leaf and into the pile below the last one
  const top = pts[0].clone().add(pts[0].clone().sub(pts[1]).multiplyScalar(0.8));
  const end = pts[pts.length - 1].clone().add(new THREE.Vector3(0, -6, 0));
  const curve = new THREE.CatmullRomCurve3([top, ...pts, end]);
  const N = order.length;
  order.forEach((name, j) => {{
    const it = byName[name];
    const u = (j / N + t) % 1;                 // each leaf travels the whole path once per loop
    const p = curve.getPointAt(u);
    const parentInv = new THREE.Matrix4().copy(it.cube.mesh.parent.matrixWorld).invert();
    it.cube.mesh.position.copy(p.clone().applyMatrix4(parentInv));
    const a = 2 * Math.PI * t, ph = j * 1.7;
    const flutter = new THREE.Quaternion().setFromEuler(new THREE.Euler(
        0.55 * Math.sin(2 * a + ph), 0.8 * Math.sin(a + ph * 0.6), 0.45 * Math.cos(3 * a + ph)));
    it.cube.mesh.quaternion.copy(it.quat).multiply(flutter);
    const s = Math.min(1, u / 0.08, (1 - u) / 0.1);         // grow in at the top, shrink into the pile
    it.cube.mesh.scale.setScalar(Math.max(0.001, s));
  }});
  rl.items.filter(i => i.name.startsWith('falling')).forEach((it, j) => {{
    const a = 2 * Math.PI * t + j * 2.1;
    it.cube.mesh.position.copy(it.pos).add(new THREE.Vector3(1.5 * Math.sin(a), 1.2 * Math.sin(2 * a), 1.0 * Math.cos(a)));
    it.cube.mesh.quaternion.copy(it.quat).multiply(new THREE.Quaternion().setFromEuler(
        new THREE.Euler(0.5 * Math.sin(2 * a), 0.6 * Math.sin(a), 0.4 * Math.cos(2 * a))));
  }});
  {camera_js()}
  await new Promise(r => setTimeout(r, 120));
  return await new Promise(resolve => Preview.selected.screenshot({{crop: false}}, resolve));
}})()""", out)
        print("frame", k, flush=True)


def background(size, colors):
    top, bottom, glow_color = colors
    bg = Image.new("RGBA", (size, size))
    draw = ImageDraw.Draw(bg)
    for y in range(size):
        k = y / (size - 1)
        draw.line([(0, y), (size, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3)) + (255,))
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([size * 0.12, size * 0.12, size * 0.88, size * 0.88], fill=glow_color + (110,))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(size * 0.08)))
    return bg


def compose():
    files = sorted(glob.glob(str(FRAMES / "f*.png")))
    shots = [Image.open(f).convert("RGBA") for f in files]
    # One crop box for all frames, so the pile stays put.
    boxes = [s.getbbox() for s in shots]
    box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
    colors = ((0x8c, 0xc4, 0xe0), (0x2f, 0x64, 0x87), (0xe6, 0xf4, 0xfa))  # the published logo's clear sky

    def frame(shot, size):
        model = shot.crop(box)
        bg = background(size, colors)
        scale = min(size * 0.9 / model.width, size * 0.9 / model.height)
        model = model.resize((int(model.width * scale), int(model.height * scale)), Image.LANCZOS)
        shadow = Image.new("RGBA", model.size, (0, 0, 0, 0))
        shadow.putalpha(model.getchannel("A").point(lambda a: int(a * 0.45)))
        x, y = (size - model.width) // 2, (size - model.height) // 2
        bg.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(size * 0.02)), (x + size // 64, y + size // 36))
        bg.alpha_composite(model, (x, y))
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=size // 8, fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(bg, (0, 0), mask)
        return out

    frame(shots[0], 512).save(HERE / "logo_anim_512.png")
    tmp = tempfile.mkdtemp()
    for k, s in enumerate(shots):
        frame(s, 400).convert("RGB").save(os.path.join(tmp, "%04d.png" % k))
    import imageio_ffmpeg
    gif = HERE / "logo_anim_400.gif"
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
    cmd = sys.argv[1] if len(sys.argv) > 1 else "probe"
    if cmd == "probe":
        print(load())
    elif cmd == "render":
        render(int(sys.argv[2]) if len(sys.argv) > 2 else 72)
    else:
        compose()
