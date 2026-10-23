"""通过 Chrome DevTools 协议在模拟器 WebView 的页面上下文里执行 fetch，用于开发时探索接口。

用法: python tools/cdp_fetch.py "/videos?limit=2" ["/search?type=video&query=miku" ...]
需要: debug 版 App 正在运行；pip install websocket-client
"""
import json
import re
import subprocess
import sys
import urllib.request

import websocket

ADB = r"F:\jetbrains\AndroidStudioSDK\platform-tools\adb.exe"
API = "https://apiq.iwara.tv"
PORT = 9333


def connect():
    sockets = subprocess.run([ADB, "shell", "cat", "/proc/net/unix"], capture_output=True, text=True).stdout
    names = re.findall(r"@(webview_devtools_remote_\d+)", sockets)
    if not names:
        sys.exit("没有找到 WebView 调试端口，请确认 App 正在运行")
    subprocess.run([ADB, "forward", f"tcp:{PORT}", f"localabstract:{names[-1]}"], check=True, capture_output=True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    pages = json.load(opener.open(f"http://127.0.0.1:{PORT}/json"))
    page = next(p for p in pages if "iwara.tv" in p.get("url", ""))
    return websocket.create_connection(page["webSocketDebuggerUrl"], suppress_origin=True, http_proxy_host=None)


def fetch(ws, path, msg_id, method="GET", body=None):
    url = path if path.startswith("http") else API + path
    opts = {"method": method, "headers": {"Accept": "application/json", "Content-Type": "application/json"}, "credentials": "include"}
    if body is not None:
        opts["body"] = json.dumps(body)
    js = f"""fetch({json.dumps(url)}, {json.dumps(opts)})
        .then(r => r.text().then(t => JSON.stringify({{status: r.status, body: t}})))
        .catch(e => JSON.stringify({{status: 0, body: String(e)}}))"""
    ws.send(json.dumps({"id": msg_id, "method": "Runtime.evaluate",
                        "params": {"expression": js, "awaitPromise": True, "returnByValue": True}}))
    while True:
        msg = json.loads(ws.recv())
        if msg.get("id") == msg_id:
            return json.loads(msg["result"]["result"]["value"])


if __name__ == "__main__":
    ws = connect()
    for i, path in enumerate(sys.argv[1:], 1):
        r = fetch(ws, path, i)
        print(f"===== {path}  ->  {r['status']}")
        body = r["body"]
        try:
            body = json.dumps(json.loads(body), ensure_ascii=False, indent=1)
        except ValueError:
            pass
        print(body[:int(sys.argv[0] and 2500)])
