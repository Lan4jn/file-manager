package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.service.PermissionAssertService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class OperationLogControllerTest {

    @Test
    void shouldDeclareAuditLogReadPermissionCode() throws Exception {
        Class<?> permissionCodesClass = Class.forName("com.lan4jn.file.rbac.PermissionCodes");
        Field field = permissionCodesClass.getDeclaredField("API_AUDIT_LOG_READ");
        assertNotNull(field);
    }

    @Test
    void shouldExposeAuditLogQueryEndpointContractAndProtectIt() throws Exception {
        if (!Files.exists(Paths.get("src/main/java/com/lan4jn/file/controller/OperationLogController.java"))) {
            fail("缺少 OperationLogController.java");
        }

        Class<?> controllerClass = Class.forName("com.lan4jn.file.controller.OperationLogController");
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        assertNotNull(classMapping);
        assertTrue(Arrays.asList(classMapping.value()).contains("/api/audit-logs"));

        Method queryMethod = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 GET 查询接口"));
        assertNotNull(queryMethod);

        Constructor<?> constructor = controllerClass.getDeclaredConstructors()[0];
        Object[] args = new Object[constructor.getParameterCount()];
        PermissionAssertService permissionAssertService = null;
        for (int i = 0; i < constructor.getParameterCount(); i++) {
            Class<?> parameterType = constructor.getParameterTypes()[i];
            args[i] = mock(parameterType);
            if (PermissionAssertService.class.equals(parameterType)) {
                permissionAssertService = (PermissionAssertService) args[i];
            }
        }
        assertNotNull(permissionAssertService);

        Object controller = constructor.newInstance(args);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        try {
            mockMvc.perform(get("/api/audit-logs"));
        } catch (Exception ignored) {
        }

        verify(permissionAssertService).assertApiPermission("API_AUDIT_LOG_READ");
    }
}
