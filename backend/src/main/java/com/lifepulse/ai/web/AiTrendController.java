package com.lifepulse.ai.web;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.service.AiTrendService;
import com.lifepulse.ai.web.dto.AiTrendResponse;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.common.web.MyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 趋势图端点（spec §v2.2 trend / CLAUDE.md §11）。
 *
 * <p><b>端点</b>：{@code GET /api/v1/ai/insight/trend?window=7|14|30}（默认 14）。
 *
 * <p><b>鉴权</b>：{@link AuthenticationPrincipal} 拿 {@code userId}（{@code JwtAuthFilter}
 * 在解析后写入 principal）。未登录走 Spring Security {@code 401} 信封。
 *
 * <p><b>限流</b>：30 次/分钟/用户（{@link AiConstants#TREND_RL_MAX}），key 形式
 * {@code lp:rl:ai:trend:<userId>}。超限返回 {@link ErrorCode#LOGIN_RATE_LIMIT}（1006）。
 *
 * <p><b>缓存</b>：6h TTL（{@link AiConstants#TREND_CACHE_TTL_SECONDS}），key 形式
 * {@code lp:ai:trend:<userId>:<windowDays>}。Redis 不可达时 fail-open 走纯计算。
 *
 * <p><b>越权保护</b>：{@code userId} 由 JWT 解析，禁止客户端传入；CLAUDE.md §11.1 约束 6。
 */
@RestController
@RequestMapping("/api/v1/ai/insight")
public class AiTrendController {

    private static final Logger log = LoggerFactory.getLogger(AiTrendController.class);

    private final AiTrendService service;
    private final RateLimiter rateLimiter;

    public AiTrendController(AiTrendService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 返回指定窗口的趋势数据。
     *
     * @param userId 当前登录用户（JWT 解析）
     * @param window 时间窗天数，必须 ∈ {@link AiConstants#TREND_WINDOWS}；默认 14
     * @return 4 槽 series（diet 永久空数组）
     * @throws BusinessException 1001 当 window 不在 {7, 14, 30}
     * @throws BusinessException 1006 当触发 30/min/user 限流
     */
    @GetMapping("/trend")
    public MyResponse<AiTrendResponse> trend(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "window", required = false,
                    defaultValue = "" + AiConstants.TREND_DEFAULT_WINDOW) int window) {
        requireUserId(userId);
        if (!AiConstants.TREND_WINDOWS.contains(window)) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "window must be one of " + AiConstants.TREND_WINDOWS + ", got " + window);
        }
        String rlKey = AiConstants.TREND_RL_KEY_PREFIX + userId;
        if (rateLimiter.hit(rlKey, AiConstants.TREND_RL_MAX, AiConstants.TREND_RL_WINDOW)) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMIT,
                    "AI 趋势请求过于频繁，请稍后重试");
        }
        AiTrendResponse r = service.getTrend(userId, window);
        log.debug("AI trend served: userId={}, window={}", userId, window);
        return MyResponse.ok(r);
    }

    /**
     * 鉴权兜底：理论上 {@link AuthenticationPrincipal} 不会为 {@code null}（{@code anyRequest()
     * .authenticated()} 已挡），但保留 null-check 防止 Security 关闭的测试场景漏过。
     */
    private static Long requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
        return userId;
    }
}