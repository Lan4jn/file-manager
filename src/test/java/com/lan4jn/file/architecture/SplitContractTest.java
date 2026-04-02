package com.lan4jn.file.architecture;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.config.MinioConfig;
import com.lan4jn.file.controller.AdminConfigController;
import com.lan4jn.file.controller.AdminInitController;
import com.lan4jn.file.controller.AdminPermissionController;
import com.lan4jn.file.controller.AdminRoleController;
import com.lan4jn.file.controller.AdminUserController;
import com.lan4jn.file.controller.FileController;
import com.lan4jn.file.controller.ShareController;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.ShareService;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitContractTest {

    @Test
    void shouldKeepLegacyFileAndShareRoutesStable() {
        assertHasRoute(FileController.class, "listBuckets", GetMapping.class, "/buckets");
        assertHasRoute(FileController.class, "list", GetMapping.class, "/list");
        assertHasRoute(FileController.class, "download", GetMapping.class, "/download");
        assertHasRoute(FileController.class, "batchDownload", GetMapping.class, "/batch-download");
        assertHasRoute(ShareController.class, "createShare", PostMapping.class, "/create");
        assertHasRoute(ShareController.class, "createBatchShare", PostMapping.class, "/batch-create");
        assertHasRoute(ShareController.class, "getShareAccessInfo", GetMapping.class, "/access-info");
        assertHasRoute(ShareController.class, "deleteShare", DeleteMapping.class, "/delete");
    }

    @Test
    void shouldKeepControllerResponseContractsStable() throws Exception {
        assertEquals(Result.class, FileController.class.getMethod("listBuckets", String.class, String.class).getReturnType());
        assertEquals(Result.class, FileController.class.getMethod("list", String.class, String.class, String.class, String.class, int.class, int.class).getReturnType());
        assertEquals(ResponseEntity.class, FileController.class.getMethod("download", String.class, String.class).getReturnType());
        assertEquals(Result.class, ShareController.class.getMethod("getShareAccessInfo", String.class).getReturnType());
        assertEquals(ResponseEntity.class, ShareController.class.getMethod("downloadByShareCode", String.class, String.class).getReturnType());
    }

    @Test
    void shouldKeepControllersFreeOfRepositoryDependencies() {
        List<Class<?>> controllers = Arrays.asList(
                FileController.class,
                ShareController.class,
                AdminUserController.class,
                AdminRoleController.class,
                AdminPermissionController.class,
                AdminConfigController.class,
                AdminInitController.class
        );

        for (Class<?> controller : controllers) {
            for (Field field : controller.getDeclaredFields()) {
                assertFalse(field.getType().getSimpleName().endsWith("Repository"),
                        controller.getSimpleName() + " should not depend on repositories directly");
            }
        }
    }

    @Test
    void shouldKeepCoordinatorServicesThinAndComposable() {
        assertFalse(hasFieldType(FileService.class, "MinioClient"));
        assertFalse(hasFieldType(FileService.class, "Repository"));
        assertTrue(hasFieldType(FileService.class, "BucketService"));
        assertTrue(hasFieldType(FileService.class, "FileQueryService"));
        assertTrue(hasFieldType(FileService.class, "FileCommandService"));
        assertTrue(hasFieldType(FileService.class, "ArchiveService"));

        assertFalse(hasFieldType(ShareService.class, "Repository"));
        assertTrue(hasFieldType(ShareService.class, "ShareLinkService"));
        assertTrue(hasFieldType(ShareService.class, "ShareAccessService"));
    }

    @Test
    void shouldAllowCoordinatorLayerInstantiationForStageRollback() {
        MinioClient minioClient = Mockito.mock(MinioClient.class);
        ConfigService configService = Mockito.mock(ConfigService.class);
        FileService fileService = assertDoesNotThrow(() -> new FileService(minioClient, configService));

        ShareLinkRepository shareLinkRepository = Mockito.mock(ShareLinkRepository.class);
        ShareFileRecordRepository shareFileRecordRepository = Mockito.mock(ShareFileRecordRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        UserShareMessageRepository userShareMessageRepository = Mockito.mock(UserShareMessageRepository.class);
        assertDoesNotThrow(() -> new ShareService(
                fileService,
                shareLinkRepository,
                shareFileRecordRepository,
                userRepository,
                userShareMessageRepository,
                configService,
                24
        ));
    }

    @Test
    void shouldBindEachSplitStageToConcreteTestsAndChecklist() throws Exception {
        List<String> expectedTestFiles = Arrays.asList(
                "src/test/java/com/lan4jn/file/service/FileQueryServiceTest.java",
                "src/test/java/com/lan4jn/file/service/FileCommandServiceTest.java",
                "src/test/java/com/lan4jn/file/service/ArchiveServiceTest.java",
                "src/test/java/com/lan4jn/file/service/ShareLinkServiceTest.java",
                "src/test/java/com/lan4jn/file/service/ShareAccessServiceTest.java",
                "src/test/java/com/lan4jn/file/service/AuthorizationServiceTest.java",
                "src/test/java/com/lan4jn/file/controller/FileControllerTest.java",
                "src/test/java/com/lan4jn/file/controller/ShareControllerTest.java",
                "src/test/java/com/lan4jn/file/controller/AdminControllerSplitTest.java",
                "src/test/java/com/lan4jn/file/config/SecurityContractTest.java"
        );

        for (String file : expectedTestFiles) {
            assertTrue(Files.exists(Path.of(file)), "Missing expected test file: " + file);
        }

        String tasks = Files.readString(Path.of("doc/architecture/tasks.md"), StandardCharsets.UTF_8);
        assertTrue(tasks.contains("11.11"));
        assertTrue(tasks.contains("11.12"));
        assertTrue(tasks.contains("11.13"));
        assertTrue(tasks.contains("11.14"));
        assertTrue(tasks.contains("11.15"));
    }

    private void assertHasRoute(Class<?> controllerClass, String methodName, Class<? extends Annotation> mappingType, String path) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + methodName));

        Annotation annotation = method.getAnnotation(mappingType);
        assertTrue(annotation != null, "Missing mapping on " + controllerClass.getSimpleName() + "." + methodName);

        String[] values;
        if (annotation instanceof GetMapping) {
            values = ((GetMapping) annotation).value();
        } else if (annotation instanceof PostMapping) {
            values = ((PostMapping) annotation).value();
        } else if (annotation instanceof DeleteMapping) {
            values = ((DeleteMapping) annotation).value();
        } else {
            throw new AssertionError("Unsupported mapping type");
        }
        assertTrue(Arrays.asList(values).contains(path),
                "Missing route " + path + " on " + controllerClass.getSimpleName() + "." + methodName);
    }

    private boolean hasFieldType(Class<?> type, String typeNameFragment) {
        return Arrays.stream(type.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().contains(typeNameFragment));
    }
}
