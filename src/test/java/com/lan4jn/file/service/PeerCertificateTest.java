package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PeerCertificateTest {

    private static final String SAMPLE_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICyzCCAbOgAwIBAgIIXlggkIufLfMwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UE\n" +
            "AxMJVGVzdCBDZXJ0MB4XDTI2MTEyMDA2MzYwM1oXDTI3MTEyMDA2MzYwM1owFDES\n" +
            "MBAGA1UEAxMJVGVzdCBDZXJ0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC\n" +
            "AQEA39gkekDMyiBUmCr3Dkr+FhpEOyVdtPQAOIzW/1b0eGFwKNL6s5IPsJW+GHg3\n" +
            "cjTxU5t71hAOBH7pNEfofb3OTTdGuSKdL9M6g7CYLECepkR5gR8tzH4SMTepBQrg\n" +
            "de+pWaed1IfLLFHvqxy+OVvgcc3Gs1YDeQv7sTwZTPwid1uUJZ9AKs7uuSDT5ToF\n" +
            "hib/Uz3wIrksONHFx/GJsNu3wyJI8mnUV/cdYjjlNpW0N1PBlspRd6D4u5FVxlAf\n" +
            "/CG/aJ/mMhrS/iockMEUHl9qeq/bvPXnLq1MnDqHWJeUp7027avJ9ytnH/D7Mwxc\n" +
            "lW7Nd9scSvZdyZhZtSCncUk3bwIDAQABoyEwHzAdBgNVHQ4EFgQUNT2iwpw5qzMI\n" +
            "ztBIRf7xeu5KbgowDQYJKoZIhvcNAQELBQADggEBALo6litXssFzCCwTt0QMceWk\n" +
            "o6S+yTWDL2tIM2Wnzcz+V4oe2XkCmZ3tg4KHkGEVbXwIzSHmhyelyXsI5kWt5Dj8\n" +
            "EkKcSBrMDb94jIJhFDrGBgaGvaFCoDGKqmYf/ESAt5apcQPvljFrUcbv4FA5sbXD\n" +
            "b9GU3enlH3XQSJasO40Fv+bsG0arHb6oSy2twWi7NUt/UuhjZ0B7FoXGS7BvkyIj\n" +
            "adcVnO6yuEilsfMg0X5YZkg/W9lXZ+DoLOU2dnWLQsBDoxWLfE2eTKP1u/YEjuPf\n" +
            "5iZuGOs62R3y75u5OshYU+ilpeh8594v9voYzUYfBjsmzHN9oTIMkKyRye9v+YQ=\n" +
            "-----END CERTIFICATE-----";

    @Test
    void fromPreservesActualTlsLeaf() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate realCert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(SAMPLE_PEM.getBytes(StandardCharsets.UTF_8)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{realCert});

        Optional<PeerCertificate> result = PeerCertificate.from(request);
        assertThat(result).isPresent();
        assertThat(result.get().leaf()).isSameAs(realCert);
        assertThat(result.get().fingerprint()).isNotBlank();
        assertThat(result.get().expiresAt()).isPositive();
    }
}
