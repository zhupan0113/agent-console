package com.lenovo.agentconsole.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handle(Exception e) {
        log.error("request failed", e);
        return ApiResult.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
