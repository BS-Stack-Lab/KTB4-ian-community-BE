package com.ian.community.common.exception;

import com.ian.community.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final java.util.Map<String, Integer>
            VALIDATION_PRIORITY = java.util.Map.of(
                    "NotBlank", 0,
                    "NotNull", 1,
                    "Email", 2,
                    "Size", 3,
                    "Pattern", 4
            );
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(
            CustomException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(
                        new ApiResponse<>(
                                errorCode.getCode(),
                                errorCode.getMessage(),
                                null
                        )
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(
                        java.util.Comparator
                                .comparingInt(
                                        GlobalExceptionHandler
                                                ::validationPriority
                                )
                                .thenComparing(
                                        FieldError::getField
                                )
                                .thenComparing(error ->
                                        java.util.Objects.toString(
                                                error.getCode(),
                                                ""
                                        )
                                )
                                .thenComparing(error ->
                                        java.util.Objects.toString(
                                                error.getDefaultMessage(),
                                                ""
                                        )
                                )
                )
                .map(ObjectError::getDefaultMessage)
                .filter(value ->
                        value != null && !value.isBlank()
                )
                .findFirst()
                .orElse("입력값을 확인해주세요.");

        return ResponseEntity
                .badRequest()
                .body(
                        new ApiResponse<>(
                                ErrorCode.INVALID_REQUEST.getCode(),
                                message,
                                null
                        )
                );
    }

    private static int validationPriority(FieldError error) {
        return VALIDATION_PRIORITY.getOrDefault(
                error.getCode(),
                Integer.MAX_VALUE
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception exception
    ) {
        log.error("Unhandled application exception", exception);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(
                        new ApiResponse<>(
                                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                                ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                                null
                        )
                );
    }
}
