package com.lan4jn.file.config;

import com.lan4jn.file.utils.JwtTokenUtil;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class MessageWebSocketAuthService {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;

    public MessageWebSocketAuthService(JwtTokenUtil jwtTokenUtil,
                                       UserDetailsService userDetailsService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
    }

    public String resolveUsername(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        if (!jwtTokenUtil.validateToken(token)) {
            return null;
        }
        String username = jwtTokenUtil.getUsernameFromToken(token);
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (userDetails == null || !userDetails.isEnabled()) {
            return null;
        }
        return username;
    }
}
