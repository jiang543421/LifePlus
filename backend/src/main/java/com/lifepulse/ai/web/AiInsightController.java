package com.lifepulse.ai.web;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.service.AiInsightService;
import com.lifepulse.ai.web.dto.AiInsightResponse;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.common.web.MyResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 洞察端点（spec §6.2 / §7.4）。
 *
 * <p>两个端点：
 * <ul>
 *   <li>{@code GET  /api/v1/ai/insight/today}    — 拉取今日洞察，缓存优先（30 min TTL）</li>
 *   <li>{@code POST /api/v1/ai/insight/refresh}  — 强制刷新，跳缓存写新值（覆写同 key）</li>
 * </ul>
 *
 * <p>鉴权：{@link AuthenticationPrincipal} 拿 {@code userId}（{@code JwtAuthFilter}
 * 在解析后写入 principal）。未登录走 Spring Security {@code 401} 信封。
 *
 * <p>限流：
 * <ul>
 *   <li>GET 60 次/分钟（{@link AiConstants#INSIGHT_GET_RL_MAX}）</li>
 *   <li>POST 6 次/分钟（{@link AiConstants#INSIGHT_REFRESH_RL_MAX}）</li>
 *   <li>key 形式 {@code lp:rl:ai:insight:<userId>}，按用户维度</li>
 *   <li>超限返回 {@link ErrorCode#LOGIN_RATE_LIMIT}（1006，HTTP 429）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai/insight")
public class AiInsightController {

    private static final Logger log = LoggerFactory.getLogger(AiInsightController.class);

    private final AiInsightService service;
    private final RateLimiter rateLimiter;

    public AiInsightController(AiInsightService service, RateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 返回今日洞察。命中缓存直接返回，未命中触发重算并写缓存。
     */
    @GetMapping("/today")
    public MyResponse<AiInsightResponse> today(@AuthenticationPrincipal Long userId) {
        requireUserId(userId);
        enforceRateLimit(userId, AiConstants.INSIGHT_GET_RL_MAX, AiConstants.INSIGHT_GET_RL_WINDOW);
        AiInsightResponse r = service.getInsight(userId);
        return MyResponse.ok(withFreshness(r));
    }

    /**
     * 强制刷新：跳过缓存读，重算并覆写缓存。
     * 刷新后立即获得新值（手动场景，例如新增任务后想看新洞察）。
     */
    @PostMapping("/refresh")
    public MyResponse<AiInsightResponse> refresh(@AuthenticationPrincipal Long userId) {
        requireUserId(userId);
        enforceRateLimit(userId, AiConstants.INSIGHT_REFRESH_RL_MAX, AiConstants.INSIGHT_REFRESH_RL_WINDOW);
        log.info("AI insight manual refresh: userId={}", userId);
        AiInsightResponse r = service.refreshInsight(userId);
        return MyResponse.ok(withFreshness(r));
    }

    // ===== 私有辅助 =====

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

    /**
     * 限流闸门：Redis 计数 + 1，超过 max 抛 {@link ErrorCode#LOGIN_RATE_LIMIT}。
     * {@link RateLimiter} 自身失败关闭（Redis 不可达 → 视为超限），避免绕过。
     */
    private void enforceRateLimit(long userId, int max, Duration window) {
        String key = AiConstants.INSIGHT_RL_KEY_PREFIX + userId;
        if (rateLimiter.hit(key, max, window)) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMIT,
                "AI 洞察请求过于频繁，请稍后重试");
        }
    }

    /**
     * 现算 freshnessSeconds 替换缓存中固定为 0 的占位，避免读到"始终新鲜"的旧响应。
     * 返回新 record（CLAUDE.md §4.1 不可变性原则）。
     */
    private static AiInsightResponse withFreshness(AiInsightResponse r) {
        return new AiInsightResponse(
            r.headline(),
            r.chips(),
            r.generatedAt(),
            AiInsightService.freshnessSeconds(r)
        );
    }
}
