package com.lan4jn.file.config;

import com.lan4jn.file.controller.AdminConfigController;
import com.lan4jn.file.controller.AdminInitController;
import com.lan4jn.file.controller.AdminPermissionController;
import com.lan4jn.file.controller.AdminRoleController;
import com.lan4jn.file.controller.AdminUserController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityContractTest {

    @Test
    void shouldEnableMethodSecurityForControllerLevelAuthorization() {
        EnableMethodSecurity annotation = SecurityConfig.class.getAnnotation(EnableMethodSecurity.class);
        assertNotNull(annotation);
        assertTrue(annotation.prePostEnabled());
    }

    @Test
    void shouldProtectAdminRoutesAndKeepShareAccessInfoPublic() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/lan4jn/file/config/SecurityConfig.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains(".requestMatchers(\"/api/admin/**\").hasRole(\"ADMIN\")"));
        assertTrue(source.contains(".requestMatchers(\"/api/share/access-info\").permitAll()"));
        assertFalse(source.contains(".requestMatchers(\"/api/file/download/**\").permitAll()"));
        assertFalse(source.contains(".requestMatchers(\"/api/file/preview/**\").permitAll()"));
    }

    @Test
    void shouldRequireAdminRoleOnSplitAdminControllers() {
        assertTrue(AdminUserController.class.isAnnotationPresent(PreAuthorize.class));
        assertTrue(AdminRoleController.class.isAnnotationPresent(PreAuthorize.class));
        assertTrue(AdminPermissionController.class.isAnnotationPresent(PreAuthorize.class));
        assertTrue(AdminConfigController.class.isAnnotationPresent(PreAuthorize.class));
        assertTrue(AdminInitController.class.isAnnotationPresent(PreAuthorize.class));
    }
}
