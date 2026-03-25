package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.CurrentPermissionInfoDTO;
import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.service.AppInfoService;
import com.lan4jn.file.service.PermissionResolverService;
import com.lan4jn.file.service.UserService;
import com.lan4jn.file.utils.JwtTokenUtil;
import com.lan4jn.file.utils.RequestValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppInfoService appInfoService;
    private final PermissionResolverService permissionResolverService;
    private final UserService userService;

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
                          UserService userService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.appInfoService = appInfoService;
        this.permissionResolverService = permissionResolverService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String encodedPassword = loginRequest.get("password");
        try {
            String decodedPassword = new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null || !passwordEncoder.matches(decodedPassword, user.getPassword())) {
                return Result.error(401, "用户名或密码错误");
            }

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
        } catch (Exception e) {
            log.error("Login failed for {}", username, e);
            return Result.error(401, "用户名或密码错误");
        }
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String encodedPassword = request.get("password");
        if (userRepository.existsByUsername(username)) {
            return Result.error(409, "用户名已存在");
        }
        try {
            String decodedPassword = new String(Base64.getDecoder().decode(encodedPassword), StandardCharsets.UTF_8);
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(decodedPassword));
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
}
