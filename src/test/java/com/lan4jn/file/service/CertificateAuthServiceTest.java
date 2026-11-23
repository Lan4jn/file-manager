package com.lan4jn.file.service;

import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserCertificateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CertificateAuthServiceTest {

    @Test
    public void testBindNewCertificate() {
        UserCertificateRepository repository = Mockito.mock(UserCertificateRepository.class);
        CertificateAuthService service = new CertificateAuthService(repository);

        when(repository.findByFingerprint("fp1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(UserCertificate.class))).thenAnswer(i -> i.getArguments()[0]);

        UserCertificate cert = service.bind(1L, new PeerCertificate("fp1", System.currentTimeMillis() + 60000));
        assertNotNull(cert);
        assertEquals("fp1", cert.getFingerprint());
    }

    @Test
    public void testBindDuplicateCertificate() {
        UserCertificateRepository repository = Mockito.mock(UserCertificateRepository.class);
        CertificateAuthService service = new CertificateAuthService(repository);

        when(repository.findByFingerprint("fp1")).thenReturn(Optional.of(new UserCertificate(2L, "fp1")));

        assertThrows(IllegalStateException.class, () -> service.bind(1L, new PeerCertificate("fp1", System.currentTimeMillis() + 60000)));
    }
}
