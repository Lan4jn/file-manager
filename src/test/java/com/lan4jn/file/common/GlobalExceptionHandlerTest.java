package com.lan4jn.file.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldTreatBrokenPipeAsClientAbort() {
        assertTrue(handler.isClientAbort(new IOException("Broken pipe")));
        assertTrue(handler.isClientAbort(new AsyncRequestNotUsableException(
                "ServletOutputStream failed to write: java.io.IOException: Broken pipe")));
    }

    @Test
    void shouldNotTreatGenericExceptionAsClientAbort() {
        assertFalse(handler.isClientAbort(new IOException("disk read failed")));
    }

    @Test
    void shouldIgnoreClientAbortWithoutReturningErrorBody() {
        Object result = handler.handleException(
                new AsyncRequestNotUsableException("ServletOutputStream failed to write: java.io.IOException: Broken pipe"));

        assertNull(result);
    }

    @Test
    void shouldStillReturnSystemErrorForGenericException() {
        Object result = handler.handleException(new Exception("boom"));

        assertTrue(result instanceof Result);
        Result<?> body = (Result<?>) result;
        assertEquals(500, body.getCode());
        assertEquals("系统异常", body.getMessage());
    }
}
