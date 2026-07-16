package com.lifepulse.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 1.3-A · {@link TraceIdFilter} 单元测试（plan §8 / §3）。
 *
 * <p>断言：(1) filter 在调用下游前后，{@code MDC("traceId")} 与响应头
 * {@code X-Trace-Id} 均被设置，且二者值相同且为合法 UUID；(2) 不论下游
 * {@code FilterChain} 是否抛异常，{@code MDC} 都必须在 filter 返回后清空
 * （线程复用安全，CLAUDE.md §4.1）。
 */
class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void doFilter_setsMdcAndResponseHeaderDuringChain() throws ServletException, IOException {
        // Arrange
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        String[] mdcDuringChain = new String[1];
        String[] headerDuringChain = new String[1];

        jakarta.servlet.FilterChain chain = (request, response) -> {
            mdcDuringChain[0] = MDC.get("traceId");
            headerDuringChain[0] = ((jakarta.servlet.http.HttpServletResponse) response).getHeader("X-Trace-Id");
        };

        // Act
        filter.doFilter(req, resp, chain);

        // Assert: 链执行时 MDC + header 均设置
        assertThat(mdcDuringChain[0])
                .as("traceId from MDC during chain")
                .isNotNull();
        assertThat(headerDuringChain[0])
                .as("X-Trace-Id header during chain")
                .isNotNull()
                .isEqualTo(mdcDuringChain[0]);

        // Assert: 值是合法 UUID
        assertThat(UUID.fromString(mdcDuringChain[0])).isNotNull();

        // Assert: filter 返回后 MDC 已清
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void doFilter_clearsMdcEvenWhenDownstreamChainThrows() {
        // Arrange
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        jakarta.servlet.FilterChain throwingChain = (r, response) -> {
            // 链执行时 MDC 应已被设置
            assertThat(MDC.get("traceId")).isNotNull();
            throw new ServletException("simulated downstream failure");
        };

        // Act + Assert: 异常向上抛（OncePerRequestFilter 默认行为）
        assertThatThrownBy(() -> filter.doFilter(req, resp, throwingChain))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("simulated downstream failure");

        // Assert: 即使下游抛异常，MDC 仍被 finally 清空
        assertThat(MDC.get("traceId"))
                .as("MDC must be cleared even after downstream exception")
                .isNull();
    }
}
