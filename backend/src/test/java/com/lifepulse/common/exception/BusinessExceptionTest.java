package com.lifepulse.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.2-A · BusinessException 单元测试（plan §9）。
 *
 * <p>断言异常承载业务错误码与消息；后续 GlobalExceptionHandler（1.3）
 * 会消费 {@link BusinessException#getCode()} 转换为 envelope。
 */
class BusinessExceptionTest {

    @Test
    void constructor_setsCodeAndMessage() {
        // Act
        BusinessException ex = new BusinessException(1002, "invalid credentials");

        // Assert
        assertThat(ex.getCode()).isEqualTo(1002);
        assertThat(ex.getMessage()).isEqualTo("invalid credentials");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
