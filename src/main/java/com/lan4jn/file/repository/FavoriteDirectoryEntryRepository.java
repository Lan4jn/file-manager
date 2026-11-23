package com.lan4jn.file.repository;

import com.lan4jn.file.entity.FavoriteDirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteDirectoryEntryRepository extends JpaRepository<FavoriteDirectoryEntry, Long> {
    List<FavoriteDirectoryEntry> findByUserIdOrderByCreateTimeDesc(Long userId);

    Optional<FavoriteDirectoryEntry> findByUserIdAndBucketNameAndDirectoryPath(Long userId, String bucketName, String directoryPath);

    void deleteByUserIdAndBucketNameAndDirectoryPath(Long userId, String bucketName, String directoryPath);
}
