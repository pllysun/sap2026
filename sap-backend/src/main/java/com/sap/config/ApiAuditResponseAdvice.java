package com.sap.config;

import com.sap.aspect.OperationLogAspect;
import com.sap.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiAuditResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter) {return true;}
    @Override public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        if(body instanceof Result<?> result && request instanceof ServletServerHttpRequest servlet)
            servlet.getServletRequest().setAttribute(OperationLogAspect.CODE,result.getCode());
        return body;
    }
}
