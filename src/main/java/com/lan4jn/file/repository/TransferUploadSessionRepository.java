package com.lan4jn.file.repository;

import com.lan4jn.file.entity.TransferUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TransferUploadSessionRepository extends JpaRepository<TransferUploadSession, String> {

    Optional<TransferUploadSession> findFirstByOwnerUsernameAndFileFingerprintAndBucketNameAndObjectNameAndTotalSizeAndStatusAndExpireTimeGreaterThanOrderByUpdateTimeDesc(
            String ownerUsername, String fileFingerprint, String bucketName, String objectName, Long totalSize,
            String status, Long now);

    List<TransferUploadSession> findByStatusAndExpireTimeLessThan(String status, Long now);

    @Modifying
    @Transactional
    @Query("update TransferUploadSession s set s.updateTime = :now "
            + "where s.id = :id and s.status = 'UPLOADING' and s.updateTime < :now")
    void touchUploadingSession(@Param("id") String id, @Param("now") long now);

    @Modifying
    @Transactional
    @Query("update TransferUploadSession s set s.status = :status, s.updateTime = :now "
            + "where s.id = :id and s.status = 'UPLOADING'")
    void closeUploadingSession(@Param("id") String id, @Param("status") String status, @Param("now") long now);
}
