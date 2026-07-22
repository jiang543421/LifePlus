package com.lifepulse.daily.web;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.WeeklyReportPayload;
import com.lifepulse.daily.service.DailyReportService;
import com.lifepulse.security.UserContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 日报 / 周报 HTTP 端点（plan §5 T7 / spec §5）。
 *
 * <p>2 个公开方法：
 * <ul>
 *   <li>{@code GET /api/v1/daily}        — 单日日报，{@code ?date=YYYY-MM-DD} 可选（缺省=今日）</li>
 *   <li>{@code GET /api/v1/daily/week}   — 周报，{@code ?date=YYYY-MM-DD} 可选（缺省取任意今日，
 *       服务内部按 ISO 周对齐到周一首日）</li>
 * </ul>
 *
 * <p><b>鉴权</b>：从 {@link UserContext#current()} 取已认证 userId 传给 Service。
 * Spring Security 链（{@code SecurityConfig}）已先一步拦截未认证请求，未认证会直接走
 * {@code JwtAuthEntryPoint} 出 401 信封——本控制器不重复判断"是否登录"。
 * 但仍做防御：万一 filter 链被旁路（例如单测 stub），
 * {@code userId == null} 即抛 {@link ErrorCode#BAD_CREDENTIALS}（1002）。
 *
 * <p><b>日期容错</b>：{@code date} 由 Spring 的
 * {@link DateTimeFormat} 解析，格式非法（如 {@code "not-a-date"}）会抛
 * {@code MethodArgumentTypeMismatchException}，由全局
 * {@code GlobalExceptionHandler} 映射为 400 + code=1001；本控制器不重复校验。
 * {@code date} 缺省=今日。{@code date} 超 {@code MAX_HISTORY_DAYS=30} 天窗口
 * 由 Service 抛 1001；未来日期允许（与 spec §6 / Service contract 一致）。
 *
 * <p><b>跨用户防御</b>：不在 Controller 层重复过滤；Service 通过 {@code userId}
 * 入参做所有 Provider 聚合，天然按 user_id 隔离。
 *
 * <p><b>构造器</b>：显式注入 Service（与 {@code TaskController} / {@code PlanController} 同款）。
 */
@RestController
@RequestMapping("/api/v1/daily")
public class DailyReportController {

    private final DailyReportService service;

    public DailyReportController(DailyReportService service) {
        this.service = service;
    }

    /**
     * 单日日报。
     *
     * <p>{@code date} 缺省 = 今日（Service 内部 {@code LocalDate.now(Asia/Shanghai)}）。
     */
    @GetMapping
    public MyResponse<DailyReportPayload> daily(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        long userId = requireUserId();
        LocalDate target = date != null ? date : service.today();
        return MyResponse.ok(service.daily(userId, target));
    }

    /**
     * 周报（含与上周对比）。
     *
     * <p>{@code date} 缺省 = 今日；Service 内部按 ISO 周对齐到周一首日（即
     * {@code previousOrSame(MONDAY)}），周日为 weekEnd。
     *
     * <p>对应 service 方法 {@link DailyReportService#week(long, LocalDate)}：
     * 7 天逐日 × 4 Provider × 2 周 = 56 次 mapper 调用，
     * 性能预算 P95 < 300ms（{@code WEEKLY_P95_BUDGET}）由 T8 IT 验证。
     */
    @GetMapping("/week")
    public MyResponse<WeeklyReportPayload> week(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        long userId = requireUserId();
        LocalDate target = date != null ? date : service.today();
        return MyResponse.ok(service.week(userId, target));
    }

    /**
     * 防御性 userId 校验：理论上 Spring Security 链会在请求到达此方法前完成认证，
     * 缺失则走 401 信封。但生产代码不做 defensive throw 就有可能在测试场景下崩
     * NullPointerException，违反 CLAUDE.md §4.5"必须显式处理错误"。
     */
    private static long requireUserId() {
        Long uid = UserContext.current();
        if (uid == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "missing authenticated user");
        }
        return uid;
    }
}
