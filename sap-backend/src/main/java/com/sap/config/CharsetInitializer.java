package com.sap.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时自动将所有业务表转换为 utf8mb4 字符集，
 * 防止 Emoji / 4字节 UTF-8 字符写入失败。
 */
@Slf4j
@Component
public class CharsetInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void ensureUtf8mb4() {
        try (Connection conn = dataSource.getConnection()) {
            String driverName = conn.getMetaData().getDriverName().toLowerCase();
            // 仅对 MySQL 执行，H2 等嵌入式数据库跳过
            if (!driverName.contains("mysql")) {
                log.info("[CharsetInitializer] 非 MySQL 数据库，跳过字符集检查");
                return;
            }

            String catalog = conn.getCatalog();
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"});

            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
            rs.close();

            for (String table : tables) {
                try {
                    jdbcTemplate.execute(
                            "ALTER TABLE `" + table + "` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                    );
                    log.debug("[CharsetInitializer] 已确认表 {} 使用 utf8mb4", table);
                } catch (Exception e) {
                    log.warn("[CharsetInitializer] 转换表 {} 字符集失败: {}", table, e.getMessage());
                }
            }
            log.info("[CharsetInitializer] 字符集检查完毕，共处理 {} 张表", tables.size());
        } catch (Exception e) {
            log.warn("[CharsetInitializer] 字符集初始化跳过: {}", e.getMessage());
        }
    }
}
