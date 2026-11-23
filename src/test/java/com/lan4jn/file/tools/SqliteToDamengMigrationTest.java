package com.lan4jn.file.tools;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteToDamengMigrationTest {

    @Test
    void shouldMakeReplicaMatchMasterAndRemoveExtraRows() throws Exception {
        try (Connection master = DriverManager.getConnection("jdbc:sqlite::memory:");
             Connection replica = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createConfigTable(master);
            createConfigTable(replica);

            execute(master, "insert into sys_config(id, config_key, config_value) values (1, 'mode', 'master')");
            execute(master, "insert into sys_config(id, config_key, config_value) values (2, 'limit', '100')");
            execute(replica, "insert into sys_config(id, config_key, config_value) values (1, 'mode', 'replica')");
            execute(replica, "insert into sys_config(id, config_key, config_value) values (3, 'extra', 'remove-me')");

            replica.setAutoCommit(false);
            SqliteToDamengMigration.synchronize(master, replica, true, 10);

            assertEquals(2L, count(replica));
            assertEquals("master", value(replica, 1L));
            assertEquals("100", value(replica, 2L));

            execute(master, "delete from sys_config where id = 2");
            execute(master, "update sys_config set config_value = 'master-2' where id = 1");
            SqliteToDamengMigration.synchronize(master, replica, true, 10);

            assertEquals(1L, count(replica));
            assertEquals("master-2", value(replica, 1L));
        }
    }

    private void createConfigTable(Connection connection) throws Exception {
        execute(connection, "create table sys_config ("
                + "id integer primary key autoincrement, "
                + "config_key varchar(255), "
                + "config_value varchar(255))");
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private long count(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from sys_config")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private String value(Connection connection, long id) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select config_value from sys_config where id = " + id)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
