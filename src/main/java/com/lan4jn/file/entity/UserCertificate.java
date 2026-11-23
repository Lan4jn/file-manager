package com.lan4jn.file.entity;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "user_certificate")
public class UserCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String fingerprint;

    @Column(nullable = false)
    private Date createdAt;

    // Nullable for pre-upgrade rows; missing validity or generation never authorizes a login.
    private Long expiresAt;
    @Column(length = 36)
    private String generation;

    @Column(name = "issuer_managed")
    private Boolean issuerManaged;

    @Column(name = "certificate_der")
    private byte[] certificateDer;

    public Long getExpiresAt() { return expiresAt; }
    public String getGeneration() { return generation; }
    public Boolean getIssuerManaged() { return issuerManaged; }
    public void setIssuerManaged(Boolean issuerManaged) { this.issuerManaged = issuerManaged; }
    public byte[] getCertificateDer() { return certificateDer; }
    public void setCertificateDer(byte[] certificateDer) { this.certificateDer = certificateDer; }

    public java.security.cert.X509Certificate parseLeafCertificate() {
        if (certificateDer == null || certificateDer.length == 0) return null;
        try {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            return (java.security.cert.X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certificateDer));
        } catch (java.security.GeneralSecurityException invalid) {
            return null;
        }
    }

    public UserCertificate(Long userId, String fingerprint, long expiresAt) {
        this(userId, fingerprint);
        this.expiresAt = expiresAt;
        this.generation = java.util.UUID.randomUUID().toString();
    }

    public boolean isUsable() {
        return generation != null && expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public UserCertificate() {}

    public UserCertificate(Long userId, String fingerprint) {
        this.userId = userId;
        this.fingerprint = fingerprint;
        this.createdAt = new Date();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
