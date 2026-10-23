"""探测关注相关接口的结构"""
import json
import sys

sys.path.insert(0, __file__.rsplit("\\", 1)[0])
import cdp_fetch as c


def show(r, n=1500):
    print(r["status"], r["body"][:n], "\n")


ws = c.connect()
username = sys.argv[1]
r = c.fetch(ws, f"/profile/{username}", 1)
print("== profile"); show(r, 600)
uid = json.loads(r["body"])["user"]["id"]

r = c.fetch(ws, f"/user/{uid}/following?page=0&limit=3", 2)
print("== following"); show(r)

body = json.loads(r["body"])
first = body["results"][0] if body.get("results") else None
if first:
    fu = first.get("user") or first
    print("following item keys:", list(first.keys()))
    r = c.fetch(ws, f"/videos?user={fu['id']}&sort=date&page=0&limit=2", 3)
    print("== videos of followed user")
    b = json.loads(r["body"])
    print(r["status"], "count=", b.get("count"), [(v["title"][:30], v["user"]["username"]) for v in b.get("results", [])])

r = c.fetch(ws, "/videos?subscribed=true&sort=date&page=0&limit=2", 4)
print("\n== subscribed feed (anonymous)"); show(r, 300)
