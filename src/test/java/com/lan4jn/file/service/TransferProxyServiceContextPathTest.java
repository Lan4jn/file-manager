package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferProxyServiceContextPathTest {

    @Test
    void shouldPrefixGeneratedDownloadUrlWithServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/fm");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            TransferProxyService service = new TransferProxyService(null, null, null, null, null, null);
            Method method = TransferProxyService.class.getDeclaredMethod("buildAppUrl", String.class);
            method.setAccessible(true);

            String url = (String) method.invoke(service, "/api/file/download?objectName=a");

            assertEquals("/fm/api/file/download?objectName=a", url);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
