package com.lan4jn.file.tools;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.database-migration.enabled", havingValue = "true")
public class DatabaseMigrationRunner implements CommandLineRunner {

    private final Environment environment;

    public DatabaseMigrationRunner(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        SqliteToDamengMigration.main(new String[]{
                "--source-url=" + property("app.database-migration.sqlite-url", "jdbc:sqlite:./data/file-manager.db"),
                "--target-url=" + requiredProperty("spring.datasource.url"),
                "--target-username=" + requiredProperty("spring.datasource.username"),
                "--target-password=" + requiredProperty("spring.datasource.password"),
                "--truncate-target=" + property("app.database-migration.truncate-target", "false"),
                "--batch-size=" + property("app.database-migration.batch-size", "500")
        });
    }

    private String requiredProperty(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("数据库迁移缺少配置: " + name);
        }
        return value.trim();
    }

    private String property(String name, String defaultValue) {
        String value = environment.getProperty(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
