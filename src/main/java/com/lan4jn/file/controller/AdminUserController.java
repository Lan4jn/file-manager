package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.service.UserService;
import com.lan4jn.file.utils.RequestValidationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public Result<List<UserDTO>> getAllUsers() {
        return Result.success(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PostMapping("/users")
    public Result<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        userDTO.setUsername(RequestValidationUtils.requireText(userDTO.getUsername(), "username"));
        userDTO.setNickname(RequestValidationUtils.requireText(userDTO.getNickname(), "nickname"));
        userDTO.setEmail(RequestValidationUtils.nullableText(userDTO.getEmail(), "email"));
        userDTO.setPhone(RequestValidationUtils.nullableText(userDTO.getPhone(), "phone"));
        userDTO.setRoles(RequestValidationUtils.normalizeTextArray(userDTO.getRoles(), "roles"));
        return Result.success(userService.createUser(userDTO));
    }

    @PutMapping("/users/{id}")
    public Result<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        userDTO.setNickname(RequestValidationUtils.nullableText(userDTO.getNickname(), "nickname"));
        userDTO.setEmail(RequestValidationUtils.nullableText(userDTO.getEmail(), "email"));
        userDTO.setPhone(RequestValidationUtils.nullableText(userDTO.getPhone(), "phone"));
        userDTO.setRoles(RequestValidationUtils.normalizeTextArray(userDTO.getRoles(), "roles"));
        return Result.success(userService.updateUser(id, userDTO));
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/users/{id}/password")
    public Result<Void> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> passwordData) {
        String oldPassword = RequestValidationUtils.requireText(passwordData.get("oldPassword"), "oldPassword");
        String newPassword = RequestValidationUtils.requireText(passwordData.get("newPassword"), "newPassword");
        userService.updatePassword(id, oldPassword, newPassword);
        return Result.success();
    }
}
