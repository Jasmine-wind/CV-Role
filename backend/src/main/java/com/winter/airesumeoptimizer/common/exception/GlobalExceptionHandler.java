package com.winter.airesumeoptimizer.common.exception;

import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.common.logging.RequestIdFilter;
import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.common.result.ResultCode;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.ai.AiFailureCode;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request) {
        HttpStatusCode status = errorStatus(exception.getCode());
        log.warn("Business exception: code={}, path={}, message={}",
                status.value(),
                request.getRequestURI(),
                LogSanitizer.sanitize(exception.getMessage()));
        String clientMessage = status.is5xxServerError()
                ? publicMessage(status)
                : LogSanitizer.sanitize(exception.getMessage());
        return failure(status, clientMessage, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return failure(HttpStatus.BAD_REQUEST,
                emptyToDefault(message, ResultCode.BAD_REQUEST.getMessage()), request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException exception, HttpServletRequest request) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return failure(HttpStatus.BAD_REQUEST,
                emptyToDefault(message, ResultCode.BAD_REQUEST.getMessage()), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining("; "));
        return failure(HttpStatus.BAD_REQUEST,
                emptyToDefault(message, ResultCode.BAD_REQUEST.getMessage()), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return failure(HttpStatus.BAD_REQUEST, "请求参数类型不正确", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return failure(HttpStatus.BAD_REQUEST, "请求体格式不正确", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return failure(HttpStatus.PAYLOAD_TOO_LARGE, "简历文件大小不能超过 10 MB", request);
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MultipartException.class
    })
    public ResponseEntity<Result<Void>> handleMultipartException(
            Exception exception,
            HttpServletRequest request) {
        return failure(HttpStatus.BAD_REQUEST, "请选择要上传的简历文件", request);
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<Result<Void>> handleFileStorageException(
            FileStorageException exception,
            HttpServletRequest request) {
        log.warn("File storage exception: path={}, message={}",
                request.getRequestURI(),
                LogSanitizer.sanitize(exception.getMessage()));
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "文件存储失败，请稍后重试", request);
    }

    @ExceptionHandler(AiGatewayException.class)
    public ResponseEntity<Result<Void>> handleAiGatewayException(
            AiGatewayException exception,
            HttpServletRequest request) {
        AiFailureCode failureCode = exception.getFailureCode();
        HttpStatus status = switch (failureCode) {
            case INVALID_CREDENTIAL, AI_CONFIGURATION_REQUIRED, CONFIGURATION_INVALID, UNSAFE_BASE_URL ->
                    HttpStatus.BAD_REQUEST;
            case CREDENTIAL_CHANGED -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        log.warn("AI gateway exception: code={}, path={}", failureCode, request.getRequestURI());
        return failure(status, failureCode.name(), request);
    }

    @ExceptionHandler(AiClientException.class)
    public ResponseEntity<Result<Void>> handleAiClientException(
            AiClientException exception,
            HttpServletRequest request) {
        log.warn("AI client exception: path={}, message={}",
                request.getRequestURI(),
                LogSanitizer.sanitize(exception.getMessage()));
        return failure(HttpStatus.BAD_GATEWAY, "AI 服务调用失败，请稍后重试", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request) {
        return failure(HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().isError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("HTTP exception: status={}, path={}, type={}",
                    status.value(), request.getRequestURI(), exception.getClass().getName());
            return failure(status, publicMessage(status), request);
        }

        log.error("Unhandled exception: path={}, type={}, message={}",
                request.getRequestURI(),
                exception.getClass().getName(),
                LogSanitizer.sanitize(exception.getMessage()));
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, ResultCode.INTERNAL_ERROR.getMessage(), request);
    }

    private ResponseEntity<Result<Void>> failure(
            HttpStatusCode status,
            String message,
            HttpServletRequest request) {
        int code = status.value();
        Result<Void> body = Result.failure(
                code,
                emptyToDefault(message, publicMessage(status)),
                request.getRequestURI(),
                RequestIdFilter.getOrCreateRequestId(request));
        return ResponseEntity.status(status).body(body);
    }

    private HttpStatusCode errorStatus(Integer code) {
        if (code == null || code < 400 || code > 599) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatusCode.valueOf(code);
    }

    private String publicMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ResultCode.BAD_REQUEST.getMessage();
            case 401 -> ResultCode.UNAUTHORIZED.getMessage();
            case 403 -> ResultCode.FORBIDDEN.getMessage();
            case 404 -> ResultCode.NOT_FOUND.getMessage();
            case 405 -> "请求方法不支持";
            case 413 -> ResultCode.PAYLOAD_TOO_LARGE.getMessage();
            case 415 -> "请求内容类型不支持";
            case 502 -> ResultCode.AI_SERVICE_ERROR.getMessage();
            default -> status.is5xxServerError() ? ResultCode.INTERNAL_ERROR.getMessage() : "请求失败";
        };
    }

    private String emptyToDefault(String message, String defaultMessage) {
        if (message == null || message.isBlank()) {
            return defaultMessage;
        }
        return message;
    }
}
