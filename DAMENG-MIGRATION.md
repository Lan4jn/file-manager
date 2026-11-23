# 达梦数据库迁移指南

## 方案说明

- SQLite 仍是默认数据库，现有部署不受影响。
- 达梦依赖位于 Maven `dm` profile 中，使用达梦时通过 `-Pdm` 打包。
- 达梦运行配置位于 `config/application-dm.yml`，设置 `ENV_FILE=dm` 后加载。
- 生产环境默认使用 `spring.jpa.hibernate.ddl-auto=validate`，禁止自动修改表结构。

## 动态多数据源模式

`config/application-multi.yml` 提供 SQLite 主库、达梦镜像库示例。系统所有业务读取和写入都使用 `spring.datasource` 主库，镜像库不参与业务读取。

```powershell
$env:ENV_FILE = "multi"
$env:DM_MIRROR_URL = "jdbc:dm://127.0.0.1:5236"
$env:DM_MIRROR_USERNAME = "FILE_MANAGER_MIRROR"
$env:DM_MIRROR_PASSWORD = "replace-me"
java -jar target/file-manager-1.9-build145.jar
```

主库事务提交后，系统会防抖触发镜像同步；同时每隔一段时间执行全量校准。同步时先按外键反向顺序清理镜像，再按正向顺序复制主库数据，并在单个镜像事务中一次提交。因此发生差异时始终以主库为准。

镜像不可用不会阻止主库启动或回滚已经提交的主库事务，失败状态可通过以下接口查看和重试：

```text
GET  /api/admin/database-mirrors/status
POST /api/admin/database-mirrors/sync
```

接口仅允许超级管理员访问。高写入量环境建议增大 `minimum-interval-ms` 和 `reconcile-interval-ms`，或者进一步接入数据库 CDC，而不是持续执行全表镜像。

## 构建达梦版本

```powershell
mvn -Pdm clean package
```

如果部署环境不能访问 Maven Central，可从达梦安装目录获取对应 JDBC 驱动，并将驱动和 Hibernate 6.4 方言安装到内部 Maven 仓库。

## 准备测试 Schema

必须使用独立的达梦用户和 Schema，不要在生产 Schema 上执行 `create` 或 `create-drop`。

```powershell
$env:ENV_FILE = "dm"
$env:DM_URL = "jdbc:dm://127.0.0.1:5236"
$env:DM_USERNAME = "FILE_MANAGER_TEST"
$env:DM_PASSWORD = "replace-me"
$env:DM_DDL_AUTO = "create"
java -jar target/file-manager-1.9-build145.jar
```

确认表结构创建成功后停止应用，将 `DM_DDL_AUTO` 改回 `validate`。

## 迁移 SQLite 数据

迁移工具要求目标 Schema 已存在表结构。默认目标表必须为空；如果是专用迁移 Schema，可显式开启清空目标表。

```powershell
$env:ENV_FILE = "dm"
$env:DM_URL = "jdbc:dm://127.0.0.1:5236"
$env:DM_USERNAME = "FILE_MANAGER_TEST"
$env:DM_PASSWORD = "replace-me"
$env:DM_DDL_AUTO = "validate"

java -jar target/file-manager-1.9-build145.jar `
  --app.database-migration.enabled=true `
  --app.database-migration.sqlite-url=jdbc:sqlite:./data/file-manager.db `
  --app.database-migration.truncate-target=true `
  --app.database-migration.batch-size=500
```

迁移工具会按外键依赖顺序复制表数据，使用 `SET IDENTITY_INSERT` 保留原主键，并在每张表完成后核对源端和目标端行数。

## 达梦集成测试

测试会执行 `create-drop`，只能连接专用测试 Schema。

```powershell
$env:DM_TEST_ENABLED = "true"
$env:DM_TEST_URL = "jdbc:dm://127.0.0.1:5236"
$env:DM_TEST_USERNAME = "FILE_MANAGER_TEST"
$env:DM_TEST_PASSWORD = "replace-me"
$env:DM_TEST_DDL_AUTO = "create-drop"
mvn -Pdm -Dtest=DamengJpaCompatibilityTest test
```

集成测试覆盖达梦连接、IDENTITY 主键、Boolean、LocalDateTime 和 CLOB 字段。

## 上线检查

- 对比 SQLite 与达梦所有业务表行数。
- 验证管理员和普通用户登录、角色权限、桶权限。
- 验证分享、消息、审计日志、归档下载和工作台数据。
- 验证达梦备份恢复流程和数据库账号最小权限。
- 正式切换前备份 `data/file-manager.db`，保留 SQLite 回滚方案。
