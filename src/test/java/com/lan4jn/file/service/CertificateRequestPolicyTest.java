package com.lan4jn.file.service;

import com.lan4jn.file.controller.CertificateAuthController;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.utils.JwtTokenUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.security.cert.X509Certificate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CertificateRequestPolicyTest {
    @Test void insecureRequestDoesNotAttemptLogin() {
        CertificateAuthController controller = new CertificateAuthController(mock(CertificateAuthService.class), mock(UserRepository.class), mock(JwtTokenUtil.class), new LoginProtectionService());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{mock(X509Certificate.class)});
        assertEquals(401, controller.certificateLogin(request).getCode());
    }
}
