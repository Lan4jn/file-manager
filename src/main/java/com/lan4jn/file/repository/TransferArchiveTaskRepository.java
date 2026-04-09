package com.lan4jn.file.repository;

import com.lan4jn.file.entity.TransferArchiveTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferArchiveTaskRepository extends JpaRepository<TransferArchiveTask, Long> {
    Optional<TransferArchiveTask> findByIdAndOwnerUsername(Long id, String ownerUsername);

    List<TransferArchiveTask> findByStatusOrderByCreateTimeAsc(String status);
}
