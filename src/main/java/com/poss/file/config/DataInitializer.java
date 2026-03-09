package com.poss.file.config;

import com.poss.file.entity.Role;
import com.poss.file.entity.User;
import com.poss.file.repository.RoleRepository;
import com.poss.file.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
            admin.setPassword(passwordEncoder.encode("admin123"));
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
