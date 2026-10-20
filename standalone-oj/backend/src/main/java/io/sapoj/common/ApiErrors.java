package io.sapoj.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(BusinessException.class) public Result<?> business(BusinessException e) { return Result.error(e.getCode(), e.getMessage()); }
    @ExceptionHandler(NotLoginException.class) public ResponseEntity<?> login() { return ResponseEntity.status(401).body(Result.error(401,"请先登录")); }
    @ExceptionHandler(NotRoleException.class) public ResponseEntity<?> role() { return ResponseEntity.status(403).body(Result.error(403,"没有操作权限")); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public Result<?> invalid() { return Result.error(400,"请求格式或字段不符合要求"); }
}
