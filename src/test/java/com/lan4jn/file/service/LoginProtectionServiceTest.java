package com.lan4jn.file.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginProtectionServiceTest {

    private LoginProtectionService loginProtectionService;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        loginProtectionService = new LoginProtectionService();
        ReflectionTestUtils.setField(loginProtectionService, "maxFailures", 5);
        ReflectionTestUtils.setField(loginProtectionService, "lockMinutes", 15L);
        request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void shouldReturnRemainingAttemptsMessageBeforeLock() {
        String first = loginProtectionService.recordFailure("ghost", request);
        String second = loginProtectionService.recordFailure("ghost", request);

        assertEquals("用户名不存在或密码错误，剩余 4 次重试机会", first);
        assertEquals("用户名不存在或密码错误，剩余 3 次重试机会", second);
    }

    @Test
    void shouldLockAfterMaxFailures() {
        for (int i = 0; i < 4; i++) {
            loginProtectionService.recordFailure("ghost", request);
        }

        String message = loginProtectionService.recordFailure("ghost", request);

        assertTrue(message.startsWith("登录失败次数过多，请在 "));
        assertTrue(message.endsWith(" 秒后再试"));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> loginProtectionService.assertAllowed("ghost", request));
        assertTrue(exception.getMessage().startsWith("登录失败次数过多，请在 "));
        assertTrue(exception.getMessage().endsWith(" 秒后再试"));
    }
}
