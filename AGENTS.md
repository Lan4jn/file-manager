# Repository Guidelines（仓库指南）

本指南面向贡献者，说明如何在基于 Spring Boot 2.7 / Java 8 的文件管理服务上开发。请保持修改小步快跑、可测试、可回溯。

## 项目结构与模块
- `src/main/java/com/poss/file/`：核心代码。子包：`controller`（REST 接口）、`service`（业务逻辑）、`repository`（JPA 数据访问）、`config`（安全、MinIO、Web、数据初始化）、`entity/model/dto/utils/common`（领域对象、DTO、工具、统一 `Result`、异常处理）。
- `src/main/resources/`：`application.yml`、静态资源、Spring 元数据。
- 根目录 `config/`：外部环境配置（`application-dev.yml`、`application-prod.yml`），运行时通过 `ENV_FILE` 选择。
- 运行产物：`data/`（SQLite `file-manager.db`）、`logs/`（Log4j2 输出）、`target/`（构建结果）。`share/` 为前端压缩包，非必要勿改。

## 构建、运行与测试
- `mvn clean package`：编译并生成 `target/file-manager-1.0.0.jar`。
- `mvn spring-boot:run`：本地启动，默认读 `src/main/resources/application.yml`；设置 `ENV_FILE=dev|prod` 可切到 `/config/application-<env>.yml`。
- `java -jar target/file-manager-1.0.0.jar`：运行打包产物，需预先配置 MinIO 凭据。
- `mvn test`：运行单元/集成测试（JUnit5，`spring-boot-starter-test`）。

## 代码风格与命名
- Java 8，4 空格缩进，UTF-8。优先构造器注入（如 `FileService`），避免字段注入。
- 控制器统一返回 `Result`；接口保持资源化命名（如 `/api/files`、`/api/shares`）。
- 日志使用 Log4j2 参数化写法：`log.info("Uploaded {}", key)`；避免通配符导入。
- 类名 PascalCase，方法/字段 camelCase，常量 UPPER_SNAKE；DTO、实体各归其包，请显式声明请求/响应字段。

## 测试指南
- 测试放在 `src/test/java`，路径镜像主代码；文件名用 `*Test`（如 `FileServiceTest`）。
- HTTP 流程优先 `@SpringBootTest` + `MockMvc`；仓储层用 `@DataJpaTest`（SQLite 配置）；MinIO 调用用测试桶或 mock 隔离。
- 覆盖关键路径：认证、上传/下载、分享生成、桶操作；修复缺陷时加回归测试。
- 提交前跑 `mvn test`，若跳过需注明原因。

## 提交与 PR 规范
- 参考历史提交，保持简短祈使句、单一职责（如 “修复下载问题”）。
- PR 需说明变更摘要、执行的测试命令、配置/DB 影响（ENV_FILE 或 schema 变更）、API 或权限改动；前端改动附截图并关联 Issue。

## 安全与配置提示
- 禁止提交真实 MinIO Key、JWT Secret。环境值放 `/config` 并用 `ENV_FILE` 读取；生产密钥由部署环境注入。
- 默认管理员账户 `admin/admin123`，首次运行后请立即修改。
- `file-manager.db` 与日志位于仓库根目录，可写但勿提交；做破坏性 schema 变更前备份数据库。
