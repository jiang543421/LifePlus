package com.lifepulse.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 traceId 入口 filter（plan §3 / §6.3；spec §03 §8）。
 *
 * <p>行为：
 * <ol>
 *   <li>每个请求生成一个 UUID，作为本链路的 traceId。</li>
 *   <li>写入 SLF4J {@code MDC("traceId")}（供日志格式 {@code %X{traceId}} 与
 *       {@code MyResponse.error(...).traceId()} 读取）。</li>
 *   <li>写入响应头 {@code X-Trace-Id}（供前端调试）。</li>
 *   <li>{@code finally} 中清理 MDC，避免线程复用导致跨请求污染（CLAUDE.md §4.1）。</li>
 * </ol>
 *
 * <p>扩展性：未来若需要在响应体 envelope 的 {@code traceId} 字段回填，filter
 * 自身不参与序列化；由 {@link GlobalExceptionHandler} 与 controller 直接
 * {@code MyResponse.ok(...)} 时通过 MDC 读出（本计划 §6.3 已落实）。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /** SLF4J MDC key；与 {@code logback-spring.xml} 中 {@code %X{traceId}} 对齐。 */
    public static final String MDC_KEY = "traceId";

    /** 响应头名；前端调试与排查用。 */
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
