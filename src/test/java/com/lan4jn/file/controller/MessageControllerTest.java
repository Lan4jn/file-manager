package com.lan4jn.file.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageControllerTest {

    @Test
    void shouldExposeInboxEndpointContract() throws Exception {
        if (!Files.exists(Paths.get("src/main/java/com/lan4jn/file/controller/MessageController.java"))) {
            fail("缺少 MessageController.java");
        }

        Class<?> controllerClass = Class.forName("com.lan4jn.file.controller.MessageController");
        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        assertNotNull(classMapping);
        assertTrue(Arrays.asList(classMapping.value()).contains("/api/messages"));

        Method inboxMethod = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .filter(method -> Arrays.asList(method.getAnnotation(GetMapping.class).value()).contains("/inbox"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 /inbox GET 接口"));

        String returnType = inboxMethod.getGenericReturnType().getTypeName();
        assertTrue(returnType.contains("PageResult") || returnType.contains("java.util.List"));
    }

    @Test
    void shouldExposeRecipientEndpointContract() throws Exception {
        Class<?> controllerClass = Class.forName("com.lan4jn.file.controller.MessageController");

        Method recipientsMethod = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .filter(method -> Arrays.asList(method.getAnnotation(GetMapping.class).value()).contains("/recipients"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 /recipients GET 接口"));

        assertNotNull(recipientsMethod);
    }
}
