package com.lan4jn.file.config;

import com.lan4jn.file.utils.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageWebSocketAuthServiceTest {

    @Test
    void shouldRejectDisabledUserEvenWhenTokenIsValid() {
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MessageWebSocketAuthService service = new MessageWebSocketAuthService(jwtTokenUtil, userDetailsService);

        when(jwtTokenUtil.validateToken("token-1")).thenReturn(true);
        when(jwtTokenUtil.getUsernameFromToken("token-1")).thenReturn("bob");
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(buildUser("bob", false));

        assertNull(service.resolveUsername("token-1"));
    }

    @Test
    void shouldReturnUsernameForEnabledUserWithValidToken() {
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MessageWebSocketAuthService service = new MessageWebSocketAuthService(jwtTokenUtil, userDetailsService);

        when(jwtTokenUtil.validateToken("token-2")).thenReturn(true);
        when(jwtTokenUtil.getUsernameFromToken("token-2")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(buildUser("alice", true));

        assertEquals("alice", service.resolveUsername("token-2"));
    }

    private UserDetails buildUser(String username, boolean enabled) {
        return User.withUsername(username)
                .password("N/A")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .disabled(!enabled)
                .build();
    }
}
