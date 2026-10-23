"""探测登录接口的错误返回格式（使用不存在的账号，不涉及真实凭证）"""
import sys

sys.path.insert(0, __file__.rsplit("\\", 1)[0])
import cdp_fetch as c

ws = c.connect()
r = c.fetch(ws, "/user/login", 1, "POST", {"email": "nobody@example.com", "password": "wrong-password"})
print("POST /user/login (bogus):", r["status"], r["body"][:200])
r = c.fetch(ws, "/user", 2)
print("GET /user (anonymous):", r["status"], r["body"][:200])
