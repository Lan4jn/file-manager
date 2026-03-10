package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.entity.User;
import com.poss.file.repository.UserRepository;
import com.poss.file.utils.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        try {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 直接验证密码
            boolean matches = passwordEncoder.matches(password, user.getPassword());
            System.out.println("[DEBUG] Password matches: " + matches + " for user: " + username);
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
    public Result<Void> register(@RequestBody User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            return Result.error("用户名已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreateTime(System.currentTimeMillis());
        user.setEnabled(true);
        userRepository.save(user);

        return Result.success();
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
