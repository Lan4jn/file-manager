# 内网 IP 直连：启用客户端证书登录

浏览器通过 `https://<内网IP>:<端口>` 直连 Spring Boot 内嵌 Tomcat，无需域名。证书是已有账号的另一种登录方式，不创建账号、不授予新权限。功能默认关闭；启用后仍保留密码登录。

## 1. 运维：准备信任材料与回退入口

- **服务器身份**：服务器证书须含 `serverAuth`，SAN 的 **IP Address** 须等于地址栏中的 IP；仅写 CN 或将 IP 写作 DNS 名不够。准备含服务器私钥、叶证书和中间链的 `server.p12`。向终端分发并核实服务器 CA 公钥证书，不得忽略浏览器证书警告。
- **设备身份**：用专用客户端 CA 给每台设备单独签发证书。Tomcat 的客户端 truststore 仅放该用途的信任锚公钥证书，不用系统公共 CA 集合，也不放私钥。CA 私钥留在受控签发系统，不交给应用服务器或终端。
- **变更前**：备份生效配置和数据库，确认本批绑定表迁移就绪。用无客户端证书的浏览器验证一个可用的密码管理员账号，保留独立运维入口。

先通过独立可信渠道核对客户端 CA 的 SHA-256 指纹，再向新建的专用库导入；命令交互输入库口令：

```sh
keytool -printcert -file client-ca.crt
keytool -importcert -alias device-ca -file client-ca.crt -storetype PKCS12 -keystore client-ca.p12
```

## 2. 运维：先开可选 TLS，再开放登录

将下例合并到实际加载的外部配置，替换路径，保留原监听端口。两个库仅允许运维和服务账号按需访问，口令由部署环境注入，不写入仓库或命令行。

```yaml
server:
  ssl:
    enabled: true
    key-store: file:/etc/file-manager/tls/server.p12
    key-store-type: PKCS12
    key-store-password: ${SERVER_STORE_PASSWORD}
    trust-store: file:/etc/file-manager/tls/client-ca.p12
    trust-store-type: PKCS12
    trust-store-password: ${CLIENT_TRUSTSTORE_PASSWORD}
    client-auth: want
app:
  security:
    client-certificate:
      enabled: false
```

重启后先用无证书终端直连：服务器身份校验应通过，密码登录应成功。`want` 允许不交证书；提交不受信的证书可能在握手时就被拒，尚未进入登录页。不要改为 `need`，否则无证书用户无法进入密码登录。确认通过后，将 `app.security.client-certificate.enabled` 改为 `true` 并重启。应用只认直连 TLS 会话中的证书，不从 HTTP 请求头读取身份。

## 3. 签发人和用户：一机一证，首次用密码绑定

1. **设备创建请求。** 优先使用受管设备的不可导出密钥容器生成私钥和 CSR。需要文件导入时，在目标设备的私有目录中创建加密私钥，交互设置口令（以下为 POSIX shell）。只把 `device.csr` 交签发人。

   ```sh
   umask 077
   openssl req -new -newkey rsa:3072 -keyout device.key -out device.csr -subj '/CN=pilot-device-01'
   ```

2. **CA 签发。** 核对申请人与设备后，按客户端模板签发短期、序列号唯一的叶证书：`CA:FALSE`、`digitalSignature`、扩展用途 `clientAuth`。登记设备、序列号、到期日及 SHA-256 指纹。将 `device.crt` 和 CA 公钥链返回申请人，不代收私钥。
3. **设备安装。** 核对指纹与用途，在原设备导出带强口令的 PKCS#12，导入当前用户的“个人证书”（浏览器有独立证书库时导入该库）。`client-chain.pem` 仅含签发 CA 公钥链。不要上传私钥或 P12 到应用，也不要用邮件、聊天或共享盘传递。导入后按组织策略清理临时私钥文件和导出包。

   ```sh
   openssl x509 -in device.crt -noout -fingerprint -sha256 -purpose -dates
   openssl pkcs12 -export -inkey device.key -in device.crt -certfile client-chain.pem -out device.p12
   ```

4. **首次绑定。** 重新建立浏览器连接，选择此设备的证书，再用已有账号和密码登录。在账号的证书管理中绑定当前证书，按提示再次输入当前密码，核对列表中的指纹（比较时可忽略冒号和字母大小写）。TLS 选证框可能在打开站点时就出现，不一定在点击登录时出现；浏览器也可能自动选择或缓存选择。若选错，关闭站点全部连接，必要时完全退出浏览器再试。
5. **验收。** 退出账号后，用已绑定设备选择证书登录，应进入原账号；用无证书的独立浏览器验证密码登录仍可用。受信但未绑定的设备不得仅凭证书登录。接口对照：登录 `POST /api/auth/certificate-login`；查看 `GET /api/account/certificates`；绑定 `POST /api/account/certificates`（请求体含 `password`）；撤销 `DELETE /api/account/certificates/{id}`。

## 4. 到期、遗失与回退

- **换证**：在旧证到期前签新证，用密码在新证连接上绑定，退出后验证新证登录，再撤销旧绑定。即使主题相同，新叶证书的指纹也不同。
- **设备遗失或私钥泄漏**：用可信设备通过密码登录，核对指纹并撤销对应绑定；无法登录时立即联系管理员按账号应急流程处置。同时通知 CA 吊销证书。删除本机证书或仅在 CA 登记吊销都不能替代应用解绑。验证旧证书不能重新登录；另按现行会话失效流程处置并验证已发令牌，不要把禁止新登录当作已有会话已退出。
- **回退**：设 `app.security.client-certificate.enabled: false`；需同时停止浏览器选证提示时，设 `server.ssl.client-auth: none`。保留 HTTPS、服务器证书和密钥配置，重启后用无证书终端验证密码登录，并确认不能新建证书登录会话。不删除绑定表、不回滚整库；再次开放前复核绑定及吊销记录。
