package com.sap;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sap.entity.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯单元测试基类。
 * <p>MyBatis-Plus 的 LambdaQueryWrapper 在 {@code .select()/.groupBy()} 等处会即时解析列名，
 * 需要实体的 TableInfo / lambda 缓存。脱离 Spring 上下文的纯 Mockito 单测里该缓存不会自动初始化，
 * 这里在类加载时手动注册全部实体，供继承的测试共享。</p>
 */
public abstract class BaseUnitTest {

    static {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.sap.test");

        Class<?>[] entities = {
                Activity.class, ActivityImage.class, Bill.class, BillImage.class,
                JoinApplication.class, JoinManager.class, LogStats.class, Message.class,
                MessageLike.class, MessageReply.class, Note.class, NoteDownload.class,
                NoteView.class, OutstandingMember.class, Position.class, Role.class,
                Setting.class, StudyActivity.class, StudyLeader.class, StudyMaterial.class,
                StudyMember.class, StudyScore.class, SysLog.class, Term.class,
                User.class, UserRole.class
        };
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
