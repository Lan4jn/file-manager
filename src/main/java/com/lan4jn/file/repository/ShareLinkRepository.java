package com.lan4jn.file.repository;

import com.lan4jn.file.model.ShareLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByShareCode(String shareCode);
    List<ShareLink> findByCreateByOrderByCreateTimeDesc(String createBy);
    @Query("select s from ShareLink s where s.createBy = ?1 and (s.hiddenByCreator is null or s.hiddenByCreator = false) order by s.createTime desc")
    List<ShareLink> findVisibleByCreateByOrderByCreateTimeDesc(String createBy);
    @Query("select s from ShareLink s where s.createBy = ?1 and (s.hiddenByCreator is null or s.hiddenByCreator = false) order by s.createTime desc")
    Page<ShareLink> findVisibleByCreateByOrderByCreateTimeDesc(String createBy, Pageable pageable);
    @Query("select s from ShareLink s where s.createBy = ?1 and (s.hiddenByCreator is null or s.hiddenByCreator = false) and (s.cancelled is null or s.cancelled = false) and s.expireTime is not null and s.expireTime > ?2 order by s.createTime desc")
    Page<ShareLink> findValidVisibleByCreateBy(String createBy, LocalDateTime now, Pageable pageable);
    void deleteByShareCode(String shareCode);
}
