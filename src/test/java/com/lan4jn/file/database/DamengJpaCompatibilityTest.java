package com.lan4jn.file.database;

import com.lan4jn.file.entity.TransferArchiveTask;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.OperationLog;
import com.lan4jn.file.model.UserShareMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DM_TEST_ENABLED", matches = "(?i)true")
class DamengJpaCompatibilityTest {

    @DynamicPropertySource
    static void configureDameng(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.driver-class-name", () -> "dm.jdbc.driver.DmDriver");
        registry.add("spring.datasource.url", () -> requiredEnvironment("DM_TEST_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironment("DM_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("DM_TEST_PASSWORD"));
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.DmDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> environment("DM_TEST_DDL_AUTO", "create-drop"));
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldPersistIdentityBooleanTimestampAndClobFields() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            assertTrue(productName.toUpperCase().contains("DM"), productName);
        }

        User user = new User();
        user.setUsername("dm-test-" + System.nanoTime());
        user.setPassword("encoded-password");
        user.setEnabled(true);
        user.setCreateTime(System.currentTimeMillis());
        entityManager.persistAndFlush(user);

        TransferArchiveTask task = new TransferArchiveTask();
        task.setTaskType("BATCH");
        task.setScene("FILE");
        task.setStatus("PENDING");
        task.setStage("QUEUED");
        task.setOwnerUsername(user.getUsername());
        task.setBucketName("bucket-a");
        task.setSourceDescriptor("x".repeat(3500));
        task.setErrorMessage("e".repeat(1500));
        task.setCreateTime(System.currentTimeMillis());
        entityManager.persistAndFlush(task);

        OperationLog operationLog = new OperationLog();
        operationLog.setOperatorUsername(user.getUsername());
        operationLog.setSuccess(true);
        operationLog.setDetailJson("{\"detail\":\"" + "d".repeat(1500) + "\"}");
        operationLog.setCreateTime(LocalDateTime.now());
        entityManager.persistAndFlush(operationLog);

        UserShareMessage message = new UserShareMessage();
        message.setRecipientUserId(user.getId());
        message.setMessageType("FEEDBACK");
        message.setBody("b".repeat(1500));
        message.setRead(false);
        message.setDeleted(false);
        message.setCreateTime(LocalDateTime.now());
        entityManager.persistAndFlush(message);

        entityManager.clear();
        TransferArchiveTask storedTask = entityManager.find(TransferArchiveTask.class, task.getId());
        User storedUser = entityManager.find(User.class, user.getId());
        OperationLog storedLog = entityManager.find(OperationLog.class, operationLog.getId());
        UserShareMessage storedMessage = entityManager.find(UserShareMessage.class, message.getId());

        assertNotNull(storedTask);
        assertEquals(3500, storedTask.getSourceDescriptor().length());
        assertEquals(Boolean.TRUE, storedUser.getEnabled());
        assertEquals(operationLog.getDetailJson(), storedLog.getDetailJson());
        assertEquals(message.getBody(), storedMessage.getBody());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("缺少达梦测试环境变量: " + name);
        }
        return value.trim();
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
