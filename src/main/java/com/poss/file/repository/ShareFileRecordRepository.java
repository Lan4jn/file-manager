package com.poss.file.repository;

import com.poss.file.model.ShareFileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShareFileRecordRepository extends JpaRepository<ShareFileRecord, Long> {
    List<ShareFileRecord> findByShareCode(String shareCode);

    void deleteByShareCode(String shareCode);
}
