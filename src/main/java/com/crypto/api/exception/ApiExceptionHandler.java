package com.crypto.api.exception;

import com.crypto.api.dto.ApiErrorResponse;
import com.crypto.common.time.IstanbulTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), "NOT_FOUND");
    }

    @ExceptionHandler({
            BadRequestException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception exception) {
        log.error("API_INTERNAL_ERROR", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_ERROR");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(false, message, code, IstanbulTimeUtil.nowText()));
    }
}
