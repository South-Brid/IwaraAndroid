"""检查某个用户名的作品接口返回：python tools/probe_user_videos.py <username>"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cdp_fetch as c  # noqa: E402

ws = c.connect()
r = c.fetch(ws, f"/profile/{sys.argv[1]}", 1)
uid = json.loads(r["body"])["user"]["id"]
r = c.fetch(ws, f"/videos?user={uid}&sort=date&rating=all&page=0&limit=24", 2)
b = json.loads(r["body"])
print(r["status"], "count=", b.get("count"), "results=", len(b.get("results", [])))
