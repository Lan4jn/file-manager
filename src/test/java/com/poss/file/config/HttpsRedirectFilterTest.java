package com.poss.file.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpsRedirectFilterTest {

    @Test
    void shouldRedirectHttpRequestToHttpsWith301() throws Exception {
        HttpsRedirectFilter filter = new HttpsRedirectFilter();
        ReflectionTestUtils.setField(filter, "forceHttps", true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/file/list");
        request.setServerName("example.com");
        request.setServerPort(80);
        request.setScheme("http");
        request.setSecure(false);
        request.setQueryString("page=1&size=10");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new AssertionError("Filter chain should not continue when redirecting");
        };

        filter.doFilter(request, response, chain);

        assertEquals(301, response.getStatus());
        assertEquals("https://example.com/api/file/list?page=1&size=10", response.getHeader("Location"));
    }

    @Test
    void shouldSkipRedirectForSecureRequest() throws Exception {
        HttpsRedirectFilter filter = new HttpsRedirectFilter();
        ReflectionTestUtils.setField(filter, "forceHttps", true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/file/list");
        request.setServerName("example.com");
        request.setServerPort(443);
        request.setScheme("https");
        request.setSecure(true);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(204);

        filter.doFilter(request, response, chain);

        assertEquals(204, response.getStatus());
    }
}
