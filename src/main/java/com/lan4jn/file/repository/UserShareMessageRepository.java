package com.lan4jn.file.repository;

import com.lan4jn.file.model.UserShareMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserShareMessageRepository extends JpaRepository<UserShareMessage, Long> {
    List<UserShareMessage> findByRecipientUserIdAndDeletedFalseOrderByCreateTimeDesc(Long recipientUserId);
    Optional<UserShareMessage> findByIdAndRecipientUserIdAndDeletedFalse(Long id, Long recipientUserId);
}
