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
        user.setPassword(passwordEncoder.encode(userDTO.getNickname())); // 临时密码
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

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
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
