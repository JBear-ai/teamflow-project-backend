package com.example.test1;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;

@EnabledIfEnvironmentVariable(named="TEAMFLOW_MYSQL_MIGRATION_URL", matches=".+")
class MySqlMigrationTests extends MembershipMigrationTests {
    @Override protected Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("TEAMFLOW_MYSQL_MIGRATION_URL"),
            System.getenv().getOrDefault("TEAMFLOW_MYSQL_TEST_USER", "root"),
            System.getenv().getOrDefault("TEAMFLOW_MYSQL_TEST_PASSWORD", ""));
    }
}
