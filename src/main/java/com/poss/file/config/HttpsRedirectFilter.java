package com.poss.file.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class HttpsRedirectFilter extends OncePerRequestFilter {

    @Value("${app.security.force-https:false}")
    private boolean forceHttps;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!forceHttps || request.isSecure()) {
            filterChain.doFilter(request, response);
            return;
        }

        StringBuilder redirectUrl = new StringBuilder("https://").append(request.getServerName());
        int serverPort = request.getServerPort();
        if (serverPort != 80 && serverPort != 443) {
            redirectUrl.append(':').append(serverPort);
        }
        redirectUrl.append(request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
            redirectUrl.append('?').append(request.getQueryString());
        }

        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader(HttpHeaders.LOCATION, redirectUrl.toString());
    }
}
