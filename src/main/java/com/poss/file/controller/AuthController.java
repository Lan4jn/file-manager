package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.entity.User;
import com.poss.file.repository.UserRepository;
import com.poss.file.utils.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // SHA512 加密
    private String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        log.info("Login request received for username: {}", loginRequest.get("username"));
        String username = loginRequest.get("username");
        String encodedPassword = loginRequest.get("password");

        try {
            // 解码 base64 密码
            String decodedPassword = new String(java.util.Base64.getDecoder().decode(encodedPassword), java.nio.charset.StandardCharsets.UTF_8);
            // 对解码后的密码进行 SHA-512 哈希
            String hashedPassword = sha512(decodedPassword);

            User user = userRepository.findByUsername(username).orElse(null);
            log.info("User found: {}", user != null);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 验证密码
            boolean matches = hashedPassword.equals(user.getPassword());
            log.info("[DEBUG] Password matches: " + matches + " for user: " + username);
            if (!matches) {
                return Result.error("用户名或密码错误");
            }

            String roles = "";
            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                roles = user.getRoles().stream()
                        .map(role -> role.getCode())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
            }

            String token = jwtTokenUtil.generateToken(username, user.getId(), roles);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("roles", roles);

            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("用户名或密码错误");
        }
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String encodedPassword = request.get("password");

        if (userRepository.existsByUsername(username)) {
            return Result.error("用户名已存在");
        }

        try {
            // 解码 base64 密码并哈希
            String decodedPassword = new String(java.util.Base64.getDecoder().decode(encodedPassword), java.nio.charset.StandardCharsets.UTF_8);
            String hashedPassword = sha512(decodedPassword);

            User user = new User();
            user.setUsername(username);
            user.setPassword(hashedPassword);
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
            return Result.error("用户不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());

        if (user.getRoles() != null) {
            data.put("roles", user.getRoles().stream()
                    .map(role -> role.getCode())
                    .toArray());
        }

        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
