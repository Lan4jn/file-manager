# 分享有效过滤与分页设计

## 背景

当前系统已有“我的分享”和收件箱能力：

- “我的分享”通过 `/api/share/list` 一次性返回当前用户创建的可见分享记录。
- 收件箱通过 `/api/messages/inbox` 支持分页参数，但前端固定请求 `page=1,size=100`，页面上没有分页控件。
- `v1.8.1` 已拆分“删除分享记录”和“取消分享”，分享记录可能处于有效、过期、已取消、被创建者隐藏等状态。

新需求是在“我的分享”和收件箱新增 `仅显示有效分享` 开关，并让分享项像文件管理一样分页显示。

## 目标

- “我的分享”新增 `仅显示有效分享` 开关。
- 收件箱新增 `仅显示有效分享` 开关。
- 开关文案统一为 `仅显示有效分享`。
- 有效分享定义为：`expireTime > 当前时间` 且 `cancelled != true`。
- “我的分享”支持后端分页，前端用 `el-pagination` 展示页码、总数和每页数量。
- 收件箱使用已有分页接口能力，前端改为真实分页，不再固定加载前 100 条。
- 切换过滤开关、页码、页大小时重新加载数据；切换过滤开关或页大小时回到第 1 页。

## 非目标

- 不改变分享访问、取消分享、删除分享记录的既有语义。
- 不新增“仅未读”“仅站内分享”等更多筛选项。
- 不把分页设置持久化到用户偏好，页面刷新后使用默认页大小。
- 不改变工作台分享提醒的数量逻辑；工作台仍只取当前收件箱数据生成提醒。

## 业务规则

### 有效分享

满足以下条件时视为有效：

1. `cancelled` 不是 `true`。
2. `expireTime` 晚于后端当前时间。

对历史数据的兼容：

- `cancelled = null` 按未取消处理。
- `expireTime = null` 不应出现在正常分享记录中；如果出现，按无效处理，避免长期展示异常记录。

### 我的分享过滤

`仅显示有效分享 = false` 时：

- 返回当前用户创建、且未被创建者删除记录的分享。
- 包含已过期和已取消记录，用于历史查看。

`仅显示有效分享 = true` 时：

- 在上述范围内额外过滤有效分享。

### 收件箱过滤

`仅显示有效分享 = false` 时：

- 保持当前收件箱语义，返回用户未删除的站内信和反馈消息。

`仅显示有效分享 = true` 时：

- 只保留关联分享仍有效的分享类消息。
- 没有关联分享的反馈类消息不展示，因为该开关语义是“有效分享”。
- 如果消息引用的 `shareCode` 不存在、已过期或已取消，则不展示。

## 后端设计

### 我的分享分页接口

扩展 `GET /api/share/list` 参数：

- `page`：页码，默认 `1`。
- `size`：每页数量，默认 `25`。
- `validOnly`：是否仅有效分享，默认 `false`。

返回结构改为 `PageResult<ShareLink>`。

兼容说明：当前前端是本仓库唯一调用方；如果为了降低兼容风险，也可以在服务层保留原 `List<ShareLink>` 方法供测试或其他内部调用使用。

Repository 增加分页查询：

- `findVisibleByCreateByOrderByCreateTimeDesc(createBy, pageable)`。
- `findValidVisibleByCreateBy(createBy, now, pageable)`。

服务层在返回分页结果前，对当前页内容补充 `recipientDisplayText`。

### 收件箱分页过滤

扩展 `GET /api/messages/inbox` 参数：

- `page`：继续沿用现有参数。
- `size`：继续沿用现有参数。
- `validOnly`：是否仅有效分享，默认 `false`。

`validOnly = false` 时保持现有查询。

`validOnly = true` 时新增服务查询逻辑：

- 只查询 `messageType = SHARE` 且当前用户未删除的消息。
- 通过 `shareCode` 关联 `ShareLink`。
- 过滤 `ShareLink.cancelled != true` 且 `ShareLink.expireTime > now`。
- 返回 `PageResult<UserShareMessage>`。

为避免 N+1 和内存分页，优先用 Repository JPQL 分页查询完成过滤。

## 前端设计

### 我的分享

新增状态：

- `sharePage = 1`
- `sharePageSize = 25`
- `shareTotal = 0`
- `shareValidOnly = false`

`loadShares()` 请求：

- `/api/share/list?page=sharePage&size=sharePageSize&validOnly=shareValidOnly`

页面展示：

- 在“我的分享”列表工具区新增 `el-checkbox`，文案 `仅显示有效分享`。
- 表格下方增加 `el-pagination`，布局参考文件管理分页：总数、每页数量、上一页/下一页、页码跳转。
- 切换 `shareValidOnly`：设置 `sharePage = 1` 后重新加载。
- 切换页大小：设置 `sharePageSize`，`sharePage = 1` 后重新加载。
- 切换页码：更新 `sharePage` 后重新加载。

### 收件箱

新增状态：

- `inboxPage = 1`
- `inboxPageSize = 25`
- `inboxTotal = 0`
- `inboxValidOnly = false`

`loadInbox()` 请求：

- `/api/messages/inbox?page=inboxPage&size=inboxPageSize&validOnly=inboxValidOnly`

页面展示：

- 在收件箱列表上方新增 `el-checkbox`，文案 `仅显示有效分享`。
- 收件箱列表下方增加 `el-pagination`。
- 切换过滤或页大小时回到第 1 页。

### 实时消息影响

当前 WebSocket 收到新消息后会把消息合并到 `inboxList`。分页后调整为：

- 如果当前在收件箱第 1 页，且消息符合当前过滤条件，则可合并到当前列表顶部。
- 如果不在第 1 页，或无法可靠判断是否符合过滤条件，则只更新未读计数/横幅提醒，不强行插入当前页。
- 最小实现可以在收到新消息后调用 `loadInbox()` 刷新当前页，保证分页总数和过滤结果正确。

## 测试设计

### 后端测试

- `ShareLinkServiceTest` 或新增 controller 测试：
  - `validOnly=false` 返回未隐藏的历史分享，包括过期/取消。
  - `validOnly=true` 只返回未过期且未取消分享。
  - 分页参数能返回正确 `total`、`page`、`size`、`items`。

- `MessageControllerTest` / `UserShareMessageServiceTest`：
  - `validOnly=false` 保持现有收件箱结果。
  - `validOnly=true` 过滤已过期、已取消、不存在分享的消息。
  - 分页参数生效。

### 前端测试

优先抽 helper，避免直接测试大型 `app.js`：

- 分享分页参数构造 helper：验证 `page/size/validOnly`。
- 收件箱分页参数构造 helper：验证 `page/size/validOnly`。
- 开关变化时页码重置 helper：验证切换过滤回到第 1 页。

同时执行：

- `node --check src/main/resources/static/js/app.js`
- 对新增 helper 执行 `node --test`。

## 版本

本次会修改后端接口、前端页面和测试，按项目规则 build 号递增。当前版本为 `1.8.1-build124`，实现时更新为 `1.8.1-build125`。
