# File Manager 文件管理系统

一个基于 Spring Boot 3、SQLite、MinIO 和 Vue 3 的文件管理系统，提供多用户、多存储桶、文件分享、站内投递、批量下载、角色授权、审计日志和在线预览能力。

## 版本信息

- 当前版本：`v1.6`
- JDK：`17+`
- Spring Boot：`3.2.5`

## 主要功能

- 用户登录认证与 JWT 鉴权
- MinIO 多存储桶管理
- 文件上传、下载、删除、预览
- 文件夹创建与目录导航
- 单文件分享、目录分享、批量分享
- 分享到链接 / 分享到用户双模式
- 链接分享自动生成 4 位提取码
- 站内信收件箱与用户定向分享
- 分享页在线浏览与批量下载
- 文本预览按扩展名白名单控制
- 角色管理、菜单权限、接口权限、桶级权限
- 审计日志查询与审计权限隔离
- 文件上传大小、分享策略等系统配置
- 完整版 / 轻量版双界面模式

## 技术栈

### 后端

- Spring Boot 3.2.5
- Spring Security
- Spring Data JPA
- SQLite
- MinIO Java SDK
- JJWT
- Log4j2

### 前端

- Vue 3
- Element Plus
- Axios

## 运行要求

- JDK 17 或更高版本
- Maven 3.8+
- 可访问的 MinIO 服务

## 快速开始

### 1. 准备配置

项目运行时优先从 Jar 同级目录的 `config/` 读取 YAML 配置：

- `config/application.yml`
- `config/application-dev.yml`
- `config/application-prod.yml`

通过环境变量 `ENV_FILE` 选择环境配置：

```bash
ENV_FILE=dev
```

加载顺序：

1. `config/application.yml`
2. `config/application-${ENV_FILE}.yml`

后者会覆盖前者的同名配置。

### 2. 关键配置项

```yaml
server:
  port: 9081

spring:
  datasource:
    url: jdbc:sqlite:./data/file-manager-dev.db

minio:
  endpoint: http://127.0.0.1:9000
  access-key: replace-me
  secret-key: replace-me
  default-bucket: file-manager
  public-url: http://127.0.0.1:9000

jwt:
  secret: replace-with-a-strong-jwt-secret-at-least-32-characters-long
  expiration: 86400000

ui:
  mode: full
```

说明：

- `minio.endpoint` 是后端 SDK 直连 MinIO 的地址
- `minio.public-url` 是前端访问、分享展示使用的地址
- `jwt.secret` 生产环境必须替换成高强度密钥
- `ui.mode` 可选 `full` 或 `lite`
- `ENV_FILE` 用于决定叠加哪份环境 YAML

### 3. 本地启动

```bash
mvn clean package
ENV_FILE=dev java -jar target/file-manager-1.6.jar
```

开发阶段也可以直接运行：

```bash
ENV_FILE=dev mvn spring-boot:run
```

### 4. 默认账号

- 用户名：`admin`
- 密码：`admin123`

首次部署后请立即修改默认管理员密码。

## 项目结构

```text
file-manager/
├── config/                           # 外部 YAML 配置、SSL、日志配置
├── data/                             # SQLite 数据文件
├── docs/                             # 项目文档
├── src/main/java/com/lan4jn/file/    # 后端代码
│   ├── config/
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── model/
│   ├── repository/
│   ├── service/
│   └── utils/
├── src/main/resources/
│   ├── static/                       # 前端静态资源
│   ├── version-notes/                # 版本说明
│   └── app-info.properties           # 前端版本号占位
├── pom.xml
└── README.md
```

## 构建与测试

```bash
# 编译并打包
mvn clean package

# 运行测试
mvn test

# 跳过测试编译
mvn -DskipTests compile
```

## 部署文档

部署说明见：

- [docs/deployment.md](/Users/mewcapoo/Project/file-manager/docs/deployment.md)

其中包含：

- Jar 方式部署
- Docker 方式部署
- 配置目录挂载
- 数据目录与日志目录说明

## 安全提示

- 不要把真实的 MinIO 访问密钥、JWT 密钥提交到仓库
- 生产环境请使用独立的 `config/application-prod.yml`
- 建议通过 HTTPS 暴露系统
- SQLite 数据库和日志目录建议定期备份

## 版本说明

当前版本说明见：

- [v1.6.md](/Users/mewcapoo/Project/file-manager/src/main/resources/version-notes/v1.6.md)

## 许可证

本项目仅供学习、内部使用和二次开发参考。
