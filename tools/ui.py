"""基于 adb + uiautomator 的简易界面测试工具（开发调试用）"""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = r"F:\jetbrains\AndroidStudioSDK\platform-tools\adb.exe"
PKG = "com.example.iwaraphone"


def adb(*args, check=True):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", check=check).stdout


def nodes():
    for _ in range(5):
        out = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        if "dumped to" in out:
            break
        time.sleep(1)
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    xml = xml[xml.find("<?xml"):]
    out = []
    for n in ET.fromstring(xml).iter("node"):
        m = re.findall(r"\d+", n.get("bounds", ""))
        if len(m) == 4:
            x1, y1, x2, y2 = map(int, m)
            out.append({"text": n.get("text", ""), "desc": n.get("content-desc", ""), "selected": n.get("selected") == "true",
                        "checked": n.get("checked") == "true", "x": (x1 + x2) // 2, "y": (y1 + y2) // 2, "box": (x1, y1, x2, y2)})
    return out


def texts():
    return [n["text"] or n["desc"] for n in nodes() if n["text"] or n["desc"]]


def find(label, exact=False):
    for n in nodes():
        for v in (n["text"], n["desc"]):
            if v and (v == label if exact else label in v):
                return n
    return None


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))


def tap_text(label, exact=False, wait=1.0):
    n = find(label, exact)
    if not n:
        raise AssertionError(f"界面上找不到「{label}」")
    tap(n["x"], n["y"])
    time.sleep(wait)
    return n


def type_text(s):
    adb("shell", "input", "text", s.replace(" ", "%s"))


def key(code):
    adb("shell", "input", "keyevent", str(code))


def wait_for(label, timeout=40, exact=False):
    end = time.time() + timeout
    while time.time() < end:
        n = find(label, exact)
        if n:
            return n
        time.sleep(1.5)
    raise AssertionError(f"等待超时：没有出现「{label}」")


def shot(path):
    with open(path, "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def restart():
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
