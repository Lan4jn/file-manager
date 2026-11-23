package com.lan4jn.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.datasource-mirror")
public class DatabaseMirrorProperties {

    private boolean enabled;
    private boolean syncOnStartup = true;
    private boolean syncAfterCommit = true;
    private long debounceMs = 2000L;
    private long minimumIntervalMs = 5000L;
    private long reconcileIntervalMs = 60000L;
    private int batchSize = 500;
    private List<Replica> replicas = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSyncOnStartup() {
        return syncOnStartup;
    }

    public void setSyncOnStartup(boolean syncOnStartup) {
        this.syncOnStartup = syncOnStartup;
    }

    public boolean isSyncAfterCommit() {
        return syncAfterCommit;
    }

    public void setSyncAfterCommit(boolean syncAfterCommit) {
        this.syncAfterCommit = syncAfterCommit;
    }

    public long getDebounceMs() {
        return debounceMs;
    }

    public void setDebounceMs(long debounceMs) {
        this.debounceMs = debounceMs;
    }

    public long getMinimumIntervalMs() {
        return minimumIntervalMs;
    }

    public void setMinimumIntervalMs(long minimumIntervalMs) {
        this.minimumIntervalMs = minimumIntervalMs;
    }

    public long getReconcileIntervalMs() {
        return reconcileIntervalMs;
    }

    public void setReconcileIntervalMs(long reconcileIntervalMs) {
        this.reconcileIntervalMs = reconcileIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public List<Replica> getReplicas() {
        return replicas;
    }

    public void setReplicas(List<Replica> replicas) {
        this.replicas = replicas == null ? new ArrayList<>() : replicas;
    }

    public static class Replica {
        private String name;
        private boolean enabled = true;
        private String driverClassName;
        private String url;
        private String username;
        private String password;
        private int maximumPoolSize = 3;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }
    }
}
