package com.lan4jn.file.service;

import com.lan4jn.file.config.DatabaseMirrorProperties;
import com.lan4jn.file.tools.SqliteToDamengMigration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "app.datasource-mirror.enabled", havingValue = "true")
public class DatabaseMirrorService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMirrorService.class);
    private static final Object TRANSACTION_RESOURCE_KEY = DatabaseMirrorService.class.getName() + ".SYNC";

    private final DataSource masterDataSource;
    private final DynamicMirrorDataSourceRegistry registry;
    private final DatabaseMirrorProperties properties;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "database-mirror-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, ReplicaStatus> replicaStatuses = new LinkedHashMap<>();
    private final Object scheduleMonitor = new Object();

    private volatile boolean applicationReady;
    private volatile long lastFinishedAt;
    private volatile ScheduledFuture<?> scheduledFuture;

    public DatabaseMirrorService(DataSource masterDataSource,
                                 DynamicMirrorDataSourceRegistry registry,
                                 DatabaseMirrorProperties properties) {
        this.masterDataSource = masterDataSource;
        this.registry = registry;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        applicationReady = true;
        if (properties.isSyncOnStartup()) {
            requestSynchronization();
        }
    }

    public void requestAfterMasterCommit() {
        if (!applicationReady || !properties.isSyncAfterCommit()) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            requestSynchronization();
            return;
        }
        if (TransactionSynchronizationManager.hasResource(TRANSACTION_RESOURCE_KEY)) {
            return;
        }
        TransactionSynchronizationManager.bindResource(TRANSACTION_RESOURCE_KEY, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                requestSynchronization();
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(TRANSACTION_RESOURCE_KEY)) {
                    TransactionSynchronizationManager.unbindResource(TRANSACTION_RESOURCE_KEY);
                }
            }
        });
    }

    public void requestSynchronization() {
        if (!applicationReady) {
            return;
        }
        dirty.set(true);
        scheduleDrain();
    }

    public boolean synchronizeNow() {
        if (!applicationReady || !running.compareAndSet(false, true)) {
            return false;
        }
        try {
            dirty.set(false);
            synchronizeAllReplicas();
            return true;
        } finally {
            lastFinishedAt = System.currentTimeMillis();
            running.set(false);
            if (dirty.get()) {
                scheduleDrain();
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.datasource-mirror.reconcile-interval-ms:60000}")
    public void reconcile() {
        requestSynchronization();
    }

    public synchronized MirrorStatus getStatus() {
        return new MirrorStatus(
                properties.isEnabled(),
                running.get(),
                dirty.get(),
                LocalDateTime.now(),
                new LinkedHashMap<>(replicaStatuses));
    }

    private void scheduleDrain() {
        synchronized (scheduleMonitor) {
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                return;
            }
            long earliest = lastFinishedAt + Math.max(0L, properties.getMinimumIntervalMs());
            long delay = Math.max(Math.max(0L, properties.getDebounceMs()), earliest - System.currentTimeMillis());
            scheduledFuture = executor.schedule(this::drain, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void drain() {
        if (!dirty.getAndSet(false)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            dirty.set(true);
            scheduleDrain();
            return;
        }
        try {
            synchronizeAllReplicas();
        } finally {
            lastFinishedAt = System.currentTimeMillis();
            running.set(false);
            if (dirty.get()) {
                scheduleDrain();
            }
        }
    }

    private void synchronizeAllReplicas() {
        List<DynamicMirrorDataSourceRegistry.MirrorDataSource> mirrors = registry.getDataSources();
        for (DynamicMirrorDataSourceRegistry.MirrorDataSource mirror : mirrors) {
            synchronizeReplica(mirror);
        }
    }

    private void synchronizeReplica(DynamicMirrorDataSourceRegistry.MirrorDataSource mirror) {
        LocalDateTime startedAt = LocalDateTime.now();
        updateStatus(mirror.name(), true, startedAt, null, null);
        try (Connection master = masterDataSource.getConnection();
             Connection replica = mirror.dataSource().getConnection()) {
            if (sameDatabase(master, replica)) {
                throw new IllegalStateException("镜像数据源与主数据源指向同一数据库");
            }
            master.setAutoCommit(false);
            try {
                master.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            } catch (Exception ignored) {
                // Some JDBC drivers expose a stable read transaction without accepting this isolation level.
            }
            replica.setAutoCommit(false);
            SqliteToDamengMigration.synchronize(master, replica, true, Math.max(1, properties.getBatchSize()));
            master.rollback();
            updateStatus(mirror.name(), false, startedAt, LocalDateTime.now(), null);
        } catch (Exception exception) {
            log.error("Database mirror synchronization failed for {}", mirror.name(), exception);
            updateStatus(mirror.name(), false, startedAt, null, exception.getMessage());
        }
    }

    private boolean sameDatabase(Connection master, Connection replica) throws Exception {
        String masterUrl = master.getMetaData().getURL();
        String replicaUrl = replica.getMetaData().getURL();
        String masterUser = master.getMetaData().getUserName();
        String replicaUser = replica.getMetaData().getUserName();
        return masterUrl != null && masterUrl.equalsIgnoreCase(replicaUrl)
                && String.valueOf(masterUser).equalsIgnoreCase(String.valueOf(replicaUser));
    }

    private synchronized void updateStatus(String name,
                                           boolean synchronizing,
                                           LocalDateTime startedAt,
                                           LocalDateTime successAt,
                                           String error) {
        replicaStatuses.put(name, new ReplicaStatus(synchronizing, startedAt, successAt, error));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record MirrorStatus(boolean enabled,
                               boolean synchronizing,
                               boolean pending,
                               LocalDateTime checkedAt,
                               Map<String, ReplicaStatus> replicas) {
    }

    public record ReplicaStatus(boolean synchronizing,
                                LocalDateTime lastStartedAt,
                                LocalDateTime lastSuccessAt,
                                String lastError) {
    }
}
