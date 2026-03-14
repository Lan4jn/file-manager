# File Manager 文件管理系统

一个基于 Spring Boot + MinIO 的轻量级文件管理系统，提供多用户、多存储桶的文件管理功能。

## 版本信息

**当前版本**: v1.1.0

## 功能特性

### 核心功能
- ✅ 用户登录认证（JWT）
- ✅ 多存储桶管理（MinIO Bucket）
- ✅ 文件上传/下载/删除
- ✅ 文件夹创建和管理
- ✅ 文件复制/移动
- ✅ 文件分享链接（支持密码和有效期）
- ✅ 批量下载（支持文件夹打包）
- ✅ 文件列表排序（先目录后文件）
- ✅ 中文文件名支持

### 用户管理
- ✅ RBAC 权限系统（ADMIN / USER）
- ✅ 用户创建/编辑/删除（仅管理员）
- ✅ 角色权限配置

### 系统配置
- ✅ 文件上传类型限制
- ✅ 文件大小限制（支持无限制）
- ✅ 分享默认有效期配置

## 快速开始

### 前置要求

- JDK 8+
- Maven 3.6+
- MinIO 对象存储服务

### 部署步骤

1. **配置 MinIO**
   - 启动 MinIO 服务
   - 创建访问密钥（Access Key）和密钥（Secret Key）

2. **配置应用**

   编辑 `config/application.properties` 文件：

```properties
# 服务端口
server.port=9080

# MinIO 配置
minio.endpoint=http://localhost:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
minio.default-bucket=file-manager
minio.public-url=http://localhost:9000

# JWT 密钥（生产环境请修改）
jwt.secret=your-secret-key-here
```

3. **启动应用**

```bash
# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/file-manager-1.1.0.jar
```

或者使用启动脚本（Windows）：
```cmd
start.bat
```

4. **访问系统**

打开浏览器访问：http://localhost:9080

默认管理员账号：
- 用户名: `admin`
- 密码: `admin123`

## 项目结构

```
file-manager/
├── config/                    # 外部配置文件目录
│   └── application.properties
├── data/                      # SQLite 数据库目录（自动创建）
├── logs/                      # 日志目录（自动创建）
├── src/
│   └── main/
│       ├── java/com/poss/file/
│       │   ├── FileManagerApplication.java    # 启动类
│       │   ├── config/                         # 配置类
│       │   ├── controller/                     # 控制器
│       │   ├── service/                        # 业务逻辑
│       │   ├── repository/                     # 数据访问
│       │   ├── entity/                         # 实体类
│       │   ├── dto/                            # 数据传输对象
│       │   ├── model/                          # 数据模型
│       │   ├── utils/                          # 工具类
│       │   └── common/                         # 通用组件
│       └── resources/
│           ├── static/          # 前端静态资源
│           ├── updates/         # 版本更新说明
│           └── META-INF/
├── target/
│   └── file-manager-1.1.0.jar  # 编译打包后的文件
├── pom.xml                      # Maven 配置
├── start.bat                    # Windows 启动脚本
└── README.md                    # 本文件
```

## 技术栈

### 后端
- Spring Boot 2.7.18
- Spring Security + JWT
- Spring Data JPA
- SQLite (数据库)
- MinIO SDK (对象存储)
- Lombok

### 前端
- Vue 3
- Element Plus
- Axios

## 配置说明

### 完整配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | 8080 |
| `spring.datasource.url` | SQLite 数据库路径 | `jdbc:sqlite:./data/file-manager.db` |
| `minio.endpoint` | MinIO 服务地址 | - |
| `minio.access-key` | MinIO 访问密钥 | - |
| `minio.secret-key` | MinIO 密钥 | - |
| `minio.default-bucket` | 默认存储桶 | file-manager |
| `minio.public-url` | MinIO 公开访问地址 | - |
| `jwt.secret` | JWT 签名密钥 | - |
| `jwt.expiration` | Token 有效期(毫秒) | 86400000 |
| `share.default-expiration` | 分享默认有效期(小时) | 24 |
| `share.directory` | 分享临时目录 | ./share |
| `batch.download.max-size` | 批量下载最大大小(字节) | 2147483648 |
| `spring.servlet.multipart.max-file-size` | 单文件最大大小 | -1 (无限制) |
| `spring.servlet.multipart.max-request-size` | 请求最大大小 | -1 (无限制) |

## 版本历史

- **v1.1.0** (2026-03-14)
  - 添加批量下载功能（文件夹打包）
  - 添加系统帮助功能
  - 添加版本更新日志查看
  - 优化文件列表排序（先目录后文件）
  - 修复中文文件名问题
  - 修复新建目录时创建空文件问题
  - 修复目录显示自己的问题
  - 优化上传进度显示
  - 优化帮助按钮样式

- **v1.0.0** (2026-03-10)
  - 初始版本发布
  - 基础文件管理功能
  - 用户权限管理
  - MinIO 多 Bucket 支持

## 许可证

本项目仅供学习和内部使用。
