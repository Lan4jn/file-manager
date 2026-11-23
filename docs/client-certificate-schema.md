# 客户端证书绑定表迁移

使用 `user_certificate` 自增主键和唯一 SHA-256 叶证书指纹。只保存绑定信息，不保存证书、私钥、密码或 JWT。默认不启用证书登录，密码登录不读取绑定表。上线前离线备份并在维护窗口按实际 JPA 命名策略核对表/列（以下为默认 snake_case）。`ddl-auto=update` 的开发库由 JPA 增量建表；禁用 DDL 的环境应由 DBA 先执行迁移。生产不要使用 `create` 或 `create-drop`。

### SQLite（无旧表）

```sql
CREATE TABLE user_certificate (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id BIGINT NOT NULL,
  fingerprint VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL,
  expires_at BIGINT,
  generation VARCHAR(36)
);
CREATE INDEX ix_user_certificate_user ON user_certificate(user_id);
```

### 达梦（无旧表）

```sql
CREATE TABLE user_certificate (
  id BIGINT IDENTITY(1,1) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  fingerprint VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL,
  expires_at BIGINT,
  generation VARCHAR(36)
);
CREATE INDEX ix_user_certificate_user ON user_certificate(user_id);
```

若早期试用版已建表，勿执行建表语句。SQLite 用 `PRAGMA table_info(user_certificate)`；达梦用 `USER_TAB_COLUMNS` 检查现有列。只对缺少的列各执行一次：

```sql
ALTER TABLE user_certificate ADD expires_at BIGINT;
ALTER TABLE user_certificate ADD generation VARCHAR(36);
```

`expires_at` 是经 TLS 验证的叶证书到期 Unix 毫秒；`generation` 是每次绑定的新 UUID。旧行的缺失值保持 NULL：可从本人账号列表中撤销，但没有有效期/代际信息的记录不能认证。**不要人工回填时间或 UUID**；请密码登录，删除旧行并用当前有效证书重新验证密码绑定。确认指纹唯一约束存在。多数据源环境须在所有认证所用数据库一致升级；达梦 SQL 需在目标版本预发布库验证。

关闭功能并重启会禁用绑定/登录入口，已发的证书 JWT 无法通过后续 HTTP 及新的消息 WebSocket 握手；无需清除绑定表。重新开启时，仍在 JWT 有效期内且绑定未撤销的 JWT 可以再次使用，如需永久撤销请删除绑定。撤销后重绑同一张证书不会恢复旧 JWT。已建立的 WebSocket 不因解绑自动断开；需要即时停止现存推送连接时，应同时终止已有会话。密码登录令牌不绑定设备，遵循现有 JWT 生命周期；更改密码本身不会删除绑定。CA 侧吊销需要独立运维，不能代替应用内解绑。
