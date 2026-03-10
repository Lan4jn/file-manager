package com.poss.file.service;

import com.poss.file.dto.UserDTO;
import com.poss.file.entity.Role;
import com.poss.file.entity.User;
import com.poss.file.repository.RoleRepository;
import com.poss.file.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

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

    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id).orElse(null);
        return user != null ? toDTO(user) : null;
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(sha512(userDTO.getNickname())); // 临时密码
        user.setNickname(userDTO.getNickname());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setEnabled(userDTO.getEnabled() != null ? userDTO.getEnabled() : true);
        user.setCreateTime(System.currentTimeMillis());

        Set<Role> roles = new HashSet<>();
        if (userDTO.getRoles() != null) {
            for (String roleCode : userDTO.getRoles()) {
                roleRepository.findByCode(roleCode).ifPresent(roles::add);
            }
        }
        user.setRoles(roles);

        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (userDTO.getNickname() != null) {
            user.setNickname(userDTO.getNickname());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
        }
        if (userDTO.getPhone() != null) {
            user.setPhone(userDTO.getPhone());
        }
        if (userDTO.getEnabled() != null) {
            user.setEnabled(userDTO.getEnabled());
        }
        if (userDTO.getRoles() != null) {
            Set<Role> roles = new HashSet<>();
            for (String roleCode : userDTO.getRoles()) {
                roleRepository.findByCode(roleCode).ifPresent(roles::add);
            }
            user.setRoles(roles);
        }

        user.setUpdateTime(System.currentTimeMillis());
        user = userRepository.save(user);
        return toDTO(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Transactional
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证原密码
        String hashedOld = sha512(oldPassword);
        if (!hashedOld.equals(user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        user.setPassword(sha512(newPassword));
        userRepository.save(user);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setEnabled(user.getEnabled());
        dto.setCreateTime(user.getCreateTime());

        if (user.getRoles() != null) {
            dto.setRoles(user.getRoles().stream()
                    .map(Role::getCode)
                    .toArray(String[]::new));
        }

        return dto;
    }
}
