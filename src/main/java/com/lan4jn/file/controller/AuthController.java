package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.CurrentPermissionInfoDTO;
import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.service.AppInfoService;
import com.lan4jn.file.service.LoginChallengeService;
import com.lan4jn.file.service.LoginProtectionService;
import com.lan4jn.file.service.PermissionResolverService;
import com.lan4jn.file.service.UserService;
import com.lan4jn.file.utils.JwtTokenUtil;
import com.lan4jn.file.utils.RequestValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String PASSWORD_SCHEME_SHA512 = "SHA512";
    private static final String PASSWORD_SCHEME_BCRYPT = "BCRYPT";

    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppInfoService appInfoService;
    private final PermissionResolverService permissionResolverService;
    private final UserService userService;
    private final LoginProtectionService loginProtectionService;
    private final LoginChallengeService loginChallengeService;

    @org.springframework.beans.factory.annotation.Value("${app.security.http-port:-1}")
    private int httpPort;

    @org.springframework.beans.factory.annotation.Value("${server.port:8080}")
    private int httpsPort;

    @org.springframework.beans.factory.annotation.Value("${app.security.force-https:false}")
    private boolean forceHttps;

    public AuthController(JwtTokenUtil jwtTokenUtil,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AppInfoService appInfoService,
                          PermissionResolverService permissionResolverService,
                          UserService userService,
                          LoginProtectionService loginProtectionService,
                          LoginChallengeService loginChallengeService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appInfoService = appInfoService;
        this.permissionResolverService = permissionResolverService;
        this.userService = userService;
        this.loginProtectionService = loginProtectionService;
        this.loginChallengeService = loginChallengeService;
    }

    @GetMapping("/login-challenge")
    public Result<Map<String, Object>> getLoginChallenge(@RequestParam("username") String username) {
        username = RequestValidationUtils.requireText(username, "username");
        LoginChallengeService.LoginChallenge challenge = loginChallengeService.issue(username);
        User user = userRepository.findByUsername(username).orElse(null);
        Map<String, Object> data = new HashMap<>();
        data.put("challengeId", challenge.challengeId());
        data.put("nonce", challenge.nonce());
        data.put("expiresInSeconds", challenge.expiresInSeconds());
        String passwordScheme = resolvePasswordScheme(user);
        data.put("passwordScheme", passwordScheme);
        if (PASSWORD_SCHEME_BCRYPT.equals(passwordScheme) && user != null) {
            data.put("passwordVerifier", user.getPassword());
        }
        return Result.success(data);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest,
                                             HttpServletRequest request) {
        String username = RequestValidationUtils.requireText(loginRequest.get("username"), "username");
        String password = RequestValidationUtils.nullableText(loginRequest.get("password"), "password");
        String challengeId = RequestValidationUtils.nullableText(loginRequest.get("challengeId"), "challengeId");
        String passwordProof = RequestValidationUtils.nullableText(loginRequest.get("passwordProof"), "passwordProof");
        try {
            loginProtectionService.assertAllowed(username, request);
            User user = userRepository.findByUsername(username).orElse(null);
            if (!matchesLoginSecret(username, user, password, challengeId, passwordProof)) {
                return Result.error(401, loginProtectionService.recordFailure(username, request));
            }
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                return Result.error(403, "当前账号已被禁用");
            }

            loginProtectionService.reset(username, request);
            String roles = user.getRoles() == null ? "" :
                    user.getRoles().stream().map(role -> role.getCode()).reduce((a, b) -> a + "," + b).orElse("");

            String token = jwtTokenUtil.generateToken(username, user.getId(), roles);
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("roles", roles);
            return Result.success(data);
        } catch (IllegalStateException e) {
            log.warn("Login rejected for {}: {}", username, e.getMessage());
            return Result.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("Login failed for {}", username, e);
            return Result.error(401, "用户名不存在或密码错误");
        }
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> request) {
        String username = RequestValidationUtils.requireText(request.get("username"), "username");
        String password = RequestValidationUtils.requireText(request.get("password"), "password");
        if (userRepository.existsByUsername(username)) {
            return Result.error(409, "用户名已存在");
        }
        try {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setNickname(username);
            user.setCreateTime(System.currentTimeMillis());
            user.setEnabled(true);
            userRepository.save(user);
            return Result.success();
        } catch (Exception e) {
            return Result.error("注册失败: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        if (user.getRoles() != null) {
            data.put("roles", user.getRoles().stream().map(role -> role.getCode()).toArray());
        }
        return Result.success(data);
    }

    @PutMapping("/profile")
    public Result<UserDTO> updateProfile(@RequestBody UserDTO userDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        userDTO.setNickname(RequestValidationUtils.requireText(userDTO.getNickname(), "nickname"));
        userDTO.setEmail(RequestValidationUtils.nullableText(userDTO.getEmail(), "email"));
        userDTO.setPhone(RequestValidationUtils.nullableText(userDTO.getPhone(), "phone"));
        return Result.success(userService.updateProfile(authentication.getName(), userDTO));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> passwordData) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String oldPassword = RequestValidationUtils.requireText(passwordData.get("oldPassword"), "oldPassword");
        String newPassword = RequestValidationUtils.requireText(passwordData.get("newPassword"), "newPassword");
        userService.changeOwnPassword(authentication.getName(), oldPassword, newPassword);
        return Result.success();
    }

    @GetMapping("/runtime-config")
    public Result<Map<String, Object>> getRuntimeConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("httpPort", httpPort);
        data.put("httpsPort", httpsPort);
        data.put("forceHttps", forceHttps);
        data.put("appVersion", appInfoService.getCurrentVersion());
        return Result.success(data);
    }

    @GetMapping("/current-permissions")
    public Result<CurrentPermissionInfoDTO> getCurrentPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(permissionResolverService.toDto(
                permissionResolverService.resolveByUsername(authentication.getName())
        ));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }

    private boolean matchesLoginSecret(String username, User user, String password, String challengeId, String passwordProof) {
        if (user == null) {
            if (challengeId != null && !challengeId.isBlank()) {
                loginChallengeService.consume(username, challengeId);
            }
            return false;
        }
        if (challengeId != null && !challengeId.isBlank()) {
            if (passwordProof == null || passwordProof.isBlank()) {
                return false;
            }
            String nonce = loginChallengeService.consume(user.getUsername(), challengeId);
            if (nonce == null) {
                throw new IllegalStateException("登录挑战已失效，请刷新后重试");
            }
            String expectedProof = sha512(user.getPassword() + ":" + nonce);
            return expectedProof.equalsIgnoreCase(passwordProof);
        }
        return password != null && !password.isBlank() && matchesStoredPassword(password, user.getPassword());
    }

    private String resolvePasswordScheme(User user) {
        if (user == null || user.getPassword() == null || user.getPassword().isBlank()) {
            return PASSWORD_SCHEME_SHA512;
        }
        return isBcryptHash(user.getPassword()) ? PASSWORD_SCHEME_BCRYPT : PASSWORD_SCHEME_SHA512;
    }

    private boolean matchesStoredPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return BCrypt.checkpw(rawPassword, storedPassword);
        }
        return passwordEncoder.matches(rawPassword, storedPassword);
    }

    private boolean isBcryptHash(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private String sha512(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算登录摘要", e);
        }
    }
}
