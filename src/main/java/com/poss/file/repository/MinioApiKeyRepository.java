package com.poss.file.repository;

import com.poss.file.model.MinioApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MinioApiKeyRepository extends JpaRepository<MinioApiKey, Long> {
    Optional<MinioApiKey> findByAccessKey(String accessKey);

    List<MinioApiKey> findByIsActive(Boolean isActive);

    void deleteById(Long id);
}
