package com.lan4jn.file.service;

import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.utils.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/** The same authorization decision gates both HTTP authentication and message socket handshakes. */
@Service
public class BearerIdentityService {
    private final JwtTokenUtil jwt;
    private final UserDetailsService details;
    private final CertificateAuthService certificates;
    private final UserRepository users;
    private final LoginProtectionService protection;
    private final IssuerCertificatePolicy policy;
    private final boolean certificatesEnabled;

    @Autowired
    public BearerIdentityService(JwtTokenUtil jwt, UserDetailsService details, CertificateAuthService certificates,
                                 UserRepository users, LoginProtectionService protection,
                                 IssuerCertificatePolicy policy,
                                 @Value("${app.security.client-certificate.enabled:false}") boolean certificatesEnabled) {
        this.jwt = jwt;
        this.details = details;
        this.certificates = certificates;
        this.users = users;
        this.protection = protection;
        this.policy = policy;
        this.certificatesEnabled = certificatesEnabled;
    }

    public BearerIdentityService(JwtTokenUtil jwt, UserDetailsService details, CertificateAuthService certificates,
                                 UserRepository users, LoginProtectionService protection,
                                 boolean certificatesEnabled) {
        this(jwt, details, certificates, users, protection, null, certificatesEnabled);
    }

    public boolean isManagedCertificateSession(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = jwt.getClaimsFromToken(token);
            if (claims == null || !claims.containsKey("bindingId")) {
                return false;
            }
            return (policy != null && policy.isEnabled()) || claims.get("bindingId") != null;
        } catch (Exception e) {
            // 异常必须返回 true 强制后续复核（fail-safe），绝不能返回 false 导致 fail-open
            return true;
        }
    }

    public UserDetails resolve(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = jwt.getClaimsFromToken(token); // verifies the signature and any expiration
            String name = claims.getSubject();
            if (name == null || name.isBlank() || claims.getExpiration() == null) return null;
            if (claims.containsKey("bindingId")) {
                if (!certificatesEnabled || protection.getStatus(name).locked()) return null;
                String[] ref = claims.get("bindingId", String.class).split(":", -1);
                if (ref.length != 2) return null;
                UserCertificate binding = certificates.findById(Long.parseLong(ref[0])).orElse(null);
                Long owner = claims.get("userId", Long.class);
                if (binding == null || !binding.isUsable() || !ref[1].equals(binding.getGeneration())
                        || owner == null || !owner.equals(binding.getUserId())
                        || users.findById(owner).filter(u -> name.equals(u.getUsername()) && Boolean.TRUE.equals(u.getEnabled())).isEmpty()) return null;

                if (!certificates.validateForBearer(binding)) {
                    return null;
                }
            }
            UserDetails user = details.loadUserByUsername(name);
            return user.isEnabled() && user.isAccountNonLocked() && user.isAccountNonExpired() && user.isCredentialsNonExpired() ? user : null;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException | org.springframework.security.core.userdetails.UsernameNotFoundException invalid) {
            return null;
        }
    }
}
