package com.lan4jn.file.config;

import com.lan4jn.file.utils.JwtTokenUtil;
import com.lan4jn.file.service.BearerIdentityService;
import org.springframework.test.util.ReflectionTestUtils;
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
        JwtTokenUtil jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", "test-only-signing-key-that-is-longer-than-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 60000L);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MessageWebSocketAuthService service = new MessageWebSocketAuthService(new BearerIdentityService(jwtTokenUtil, userDetailsService, null, null, null, false));

        String token = jwtTokenUtil.generateToken("bob", 1L, "");
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(buildUser("bob", false));

        assertNull(service.resolveUsername(token));
    }

    @Test
    void shouldReturnUsernameForEnabledUserWithValidToken() {
        JwtTokenUtil jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", "test-only-signing-key-that-is-longer-than-thirty-two-bytes");
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", 60000L);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        MessageWebSocketAuthService service = new MessageWebSocketAuthService(new BearerIdentityService(jwtTokenUtil, userDetailsService, null, null, null, false));

        String token = jwtTokenUtil.generateToken("alice", 1L, "");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(buildUser("alice", true));

        assertEquals("alice", service.resolveUsername(token));
    }

    private UserDetails buildUser(String username, boolean enabled) {
        return User.withUsername(username)
                .password("N/A")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .disabled(!enabled)
                .build();
    }
}
