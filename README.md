  file-manager/
  ├── pom.xml
  └── src/
      ├── main/
      │   ├── java/com/poss/file/
      │   │   ├── FileManagerApplication.java
      │   │   ├── config/
      │   │   │   ├── MinioConfig.java
      │   │   │   └── WebMvcConfig.java
      │   │   ├── controller/
      │   │   │   ├── FileController.java
      │   │   │   └── ShareController.java
      │   │   ├── service/
      │   │   │   ├── FileService.java
      │   │   │   └── ShareService.java
      │   │   ├── model/
      │   │   │   ├── FileInfo.java
      │   │   │   └── ShareLink.java
      │   │   ├── dto/
      │   │   │   ├── UploadResponse.java
      │   │   │   └── CreateShareRequest.java
      │   │   └── common/
      │   │       ├── Result.java
      │   │       └── GlobalExceptionHandler.java
      │   └── resources/
      │       └── application.yml
      └── test/

  功能特性：
  - 文件上传/下载/删除
  - 文件列表/预览
  - 文件夹创建
  - 文件复制/移动
  - 分享链接创建（支持密码和有效期）
  - 统一响应格式
  - 异常处理

  配置项（application.yml）：
  - MinIO 连接信息（endpoint、access-key、secret-key）
  - 存储桶名称
  - 允许的文件类型
  - 分享链接默认有效期

  运行项目前，需要修改 application.yml 中的 MinIO 配置，指向你的 MinIO 服务器。

    初始化账号密码           
                                                                                
  - 用户名: admin      
  - 密码: admin123                                                              
                                                            
  首次启动后会自动创建管理员账号。                                              
                                                                                
  新增功能                                                                      
                                                                                
  1. 外部配置文件加载
    - 配置文件放在 jar包同目录/config/ 下
    - 通过环境变量 ENV_FILE 指定环境（如 dev, prod）
    - 示例: ENV_FILE=dev java -jar file-manager-1.0.0.jar
  2. SQLite数据库
    - 用户、角色、权限、配置信息存储在 SQLite 数据库中
    - 数据库文件: file-manager.db
  3. 完整RBAC权限系统
    - 用户管理（管理员可创建/编辑/删除用户）
    - 角色管理（ADMIN / USER）
    - 权限管理（细粒度按钮/菜单权限）
  4. 文件上传限制
    - 管理员可在后台配置允许上传的文件类型
    - 管理员可配置最大文件大小
    - 普通用户上传时自动校验
  5. Vue3前端
    - 登录页面
    - 文件管理（上传/下载/删除/分享）
    - 用户管理（仅管理员）
    - 角色管理（仅管理员）
    - 配置管理（仅管理员）

  配置文件位置

  file-manager/
  ├── config/
  │   ├── application.yml        # 默认配置
  │   ├── application-dev.yml    # 开发环境
  │   └── application-prod.yml   # 生产环境
  └── target/
      └── file-manager-1.0.0.jar

  需要我现在构建项目吗？

  新增功能                                         
                                                        
  1. MinIO多Bucket管理                                                          
                                                                                
  - 存储桶列表：显示所有Bucket、文件数量、总大小                                
  - 创建存储桶：可创建新的Bucket                                                
  - 删除存储桶：删除Bucket及其所有文件                      
  - 跨Bucket文件操作：支持在不同Bucket间复制/移动文件

  2. 功能说明Tooltip

  每个功能按钮都添加了问号图标，Hover显示详细说明：

  ┌────────────┬───────────────────────────────────────────────┐
  │    功能    │                     说明                      │
  ├────────────┼───────────────────────────────────────────────┤
  │ 存储桶管理 │ 管理MinIO所有存储桶，包括创建、删除、查看文件 │
  ├────────────┼───────────────────────────────────────────────┤
  │ 文件管理   │ 浏览、上传、下载、删除文件，支持文件夹操作    │
  ├────────────┼───────────────────────────────────────────────┤
  │ 我的分享   │ 创建文件分享链接，支持设置密码和有效期        │
  ├────────────┼───────────────────────────────────────────────┤
  │ 用户管理   │ 管理系统用户，创建、编辑、删除用户账号        │
  ├────────────┼───────────────────────────────────────────────┤
  │ 角色管理   │ 管理系统角色，配置角色权限                    │
  ├────────────┼───────────────────────────────────────────────┤
  │ 系统配置   │ 配置允许上传的文件类型和大小限制              │
  └────────────┴───────────────────────────────────────────────┘

  3. 系统配置管理

  管理员可配置：
  - 允许上传的文件扩展名
  - 单个文件最大大小

  ---
  初始化账号: admin / admin123

配置文件模板：
# 默认配置
# 使用方式: java -jar file-manager-1.0.0.jar
# 或指定环境: ENV_FILE=dev java -jar file-manager-1.0.0.jar

server:
port: 8080

spring:
datasource:
driver-class-name: org.sqlite.JDBC
url: jdbc:sqlite:./data/file-manager.db
jpa:
database-platform: org.sqlite.hibernate.dialect.SQLiteDialect
hibernate:
ddl-auto: update
show-sql: false

minio:
endpoint: http://localhost:9000
access-key: minioadmin
secret-key: minioadmin
default-bucket: file-manager
public-url: http://localhost:9000

jwt:
secret: default-jwt-secret-key
expiration: 86400000

logging:
level:
com.poss.file: info


 
