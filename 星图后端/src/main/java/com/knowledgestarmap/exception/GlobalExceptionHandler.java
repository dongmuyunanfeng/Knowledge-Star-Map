package com.knowledgestarmap.exception;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.security.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        if (e.getCode() >= 1901 && e.getCode() <= 1999) {
            log.warn("LLM服务异常: code={}, message={}", e.getCode(), e.getMessage());
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Result<?> handleUnauthorized(UnauthorizedException e) {
        return Result.fail(BizErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleForbidden(AccessDeniedException e) {
        return Result.fail(BizErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(BizErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(BizErrorCode.PARAM_ERROR, "请求体格式错误");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return Result.fail(BizErrorCode.PARAM_ERROR, "数据唯一性冲突，请检查后重试");
    }

    @ExceptionHandler(MybatisPlusException.class)
    public Result<?> handleMybatisPlusException(MybatisPlusException e) {
        log.error("数据库操作异常: {}", e.getMessage(), e);
        return Result.fail(BizErrorCode.INTERNAL_ERROR, "数据库操作失败");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(BizErrorCode.INTERNAL_ERROR);
    }
}
