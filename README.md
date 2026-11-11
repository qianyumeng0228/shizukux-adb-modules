# shizukux-adb-modules

ShizukuX「ADB 模块」商店的提交与审核链路，独立于 ShizukuX 主仓库维护：

```
ShizukuX 模块商店（ADB 模块）
        │  选择 ZIP、本机校验
        ▼
  POST /submit ──────────►  worker/   提交接收 + 审核队列 + 限流 + issue 同步
                            │            ├─ pending/<id>-<version>.zip
                            │            ├─ pending/index.json（审计）
                            │            └─ submission issue（公开痕迹）
        ┌───────────────────┘
        │  GET /pending、POST /review（X-Admin-Token）
        ▼
  audit-app/   审核 App：待审列表 / 通过 / 驳回 / 已上架
                            │ 通过
                            ▼
              qianyumeng0228/shizukux-modules
                ├─ modules/<id>-<version>.zip
                └─ modules.json（合并索引，公开可读）
```

## 目录

| 路径 | 作用 |
| --- | --- |
| `worker/` | Cloudflare Worker：`POST /submit`、`GET /pending`、`POST /review`、`GET /index`、`GET /healthz`，附 `review.py` 命令行 |
| `audit-app/` | Android 审核 App（Kotlin + Compose），给管理员用的图形端 |

## 部署 Worker

见 [`worker/README.md`](worker/README.md)：`wrangler kv namespace create` → 三个 secret → `deploy`。
当前线上端点 `https://submit.zanzhuwang.cc.cd`，`/healthz` 应返回
`reviewQueue:true, rateLimit:true, adminConfigured:true, issueSync:true`。

客户端（ShizuX 模块商店）以 `ModuleUploadClient.ENDPOINT` 指向同一端点。

## 审核 App

```bash
cd audit-app
./gradlew :app:assembleDebug        # Windows: .\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次打开：

1. 进「设置」，端点默认 `https://submit.zanzhuwang.cc.cd`，粘贴管理员令牌（与 Worker 的
   `ADMIN_TOKEN` 一致），点「保存」；点「测试连接」会依次请求 `/healthz` 与 `/pending`，
   令牌错会直接报 401。
2. 回「待审」点「刷新」，卡片显示名称、版本、大小、`id`、简介、SHA-256 前 16 位、提交时间与
   issue 编号。
3. 「通过」有二次确认，通过后 Worker 重算 SHA-256 并发布，App 提示写入路径；「驳回」必须填原因，
   原因会写进队列并评论到 issue。
4. 「已上架」走 `/index` 回源读取索引，刚审核完的条目立刻可见（不走 CDN 缓存）。

令牌只存在本机 `SharedPreferences`，除 `X-Admin-Token` 头以外不随任何请求发送。

## 与 ShizuSU `module-uploader` 的区别

参照过 `com.qym.shizusu.uploader`（ShizuSU 的审核 App）的结构，但服务端模型不同：

| 维度 | ShizuSU module-uploader | 本项目 |
| --- | --- | --- |
| 提交身份 | 提交者需要自己的 GitHub 号 + gist 令牌 | 匿名提交，无需 GitHub 账号 |
| 传输 | Base64 切片进 private gist，审核端拼回 | multipart 直接 POST 到 Worker |
| 限流 | 无 | 按 IP 哈希 3/分钟、10/小时、20/天 |
| 防掉包 | 无（gist 可被作者改写） | 审核时重算 SHA-256 比对，不一致 409 |
| 审核通道 | 读 label=`submission` 的 issue | Worker 队列 + `X-Admin-Token` |
| 公开痕迹 | issue 即提交单 | issue 为审计痕迹，审核走队列 |

两条路都能跑通；这里选「匿名可传 + 服务端重算」是为了让不折腾 GitHub 的用户也能提交。

## 相关仓库

- 目录/索引仓库：`qianyumeng0228/shizukux-modules`（`modules.json`、`modules/`、`pending/`、issues）
- 客户端：`ShizukuX` 的 `manager/.../module/store/ModuleUploadClient.kt`、`ModuleUploadSheet.kt`
- 镜像基址：`https://cdn.jsdelivr.net/gh/qianyumeng0228/shizukux-modules@main`、
  `https://raw.githubusercontent.com/qianyumeng0228/shizukux-modules/main`
