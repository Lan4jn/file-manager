package com.lan4jn.file.repository;

import com.lan4jn.file.model.UserShareMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserShareMessageRepository extends JpaRepository<UserShareMessage, Long> {
    List<UserShareMessage> findByRecipientUserIdAndDeletedFalseOrderByCreateTimeDesc(Long recipientUserId);
    Optional<UserShareMessage> findByIdAndRecipientUserIdAndDeletedFalse(Long id, Long recipientUserId);
    List<UserShareMessage> findByShareCode(String shareCode);
    @Query("select m from UserShareMessage m join ShareLink s on s.shareCode = m.shareCode "
            + "where m.recipientUserId = ?1 and (m.deleted is null or m.deleted = false) "
            + "and m.messageType = 'SHARE' "
            + "and (s.cancelled is null or s.cancelled = false) "
            + "and s.expireTime is not null and s.expireTime > ?2 "
            + "order by m.createTime desc")
    Page<UserShareMessage> findValidShareMessagesByRecipientUserId(Long recipientUserId, LocalDateTime now, Pageable pageable);
}
