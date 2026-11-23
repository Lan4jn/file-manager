package com.lan4jn.file.service;

import com.lan4jn.file.config.DatabaseMirrorProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "app.datasource-mirror.enabled", havingValue = "true")
public class DynamicMirrorDataSourceRegistry {

    private final DatabaseMirrorProperties properties;
    private final Map<String, HikariDataSource> dataSources = new LinkedHashMap<>();

    public DynamicMirrorDataSourceRegistry(DatabaseMirrorProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public synchronized void initialize() {
        closeDataSources();
        Set<String> names = new HashSet<>();
        for (DatabaseMirrorProperties.Replica replica : properties.getReplicas()) {
            if (!replica.isEnabled()) {
                continue;
            }
            validateReplica(replica, names);
            HikariConfig config = new HikariConfig();
            config.setPoolName("mirror-" + replica.getName());
            config.setDriverClassName(replica.getDriverClassName());
            config.setJdbcUrl(replica.getUrl());
            config.setUsername(replica.getUsername());
            config.setPassword(replica.getPassword());
            config.setMinimumIdle(0);
            config.setMaximumPoolSize(Math.max(1, replica.getMaximumPoolSize()));
            config.setConnectionTimeout(30000L);
            config.setInitializationFailTimeout(-1L);
            HikariDataSource dataSource = new HikariDataSource(config);
            dataSources.put(replica.getName(), dataSource);
        }
    }

    public synchronized List<MirrorDataSource> getDataSources() {
        List<MirrorDataSource> result = new ArrayList<>();
        dataSources.forEach((name, dataSource) -> result.add(new MirrorDataSource(name, dataSource)));
        return Collections.unmodifiableList(result);
    }

    @PreDestroy
    public synchronized void closeDataSources() {
        dataSources.values().forEach(HikariDataSource::close);
        dataSources.clear();
    }

    private void validateReplica(DatabaseMirrorProperties.Replica replica, Set<String> names) {
        if (replica.getName() == null || replica.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("镜像数据源名称不能为空");
        }
        if (replica.getDriverClassName() == null || replica.getDriverClassName().trim().isEmpty()) {
            throw new IllegalArgumentException("镜像数据源驱动不能为空: " + replica.getName());
        }
        if (replica.getUrl() == null || replica.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("镜像数据源 URL 不能为空: " + replica.getName());
        }
        if (!names.add(replica.getName())) {
            throw new IllegalArgumentException("镜像数据源名称重复: " + replica.getName());
        }
    }

    public record MirrorDataSource(String name, DataSource dataSource) {
    }
}
