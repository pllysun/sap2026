# 接口说明

后端使用 JSON `{code,message,data}`，正常业务 code 为 200；业务失败可能仍是 HTTP 200，因此客户端必须检查 code。未登录和缺少角色权限分别返回 HTTP 401 / 403。认证头为 `sap-token`，不使用 cookie 或 URL 参数读取登录令牌。

应用与管理端同源。ID、revision、SSE sequence 等 Long 值序列化为字符串，避免 JavaScript 精度丢失；客户端不能把它们当作浮点数做持久标识。时间字段按接口返回值解释，比赛时间使用毫秒时间戳与服务器时钟。

1.1.0 的会话保存在当前业务 MySQL 的 auth_state 表，不需要 Redis；有效会话在主服务重启后保留。退出、过期、密码重置与账号禁用仍按接口规则使登录失效。每个独立站点使用独立数据库，本版为单主服务实例。

## 认证与账号

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 公开健康探针 |
| GET | `/api/public/config` | 站点名称及注册开关 |
| POST | `/api/auth/login` | 用户登录，body 为 studentId / password |
| POST | `/api/auth/admin/login` | 管理登录，检查管理角色 |
| POST | `/api/auth/register` | 开放注册时创建普通用户，body 为 studentId / password / nickname |
| GET | `/api/auth/info` | 当前 user / roles，不返回密码 |
| POST | `/api/auth/logout` | 注销当前会话 |
| PUT | `/api/auth/profile` | 更新当前账号 nickname，忽略权限字段 |
| PUT | `/api/auth/password` | currentPassword / password，修改后旧会话失效 |
| GET / POST | `/api/admin/users` | 超级管理员分页查询、创建账号 |
| PUT | `/api/admin/users/{id}` | 超级管理员修改 nickname / roleCode / status |
| PUT | `/api/admin/users/{id}/password` | 超级管理员重置密码 |
| GET | `/api/admin/audit` | 管理角色查看操作审计 |

studentId 是为了兼容题库协议保留的账号字段名，不限于学生学号。账号规范是 3–32 位字母、数字、点、下划线或短横线，首位必须为字母或数字，存储时转小写。

## 普通题库

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/oj/problems` | 登录后分页题目目录 |
| GET | `/api/oj/filters` | 筛选项 |
| GET | `/api/oj/progress` | 本人的题目通过状态 |
| GET | `/api/oj/problems/{id}` | 可见题目、初始模板与公开样例 |
| GET | `/api/oj/languages` | 当前启用语言 |
| GET | `/api/oj/runtime` | 可用资源，不暴露私有节点凭据 |
| POST | `/api/oj/run` | 运行公开样例或用户提供的测试输入 |
| POST | `/api/oj/submit` | 正式提交，使用保存的全部测试 |
| GET | `/api/oj/submissions` | 本人的历史，支持 problemId / page |
| GET | `/api/oj/submissions/{id}` | 本人任务结果与代码 |
| GET | `/api/oj/submissions/{id}/events` | 本人任务 SSE 进度 |
| GET | `/api/oj/submissions/{id}/performance` | 本人正式提交性能分布 |
| GET | `/api/oj/problems/{id}/ranking` | language / mode 对应排行 |
| GET | `/api/oj/leaderboard` | 全局通过统计 |
| GET | `/api/oj/problems/{id}/solution` | 当前可见题目对应题解 |

运行 / 提交 body：

```json
{"problemId":"1","language":"cpp","mode":"STDIO","code":"完整程序或函数代码"}
```

run 可额外提供 input 字符串。返回的是已持久化任务，不等于同步判题结果。依次处理 QUEUED、RUNNING 和终态 AC / WA / CE / TLE / MLE / RE / OLE / IE。不要把打开弹窗或关闭连接当成取消后台任务。

SSE 通过带 sap-token 的 fetch 连接，不能用无法设置认证头的裸 EventSource。进度事件包括入队、分配、编译、逐例执行和 FINISHED。最终可靠结果仍可通过任务详情读取；连接断开可重新查询或重连。响应声明 `no-store, no-transform` 和 `X-Accel-Buffering: no`。

## 管理题目与题解

管理角色为 0 / 1 / 2，其中 1 只为协议兼容保留，新账号界面创建 0 / 2 / 3。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET / POST | `/api/admin/oj/problems` | 管理目录 / 创建 ProblemPack 草稿 |
| GET | `/api/admin/oj/problems/{id}` | 包含 pack、revision 的私有详情 |
| PUT | `/api/admin/oj/problems/{id}` | body 为 pack / revision，乐观并发更新 |
| POST | `/api/admin/oj/problems/{id}/validate` | 新建完整验证任务 |
| GET | `/api/admin/oj/jobs/{id}` | 验证状态与管理详情 |
| PUT | `/api/admin/oj/problems/{id}/status` | DRAFT / PUBLISHED / CONTEST_ONLY / DISABLED |
| GET / PUT | `/api/admin/oj/problems/{id}/solution` | 题解查询 / 保存 |
| POST | `/api/admin/oj/solutions/import` | 批量题解文档 |
| GET / PUT | `/api/admin/oj/order` | 读取 / 更新完整有序题目 ID 列表 |
| GET / PUT | `/api/admin/oj/languages` | 语言启用与时间、内存限制 |
| GET | `/api/admin/oj/health` | 判题池健康与容量 |
| GET | `/api/admin/oj/monitor` | 队列与运行记录 |
| GET | `/api/admin/oj/monitor/{id}` | 管理任务时间线 |
| GET | `/api/admin/oj/monitor/{id}/code` | 管理角色查看提交代码 |

管理接口中的 pack 含隐藏测试和参考代码，只能在管理上下文处理。status 发布接口会验证当前题目与语言配置签名，不能通过 create / update 的 body 直接绕过为已发布状态。

## 题单与比赛

用户接口统一前缀 `/api/oj/sets`，管理接口 `/api/admin/oj/sets`。创建或编辑请求字段以 `ProblemSetRequest.java` 为准，使用服务端返回的 revision。

| 用户操作 | 路径 |
| --- | --- |
| 列表 / 详情 | GET 根路径 / `/{id}` |
| 加入练习或报名 | POST `/{id}/join` |
| 题面 | GET `/{id}/items/{itemId}` |
| 样例运行 / 正式提交 | POST `/{id}/items/{itemId}/run` / `submit` |
| 排名 / 提交记录 | GET `/{id}/ranking` / `submissions` |
| 代码查看 | GET `/{id}/submissions/{jobId}/code` |
| 题解 | GET `/api/oj/sets/{id}/items/{itemId}/solution` |

管理接口还包括创建、编辑、复制、status、public-code、extend、items/{itemId}/cancel、participants 和 submissions/{jobId}/rejudge。题单题目使用快照；比赛提交是否在截止前接收由服务器决定，浏览器时间不作为依据。

比赛结束前不开放题解。练习、比赛和普通题库的通过状态分别计算，历史普通题库 AC 不直接计入新题单。参赛名单和代码公开策略不能由普通用户改写。

## 学习档案

GET `/api/oj/learning` 获取当前账号的统计与私人学习档案。POST `/api/oj/learning/state` 更新档案动作，结构以 `LearningAction.java` 为准。学习计划、错题、收藏、复习和笔记按用户隔离，不从前端传来的 userId 决定所属账号。

## 判题节点

GET / POST `/api/admin/oj/nodes` 读取或登记节点，PUT / DELETE `/{id}` 更新或移除。POST `/{id}/{action}` 执行 start / stop / refresh 动作。登记格式为 name / endpoint / token / maxConcurrency，不回传可用明文令牌。

节点资源上报 POST `/api/oj-nodes/{id}/heartbeat` 使用 `Authorization: Bearer <节点令牌>`，不使用用户 sap-token 代替。节点控制接口 5051 不向普通用户开放。外部节点需要同一 runtimeId、有效令牌和正常资源隔离。

## 错误处理

- 400：字段、代码协议或校验失败，按 message 修复。
- 401：重新登录正确站点，不向其他地址转发令牌。
- 403：检查账号状态、角色、参赛范围或比赛时间。
- 404：不可见 / 不存在的题目、任务或节点，不能枚举绕过权限。
- 409：revision、节点容量或数据状态冲突，重新读取再决定操作。
- 429：账号任务或认证限流，等待窗口结束。
- 503：没有可用判题资源，不降级成宿主直接执行。

## 业务设置与对象存储

GET / PUT `/api/admin/settings` 仅超级管理员可用，字段为 siteName / registrationEnabled / queueCapacity / dailySubmissionLimit / maxJudgeGroupMs。基础服务连接、数据库密码和 S3 密钥不在这个接口中处理。具体边界见部署手册。

`/api/admin/storage` 的状态、连接检查、题包归档和下载仅限角色 0 / 2，完整流程见 `STORAGE.md`。私有归档下载直接返回带 no-store 的 JSON 文件，其他 API 仍返回 Result 包装。
