package com.lan4jn.file.service;

import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SuperAdminGuardService {

    private final UserRepository userRepository;

    public SuperAdminGuardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void assertCanDelete(User user) {
        if (isSuperAdmin(user)) {
            throw new IllegalStateException("超级管理员不可删除");
        }
    }

    public void assertCanUpdate(User user, UserDTO dto) {
        if (!isSuperAdmin(user)) {
            return;
        }
        if (dto.getEnabled() != null && !dto.getEnabled()) {
            throw new IllegalStateException("超级管理员不可禁用");
        }
        if (dto.getRoles() != null) {
            Set<String> roleCodes = Set.of(dto.getRoles());
            if (!roleCodes.contains("ADMIN")) {
                throw new IllegalStateException("超级管理员不可降权");
            }
        }
    }

    public boolean isSuperAdmin(User user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return "admin".equalsIgnoreCase(user.getUsername()) &&
                user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()).contains("ADMIN");
    }
}
