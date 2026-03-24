package com.lan4jn.file.service;

import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final SuperAdminGuardService superAdminGuardService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthorizationService authorizationService,
                       SuperAdminGuardService superAdminGuardService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.superAdminGuardService = superAdminGuardService;
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        return userRepository.findById(id).map(this::toDTO).orElse(null);
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new IllegalStateException("用户名已存在");
        }

        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getUsername()));
        user.setNickname(userDTO.getNickname());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setEnabled(userDTO.getEnabled() != null ? userDTO.getEnabled() : true);
        user.setCreateTime(System.currentTimeMillis());
        user.setRoles(authorizationService.resolveRoles(userDTO.getRoles()));

        return toDTO(userRepository.save(user));
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        superAdminGuardService.assertCanUpdate(user, userDTO);

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
            user.setRoles(authorizationService.resolveRoles(userDTO.getRoles()));
        }
        user.setUpdateTime(System.currentTimeMillis());
        return toDTO(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        superAdminGuardService.assertCanDelete(user);
        userRepository.deleteById(id);
    }

    @Transactional
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
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
        Set<Role> roles = user.getRoles();
        if (roles != null) {
            dto.setRoles(roles.stream().map(Role::getCode).toArray(String[]::new));
        }
        return dto;
    }
}
