package com.lan4jn.file.repository;

import com.lan4jn.file.model.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByShareCode(String shareCode);
    List<ShareLink> findByCreateByOrderByCreateTimeDesc(String createBy);
    void deleteByShareCode(String shareCode);
}

