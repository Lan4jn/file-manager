package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.CertificateBindingDTO;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.service.CertificateAuthService;
import com.lan4jn.file.service.LoginProtectionService;
import com.lan4jn.file.service.PeerCertificate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/account/certificates")
@ConditionalOnProperty(name = "app.security.client-certificate.enabled", havingValue = "true")
public class AccountCertificateController {
    private final CertificateAuthService certificates;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final LoginProtectionService protection;

    public AccountCertificateController(CertificateAuthService certificates, UserRepository users, @org.springframework.beans.factory.annotation.Qualifier("passwordEncoder") PasswordEncoder passwords,
                                        LoginProtectionService protection) {
        this.certificates = certificates;
        this.users = users;
        this.passwords = passwords;
        this.protection = protection;
    }

    private User current(Authentication auth) {
        if (auth == null) return null;
        return users.findByUsername(auth.getName()).filter(u -> Boolean.TRUE.equals(u.getEnabled())
                && !protection.getStatus(u.getUsername()).locked()).orElse(null);
    }

    @GetMapping
    public Result<List<CertificateBindingDTO>> listCertificates(Authentication auth) {
        User user = current(auth);
        if (user == null) return Result.error(403, "账号不可用");
        return Result.success(certificates.listByUser(user.getId()).stream().map(CertificateBindingDTO::from).toList());
    }

    public record BindRequest(String password) {}

    @PostMapping
    public Result<CertificateBindingDTO> bindCertificate(@RequestBody BindRequest body, HttpServletRequest request, Authentication auth) {
        User user = current(auth);
        if (user == null) return Result.error(403, "账号不可用");
        PeerCertificate peer = PeerCertificate.from(request).orElse(null);
        if (peer == null) return Result.error(400, "未检测到有效的客户端证书");
        if (body.password() == null || body.password().isBlank()) return Result.error(400, "请输入当前密码");
        if (!StoredPassword.matches(body.password(), user.getPassword(), passwords)) {
            return Result.error(401, protection.recordFailure(user.getUsername(), request));
        }
        try {
            UserCertificate binding = certificates.bind(user.getId(), peer);
            protection.reset(user.getUsername(), request);
            return Result.success(CertificateBindingDTO.from(binding));
        } catch (IllegalStateException | DataIntegrityViolationException duplicate) {
            return Result.error(409, "此证书已绑定或无法绑定");
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> unbindCertificate(@PathVariable Long id, Authentication auth) {
        User user = current(auth);
        if (user == null) return Result.error(403, "账号不可用");
        try {
            certificates.unbind(user.getId(), id);
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException notOwned) {
            return Result.error(404, "未找到此账号的绑定");
        }
    }
}
