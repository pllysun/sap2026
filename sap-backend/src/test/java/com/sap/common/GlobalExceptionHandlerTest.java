package com.sap.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试：逐个构造异常调用对应 @ExceptionHandler，
 * 断言返回 Result 的 code/message。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessException_usesExceptionCodeAndMessage() {
        BusinessException ex = new BusinessException(401, "未授权");
        Result<?> r = handler.handleBusinessException(ex);
        assertEquals(401, r.getCode());
        assertEquals("未授权", r.getMessage());
    }

    @Test
    void handleBusinessException_defaultCode() {
        BusinessException ex = new BusinessException("出错了");
        Result<?> r = handler.handleBusinessException(ex);
        assertEquals(500, r.getCode());
        assertEquals("出错了", r.getMessage());
    }

    @Test
    void handleNotLoginException_returns401() {
        NotLoginException ex = NotLoginException.newInstance("login", NotLoginException.NOT_TOKEN, null, null);
        Result<?> r = handler.handleNotLoginException(ex);
        assertEquals(401, r.getCode());
        assertEquals("未登录或登录已过期", r.getMessage());
    }

    @Test
    void handleNotPermissionException_returns403() {
        NotPermissionException ex = new NotPermissionException("user:add", "login");
        Result<?> r = handler.handleNotPermissionException(ex);
        assertEquals(403, r.getCode());
        assertEquals("无权限访问", r.getMessage());
    }

    @Test
    void handleNotRoleException_returns403() {
        NotRoleException ex = new NotRoleException("admin", "login");
        Result<?> r = handler.handleNotRoleException(ex);
        assertEquals(403, r.getCode());
        assertEquals("角色权限不足", r.getMessage());
    }

    @Test
    void handleValidationException_concatenatesFieldErrors() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "不能为空"));
        bindingResult.addError(new FieldError("target", "age", "必须大于0"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException((MethodParameter) null, bindingResult);

        Result<?> r = handler.handleValidationException(ex);
        assertEquals(400, r.getCode());
        assertEquals("name: 不能为空; age: 必须大于0", r.getMessage());
    }

    @Test
    void handleValidationException_emptyFieldErrorsFallsBackToDefault() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException((MethodParameter) null, bindingResult);

        Result<?> r = handler.handleValidationException(ex);
        assertEquals(400, r.getCode());
        assertEquals("参数校验失败", r.getMessage());
    }

    @Test
    void handleMissingParam_includesParameterName() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("page", "String");
        Result<?> r = handler.handleMissingParam(ex);
        assertEquals(400, r.getCode());
        assertEquals("缺少必要参数: page", r.getMessage());
    }

    @Test
    void handleTypeMismatch_includesParameterName() {
        MethodArgumentTypeMismatchException ex = Mockito.mock(MethodArgumentTypeMismatchException.class);
        Mockito.when(ex.getName()).thenReturn("id");
        Result<?> r = handler.handleTypeMismatch(ex);
        assertEquals(400, r.getCode());
        assertEquals("参数类型错误: id", r.getMessage());
    }

    @Test
    void handleNotReadable_returns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad", new MockHttpInputMessage(new byte[0]));
        Result<?> r = handler.handleNotReadable(ex);
        assertEquals(400, r.getCode());
        assertEquals("请求体格式错误", r.getMessage());
    }

    @Test
    void handleDuplicateKey_returns409() {
        DuplicateKeyException ex = new DuplicateKeyException("duplicate entry");
        Result<?> r = handler.handleDuplicateKey(ex);
        assertEquals(409, r.getCode());
        assertEquals("数据已存在或重复提交", r.getMessage());
    }

    @Test
    void handleException_returnsGenericServerError() {
        Result<?> r = handler.handleException(new RuntimeException("boom"));
        assertEquals(500, r.getCode());
        assertEquals("服务器内部错误，请稍后再试", r.getMessage());
    }
}
