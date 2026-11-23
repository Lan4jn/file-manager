package com.lan4jn.file.dto;

import com.lan4jn.file.entity.UserCertificate;
import java.time.Instant;

public record CertificateBindingDTO(Long id, String fingerprint, Instant boundAt, Instant expiresAt) {
    public static CertificateBindingDTO from(UserCertificate binding) {
        return new CertificateBindingDTO(binding.getId(), binding.getFingerprint(),
                binding.getCreatedAt().toInstant(), binding.getExpiresAt() == null ? null : Instant.ofEpochMilli(binding.getExpiresAt()));
    }
}
