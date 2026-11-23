# 标准客户端证书：文件管理器与签发服务联动

这个开关只控制签发代理和签发端状态检查，不替代文件管理器的 TLS 客户端证书验证、账号绑定、密码验证或锁定策略。默认关闭；从未联动的独立部署无需配置上游。证书私钥在设备端生成并保管，文件管理器只接收 CSR、叶证书和公开证书链。

## 服务端配置

在现有外部 Spring 配置中增加下列属性并重启；不要把项目密钥放入前端、源码、命令行参数或日志。

```yaml
issuer-bridge:
  enabled: true
  base-url: https://issuer.example.internal:8443
  project-id: ${FILE_MANAGER_ISSUER_PROJECT_ID}  # GM 项目 UUID
  api-key: ${FILE_MANAGER_ISSUER_TOKEN}
  issuer-ca-file: /etc/file-manager/issuer-signers.pem
```

| 项 | 契约 |
| --- | --- |
| `enabled` | 默认 `false`；启用时需要完整配置，不随请求切换。 |
| `base-url` | 固定 HTTPS 源（协议、主机和可选端口）；不包含路径、查询、用户信息或片段。上游不重定向。 |
| `project-id` | 文件管理器绑定的唯一项目，所有请求使用此值。 |
| `api-key` | 上游为这个项目签发的专用集成 Bearer 密钥；不是用户 JWT、管理员密码或证书私钥。 |
| `issuer-ca-file` | 本机 X.509 PEM 公钥证书包，包含该服务**所有直接签发**标准客户端证书的 CA（含轮换期旧 CA）；不是上游 HTTPS 服务证书，也不是只有根的链。 |

三种信任不可混用：① 文件管理器的入站 `server.ssl.trust-store` 验证设备 TLS 链；② 出站 Java TLS 信任库验证上游 HTTPS 服务证书及主机名（内部 HTTPS CA 可通过 JVM `javax.net.ssl.trustStore` 配置）；③ `issuer-ca-file` 的公钥验证设备叶证书的实际签名，决定是否必须向上游查询状态。不要因为 CN 相同就认定证书来自联动 CA；不要为了连通而跳过 TLS 校验。入站证书登录与绑定仍需 `app.security.client-certificate.enabled=true`、HTTPS 和现有受信 TLS 配置。

## HTTP 契约

文件管理器管理集合为 `/api/admin/standard-client-certificates`。要求已登录且具备超级管理员权限。一律按本机配置访问 `/integration/v1/projects/{pid}/standard-client-certificates`，上游凭据不转发给浏览器；不能请求其它项目或自选上游 URL。`GET /api/auth/runtime-config` 的 `data.certificateIssuanceEnabled` 只用于界面显示，不是授权依据。

| 管理操作（相对于集合） | 请求 | 成功时的 `Result.data` |
| --- | --- | --- |
| `GET` | `?fingerprint=&page=0&size=50`；可按证书 SHA-256 指纹过滤 | `{items:[记录],total}` |
| `POST` | `{requestId,csrPem,deviceLabel}` | 单条记录 |
| `GET /{id}` | 无请求体 | 单条记录，其中的 PEM 可保存为证书与链 |
| `POST /{id}/revoke` | 无请求体 | 已吊销记录 |

上游成功响应是裸记录或裸分页对象；下游统一包装 `Result`。记录包含 `id, projectId, requestId, deviceLabel, fingerprint, serial, expiresAt, status, certificatePem, chainPem`，下游另有本地 `bound` 布尔标记。`expiresAt` 为 Unix 毫秒，`status` 为 `VALID`、`REVOKED` 或 `EXPIRED`。`bound` 只表示本机有相同指纹绑定，不是“仍可登录”。签发不自动绑定账号。响应不包含私钥。客户端保留同一签发操作的 `requestId`，遇到超时应查询上游或使用相同的 `requestId` 重试，不要重新生成 CSR/请求标识盲目签发。

## 迁移与轮换

先备份业务库，在每个节点及镜像库上验证 `user_certificate` 能新增可空的 `issuer_managed`（布尔）与 `certificate_der`（公开叶证书的二进制 DER）列。不要把历史行批量设为 `false`，不要凭 DN、项目名称或“上游查不到记录”给旧绑定免责。

* 旧行缺少 DER 时，启用联动后旧证书 JWT 不能证明签发来源，拒绝授权。用户需携带原证书经 TLS 再次执行证书登录，由服务端用真实叶证书分类并补齐资料。缺少原有 `generation`/到期信息的更早绑定仍需走原解绑、重绑流程。
* 新绑定保存公开 DER。已确认属于联动 CA 的绑定保留 `issuer_managed=true`；以后关掉桥或移除旧 CA 不能使这个绑定降级为本地证书。没有确认过来源的绑定在启用时按当前公钥集合验签，而不是永久缓存“独立 CA”。从未启用桥、也从未标记受管的部署继续沿用原认证逻辑。
* 上线或换 CA 时，**先**向文件管理器的 issuer 公钥包加入所有仍可签发/仍有在用设备的历史和新签发密钥，再切换签发端/入站信任；所有节点使用相同完整集合。对尚未成功登录分类的历史证书，过早移除旧公钥会丢失判别来源的依据。不要用关闭开关替代吊销。关闭桥时不能判定新绑定的签发来源；若曾部署联动，退出联动前应先从**所有节点的入站 TLS 信任库**移除联动 CA（或停止客户端证书认证），防止未分类或重新绑定的设备被当作独立设备。
* 运维从 GM 的 `GMSSL_STANDARD_CA_PKCS12` 离线导出直接签发 CA 的**公开证书**供上述挂载；不要向文件管理器或设备分发包含签发私钥的库。上游 `projectId`、记录 `id` 为 UUID 字符串，`requestId` 建议使用 UUID；`chainPem` 是不含叶证书、以签发 CA 开头的连续 PEM 证书，`certificatePem` 单独返回叶证书。

## 撤销和故障边界

* 受管叶证书绑定、证书登录、使用该绑定签发的 JWT 访问受保护资源时都校验状态。只有固定项目下按指纹精确查询得到**唯一**的 `VALID` 且未到期记录才能放行。其他项目、未收录、歧义、吊销、到期、响应异常或网络故障均不能生成新的允许判断。
* 每个文件管理器进程最多缓存 1,024 个已确认有效的指纹。有效期从查询开始算起，最长 30 秒，并受上游 `expiresAt`、本地证书到期和绑定有效性约束；不会因读取或错误续期。上游直接吊销或在其它实例吊销，现有正缓存最多保留这段窗口。由本实例代理吊销时，即使响应在途中丢失，也清空本实例缓存，且不接受吊销前发出的查询回填。
* 网络故障时，已确认的状态只可用到其原有截止时间。没有有效缓存即拒绝鉴权；签发总是需要一次真实上游响应，绝不使用缓存签发或降级为本地 CA。
* 建议先在测试项目验证：启用后签发、用 TLS 证书绑定并登录、保留一次证书 JWT，然后吊销，同一 JWT 的 HTTP 请求与 WS 授权应被拒绝；密码登录不受影响。再验证断开上游、超过 30 秒后，受管证书不再授权。
