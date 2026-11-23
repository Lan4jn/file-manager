package com.lan4jn.file.config;

import com.lan4jn.file.service.BearerIdentityService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class MessageWebSocketAuthService {
    private final BearerIdentityService identities;
    public MessageWebSocketAuthService(BearerIdentityService identities) { this.identities = identities; }

    public String resolveUsername(String token) {
        UserDetails user = identities.resolve(token);
        return user == null ? null : user.getUsername();
    }

    public boolean isManagedCertificateSession(String token) {
        return identities.isManagedCertificateSession(token);
    }
}
