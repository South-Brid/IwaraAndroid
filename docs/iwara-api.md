# iwara 接口文档（2026-10 实测）

所有结论均在模拟器上通过 App 的浏览器通道实际验证。iwara 是非官方接口，可能随网站改版变化；
变化时用 `tools/cdp_fetch.py` 或 App 内「调试」页重新验证。

## 访问方式

| 域名 | 用途 | Cloudflare 验证 | 访问方式 |
|---|---|---|---|
| `apiq.iwara.tv` | JSON 接口 | **有** | 必须经 `WebBridge`（WebView 内 `fetch`） |
| `i.iwara.tv` | 封面、头像 | 无 | 原生直接加载（带 `Referer: https://www.iwara.tv/`） |
| `*.iwara.tv` 视频文件 | 播放地址 | 无 | ExoPlayer 原生播放（带 Referer） |

通用请求头：`Accept: application/json`、`Content-Type: application/json`，登录后加 `Authorization: Bearer <访问令牌>`。

错误返回格式：`{"message": "errors.xxx"}`，常见 `errors.invalidLogin`、`errors.serverError`、`errors.notFound`。

## 列表分页

返回 `{count, limit, page, results: [...]}`。`page` 从 0 开始。
注意：`/videos` 的 `count` 不是总数，而是"已翻过的数量 + 1"（有下一页时），只能用来判断是否还有下一页。

## 视频

### `GET /videos` 视频列表
| 参数 | 说明 |
|---|---|
| `sort` | `date` / `trending` / `popularity` / `views` / `likes` |
| `rating` | `all` / `general` / `ecchi`（服务端过滤，有效） |
| `page`, `limit` | 分页，`limit` 用 24 |
| `tags` | 多个标签逗号分隔，按标签过滤（有效） |
| `user` | 用户 id，查某个作者的作品 |
| `subscribed=true` | 关注的人发布的视频（需登录） |

实测无效的参数：`date`、`exclude`、`query`、`search`（会被忽略）。

### `GET /search` 关键词搜索
| 参数 | 说明 |
|---|---|
| `type` | **必须是 `videos`（复数）**；写成 `video` 会返回 500（很多开源客户端还在用旧写法） |
| `query` | 关键词 |
| `page` | 分页，每页固定 32 条 |
| `sort` | `relevance`（默认）/ `date` / `views` / `likes`；`trending` 返回 400 |

不支持 `limit`（加了也是 500）；`rating` 会被忽略，需客户端过滤。`type=users` 可搜用户。

### `GET /video/{id}` 视频详情
关键字段：`title`、`body`、`numViews`、`numLikes`、`rating`、`createdAt`、`user{}`、`tags[{id}]`、
`file{id, duration}`、`thumbnail`（序号）、`customThumbnail{id,name}`、`fileUrl`、`embedUrl`（外链视频）。

封面地址：
- 有 `customThumbnail`：`https://i.iwara.tv/image/thumbnail/{customThumbnail.id}/{customThumbnail.name}`
- 否则：`https://i.iwara.tv/image/thumbnail/{file.id}/thumbnail-{thumbnail 两位数}.jpg`

### `GET {fileUrl}` 播放地址
必须带请求头 `X-Version`：`sha1("{fileUrl 最后一段路径}_{expires 参数}_{盐}")`，实现见 `VideoRepository.xVersion`。
返回数组：`[{name: "Source" | "540" | "360" | "preview", src: {view: "//...", download: "//..."}}]`，`view` 补上 `https:` 即可播放。

### `GET /video/{id}/comments?page=0` 评论

## 标签

- `GET /autocomplete/tags?query=miku` 联想，返回 `results[{id, type, sensitive}]`，`type` 有 `general` / `character` / `category` / `artist`
- `GET /tags?filter=miku&page=0` 按前缀筛选

## 用户与登录

| 接口 | 说明 |
|---|---|
| `POST /user/login` `{email, password}` | 成功返回 `{token}`（刷新令牌）；失败 400 `errors.invalidLogin` |
| `POST /user/token`（`Authorization: Bearer <刷新令牌>`） | 返回 `{accessToken}`，有效期较短，App 内缓存 30 分钟 |
| `GET /user` | 当前用户 `{user:{id,name,username,avatar}, ...}`；未登录 401，`user.id` 为 null |
| `GET /profile/{username}` | 用户资料 `{user:{...}}`，用于由用户名查 id |
| `GET /user/{id}/following?page=0&limit=32` | 关注列表，每项 `{id, createdAt, user:{...}}` |
| `GET /user/{id}/followers` | 粉丝列表，结构同上 |

头像地址：`https://i.iwara.tv/image/avatar/{avatar.id}/{avatar.name}`。

网页端登录后，刷新令牌保存在 `localStorage.token`，App 会作为备用读取。
