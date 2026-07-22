package com.lifepulse.common.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.2-A · MyResponse envelope 单元测试（plan §9）。
 *
 * <p>{@link MyResponse} 是全局返回信封（spec §03）：
 * <pre>{code, message, data, traceId}</pre>
 * 成功 {@code code=0}。{@code @RestControllerAdvice} 在 1.3 落地，
 * 本阶段仅供 IT 与手工断言使用。
 */
class MyResponseTest {

    @Test
    void ok_wrapsDataWithSuccessCodeAndDefaultMessage() {
        // Act
        MyResponse<String> r = MyResponse.ok("payload");

        // Assert
        assertThat(r.code()).isEqualTo(0);
        assertThat(r.data()).isEqualTo("payload");
        assertThat(r.message()).isEqualTo("ok");
        assertThat(r.traceId()).isNull();
    }

    @Test
    void error_setsCodeAndMessageWithNullData() {
        // Act
        MyResponse<Void> r = MyResponse.error(1002, "invalid credentials");

        // Assert
        assertThat(r.code()).isEqualTo(1002);
        assertThat(r.message()).isEqualTo("invalid credentials");
        assertThat(r.data()).isNull();
    }

    @Test
    void ok_pullsTraceIdFromMdcWhenPresent() {
        // Arrange
        org.slf4j.MDC.put("traceId", "fixed-trace-id-1");

        try {
            // Act
            MyResponse<String> r = MyResponse.ok("payload");

            // Assert
            assertThat(r.traceId()).isEqualTo("fixed-trace-id-1");
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    @Test
    void error_pullsTraceIdFromMdcWhenPresent() {
        org.slf4j.MDC.put("traceId", "fixed-trace-id-2");

        try {
            MyResponse<Void> r = MyResponse.error(1401, "refresh invalid");

            assertThat(r.traceId()).isEqualTo("fixed-trace-id-2");
        } finally {
            org.slf4j.MDC.clear();
        }
    }
}
