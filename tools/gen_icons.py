"""由 art/icon_source.png 生成全套启动图标（自适应图标前景/单色层 + Android 7.x 位图图标）。

用法: python tools/gen_icons.py   （需要 Pillow）
原图为白底时会从四周向内抠掉背景（主体有深色描边，不会把主体内部的白色抠掉）。
"""
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art", "icon_source.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
DENSITY = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# 与 drawable/ic_launcher_background.xml 的渐变一致
BG_START, BG_END = (0xE8, 0xFF, 0xF9), (0xBD, 0xED, 0xE6)
ADAPTIVE_FILL = 0.62  # 自适应图标：主体占 108dp 画布的比例（安全区约 61%）
LEGACY_FILL = 0.80    # 位图图标：没有系统裁切，主体可以更大


def load_subject() -> Image.Image:
    im = Image.open(SRC).convert("RGBA")
    if im.getchannel("A").getextrema()[0] == 255:  # 不透明 → 抠白底
        for seed in [(0, 0), (im.width - 1, 0), (0, im.height - 1), (im.width - 1, im.height - 1)]:
            if im.getpixel(seed)[3] != 0:
                ImageDraw.floodfill(im, seed, (255, 255, 255, 0), thresh=40)
    return im.crop(im.getchannel("A").getbbox())


def fit(subject: Image.Image, canvas: int, fill: float) -> Image.Image:
    s = subject.copy()
    s.thumbnail((int(canvas * fill), int(canvas * fill)), Image.LANCZOS)
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(s, ((canvas - s.width) // 2, (canvas - s.height) // 2), s)
    return out


def gradient(n: int) -> Image.Image:
    img = Image.new("RGBA", (n, n))
    px = img.load()
    for y in range(n):
        for x in range(n):
            t = (x + y) / (2 * (n - 1))
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(BG_START, BG_END)) + (255,)
    return img


def shaped(img: Image.Image, round_: bool) -> Image.Image:
    n = img.size[0]
    mask = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(mask)
    if round_:
        d.ellipse([0, 0, n - 1, n - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, n - 1, n - 1], radius=n * 0.22, fill=255)
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


if __name__ == "__main__":
    subject = load_subject()
    for name, k in DENSITY.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        # 自适应图标前景（108dp）与单色层（Android 13 主题图标，只用透明度）
        fg = fit(subject, round(108 * k), ADAPTIVE_FILL)
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"))
        mono = Image.new("RGBA", fg.size, (255, 255, 255, 0))
        mono.putalpha(fg.getchannel("A"))
        mono.save(os.path.join(folder, "ic_launcher_monochrome.png"))
        # 位图图标（48dp），4 倍超采样后缩小
        px = round(48 * k)
        big = gradient(px * 4)
        big.alpha_composite(fit(subject, px * 4, LEGACY_FILL))
        for file, round_ in (("ic_launcher", False), ("ic_launcher_round", True)):
            shaped(big, round_).resize((px, px), Image.LANCZOS).save(os.path.join(folder, f"{file}.webp"), "WEBP", lossless=True)
    preview = gradient(512)
    preview.alpha_composite(fit(subject, 512, ADAPTIVE_FILL))
    shaped(preview, False).save(os.path.join(ROOT, "build", "icon_preview.png"))
    print("icons written")
