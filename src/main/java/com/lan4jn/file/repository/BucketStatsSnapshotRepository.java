package com.lan4jn.file.repository;

import com.lan4jn.file.entity.BucketStatsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BucketStatsSnapshotRepository extends JpaRepository<BucketStatsSnapshot, String> {
    List<BucketStatsSnapshot> findByBucketNameIn(Collection<String> bucketNames);

    void deleteByBucketName(String bucketName);

    void deleteByBucketNameNotIn(Collection<String> bucketNames);
}
