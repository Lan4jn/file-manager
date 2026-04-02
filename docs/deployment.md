# 部署文档

本文档说明 `file-manager` 的两种常见部署方式：

- Jar 部署
- Docker 部署

本文基于当前版本 `1.6-dev`，运行环境为 `Java 17`。

## 一、部署前准备

### 1. 依赖项

- JDK 17+
- Maven 3.8+
- 可访问的 MinIO 服务
- 可写目录：
  - `config/`
  - `data/`
  - `logs/`

### 2. 关键目录说明

项目运行时默认依赖以下目录：

- `config/`：外部 YAML 配置、SSL 证书、Log4j2 配置
- `data/`：SQLite 数据库文件
- `logs/`：应用日志

建议部署目录示例：

```text
/opt/file-manager/
├── app/
│   └── file-manager-1.6-dev.jar
├── config/
│   ├── application.yml
│   ├── application-prod.yml
│   ├── log4j2.xml
│   └── ssl/
│       └── keystore.p12
├── data/
└── logs/
```

### 3. 配置加载规则

程序启动时会读取 Jar 同级目录下的 `config/` 目录：

1. `config/application.yml`
2. `config/application-${ENV_FILE}.yml`

例如：

```bash
ENV_FILE=prod
```

则会依次加载：

- `config/application.yml`
- `config/application-prod.yml`

## 二、配置示例

### 1. 基础配置 `config/application.yml`

```yaml
server:
  port: 19080
  forward-headers-strategy: framework
  ssl:
    enabled: true
    key-store: file:./config/ssl/keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: file-manager

spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 10GB
      max-request-size: 10GB
  datasource:
    driver-class-name: org.sqlite.JDBC
    url: jdbc:sqlite:./data/file-manager.db
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    show-sql: false

minio:
  endpoint: http://127.0.0.1:9000
  access-key: replace-me
  secret-key: replace-me
  default-bucket: file-manager
  public-url: https://127.0.0.1:9000

jwt:
  secret: replace-with-a-strong-jwt-secret-at-least-32-characters-long
  expiration: 86400000

logging:
  config: ./config/log4j2.xml

ui:
  mode: full
```

### 2. 生产覆盖配置 `config/application-prod.yml`

```yaml
server:
  port: 8080
  forward-headers-strategy: framework

spring:
  datasource:
    url: jdbc:sqlite:/opt/file-manager/data/file-manager.db

minio:
  endpoint: http://minio.internal:9000
  access-key: your-access-key
  secret-key: your-secret-key
  default-bucket: file-manager
  public-url: https://files.example.com

jwt:
  secret: change-this-to-a-strong-production-secret
  expiration: 86400000

app:
  security:
    force-https: true
```

说明：

- `minio.endpoint` 必须是后端服务实际可访问的 MinIO API 地址
- `minio.public-url` 是对外访问、分享页展示使用的地址
- `jwt.secret` 请使用至少 32 字符的高强度密钥

## 三、Jar 部署

### 1. 打包

在项目根目录执行：

```bash
mvn clean package
```

产物位于：

```text
target/file-manager-1.6-dev.jar
```

### 2. 复制部署文件

将以下内容复制到目标服务器：

- `target/file-manager-1.6-dev.jar`
- `config/`

如果目标机器还没有对应目录，请提前创建：

```bash
mkdir -p /opt/file-manager/app
mkdir -p /opt/file-manager/config/ssl
mkdir -p /opt/file-manager/data
mkdir -p /opt/file-manager/logs
```

### 3. 启动命令

```bash
cd /opt/file-manager/app
ENV_FILE=prod java -jar file-manager-1.6-dev.jar
```

后台运行示例：

```bash
cd /opt/file-manager/app
nohup env ENV_FILE=prod java -jar file-manager-1.6-dev.jar > ../logs/stdout.log 2>&1 &
```

### 4. systemd 示例

`/etc/systemd/system/file-manager.service`

```ini
[Unit]
Description=File Manager
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/file-manager/app
Environment=ENV_FILE=prod
ExecStart=/usr/bin/java -jar /opt/file-manager/app/file-manager-1.6-dev.jar
Restart=always
RestartSec=5
User=www-data
Group=www-data

[Install]
WantedBy=multi-user.target
```

启用：

```bash
sudo systemctl daemon-reload
sudo systemctl enable file-manager
sudo systemctl start file-manager
sudo systemctl status file-manager
```

## 四、Docker 部署

当前仓库没有内置 `Dockerfile`，但可以按下面方式部署。

### 1. 准备 Dockerfile

在项目根目录新建 `Dockerfile`：

```dockerfile
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY target/file-manager-1.6-dev.jar /app/file-manager.jar

EXPOSE 8080 19080

ENV ENV_FILE=prod

ENTRYPOINT ["java", "-jar", "/app/file-manager.jar"]
```

### 2. 构建镜像

```bash
mvn clean package
docker build -t file-manager:1.6-dev .
```

### 3. 运行容器

```bash
docker run -d \
  --name file-manager \
  -p 8080:8080 \
  -v /opt/file-manager/config:/app/config \
  -v /opt/file-manager/data:/app/data \
  -v /opt/file-manager/logs:/app/logs \
  -e ENV_FILE=prod \
  file-manager:1.6-dev
```

说明：

- `/app/config`：挂载外部配置目录
- `/app/data`：挂载 SQLite 数据目录
- `/app/logs`：挂载日志目录
- `ENV_FILE=prod`：启用生产配置覆盖

### 4. docker compose 示例

如果使用 Docker Compose，可参考：

```yaml
services:
  file-manager:
    image: file-manager:1.6-dev
    container_name: file-manager
    restart: always
    ports:
      - "8080:8080"
    environment:
      ENV_FILE: prod
    volumes:
      - /opt/file-manager/config:/app/config
      - /opt/file-manager/data:/app/data
      - /opt/file-manager/logs:/app/logs
```

启动：

```bash
docker compose up -d
```

## 五、反向代理建议

如果通过 Nginx 对外提供服务，建议由 Nginx 负责 HTTPS 终止。

示例：

```nginx
server {
    listen 443 ssl http2;
    server_name files.example.com;

    ssl_certificate     /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Host $host;
    }
}
```

## 六、升级与备份建议

升级前建议备份：

- `data/file-manager.db`
- `config/`
- `logs/`

升级流程建议：

1. 备份数据库与配置
2. 停止旧服务
3. 替换 Jar 或镜像
4. 检查 `config/application*.yml`
5. 启动服务并验证登录、文件列表、上传、分享

## 七、常见问题

### 1. 启动时报 SQLite 方言错误

确认 `spring.jpa.database-platform` 使用的是：

```yaml
org.hibernate.community.dialect.SQLiteDialect
```

### 2. MinIO 报 502 或 Non-XML response

通常是 `minio.endpoint` 配置错误，应填写后端可访问的 MinIO API 地址，而不是错误的代理地址或控制台地址。

### 3. 分享或预览访问异常

请检查：

- `minio.public-url` 是否正确
- 反向代理是否透传了 `X-Forwarded-*` 头
- HTTPS 证书是否有效

### 4. 旧版 MinIO 某些桶不可操作

如果桶名不符合 Amazon S3 规范，系统会将其标记为不兼容并禁用访问。建议后续迁移为合规桶名。
