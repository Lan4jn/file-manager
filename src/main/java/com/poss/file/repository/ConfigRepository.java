package com.poss.file.repository;

import com.poss.file.entity.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigRepository extends JpaRepository<SysConfig, Long> {
    Optional<SysConfig> findByConfigKey(String configKey);
}
