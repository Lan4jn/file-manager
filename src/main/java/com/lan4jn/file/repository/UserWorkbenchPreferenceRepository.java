package com.lan4jn.file.repository;

import com.lan4jn.file.entity.UserWorkbenchPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWorkbenchPreferenceRepository extends JpaRepository<UserWorkbenchPreference, Long> {
    Optional<UserWorkbenchPreference> findByUserId(Long userId);
}
