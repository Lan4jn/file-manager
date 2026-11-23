package com.lan4jn.file.repository;

import com.lan4jn.file.entity.UserCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserCertificateRepository extends JpaRepository<UserCertificate, Long> {
    List<UserCertificate> findByUserId(Long userId);
    Optional<UserCertificate> findByFingerprint(String fingerprint);
}
