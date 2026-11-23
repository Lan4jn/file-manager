package com.lan4jn.file.service;

import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserCertificateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class CertificateAuthService {

    private final UserCertificateRepository repository;
    private final IssuerCertificatePolicy policy;

    @Autowired
    public CertificateAuthService(UserCertificateRepository repository, IssuerCertificatePolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public CertificateAuthService(UserCertificateRepository repository) {
        this(repository, null);
    }

    public UserCertificate bind(Long userId, PeerCertificate peer) {
        String fingerprint = peer.fingerprint();
        if (repository.findByFingerprint(fingerprint).isPresent()) {
            throw new IllegalStateException("证书已被其他用户绑定");
        }
        UserCertificate cert = new UserCertificate(userId, fingerprint, peer.expiresAt());
        byte[] der = null;
        if (peer.leaf() != null) {
            try {
                der = peer.leaf().getEncoded();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("无法读取客户端证书内容", e);
            }
        }
        cert.setCertificateDer(der);

        if (policy != null && policy.isEnabled()) {
            if (peer.leaf() == null) {
                throw new IllegalStateException("无法获取TLS客户端证书以供验签");
            }
            boolean managed = policy.isManaged(peer.leaf());
            if (managed) {
                if (!policy.isValid(fingerprint)) {
                    throw new IllegalStateException("证书无效或已吊销");
                }
                cert.setIssuerManaged(true);
            } else {
                // 非联动不持久化 false，保持 null 并保留 leaf DER
                cert.setIssuerManaged(null);
            }
        } else {
            // 禁用桥时新绑定必须保留 null（下次启用可重新分类）
            cert.setIssuerManaged(null);
        }
        return repository.saveAndFlush(cert);
    }

    /**
     * 校验登录请求中的证书
     */
    public boolean validateForLogin(UserCertificate cert, PeerCertificate peer) {
        if (cert == null || !cert.isUsable() || peer == null) {
            return false;
        }

        // 1. 已标 true（曾受管）：即使关桥也拒绝；开桥时必须 isValid
        if (Boolean.TRUE.equals(cert.getIssuerManaged())) {
            if (policy == null || !policy.isEnabled()) {
                return false;
            }
            return policy.isValid(peer.fingerprint());
        }

        // 2. Boolean.FALSE 或 null（外部导入/历史无标）：必须当未知，绝不能跳过验签！
        if (policy != null && policy.isEnabled()) {
            if (peer.leaf() == null) {
                return false;
            }
            try {
                boolean managed = policy.isManaged(peer.leaf());
                if (managed) {
                    cert.setIssuerManaged(true);
                    if (cert.getCertificateDer() == null) {
                        cert.setCertificateDer(peer.leaf().getEncoded());
                    }
                    repository.saveAndFlush(cert);
                    return policy.isValid(peer.fingerprint());
                } else {
                    // 非联动恢复：保持未知（null），补齐 certificateDer 以供换CA/扩容时重新分类
                    if (cert.getCertificateDer() == null) {
                        cert.setCertificateDer(peer.leaf().getEncoded());
                        repository.saveAndFlush(cert);
                    }
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        // 3. 桥未启用且非受管：保持独立部署原样放行
        return true;
    }

    /**
     * 校验会话（JWT Bearer Token）中的证书绑定
     */
    public boolean validateForBearer(UserCertificate binding) {
        if (binding == null || !binding.isUsable()) {
            return false;
        }

        // 1. 已标 true（曾受管）：即使关桥也拒绝；开桥时必须 isValid
        if (Boolean.TRUE.equals(binding.getIssuerManaged())) {
            if (policy == null || !policy.isEnabled()) {
                return false;
            }
            return policy.isValid(binding.getFingerprint());
        }

        // 2. Boolean.FALSE 或 null（外部导入/历史无标）：必须当未知，绝不能跳过验签！
        if (policy != null && policy.isEnabled()) {
            byte[] der = binding.getCertificateDer();
            if (der == null || der.length == 0) {
                return false;
            }

            // 确认 DER 的 SHA-256 == 绑定指纹；缺损即拒绝
            try {
                String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
                if (!sha256.equalsIgnoreCase(binding.getFingerprint())) {
                    return false;
                }
            } catch (GeneralSecurityException e) {
                return false;
            }

            X509Certificate leaf = binding.parseLeafCertificate();
            if (leaf == null) {
                return false;
            }

            try {
                boolean managed = policy.isManaged(leaf);
                if (managed) {
                    // JWT从DER重分类发现受管时保持 sticky true（即使后来关桥）并验证有效性
                    binding.setIssuerManaged(true);
                    repository.saveAndFlush(binding);
                    return policy.isValid(binding.getFingerprint());
                }
                // 非联动证书：当前策略下不属于受管，放行
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // 3. 桥未启用且非受管：保持独立部署原样放行
        return true;
    }

    public List<UserCertificate> listByUser(Long userId) {
        return repository.findByUserId(userId);
    }
    
    public Optional<UserCertificate> findByFingerprint(String fingerprint) {
        return repository.findByFingerprint(fingerprint);
    }

    public Optional<UserCertificate> findById(Long id) {
        return repository.findById(id);
    }

    public void unbind(Long userId, Long certificateId) {
        UserCertificate cert = repository.findById(certificateId)
            .orElseThrow(() -> new IllegalArgumentException("证书绑定不存在"));
        if (!cert.getUserId().equals(userId)) {
            throw new IllegalStateException("无权解绑该证书");
        }
        repository.delete(cert);
    }
}
