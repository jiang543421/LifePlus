package com.lifepulse.common.exception;

import com.lifepulse.common.web.MyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理（plan §3 / §6.3；spec §03 §8）。
 *
 * <p>CLAUDE.md §4.5：所有业务异常统一抛 {@code BusinessException(code, msg)}，
 * 由本类转统一信封。校验异常→{@code 1001}；其他异常→{@code 1500}。traceId 通过
 * {@link MyResponse} 自动从 MDC 注入（{@link com.lifepulse.common.web.TraceIdFilter}
 * 写入）。
 *
 * <p>HTTP 状态映射（{@link #mapStatus}）：
 * <pre>
 *   1001 → 400 (参数校验失败)
 *   1002, 1401 → 401 (未登录 / refresh 失效)
 *   1003 → 403 (跨用户)
 *   1004 → 404 (资源不存在)
 *   1005 → 409 (冲突)
 *   1006 → 429 (限流)
 *   1501 → 503 (AI 降级；spec ai-v2-design.md §6.4)
 *   其他 → 500
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 系统兜底错误码。 */
    private static final int CODE_SERVER = 1500;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<MyResponse<Void>> handleBusiness(BusinessException ex) {
        log.debug("business exception: code={}, msg={}", ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(mapStatus(ex.getCode()))
                .body(MyResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MyResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("validation failed");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(MyResponse.error(ErrorCode.VALIDATION, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MyResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(MyResponse.error(ErrorCode.VALIDATION, "malformed request body"));
    }

    /**
     * 查询参数 / 路径变量类型转换失败（例如 {@code ?date=not-a-date}）→ 400 / 1001。
     * 与 {@link #handleUnreadable} 对称：请求体 JSON 无法解析 vs 请求参数无法转换，
     * 都是"输入格式无法处理"，统一走校验错码。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<MyResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        Object value = ex.getValue();
        String msg = name + ": invalid value '" + value + "'";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(MyResponse.error(ErrorCode.VALIDATION, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MyResponse<Void>> handleGeneric(Exception ex) {
        log.warn("unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MyResponse.error(CODE_SERVER, "internal server error"));
    }

    /** 错误码 → HTTP 状态映射（plan §5）。 */
    static HttpStatus mapStatus(int code) {
        return switch (code) {
            case ErrorCode.VALIDATION -> HttpStatus.BAD_REQUEST;                   // 1001 → 400
            case ErrorCode.BAD_CREDENTIALS,
                 ErrorCode.REFRESH_INVALID -> HttpStatus.UNAUTHORIZED;             // 1002 / 1401 → 401
            case ErrorCode.CROSS_USER -> HttpStatus.FORBIDDEN;                     // 1003 → 403
            case ErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND;                      // 1004 → 404
            case ErrorCode.EMAIL_TAKEN -> HttpStatus.CONFLICT;                      // 1005 → 409
            case ErrorCode.LOGIN_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;        // 1006 → 429
            case ErrorCode.AI_DEGRADED -> HttpStatus.SERVICE_UNAVAILABLE;          // 1501 → 503
            default -> HttpStatus.INTERNAL_SERVER_ERROR;                           // 其他 → 500
        };
    }
}
