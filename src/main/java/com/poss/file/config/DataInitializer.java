package com.poss.file.config;

import com.poss.file.entity.Role;
import com.poss.file.entity.User;
import com.poss.file.repository.RoleRepository;
import com.poss.file.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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

    @Override
    public void run(String... args) throws Exception {
        // Create ADMIN role if not exists
        Role adminRole = roleRepository.findByCode("ADMIN").orElse(null);
        if (adminRole == null) {
            adminRole = new Role();
            adminRole.setCode("ADMIN");
            adminRole.setName("管理员");
            adminRole.setDescription("系统管理员");
            adminRole.setCreateTime(System.currentTimeMillis());
            adminRole = roleRepository.save(adminRole);
        }

        // Create USER role if not exists
        Role userRole = roleRepository.findByCode("USER").orElse(null);
        if (userRole == null) {
            userRole = new Role();
            userRole.setCode("USER");
            userRole.setName("普通用户");
            userRole.setDescription("普通用户");
            userRole.setCreateTime(System.currentTimeMillis());
            userRole = roleRepository.save(userRole);
        }

        // Create admin user if not exists
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(sha512("admin123"));
            admin.setNickname("Administrator");
            admin.setEnabled(true);
            admin.setCreateTime(System.currentTimeMillis());

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            admin.setRoles(roles);

            userRepository.save(admin);
            System.out.println("=== Admin user created: admin / admin123 ===");
        }
    }
}
