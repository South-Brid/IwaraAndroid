"""逐项功能测试（在模拟器上运行 debug 版 App）

用法: python tools/test_features.py
登录测试需要环境变量 IW_EMAIL / IW_PWD（不写入任何文件）；未设置则跳过登录用例。
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ui import ADB, find, key, restart, shot, tap, tap_text, texts, type_text, wait_for  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "test")
os.makedirs(OUT, exist_ok=True)
results = []


def case(name):
    def deco(fn):
        def run():
            try:
                results.append(("PASS", name, fn() or ""))
            except Exception as e:  # noqa: BLE001
                path = os.path.join(OUT, f"fail_{len(results):02d}.png")
                shot(path)
                results.append(("FAIL", name, f"{e} | 截图 {path}"))
        return run
    return deco


def cards():
    return [t for t in texts() if t.startswith("▶ ")]


def wait_cards(timeout=45):
    wait_for("▶ ", timeout=timeout)
    return f"{len(cards())} 个视频"


def go_tab(name):
    tap_text(name, exact=True, wait=1.5)


# ---------------------------------------------------------------- 首页
@case("首页 · 最新列表")
def t_home():
    restart()
    time.sleep(3)
    return wait_cards(90)


@case("首页 · 切换热门")
def t_home_sort():
    tap_text("热门", exact=True, wait=5)
    return wait_cards()


# ---------------------------------------------------------------- 搜索
@case("搜索 · 关键词 miku")
def t_search():
    go_tab("搜索")
    tap_text("关键词", wait=0.5)
    type_text("miku")
    time.sleep(1)
    key(66)
    return wait_cards()


@case("搜索 · 排序点赞最多")
def t_search_sort():
    tap_text("点赞最多", exact=True, wait=5)
    return wait_cards()


@case("搜索 · 分级普通（本地过滤）")
def t_search_rating():
    tap_text("普通", exact=True, wait=3)
    end = time.time() + 90
    while time.time() < end and not cards():
        assert not find("加载失败"), "加载失败"
        time.sleep(2)
    n = len(cards())
    assert n > 0, "过滤后没有结果"
    tap_text("全部", exact=True, wait=3)
    return f"{n} 个视频"


@case("搜索 · 标签联想 hatsune")
def t_suggest():
    tap_text("清除", wait=1)
    tap_text("关键词", wait=0.5)
    type_text("hatsune")
    wait_for("# hatsune_miku", timeout=20)
    return "出现 # hatsune_miku"


@case("搜索 · 选择标签后按标签浏览")
def t_tag():
    tap_text("# hatsune_miku", exact=True, wait=5)
    wait_for("#hatsune_miku", timeout=10)
    return wait_cards()


@case("搜索 · 标签模式排序热门")
def t_tag_sort():
    tap_text("热门", exact=True, wait=5)
    return wait_cards()


@case("搜索 · 移除标签后显示搜索历史")
def t_history():
    tap_text("#hatsune_miku", wait=2)
    wait_for("搜索历史", timeout=10)
    assert find("miku", exact=True), "历史中没有 miku"
    return "历史包含 miku"


# ---------------------------------------------------------------- 登录
@case("我的 · 退出后重新登录")
def t_login():
    email, pwd = os.environ.get("IW_EMAIL"), os.environ.get("IW_PWD")
    if not email:
        return "跳过（未提供账号）"
    go_tab("我的")
    time.sleep(2)
    if find("退出登录", exact=True):
        tap_text("退出登录", exact=True, wait=3)
    wait_for("登录 iwara", timeout=20)
    tap_text("邮箱", exact=True, wait=0.5)
    type_text(email)
    tap_text("密码", exact=True, wait=0.5)
    type_text(pwd)
    key(4)
    time.sleep(1)
    tap_text("登录", exact=True, wait=1)
    wait_for("退出登录", exact=True, timeout=40)
    name = next(t for t in texts() if t.startswith("@"))
    return f"登录成功，显示用户 {name}"


# ---------------------------------------------------------------- 关注
@case("关注 · 关注列表")
def t_follow():
    go_tab("关注")
    wait_for("作品 ›", timeout=40)
    users = [t for t in texts() if t.startswith("@")]
    return f"{len(users)} 个关注的人（首屏）"


@case("关注 · 点击作者进入作品页")
def t_follow_user():
    first = next(t for t in texts() if t.startswith("@"))
    tap_text(first, exact=True, wait=3)
    wait_for(first, timeout=10)
    r = wait_cards()
    key(4)
    time.sleep(2)
    wait_for("关注的人", timeout=10)
    return f"{first}：{r}，返回正常"


@case("关注 · 关注动态")
def t_feed():
    tap_text("关注动态", exact=True, wait=5)
    return wait_cards()


# ---------------------------------------------------------------- 视频页
def player_box():
    out = subprocess.run([ADB, "shell", "wm", "size"], capture_output=True, text=True).stdout
    w = int(out.strip().split()[-1].split("x")[0])
    return w // 2, int(w * 9 / 16 / 2) + 140


@case("视频 · 打开并播放")
def t_video():
    go_tab("首页")
    time.sleep(2)
    first = next(t for t in texts() if t.startswith("▶ "))
    tap_text(first, exact=True, wait=12)
    wait_for("清晰度", timeout=30)
    assert not find("播放失败"), "出现播放失败"
    return "已加载清晰度和播放器，无播放错误"


@case("视频 · 倍速 1.5x")
def t_speed():
    n = tap_text("1.5x", exact=True, wait=1)
    return "已选择 1.5x，长按提示变为 3.0x" if find("3.0x 倍速") else "已选择 1.5x"


@case("视频 · 长按倍速")
def t_longpress():
    x, y = player_box()
    subprocess.Popen([ADB, "shell", "input", "swipe", str(x), str(y), str(x), str(y), "3000"])
    time.sleep(1.8)
    ok = find("倍速播放中")
    time.sleep(2)
    gone = not find("倍速播放中")
    assert ok, "长按时没有出现倍速提示"
    assert gone, "松手后倍速提示没有消失"
    return f"长按显示「{ok['text']}」，松手恢复"


@case("视频 · 点击作者进入作品页")
def t_author():
    tap_text("作品 ›", wait=3)
    r = wait_cards()
    key(4)
    time.sleep(2)
    wait_for("清晰度", timeout=10)
    return f"{r}，返回视频页正常"


@case("视频 · 点击标签跳转搜索")
def t_tag_jump():
    tag = next((t for t in texts() if t.startswith("#")), None)
    assert tag, "视频没有标签"
    tap_text(tag, exact=True, wait=5)
    wait_for(tag, timeout=10)
    return f"跳转搜索 {tag}：{wait_cards()}"


if __name__ == "__main__":
    for t in [t_home, t_home_sort, t_search, t_search_sort, t_search_rating, t_suggest, t_tag, t_tag_sort, t_history,
              t_login, t_follow, t_follow_user, t_feed, t_video, t_speed, t_longpress, t_author, t_tag_jump]:
        t()
    print("\n========== 测试结果 ==========")
    for status, name, detail in results:
        print(f"[{status}] {name}  {detail}")
    print(f"\n通过 {sum(r[0] == 'PASS' for r in results)} / {len(results)}")
