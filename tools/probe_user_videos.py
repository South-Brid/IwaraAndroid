"""检查某个用户名的作品接口返回和耗时：python tools/probe_user_videos.py <username>"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cdp_fetch as c  # noqa: E402

ws = c.connect()
r = c.fetch(ws, f"/profile/{sys.argv[1]}", 1)
uid = json.loads(r["body"])["user"]["id"]
for i, sort in enumerate(["date", "popularity", "trending"], 2):
    t0 = time.time()
    r = c.fetch(ws, f"/videos?sort={sort}&rating=all&page=0&limit=24&user={uid}", i)
    dt = time.time() - t0
    try:
        n = len(json.loads(r["body"]).get("results", []))
    except ValueError:
        n = r["body"][:80]
    print(f"sort={sort:<10} status={r['status']}  {dt:5.1f}s  results={n}")
