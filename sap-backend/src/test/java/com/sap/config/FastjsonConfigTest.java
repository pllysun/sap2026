package com.sap.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FastjsonConfig 单元测试：验证 configureMessageConverters 在列表头部插入 FastJson 转换器。
 */
class FastjsonConfigTest {

    @Test
    void configureMessageConverters_insertsFastJsonConverterAtHead() {
        FastjsonConfig config = new FastjsonConfig();
        List<HttpMessageConverter<?>> converters = new ArrayList<>();

        config.configureMessageConverters(converters);

        assertEquals(1, converters.size());
        HttpMessageConverter<?> converter = converters.get(0);
        assertNotNull(converter);
        assertInstanceOf(FastJsonHttpMessageConverter.class, converter);
    }

    @Test
    void configureMessageConverters_prependsBeforeExisting() {
        FastjsonConfig config = new FastjsonConfig();
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        HttpMessageConverter<?> placeholder =
                new org.springframework.http.converter.StringHttpMessageConverter();
        converters.add(placeholder);

        config.configureMessageConverters(converters);

        assertEquals(2, converters.size());
        assertInstanceOf(FastJsonHttpMessageConverter.class, converters.get(0));
        assertSame(placeholder, converters.get(1));
    }
}
