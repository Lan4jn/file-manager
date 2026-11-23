package com.lan4jn.file.tools;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class SqliteToDamengMigration {

    private static final List<String> TABLE_ORDER = Arrays.asList(
            "sys_permission",
            "sys_role",
            "sys_user",
            "sys_role_permission",
            "sys_user_role",
            "role_bucket_permission",
            "sys_config",
            "sys_minio_api_key",
            "sys_share_link",
            "sys_share_file_record",
            "sys_user_share_message",
            "sys_operation_log",
            "transfer_archive_task",
            "directory_history_entry",
            "favorite_directory_entry",
            "user_workbench_preference",
            "bucket_stats_snapshot"
    );

    private SqliteToDamengMigration() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Class.forName("org.sqlite.JDBC");
        Class.forName("dm.jdbc.driver.DmDriver");

        try (Connection source = DriverManager.getConnection(options.sourceUrl);
             Connection target = DriverManager.getConnection(
                     options.targetUrl,
                     options.targetUsername,
                     options.targetPassword)) {
            target.setAutoCommit(false);
            migrate(source, target, options);
        }
    }

    static void migrate(Connection source, Connection target, Options options) throws SQLException {
        List<String> sourceTables = loadBusinessTables(source);
        Map<String, String> targetTables = loadTargetTables(target);
        List<String> missingTables = sourceTables.stream()
                .filter(sourceTable -> !targetTables.containsKey(sourceTable.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toList());
        if (!missingTables.isEmpty()) {
            throw new IllegalStateException("目标数据库缺少业务表: " + missingTables);
        }
        List<TableMapping> mappings = buildMappings(sourceTables, targetTables);
        if (mappings.isEmpty()) {
            throw new IllegalStateException("没有找到可迁移的业务表，请先在达梦测试 schema 中创建表结构");
        }

        if (options.truncateTarget) {
            truncateTargetTables(target, mappings);
        } else {
            assertTargetTablesEmpty(target, mappings);
        }

        for (TableMapping mapping : mappings) {
            migrateTable(source, target, mapping, options.batchSize);
        }
        target.commit();
        System.out.println("[DB-MIGRATION] 迁移完成，共处理 " + mappings.size() + " 张表");
    }

    public static void synchronize(Connection master,
                                   Connection replica,
                                   boolean replaceReplicaData,
                                   int batchSize) throws SQLException {
        migrate(master, replica, new Options("", "", "", "", replaceReplicaData, batchSize));
    }

    private static List<String> loadBusinessTables(Connection connection) throws SQLException {
        Map<String, String> knownTables = TABLE_ORDER.stream()
                .collect(Collectors.toMap(
                        table -> table.toUpperCase(Locale.ROOT),
                        table -> table,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        String schema = safeSchema(connection);
        try (ResultSet resultSet = metadata.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName != null && knownTables.containsKey(tableName.toUpperCase(Locale.ROOT))) {
                    tables.add(tableName);
                }
            }
        }
        return tables;
    }

    private static Map<String, String> loadTargetTables(Connection target) throws SQLException {
        Map<String, String> tables = new LinkedHashMap<>();
        DatabaseMetaData metadata = target.getMetaData();
        String schema = safeSchema(target);
        try (ResultSet resultSet = metadata.getTables(null, schema, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                String tableName = resultSet.getString("TABLE_NAME");
                if (tableName != null) {
                    tables.put(tableName.toUpperCase(Locale.ROOT), tableName);
                }
            }
        }
        return tables;
    }

    private static String safeSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | AbstractMethodError ignored) {
            return null;
        }
    }

    private static List<TableMapping> buildMappings(List<String> sourceTables, Map<String, String> targetTables) {
        Map<String, Integer> order = new HashMap<>();
        for (int index = 0; index < TABLE_ORDER.size(); index++) {
            order.put(TABLE_ORDER.get(index).toUpperCase(Locale.ROOT), index);
        }

        return sourceTables.stream()
                .map(sourceTable -> new TableMapping(
                        sourceTable,
                        targetTables.get(sourceTable.toUpperCase(Locale.ROOT))))
                .filter(mapping -> mapping.targetTable != null)
                .sorted(Comparator
                        .comparingInt((TableMapping mapping) -> order.getOrDefault(
                                mapping.sourceTable.toUpperCase(Locale.ROOT), Integer.MAX_VALUE))
                        .thenComparing(mapping -> mapping.sourceTable))
                .collect(Collectors.toList());
    }

    private static void assertTargetTablesEmpty(Connection target, List<TableMapping> mappings) throws SQLException {
        for (TableMapping mapping : mappings) {
            long count = countRows(target, mapping.targetTable);
            if (count > 0) {
                throw new IllegalStateException("目标表 " + mapping.targetTable
                        + " 已存在 " + count + " 条数据；请使用空 schema，或显式传入 --truncate-target=true");
            }
        }
    }

    private static void truncateTargetTables(Connection target, List<TableMapping> mappings) throws SQLException {
        List<TableMapping> reversed = new ArrayList<>(mappings);
        Collections.reverse(reversed);
        try (Statement statement = target.createStatement()) {
            for (TableMapping mapping : reversed) {
                statement.executeUpdate("DELETE FROM " + safeIdentifier(mapping.targetTable));
            }
        }
    }

    private static void migrateTable(Connection source,
                                     Connection target,
                                     TableMapping mapping,
                                     int batchSize) throws SQLException {
        List<ColumnMapping> columns = resolveColumns(source, target, mapping);
        if (columns.isEmpty()) {
            System.out.println("[DB-MIGRATION] 跳过 " + mapping.sourceTable + "：没有匹配列");
            return;
        }

        long sourceCount = countRows(source, safeIdentifier(mapping.sourceTable));
        if (sourceCount == 0) {
            System.out.println("[DB-MIGRATION] " + mapping.sourceTable + "：0 条");
            return;
        }

        boolean identityInsertEnabled = enableIdentityInsertIfRequired(target, mapping, columns);
        String sourceSql = "SELECT " + columns.stream()
                .map(column -> safeIdentifier(column.sourceName))
                .collect(Collectors.joining(", "))
                + " FROM " + safeIdentifier(mapping.sourceTable);
        String targetSql = "INSERT INTO " + safeIdentifier(mapping.targetTable)
                + " (" + columns.stream()
                .map(column -> safeIdentifier(column.targetName))
                .collect(Collectors.joining(", ")) + ") VALUES ("
                + columns.stream().map(column -> "?").collect(Collectors.joining(", ")) + ")";

        long migrated = 0;
        try (Statement sourceStatement = source.createStatement();
             ResultSet sourceRows = sourceStatement.executeQuery(sourceSql);
             PreparedStatement targetInsert = target.prepareStatement(targetSql)) {
            while (sourceRows.next()) {
                for (int index = 0; index < columns.size(); index++) {
                    ColumnMapping column = columns.get(index);
                    bindValue(targetInsert, index + 1, sourceRows.getObject(index + 1), column.targetJdbcType);
                }
                targetInsert.addBatch();
                migrated++;
                if (migrated % batchSize == 0) {
                    targetInsert.executeBatch();
                }
            }
            targetInsert.executeBatch();
        } catch (SQLException | RuntimeException exception) {
            target.rollback();
            throw exception;
        } finally {
            if (identityInsertEnabled) {
                setIdentityInsert(target, mapping.targetTable, false);
            }
        }

        long targetCount = countRows(target, mapping.targetTable);
        if (targetCount != sourceCount) {
            throw new IllegalStateException("表 " + mapping.sourceTable + " 行数校验失败：source="
                    + sourceCount + ", target=" + targetCount);
        }
        System.out.println("[DB-MIGRATION] " + mapping.sourceTable + "：" + migrated + " 条");
    }

    private static List<ColumnMapping> resolveColumns(Connection source,
                                                      Connection target,
                                                      TableMapping mapping) throws SQLException {
        Map<String, SourceColumn> sourceColumns = new LinkedHashMap<>();
        try (Statement statement = source.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM " + safeIdentifier(mapping.sourceTable) + " WHERE 1 = 0")) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                String name = metadata.getColumnName(index);
                sourceColumns.put(name.toUpperCase(Locale.ROOT), new SourceColumn(name));
            }
        }

        List<ColumnMapping> mappings = new ArrayList<>();
        try (Statement statement = target.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT * FROM " + safeIdentifier(mapping.targetTable) + " WHERE 1 = 0")) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                String targetName = metadata.getColumnName(index);
                SourceColumn sourceColumn = sourceColumns.get(targetName.toUpperCase(Locale.ROOT));
                if (sourceColumn != null) {
                    mappings.add(new ColumnMapping(
                            sourceColumn.name,
                            targetName,
                            metadata.getColumnType(index),
                            metadata.isAutoIncrement(index)));
                }
            }
        }
        return mappings;
    }

    private static boolean enableIdentityInsertIfRequired(Connection target,
                                                          TableMapping mapping,
                                                          List<ColumnMapping> columns) throws SQLException {
        boolean required = columns.stream().anyMatch(column -> column.autoIncrement
                || "id".equalsIgnoreCase(column.targetName));
        if (!required || !isDameng(target)) {
            return false;
        }
        setIdentityInsert(target, mapping.targetTable, true);
        return true;
    }

    private static void setIdentityInsert(Connection target, String tableName, boolean enabled) throws SQLException {
        String sql = "SET IDENTITY_INSERT " + safeIdentifier(tableName) + (enabled ? " ON" : " OFF");
        try (Statement statement = target.createStatement()) {
            statement.execute(sql);
        }
    }

    private static boolean isDameng(Connection connection) throws SQLException {
        String productName = connection.getMetaData().getDatabaseProductName();
        return productName != null && productName.toUpperCase(Locale.ROOT).contains("DM");
    }

    private static void bindValue(PreparedStatement statement,
                                  int parameterIndex,
                                  Object value,
                                  int targetJdbcType) throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, targetJdbcType);
            return;
        }

        switch (targetJdbcType) {
            case Types.BOOLEAN:
            case Types.BIT:
                statement.setBoolean(parameterIndex, toBoolean(value));
                return;
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                statement.setTimestamp(parameterIndex, toTimestamp(value));
                return;
            case Types.CLOB:
            case Types.LONGVARCHAR:
            case Types.LONGNVARCHAR:
                statement.setString(parameterIndex, String.valueOf(value));
                return;
            case Types.BIGINT:
                if (value instanceof Number number) {
                    statement.setLong(parameterIndex, number.longValue());
                    return;
                }
                break;
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                if (value instanceof Number number) {
                    statement.setInt(parameterIndex, number.intValue());
                    return;
                }
                break;
            case Types.NUMERIC:
            case Types.DECIMAL:
                if (value instanceof BigDecimal decimal) {
                    statement.setBigDecimal(parameterIndex, decimal);
                    return;
                }
                if (value instanceof Number number) {
                    statement.setBigDecimal(parameterIndex, new BigDecimal(number.toString()));
                    return;
                }
                break;
            default:
                break;
        }
        statement.setObject(parameterIndex, value, targetJdbcType);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = String.valueOf(value).trim();
        return "1".equals(normalized) || "true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized);
    }

    private static Timestamp toTimestamp(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof Number number) {
            return new Timestamp(number.longValue());
        }

        String text = String.valueOf(value).trim();
        try {
            return Timestamp.valueOf(text.replace('T', ' '));
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(text).toInstant());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Timestamp.from(Instant.parse(text));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("无法转换时间值: " + text, exception);
        }
    }

    private static long countRows(Connection connection, String tableExpression) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableExpression)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String safeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法数据库标识符: " + identifier);
        }
        return identifier;
    }

    private static final class SourceColumn {
        private final String name;

        private SourceColumn(String name) {
            this.name = name;
        }
    }

    private static final class ColumnMapping {
        private final String sourceName;
        private final String targetName;
        private final int targetJdbcType;
        private final boolean autoIncrement;

        private ColumnMapping(String sourceName, String targetName, int targetJdbcType, boolean autoIncrement) {
            this.sourceName = sourceName;
            this.targetName = targetName;
            this.targetJdbcType = targetJdbcType;
            this.autoIncrement = autoIncrement;
        }
    }

    private static final class TableMapping {
        private final String sourceTable;
        private final String targetTable;

        private TableMapping(String sourceTable, String targetTable) {
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
        }
    }

    static final class Options {
        private final String sourceUrl;
        private final String targetUrl;
        private final String targetUsername;
        private final String targetPassword;
        private final boolean truncateTarget;
        private final int batchSize;

        private Options(String sourceUrl,
                        String targetUrl,
                        String targetUsername,
                        String targetPassword,
                        boolean truncateTarget,
                        int batchSize) {
            this.sourceUrl = sourceUrl;
            this.targetUrl = targetUrl;
            this.targetUsername = targetUsername;
            this.targetPassword = targetPassword;
            this.truncateTarget = truncateTarget;
            this.batchSize = batchSize;
        }

        private static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    throw new IllegalArgumentException("参数格式必须为 --key=value: " + arg);
                }
                int separator = arg.indexOf('=');
                values.put(arg.substring(2, separator), arg.substring(separator + 1));
            }

            String sourceUrl = value(values, "source-url", "SQLITE_URL", "jdbc:sqlite:./data/file-manager.db");
            String targetUrl = required(values, "target-url", "DM_URL");
            String targetUsername = required(values, "target-username", "DM_USERNAME");
            String targetPassword = required(values, "target-password", "DM_PASSWORD");
            boolean truncateTarget = Boolean.parseBoolean(
                    value(values, "truncate-target", "DM_TRUNCATE_TARGET", "false"));
            int batchSize = Integer.parseInt(value(values, "batch-size", "DM_MIGRATION_BATCH_SIZE", "500"));
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batch-size 必须大于 0");
            }
            return new Options(sourceUrl, targetUrl, targetUsername, targetPassword, truncateTarget, batchSize);
        }

        private static String required(Map<String, String> values, String key, String environmentName) {
            String result = value(values, key, environmentName, null);
            if (result == null || result.trim().isEmpty()) {
                throw new IllegalArgumentException("缺少参数 --" + key + " 或环境变量 " + environmentName);
            }
            return result;
        }

        private static String value(Map<String, String> values,
                                    String key,
                                    String environmentName,
                                    String defaultValue) {
            String result = values.get(key);
            if (result == null || result.trim().isEmpty()) {
                result = System.getenv(environmentName);
            }
            return result == null || result.trim().isEmpty() ? defaultValue : result.trim();
        }
    }
}
