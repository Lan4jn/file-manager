# Bucket Stats Snapshot Design

## Goal

将存储桶管理页的“列桶”和“计算桶大小/文件数量”彻底解耦：

- `GET /api/file/buckets` 只负责快速返回可见桶列表和数据库中的统计快照
- 桶统计通过单独的后台刷新流程计算并落库
- 支持手动刷新和定时刷新两种入口

这样可以避免桶列表接口因为实时遍历 MinIO 对象而变慢或卡死。

## Current Problem

当前实现中，`GET /api/file/buckets` 会在返回列表前逐个桶调用 `describeBucket()`，遍历桶内所有对象并实时累计：

- `objectCount`
- `totalSize`

这导致以下问题：

1. 桶列表接口响应时间与桶内对象数量强耦合
2. 存储桶管理页首次打开会被实时扫描阻塞
3. 排序按大小时成本更高，因为大小本身来自实时扫描
4. 桶统计失败时没有稳定的持久化状态，页面只能临时显示结果

## Proposed Architecture

### 1. Bucket list becomes snapshot-based

保留现有的 `GET /api/file/buckets` 接口，但语义改为：

- 先列出当前用户可见桶
- 再从数据库读取每个桶的统计快照
- 将桶基础信息与快照合并返回

该接口不再实时扫描对象，不再直接计算桶大小。

### 2. Introduce bucket stats snapshot storage

新增一张桶统计快照表，用于存储最近一次计算结果。

建议字段：

- `bucket_name`：桶名，唯一键
- `object_count`：对象数量
- `total_size`：总大小，字节
- `status`：`NOT_CALCULATED` / `PENDING` / `RUNNING` / `SUCCESS` / `FAILED`
- `error_message`：失败原因，成功时为空
- `last_calculated_at`：最近一次计算完成时间
- `updated_at`：最近一次更新时间

设计原则：

- 页面只读快照，不读实时统计
- 刷新流程逐桶更新快照
- 单桶失败不会让整批刷新终止

### 3. Refresh process is asynchronous and shared

新增统一的桶统计刷新服务，供两类入口复用：

- 手动刷新
- 定时刷新

两种入口都调用同一套刷新逻辑，避免重复实现。

刷新时：

1. 获取需要刷新的桶集合
2. 将对应快照置为 `PENDING` 或 `RUNNING`
3. 逐桶扫描对象并计算统计值
4. 每处理完一个桶立即写库
5. 全部处理结束后退出

同一时间只允许一个刷新任务在跑，避免重复扫描和状态互相覆盖。

## API Design

### GET `/api/file/buckets`

职责：

- 返回用户可见桶列表
- 合并数据库快照

返回字段沿用现有 `BucketInfo`，并补充统计状态字段：

- `objectCount`
- `totalSize`
- `statsStatus`
- `statsErrorMessage`
- `statsUpdatedAt`

兼容策略：

- 保留 `BucketInfo` 现有字段，降低前端改动面
- `objectCount` / `totalSize` 的值改为来自快照
- 如果没有快照，则默认：
  - `objectCount = 0`
  - `totalSize = 0`
  - `statsStatus = NOT_CALCULATED`

排序规则：

- `name`：按桶名
- `time`：按桶创建时间
- `size`：按快照 `totalSize`

### POST `/api/file/buckets/stats/refresh`

职责：

- 触发一次异步桶统计刷新
- 立即返回，不等待扫描完成

权限建议：

- 仅管理员或具备桶管理权限的用户可调用

返回内容建议包含：

- `started`：是否成功启动
- `message`：提示信息

行为约束：

- 若当前已有刷新任务在执行，则直接返回“任务已在执行中”

### GET `/api/file/buckets/stats/status`

职责：

- 查询当前刷新任务状态

返回字段建议：

- `status`：`IDLE` / `RUNNING` / `SUCCESS` / `FAILED`
- `processedBuckets`
- `totalBuckets`
- `currentBucket`
- `message`
- `lastFinishedAt`

该接口仅服务于前端刷新按钮后的轮询反馈，不参与桶列表数据本身。

## Backend Components

### Bucket stats snapshot entity/repository

新增桶统计快照实体和仓储层，用于持久化统计数据。

职责：

- 按桶名查询快照
- 批量查询多个桶快照
- 创建或更新快照
- 删除桶快照

### Bucket stats refresh service

新增桶统计刷新服务，封装以下职责：

- 防止并发刷新
- 逐桶扫描 MinIO 对象
- 更新快照状态和结果
- 暴露当前任务状态给控制器

该服务不负责权限判断，权限由控制器处理。

### Scheduled job

新增定时任务，周期性触发桶统计刷新。

设计要求：

- 调用与手动刷新同一套服务方法
- 若任务已在执行中，则本次定时刷新跳过
- 刷新周期放配置项，避免硬编码

## Frontend Changes

### Bucket page loads immediately

存储桶管理页首次加载时：

1. 调用 `GET /api/file/buckets`
2. 直接渲染列表
3. 显示快照状态，而不是等待实时计算

### Add refresh button

在“存储桶管理”页头部新增“刷新桶统计”按钮。

点击后：

1. 调用 `POST /api/file/buckets/stats/refresh`
2. 前端轮询 `GET /api/file/buckets/stats/status`
3. 当任务结束时重新调用 `GET /api/file/buckets`

### Status rendering

表格展示规则：

- `NOT_CALCULATED`：显示“未统计”
- `PENDING` / `RUNNING`：显示“统计中”
- `FAILED`：显示“统计失败”，支持 tooltip 展示错误
- `SUCCESS`：正常显示文件数量和总大小

建议增加“更新时间”列或在大小列旁显示最近统计时间，明确这是快照值而非实时值。

## Data Flow

### Page load flow

1. 用户进入存储桶管理页
2. 前端调用 `GET /api/file/buckets`
3. 后端列出可见桶
4. 后端读取这些桶对应的快照
5. 后端合并后返回
6. 前端直接渲染

### Manual refresh flow

1. 用户点击“刷新桶统计”
2. 前端调用 `POST /api/file/buckets/stats/refresh`
3. 后端若空闲则启动异步刷新任务
4. 前端轮询 `GET /api/file/buckets/stats/status`
5. 后端逐桶写入统计快照
6. 状态变为完成后，前端重新加载桶列表

### Scheduled refresh flow

1. 定时任务触发
2. 后端调用桶统计刷新服务
3. 若已有任务运行则跳过
4. 逐桶计算并落库

## Error Handling

### Single bucket failure

单个桶统计失败时：

- 将该桶快照标记为 `FAILED`
- 写入 `errorMessage`
- 保留上一次成功的 `objectCount/totalSize`
- 仅更新 `status=FAILED` 和 `errorMessage`

理由：

- 页面能看到最近一次可用结果
- 不会因为一次临时失败把统计值清空

### Full task failure

整批刷新流程出现系统级异常时：

- 当前任务状态标记为 `FAILED`
- 已经成功写入的桶快照保持不变
- 未处理桶维持原状态

### Bucket deletion and creation

- 创建桶成功后，初始化一条 `NOT_CALCULATED` 快照
- 删除桶成功后，删除对应快照记录
- 定时刷新时若发现数据库里存在已删除桶的快照，应清理脏数据

## Testing Strategy

### Controller tests

覆盖以下场景：

- `GET /api/file/buckets` 返回基础列表与快照合并结果
- `POST /api/file/buckets/stats/refresh` 能成功启动任务
- 刷新进行中再次触发会返回“已在执行中”
- `GET /api/file/buckets/stats/status` 能返回任务状态

### Service tests

覆盖以下场景：

- 无快照时返回 `NOT_CALCULATED`
- 成功刷新后快照写入 `SUCCESS`
- 单桶失败时写入 `FAILED` 且不影响其它桶
- 删除桶后快照被清理
- 同时触发两次刷新只会运行一次

### Frontend tests

覆盖以下场景：

- 存储桶管理页首次加载只调用列表接口
- 点击刷新按钮后开始轮询状态接口
- 刷新完成后重新加载列表
- 不同 `statsStatus` 的显示符合预期

## Scope Boundaries

本次设计只覆盖：

- 存储桶列表接口与桶统计解耦
- 桶统计快照持久化
- 手动刷新与定时刷新
- 存储桶管理页的状态展示

本次不做：

- 精细化的分桶增量统计
- 对象变更后实时推送桶大小更新
- 独立的任务中心 UI
- 非桶管理页面的统计联动重构

## Open Decisions Resolved

以下关键决策已固定：

- 页面只读数据库快照，不读实时统计
- 提供手动刷新和定时刷新两种入口
- 列桶与统计刷新分开两个接口
- 保留现有 `BucketInfo` 主体结构，新增统计状态字段
- 同一时间只允许一个刷新任务运行
