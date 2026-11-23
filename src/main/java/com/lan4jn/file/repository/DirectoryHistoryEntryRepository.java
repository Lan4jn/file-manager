package com.lan4jn.file.repository;

import com.lan4jn.file.entity.DirectoryHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectoryHistoryEntryRepository extends JpaRepository<DirectoryHistoryEntry, Long> {
    List<DirectoryHistoryEntry> findTop10ByUserIdOrderByLastAccessTimeDesc(Long userId);

    Optional<DirectoryHistoryEntry> findByUserIdAndBucketNameAndDirectoryPath(Long userId, String bucketName, String directoryPath);

    long countByUserId(Long userId);
}
