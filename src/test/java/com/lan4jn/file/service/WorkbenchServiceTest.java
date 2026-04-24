package com.lan4jn.file.service;

import com.lan4jn.file.entity.DirectoryHistoryEntry;
import com.lan4jn.file.entity.FavoriteDirectoryEntry;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.entity.UserWorkbenchPreference;
import com.lan4jn.file.repository.DirectoryHistoryEntryRepository;
import com.lan4jn.file.repository.FavoriteDirectoryEntryRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserWorkbenchPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkbenchServiceTest {

    @Test
    void shouldReturnDefaultWorkbenchFalseWhenPreferenceMissing() {
        UserRepository userRepository = mock(UserRepository.class);
        UserWorkbenchPreferenceRepository preferenceRepository = mock(UserWorkbenchPreferenceRepository.class);
        DirectoryHistoryEntryRepository historyRepository = mock(DirectoryHistoryEntryRepository.class);
        FavoriteDirectoryEntryRepository favoriteRepository = mock(FavoriteDirectoryEntryRepository.class);

        User user = user(7L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(historyRepository.findTop10ByUserIdOrderByLastAccessTimeDesc(7L)).thenReturn(List.of());
        when(favoriteRepository.findByUserIdOrderByCreateTimeDesc(7L)).thenReturn(List.of());

        WorkbenchService service = new WorkbenchService(
                userRepository, preferenceRepository, historyRepository, favoriteRepository);

        var overview = service.getOverview("alice");

        assertFalse(overview.isDefaultWorkbench());
        assertEquals(0, overview.getRecentDirectories().size());
        assertEquals(0, overview.getFavoriteDirectories().size());
    }

    @Test
    void shouldPersistWorkbenchPreferenceForCurrentUser() {
        UserRepository userRepository = mock(UserRepository.class);
        UserWorkbenchPreferenceRepository preferenceRepository = mock(UserWorkbenchPreferenceRepository.class);
        DirectoryHistoryEntryRepository historyRepository = mock(DirectoryHistoryEntryRepository.class);
        FavoriteDirectoryEntryRepository favoriteRepository = mock(FavoriteDirectoryEntryRepository.class);

        User user = user(7L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(preferenceRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(UserWorkbenchPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkbenchService service = new WorkbenchService(
                userRepository, preferenceRepository, historyRepository, favoriteRepository);

        service.updatePreference("alice", true);

        ArgumentCaptor<UserWorkbenchPreference> captor = ArgumentCaptor.forClass(UserWorkbenchPreference.class);
        verify(preferenceRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(true, captor.getValue().getDefaultWorkbench());
    }

    @Test
    void shouldAddFavoriteDirectoryOncePerUserAndPath() {
        UserRepository userRepository = mock(UserRepository.class);
        UserWorkbenchPreferenceRepository preferenceRepository = mock(UserWorkbenchPreferenceRepository.class);
        DirectoryHistoryEntryRepository historyRepository = mock(DirectoryHistoryEntryRepository.class);
        FavoriteDirectoryEntryRepository favoriteRepository = mock(FavoriteDirectoryEntryRepository.class);

        User user = user(7L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(favoriteRepository.findByUserIdAndBucketNameAndDirectoryPath(7L, "docs", "reports/")).thenReturn(Optional.empty());
        when(favoriteRepository.save(any(FavoriteDirectoryEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkbenchService service = new WorkbenchService(
                userRepository, preferenceRepository, historyRepository, favoriteRepository);

        service.addFavorite("alice", "docs", "reports/", "报表");

        ArgumentCaptor<FavoriteDirectoryEntry> captor = ArgumentCaptor.forClass(FavoriteDirectoryEntry.class);
        verify(favoriteRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("docs", captor.getValue().getBucketName());
        assertEquals("reports/", captor.getValue().getDirectoryPath());
    }

    @Test
    void shouldRejectDuplicateFavoriteDirectory() {
        UserRepository userRepository = mock(UserRepository.class);
        UserWorkbenchPreferenceRepository preferenceRepository = mock(UserWorkbenchPreferenceRepository.class);
        DirectoryHistoryEntryRepository historyRepository = mock(DirectoryHistoryEntryRepository.class);
        FavoriteDirectoryEntryRepository favoriteRepository = mock(FavoriteDirectoryEntryRepository.class);

        User user = user(7L, "alice");
        FavoriteDirectoryEntry favorite = new FavoriteDirectoryEntry();
        favorite.setUserId(7L);
        favorite.setBucketName("docs");
        favorite.setDirectoryPath("reports/");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(favoriteRepository.findByUserIdAndBucketNameAndDirectoryPath(7L, "docs", "reports/"))
                .thenReturn(Optional.of(favorite));

        WorkbenchService service = new WorkbenchService(
                userRepository, preferenceRepository, historyRepository, favoriteRepository);

        assertThrows(IllegalStateException.class, () -> service.addFavorite("alice", "docs", "reports/", "报表"));
        verify(favoriteRepository, never()).save(any(FavoriteDirectoryEntry.class));
    }

    @Test
    void shouldDeleteFavoriteDirectoryForCurrentUser() {
        UserRepository userRepository = mock(UserRepository.class);
        UserWorkbenchPreferenceRepository preferenceRepository = mock(UserWorkbenchPreferenceRepository.class);
        DirectoryHistoryEntryRepository historyRepository = mock(DirectoryHistoryEntryRepository.class);
        FavoriteDirectoryEntryRepository favoriteRepository = mock(FavoriteDirectoryEntryRepository.class);

        User user = user(7L, "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        WorkbenchService service = new WorkbenchService(
                userRepository, preferenceRepository, historyRepository, favoriteRepository);

        service.deleteFavorite("alice", "docs", "reports/");

        verify(favoriteRepository).deleteByUserIdAndBucketNameAndDirectoryPath(7L, "docs", "reports/");
    }

    @Test
    void shouldRecordDirectoryHistoryByUpdatingExistingEntry() {
        UserRepository userRepository = mock(UserRepository.class);
        UserWorkbenchPreferenceRepository preferenceRepository = mock(UserWorkbenchPreferenceRepository.class);
        DirectoryHistoryEntryRepository historyRepository = mock(DirectoryHistoryEntryRepository.class);
        FavoriteDirectoryEntryRepository favoriteRepository = mock(FavoriteDirectoryEntryRepository.class);

        User user = user(7L, "alice");
        DirectoryHistoryEntry existing = new DirectoryHistoryEntry();
        existing.setId(1L);
        existing.setUserId(7L);
        existing.setBucketName("docs");
        existing.setDirectoryPath("reports/");
        existing.setDirectoryName("旧名称");
        existing.setLastAccessTime(LocalDateTime.now().minusDays(1));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(historyRepository.findByUserIdAndBucketNameAndDirectoryPath(7L, "docs", "reports/"))
                .thenReturn(Optional.of(existing));
        when(historyRepository.countByUserId(7L)).thenReturn(1L);

        WorkbenchService service = new WorkbenchService(
                userRepository, preferenceRepository, historyRepository, favoriteRepository);

        service.recordDirectoryAccess("alice", "docs", "reports/", "报表");

        verify(historyRepository).save(existing);
        assertEquals("报表", existing.getDirectoryName());
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
