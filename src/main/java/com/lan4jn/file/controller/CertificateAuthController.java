package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.service.CertificateAuthService;
import com.lan4jn.file.service.LoginProtectionService;
import com.lan4jn.file.service.PeerCertificate;
import com.lan4jn.file.utils.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "app.security.client-certificate.enabled", havingValue = "true")
public class CertificateAuthController {

    private final CertificateAuthService certificateAuthService;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final LoginProtectionService loginProtectionService;

    @Autowired
    public CertificateAuthController(CertificateAuthService certificateAuthService, 
                                     UserRepository userRepository,
                                     JwtTokenUtil jwtTokenUtil,
                                     LoginProtectionService loginProtectionService) {
        this.certificateAuthService = certificateAuthService;
        this.userRepository = userRepository;
        this.jwtTokenUtil = jwtTokenUtil;
        this.loginProtectionService = loginProtectionService;
    }

    @PostMapping("/certificate-login")
    public Result<Map<String, Object>> certificateLogin(HttpServletRequest request) {
        PeerCertificate peer = PeerCertificate.from(request).orElse(null);
        if (peer == null) return Result.error(401, "未检测到有效的客户端证书");
        UserCertificate cert = certificateAuthService.findByFingerprint(peer.fingerprint()).orElse(null);
        if (cert == null || !cert.isUsable()) return Result.error(401, "客户端证书未绑定或已失效");

        if (!certificateAuthService.validateForLogin(cert, peer)) {
            return Result.error(401, "客户端证书验证失败或已失效");
        }

        User user = userRepository.findById(cert.getUserId()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) return Result.error(403, "账号不可用");
        if (loginProtectionService.getStatus(user.getUsername()).locked()) return Result.error(401, "账号已锁定");

        String roles = user.getRoles() == null ? "" :
                user.getRoles().stream().map(role -> role.getCode()).reduce((a, b) -> a + "," + b).orElse("");
        
        String token = jwtTokenUtil.generateToken(user.getUsername(), user.getId(), roles, cert.getId() + ":" + cert.getGeneration());
        
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("roles", roles);
        data.put("nickname", user.getNickname());
        return Result.success(data);
    }

}
