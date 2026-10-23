"""生成 Android 7.x（API 24/25，不支持自适应图标）用的位图启动图标，图案与 drawable/ic_launcher_*.xml 一致。

用法: python tools/gen_icons.py   （需要 Pillow）
"""
import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4  # 超采样倍数，用于抗锯齿
START, END = (0x46, 0xD3, 0xC4), (0x1E, 0x7F, 0xC2)
# 位图图标没有自适应图标的安全区裁切，图案放大一些更饱满；图案中心在 108 视口的 (54, 64)
SCALE, CX, CY = 1.45, 54, 64


def render(px: int) -> Image.Image:
    n = px * SS
    img = Image.new("RGB", (n, n))
    pixels = img.load()
    for y in range(n):
        for x in range(n):
            t = (x + y) / (2 * (n - 1))
            pixels[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(START, END))
    d = ImageDraw.Draw(img)
    k = n / 108 * SCALE

    def p(x, y):
        return n / 2 + (x - CX) * k, n / 2 + (y - CY) * k

    # 头梁：圆心 (54,62) 半径 21 的上半圆弧
    x0, y0 = p(33, 41)
    x1, y1 = p(75, 83)
    d.arc([x0, y0, x1, y1], 180, 360, fill="white", width=max(1, round(5 * k)))
    # 耳罩
    for left in (29, 67):
        a, b = p(left, 57)
        c, e = p(left + 12, 77)
        d.rounded_rectangle([a, b, c, e], radius=4 * k, fill="white")
    # 播放键
    d.polygon([p(49.5, 57), p(62, 64), p(49.5, 71)], fill="white")
    return img


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
            icon.save(os.path.join(RES, f"mipmap-{density}", f"{name}.webp"), "WEBP", lossless=True)
    print("icons written")
