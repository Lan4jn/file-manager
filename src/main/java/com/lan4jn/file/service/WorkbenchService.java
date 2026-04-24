package com.lan4jn.file.service;

import com.lan4jn.file.dto.WorkbenchOverviewResponse;
import com.lan4jn.file.entity.DirectoryHistoryEntry;
import com.lan4jn.file.entity.FavoriteDirectoryEntry;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.entity.UserWorkbenchPreference;
import com.lan4jn.file.repository.DirectoryHistoryEntryRepository;
import com.lan4jn.file.repository.FavoriteDirectoryEntryRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserWorkbenchPreferenceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WorkbenchService {

    private final UserRepository userRepository;
    private final UserWorkbenchPreferenceRepository preferenceRepository;
    private final DirectoryHistoryEntryRepository historyRepository;
    private final FavoriteDirectoryEntryRepository favoriteRepository;

    public WorkbenchService(UserRepository userRepository,
                            UserWorkbenchPreferenceRepository preferenceRepository,
                            DirectoryHistoryEntryRepository historyRepository,
                            FavoriteDirectoryEntryRepository favoriteRepository) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.historyRepository = historyRepository;
        this.favoriteRepository = favoriteRepository;
    }

    public WorkbenchOverviewResponse getOverview(String username) {
        User user = requireUser(username);
        WorkbenchOverviewResponse response = new WorkbenchOverviewResponse();
        response.setDefaultWorkbench(preferenceRepository.findByUserId(user.getId())
                .map(UserWorkbenchPreference::getDefaultWorkbench)
                .orElse(false));
        response.setRecentDirectories(historyRepository.findTop10ByUserIdOrderByLastAccessTimeDesc(user.getId())
                .stream()
                .map(this::toDirectoryItem)
                .toList());
        response.setFavoriteDirectories(favoriteRepository.findByUserIdOrderByCreateTimeDesc(user.getId())
                .stream()
                .map(this::toDirectoryItem)
                .toList());
        return response;
    }

    public void updatePreference(String username, boolean defaultWorkbench) {
        User user = requireUser(username);
        UserWorkbenchPreference preference = preferenceRepository.findByUserId(user.getId())
                .orElseGet(UserWorkbenchPreference::new);
        preference.setUserId(user.getId());
        preference.setDefaultWorkbench(defaultWorkbench);
        preference.setUpdatedAt(LocalDateTime.now());
        preferenceRepository.save(preference);
    }

    public void addFavorite(String username, String bucketName, String directoryPath, String directoryName) {
        User user = requireUser(username);
        String normalizedBucket = requireText(bucketName, "bucketName不能为空");
        String normalizedPath = normalizeDirectoryPath(directoryPath);
        if (favoriteRepository.findByUserIdAndBucketNameAndDirectoryPath(user.getId(), normalizedBucket, normalizedPath).isPresent()) {
            throw new IllegalStateException("该目录已收藏");
        }
        FavoriteDirectoryEntry entry = new FavoriteDirectoryEntry();
        entry.setUserId(user.getId());
        entry.setBucketName(normalizedBucket);
        entry.setDirectoryPath(normalizedPath);
        entry.setDirectoryName(resolveDirectoryName(normalizedPath, directoryName));
        entry.setCreateTime(LocalDateTime.now());
        favoriteRepository.save(entry);
    }

    public void deleteFavorite(String username, String bucketName, String directoryPath) {
        User user = requireUser(username);
        favoriteRepository.deleteByUserIdAndBucketNameAndDirectoryPath(
                user.getId(),
                requireText(bucketName, "bucketName不能为空"),
                normalizeDirectoryPath(directoryPath));
    }

    public void recordDirectoryAccess(String username, String bucketName, String directoryPath, String directoryName) {
        User user = requireUser(username);
        String normalizedBucket = requireText(bucketName, "bucketName不能为空");
        String normalizedPath = normalizeDirectoryPath(directoryPath);
        DirectoryHistoryEntry entry = historyRepository.findByUserIdAndBucketNameAndDirectoryPath(
                        user.getId(), normalizedBucket, normalizedPath)
                .orElseGet(DirectoryHistoryEntry::new);
        entry.setUserId(user.getId());
        entry.setBucketName(normalizedBucket);
        entry.setDirectoryPath(normalizedPath);
        entry.setDirectoryName(resolveDirectoryName(normalizedPath, directoryName));
        entry.setLastAccessTime(LocalDateTime.now());
        historyRepository.save(entry);
    }

    private WorkbenchOverviewResponse.DirectoryItem toDirectoryItem(DirectoryHistoryEntry entry) {
        WorkbenchOverviewResponse.DirectoryItem item = new WorkbenchOverviewResponse.DirectoryItem();
        item.setBucketName(entry.getBucketName());
        item.setDirectoryPath(entry.getDirectoryPath());
        item.setDirectoryName(entry.getDirectoryName());
        item.setTime(entry.getLastAccessTime());
        return item;
    }

    private WorkbenchOverviewResponse.DirectoryItem toDirectoryItem(FavoriteDirectoryEntry entry) {
        WorkbenchOverviewResponse.DirectoryItem item = new WorkbenchOverviewResponse.DirectoryItem();
        item.setBucketName(entry.getBucketName());
        item.setDirectoryPath(entry.getDirectoryPath());
        item.setDirectoryName(entry.getDirectoryName());
        item.setTime(entry.getCreateTime());
        return item;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeDirectoryPath(String directoryPath) {
        String normalized = requireText(directoryPath, "directoryPath不能为空");
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String resolveDirectoryName(String directoryPath, String directoryName) {
        String name = directoryName == null ? "" : directoryName.trim();
        if (!name.isEmpty()) {
            return name;
        }
        String normalizedPath = normalizeDirectoryPath(directoryPath);
        String trimmed = normalizedPath.substring(0, normalizedPath.length() - 1);
        int slashIndex = trimmed.lastIndexOf('/');
        return slashIndex >= 0 ? trimmed.substring(slashIndex + 1) : trimmed;
    }
}
