# 标准客户端证书：双后端隔离联调

执行目录为 file-manager 根目录。准备 Python 3.11+、`requests` 和 `cryptography>=45`（支持 Java PKCS#12 信任库）、GM 的 JDK 21 和 FM 的 JDK 17，以及**待验证版本**的两个可执行 Spring Boot JAR。

```powershell
$test = 'src/test/integration/issuer-interop/run.py'
python -B $test --fixture-only
python -B $test --gm-java $gmJava --gm-jar $gmJar --fm-java $fmJava --fm-jar $fmJar
```

四个变量均指向本机文件，不能传已有服务 URL。脚本新建系统临时目录、签发七天有效的测试 CA 和环回 HTTPS 服务证书，分别启动 GM（H2）及 FM（SQLite），在 GM 创建新项目后重启以固定集成令牌的项目范围。FM 的 MinIO 配置指向不可用的环回服务；不测对象存储。退出时关闭自己启动的进程，清除临时目录。不要通过 `python -O` 关闭断言。运行期间勿分享临时目录：含测试密钥和会话数据库。

`--fixture-only` **不验证任何 API 或客户端认证**。正常联调分别经 GM 管理会话/CSRF 和 FM 管理员密码 JWT 提交真实 CSR；还经 GM HTTPS 后台 bearer 读取列表和详情。测试重新解析返回 PEM，检查临时 CA 签名、CSR 公钥、clientAuth 和叶证书 DER SHA-256，重复提交检查幂等，换另一入口吊销后复查状态和认证。

**证据边界：** `PASS programmatic mTLS` 表示软件客户端显式携带密钥进行真实 HTTPS、绑定、证书登录并访问受保护 API；吊销后的 HTTP 拒绝不等于 TLS 握手被拒。脚本不触碰 Windows、操作系统或浏览器的证书库，也不驱动系统选证弹窗。系统选证只有在单独的可丢弃桌面/虚拟机中由人实际选证并完成登录才算验证，不能用脚本进程成功代替。

## 实测记录（2026-11-20）

GM `platform-service-0.1.0-SNAPSHOT.jar`（JDK 21）和 FM `file-manager-1.9-build145.jar`（JDK 17）均由启动脚本复制后运行。本次双后端验收退出码 0。

|签发入口|叶证书 SHA-256（三路读取一致）|另一入口吊销后，旧设备 JWT 首次被拒|
|---|---|---|
|GM 会话 + CSRF|`a15b23778f3c75dd3edf7e5bbaff5b08ee373c582a5f92f455db6ff933d47e97`|FM 吊销后 0.02 秒|
|FM 密码 JWT|`2bafb2d6461b0d3052a7fc8ba979f1f82c027226b4237f914fe3a8b7ea682158`|GM 吊销后 30.02 秒|

两张证书均经程序化真实 TLS：未绑定时证书登录被拒，绑定后取得设备 JWT 并读取 `/api/auth/info`；另一入口吊销后，状态一致，旧 JWT 与无 bearer 的新证书登录均被拒。系统浏览器选证：未运行。

### 固定最终构建复验（2026-11-20）

先复制两个 JAR 并计算 SHA-256，再仅以这两份副本运行。

|构建|JAR SHA-256|
|---|---|
|GM platform-service 0.1.0-SNAPSHOT|`20ea332762bbe1c28893537179d07d2c837093dd972d229d4f745a9c602704fe`|
|FM 1.9-build145|`434b51332adf927d93910f5b2dab3c733bb04604b8dc762c0152cb13499480dd`|

最终复验 **退出码 0**，两条签发、检索、绑定、程序化真实握手与吊销路径全部通过。FM 发起吊销后，旧设备 JWT 首次被拒为 **0.02 秒**；GM 发起吊销后为 **30.00 秒**。对应叶证书 SHA-256 分别为 `190edaac174b4dfdb738df5f22446145458161f261f4d41a132c24fd371950f8`、`79d5024ad767681bb9fb3efbf06adc8d9b030232e07b52042570fd9da4ee0433`。未验证系统浏览器选证。

启动说明：只等 HTTPS 可用时，两次在新库管理员登录阶段收到 401。启动脚本现以只读方式等待独立 SQLite 的管理员记录，最终运行多等了 0.12 秒；不重试或放宽认证断言。诊断过程中另有一次业务断言均通过但临时数据库句柄未关闭，清理退出码为 1，不计作成功；最终运行显式关闭只读句柄，临时目录清理成功。
