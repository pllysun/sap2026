package com.sap.config;

import com.sap.BaseUnitTest;
import com.sap.entity.Activity;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyMetaObjectHandler 自动填充测试。
 * 依赖实体 TableInfo（含 FieldFill 元数据），故继承 BaseUnitTest。
 */
class MyMetaObjectHandlerTest extends BaseUnitTest {

    private final MyMetaObjectHandler handler = new MyMetaObjectHandler();

    @Test
    void insertFill_setsCreatedAtAndUpdatedAt_whenNull() {
        Activity entity = new Activity();
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());

        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.insertFill(metaObject);

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void insertFill_doesNotOverrideExistingValues() {
        Activity entity = new Activity();
        LocalDateTime fixed = LocalDateTime.of(2020, 1, 1, 0, 0);
        entity.setCreatedAt(fixed);
        entity.setUpdatedAt(fixed);

        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.insertFill(metaObject);

        // strictInsertFill 仅在字段为 null 时填充
        assertEquals(fixed, entity.getCreatedAt());
        assertEquals(fixed, entity.getUpdatedAt());
    }

    @Test
    void updateFill_setsUpdatedAt_whenNull() {
        Activity entity = new Activity();
        assertNull(entity.getUpdatedAt());

        MetaObject metaObject = SystemMetaObject.forObject(entity);
        handler.updateFill(metaObject);

        assertNotNull(entity.getUpdatedAt());
        // updateFill 不应触碰 createdAt
        assertNull(entity.getCreatedAt());
    }
}
