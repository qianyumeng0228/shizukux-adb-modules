# 模块提交接收端（Cloudflare Worker）

ShizukuX 模块商店的「上传模块」需要一条能写入目录仓库、又不把 GitHub 写权限塞进 App 的链路。
这个 Worker 接收 ZIP，重算校验后**先入审核队列**，通过审核才写入
`qianyumeng0228/shizukux-modules`；每次提交同时在仓库里开一个 `submission` issue，审核结论
评论到该 issue 并关闭它。

- 提交：ZIP 暂存到 `pending/<id>-<version>.zip`，`pending/index.json` 记一条待审记录，
  并在目录仓库开一个 `[提交] <name> v<version>` issue
- 通过：重读 pending ZIP 比对 SHA-256 → 写入 `modules/` + 合并 `modules.json` → 删 pending 副本
  → issue 评论下载地址并关闭
- 驳回：保留审计记录（`status: rejected` + 备注）→ 删 pending 副本 → issue 评论原因并关闭

`pending/index.json` 同时是可读的审计日志：每条记录含 ticket、id/version、sha256、大小、
提交时间、提交者 IP 的 SHA-256 前 16 位（不存明文 IP）、审核状态、备注与 issue 编号。

## 为什么需要它

商店索引是纯静态文件（`modules.json` / `community.json`），App 只有读路径。没有接收端时，
「上传」只能在本机生效，别人下载不到。Worker 是唯一能同时做到「匿名可传 + 不暴露写权限」的落点。

GitHub Pages 替代不了它：Pages 只托管静态文件，接不了 POST，也不能回写仓库。Pages 可以托管
索引（这一点已经在用），但收不了提交。

## 部署

```bash
cd worker
npx wrangler login                        # 大陆网络需先设置 HTTPS_PROXY
npx wrangler kv namespace create SUBMIT_KV    # 把输出的 id 填进 wrangler.toml
npx wrangler secret put GITHUB_TOKEN       # 需 repo + gist scope（建 issue、写仓库）
npx wrangler secret put ADMIN_TOKEN        # /pending 与 /review 的通行证
npx wrangler secret put SUBMIT_TOKEN       # 可选；设了就会校验 X-Submit-Token
npx wrangler deploy
```

首次部署后到目录仓库手工建一个 `submission` label（`POST /repos/{owner}/{repo}/labels`）。
不建也能跑：创建 issue 收到 422 时 Worker 会去掉 label 重试一次，只是标签缺失。

端点写死在客户端：

```
manager/src/main/java/af/shizuku/manager/module/store/ModuleUploadClient.kt
    const val ENDPOINT = "https://submit.zanzhuwang.cc.cd/submit"
```

留空时 App 不提交，上传面板降级为「导出模块信息」。

## 接口契约

### `POST /submit`（App 用）

`multipart/form-data`：

| part   | 内容 |
| ------ | ---- |
| `meta` | JSON：id/name/version/versionCode/author/description/usesShellBridge/minSdk/sizeBytes/sha256 |
| `file` | 模块 ZIP（≤ 32 MB） |

成功：`202 {"ok":true,"status":"pending","ticket":"t_…","message":"… 已进入审核队列","sha256":"…","sizeBytes":N,"issueNumber":N,"issueUrl":"…"}`
失败：`{"ok":false,"error":"…"}`，HTTP 400 / 401 / 409 / 429 / 502。

`issueNumber` / `issueUrl` 在 issue 建成功时才有；建失败不影响提交，两个字段为 `null`。

### `GET /pending`（管理端）

需要 `X-Admin-Token`。返回 `{"ok":true,"count":N,"submissions":[…]}`，记录字段与
`pending/index.json` 一致（含 `issueNumber` / `issueUrl`）。

### `POST /review`（管理端）

需要 `X-Admin-Token`，JSON：

```json
{"action":"approve","ticket":"t_abc123"}
{"action":"reject","ticket":"t_abc123","reason":"缺少 service.sh 说明"}
```

也接受 `{"action":"approve","id":"upload.selftest","version":"1.0.2"}` 代替 ticket。

通过时重读 pending ZIP、重算 SHA-256 与记录比对，不一致直接 409（防止审核后被掉包）。
两个动作都会 `issueClosed` 告知是否成功关掉 issue（issue 不存在时为 `false`，不影响审核结果）。

### `GET /modules`（管理端）

需要 `X-Admin-Token`。列出 `modules/` 下每个 ZIP，并按索引里的 id 反解所属模块与版本：

```json
{"ok":true,"count":4,"files":[
  {"name":"shizukux.recorder-1.0.0.zip","id":"shizukux.recorder","version":"1.0.0","sizeBytes":2028,"inCatalog":true}
]}
```

索引里已没有的 ZIP 仍会列出，此时 `id`/`version` 为 `null`、`inCatalog` 为 `false`（孤儿文件）。
归属判定用最长 id 前缀匹配，避免 `a.b` 把 `a.bc-1.0.zip` 误判成自己的版本。

### `POST /unpublish`（管理端）

需要 `X-Admin-Token`，JSON：

```json
{"id":"upload.selftest"}
{"id":"upload.selftest","version":"1.0.2","reason":"历史版本清理"}
```

- 只给 `id`：整模块下架——移除 `modules.json` 条目并删除 `modules/<id>-*.zip` 全部文件，
  返回 `{"ok":true,"id":…,"deletedFiles":N,"indexRemoved":true}`。
- 再给 `version`：只删这一个 ZIP；若它正是索引当前版本，索引条目同时移除（模块即从商店消失），
  返回 `{"ok":true,"id":…,"version":…,"fileRemoved":true,"indexRemoved":false}`。
- 两者都不存在时返回 404；索引更新失败时返回 502，但已删文件不会回滚（Git 历史可恢复）。

### `GET /index`（公开）

直接回源读取仓库里的 `modules.json`。审核 App 用它刷「已上架」，避免 jsDelivr / raw 的缓存延迟。

### `GET /healthz`

```json
{"ok":true,"reviewQueue":true,"rateLimit":true,"adminConfigured":true,"issueSync":true,"issueLabel":"submission","indexRoute":true}
```

用于探活与自查配置：`rateLimit:false` 说明没绑 `SUBMIT_KV`，`issueSync:false` 说明没有 `GITHUB_TOKEN`。

## 管理端命令行

```bash
python worker/review.py list
python worker/review.py approve t_abc123
python worker/review.py reject upload.selftest@1.0.2 --reason "重复提交"
```

令牌优先取 `--token`，其次 `ADMIN_TOKEN` 环境变量，其次 `~/.shizukux_admin_token`。
需要代理时设置 `HTTPS_PROXY`，脚本会沿用。图形界面见 `audit-app/`。

## 限流

按提交者 IP（`CF-Connecting-IP` 的 SHA-256）在 KV 里计数，三个窗口任一超限即 `429` +
`Retry-After`：

| 窗口 | 上限 |
| ---- | ---- |
| 每分钟 | 3 |
| 每小时 | 10 |
| 每天 | 20 |

没绑 `SUBMIT_KV` 时 Worker 仍可工作，但等于关掉限流，`/healthz` 会显示 `rateLimit:false`。

## 服务端校验

客户端已经校验一遍，但关键字段**以 ZIP 内容为准重算**，`meta` 只补展示字段：

- 大小 ≤ 32 MB，entry 数 ≤ 2048（读 central directory，不解压整包）
- 必须有 `module.prop`，`id` 符合 `[A-Za-z][A-Za-z0-9._-]{1,63}`，`version` 必填
- `version` 统一去掉前导 `v`（`v1.0.2` → `1.0.2`），与索引里的裸版本号一致
- SHA-256 由服务端重算；与 `meta.sha256` 不一致直接拒绝
- 已发布同版本、队列里已有同版本、目录里已有更高 versionCode → 409

## issue 同步

提交成功后 Worker 用 `GITHUB_TOKEN` 在目录仓库开 issue：

- 标题 `[提交] <name> v<version>`，标签 `submission`
- 正文是人读表格 + 一个 ```json``` 块，含 ticket / id / version / versionCode / name / author /
  description / nameZh / descriptionZh / sha256 / sizeBytes / pendingPath / submittedAt / submitterHash
- 审核后评论结论（通过附 jsDelivr 下载地址，驳回附原因）并 `PATCH state=closed`

issue 是公开痕迹，不是审核通道：审核只走 `/review`。issue 创建失败、评论失败、关闭失败都不会
阻断提交或审核，只在响应里体现 `issueClosed`。

## 已知限制

- 32 MB 的 ZIP 会走 Contents API（base64，请求体内约 43 MB）。Workers 允许，但大包慢；
  以后模块变大应改成 Git Data API 或对象存储 + 索引只存直链。
- 审核通过是同步操作：`/review` 要读一次 pending ZIP 再写一次，大包可能接近 Worker 的
  CPU/内存上限；超大模块建议手工把 ZIP 放进 `modules/` 再改索引。
- `community.json` 不由这里写入：它是从 GitHub `shevery-modules` topic 汇聚的快照。
- 审核只有「通过 / 驳回」，没有二次编辑（作者信息等以 `module.prop` 为准）。
- 公开仓库里由本 Worker 创建的 issue 无法通过 REST 删除（`DELETE` 返回 404），删测试数据时
  只能关闭并补一条说明评论。
