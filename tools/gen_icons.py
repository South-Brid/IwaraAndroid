"""生成 Android 7.x（API 24/25，不支持自适应图标）用的位图启动图标，图案与 drawable/ic_launcher_*.xml 一致。

用法: python tools/gen_icons.py   （需要 Pillow）
"""
import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4  # 超采样倍数，用于抗锯齿
START, END = (0xFF, 0x5C, 0x8A), (0x7B, 0x5C, 0xFF)


def render(px: int) -> Image.Image:
    n = px * SS
    # 渐变背景（左上 → 右下）
    grad = Image.new("RGB", (n, n))
    pixels = grad.load()
    for y in range(n):
        for x in range(n):
            t = (x + y) / (2 * (n - 1))
            pixels[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(START, END))
    d = ImageDraw.Draw(grad)
    # 图案坐标按 108 的视口换算，并放大 1.35 倍让传统图标中的图形更饱满（自适应图标的安全区不需要留给位图）
    k = n / 108 * 1.35
    cx = n / 2

    def p(x, y):
        return cx + (x - 54) * k, cx + (y - 50) * k

    d.polygon([p(46, 36), p(73, 50), p(46, 64)], fill="white")
    d.polygon([p(37.5, 31), p(39.2, 35.8), p(44, 37.5), p(39.2, 39.2), p(37.5, 44), p(35.8, 39.2), p(31, 37.5), p(35.8, 35.8)], fill="white")
    return grad


def masked(img: Image.Image, round_: bool) -> Image.Image:
    n = img.size[0]
    mask = Image.new("L", (n, n), 0)
    md = ImageDraw.Draw(mask)
    if round_:
        md.ellipse([0, 0, n - 1, n - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, n - 1, n - 1], radius=n * 0.22, fill=255)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


if __name__ == "__main__":
    for density, px in SIZES.items():
        base = render(px)
        for name, round_ in (("ic_launcher", False), ("ic_launcher_round", True)):
            icon = masked(base, round_).resize((px, px), Image.LANCZOS)
            path = os.path.join(RES, f"mipmap-{density}", f"{name}.webp")
            icon.save(path, "WEBP", lossless=True)
            print("wrote", path)
