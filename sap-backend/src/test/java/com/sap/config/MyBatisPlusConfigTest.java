package com.sap.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatisPlusConfig 单元测试：验证分页拦截器 Bean 构造，并覆盖 H2 / MySQL 两条 DbType 分支。
 */
class MyBatisPlusConfigTest {

    private MyBatisPlusConfig configWithUrl(String url) {
        MyBatisPlusConfig config = new MyBatisPlusConfig();
        ReflectionTestUtils.setField(config, "datasourceUrl", url);
        return config;
    }

    @Test
    void mybatisPlusInterceptor_returnsNonNullWithPaginationInner_forMysql() {
        MybatisPlusInterceptor interceptor =
                configWithUrl("jdbc:mysql://localhost:3306/sap").mybatisPlusInterceptor();

        assertNotNull(interceptor);
        List<InnerInterceptor> inners = interceptor.getInterceptors();
        assertEquals(1, inners.size());
        assertInstanceOf(PaginationInnerInterceptor.class, inners.get(0));
    }

    @Test
    void mybatisPlusInterceptor_supportsH2Branch() {
        MybatisPlusInterceptor interceptor =
                configWithUrl("jdbc:h2:mem:test").mybatisPlusInterceptor();

        assertNotNull(interceptor);
        assertEquals(1, interceptor.getInterceptors().size());
        assertInstanceOf(PaginationInnerInterceptor.class, interceptor.getInterceptors().get(0));
    }

    @Test
    void mybatisPlusInterceptor_emptyUrlDefaultsToMysqlBranch() {
        MybatisPlusInterceptor interceptor = configWithUrl("").mybatisPlusInterceptor();
        assertNotNull(interceptor);
        assertEquals(1, interceptor.getInterceptors().size());
    }
}
