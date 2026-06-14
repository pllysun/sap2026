package com.sap.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CharsetInitializer 单元测试，覆盖 @PostConstruct 的 utf8mb4 处理逻辑各分支。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CharsetInitializerTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock DataSource dataSource;
    @Mock Connection connection;
    @Mock DatabaseMetaData metaData;
    @Mock ResultSet resultSet;

    @InjectMocks CharsetInitializer initializer;

    @Test
    void ensureUtf8mb4_skipsForNonMysqlDriver() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDriverName()).thenReturn("H2 JDBC Driver");

        assertDoesNotThrow(() -> initializer.ensureUtf8mb4());

        // 非 MySQL 不会枚举表，也不会执行 ALTER
        verify(metaData, never()).getTables(any(), any(), anyString(), any());
        verify(jdbcTemplate, never()).execute(anyString());
        verify(connection).close();
    }

    @Test
    void ensureUtf8mb4_altersEachTableForMysql() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(connection.getCatalog()).thenReturn("sap");
        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(resultSet);
        // 两张表
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("TABLE_NAME")).thenReturn("sys_user", "sys_role");

        assertDoesNotThrow(() -> initializer.ensureUtf8mb4());

        verify(jdbcTemplate).execute(contains("`sys_user`"));
        verify(jdbcTemplate).execute(contains("`sys_role`"));
        verify(resultSet).close();
        verify(connection).close();
    }

    @Test
    void ensureUtf8mb4_swallowsAlterFailurePerTable() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDriverName()).thenReturn("mysql");
        when(connection.getCatalog()).thenReturn("sap");
        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("TABLE_NAME")).thenReturn("bad_table");
        doThrow(new RuntimeException("alter failed")).when(jdbcTemplate).execute(anyString());

        // 单表失败被 catch，整体不抛
        assertDoesNotThrow(() -> initializer.ensureUtf8mb4());
        verify(jdbcTemplate).execute(contains("`bad_table`"));
    }

    @Test
    void ensureUtf8mb4_swallowsConnectionFailure() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("no db"));

        assertDoesNotThrow(() -> initializer.ensureUtf8mb4());
        verify(jdbcTemplate, never()).execute(anyString());
    }
}
