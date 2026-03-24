package com.lan4jn.file.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid request: {}", e.getMessage());
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> handleIllegalStateException(IllegalStateException e) {
        log.warn("Conflict request: {}", e.getMessage());
        return Result.error(409, e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public Result<Void> handleSecurityException(SecurityException e) {
        log.warn("Forbidden request: {}", e.getMessage());
        return Result.error(403, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("Runtime exception: {}", e.getMessage(), e);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("File size exceeds limit: {}", e.getMessage());
        return Result.error(413, "文件大小超出限制");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return Result.error("系统异常");
    }
}
