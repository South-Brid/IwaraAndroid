"""复现：进入全屏再退出后，浏览器通道是否还能正常请求"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cdp_fetch as c  # noqa: E402
from ui import find, key, nodes, restart, tap, tap_text, texts, wait_for  # noqa: E402


def probe(tag):
    ws = c.connect()
    t0 = time.time()
    r = c.fetch(ws, "/videos?limit=1", 1)
    print(f"{tag}: status={r['status']} {time.time() - t0:.1f}s")


restart()
time.sleep(3)
wait_for("▶ ", timeout=90)
probe("启动后")
first = next(t for t in texts() if t.startswith("▶ "))
tap_text(first, exact=True, wait=10)
wait_for("作品 ›", timeout=40)
time.sleep(3)
probe("视频页播放中")
for _ in range(3):
    tap(250, 250)
    time.sleep(0.4)
    n = next((n for n in nodes() if n["desc"] == "全屏"), None)
    if n:
        tap(n["x"], n["y"])
        break
time.sleep(4)
g = find("Got it", exact=True)
if g:
    tap(g["x"], g["y"])
probe("全屏中")
key(4)
time.sleep(3)
probe("退出全屏后")
tap_text("作品 ›", wait=2)
probe("进入作者页后")
time.sleep(8)
print("作者页卡片数:", len([t for t in texts() if t.startswith("▶ ")]), "| 失败提示:", bool(find("加载失败")))
