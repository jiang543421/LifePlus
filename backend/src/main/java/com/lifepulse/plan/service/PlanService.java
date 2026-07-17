package com.lifepulse.plan.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.plan.dto.PlanCreateRequest;
import com.lifepulse.plan.dto.PlanFilter;
import com.lifepulse.plan.dto.PlanListItem;
import com.lifepulse.plan.dto.PlanResponse;
import com.lifepulse.plan.dto.PlanUpdateRequest;
import com.lifepulse.plan.entity.Plan;
import com.lifepulse.plan.repository.PlanMapper;
import com.lifepulse.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 计划服务（spec §5.4）。
 *
 * <p>所有公开方法以 {@link UserContext#current()} 取当前用户 id；
 * 跨用户越权（不存在/不属于当前 user/已软删）一律抛
 * {@link BusinessException}{@code (ErrorCode.CROSS_USER)}，禁止用
 * {@code Optional.empty()} 隐式掩盖（CLAUDE.md §4.5）。
 *
 * <p><b>all_day 归一化</b>（CLAUDE.md Phase 3 决策）：{@code create} 与
 * {@code update} 中若 {@code allDay=1}，归一化
 * {@code startTime → 当日 00:00:00}、{@code endTime → 当日 23:59:59}，
 * 避免前端 el-date-picker 在跨日交互中上传非规整时间。
 *
 * <p>构造器显式注入（与 {@code TaskService} 同款），便于单测手写 mock。
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    /** allDay = 1 的字面值，与 PlanConstants / DB DEFAULT 0 对齐。 */
    private static final int ALL_DAY = 1;

    private final PlanMapper mapper;

    public PlanService(PlanMapper mapper) {
        this.mapper = mapper;
    }

    // ---------- create ----------

    /**
     * 创建计划：当前 user 即归属者；未登录（防御性）→ 抛 1002。
     * 跨字段校验 {@code endTime > startTime} 在 service 层完成（spec §2.3）。
     */
    public PlanResponse create(PlanCreateRequest req) {
        Long userId = requireUserId();
        validateTimeRange(req.startTime(), req.endTime());

        Plan p = new Plan();
        p.setUserId(userId);
        p.setTitle(req.title());
        p.setStartTime(req.startTime());
        p.setEndTime(req.endTime());
        p.setAllDay(req.allDay() == null ? 0 : req.allDay());
        p.setLocation(req.location());
        p.setNote(req.note());
        p.setReminderMin(req.reminderMin()); // null → DB DEFAULT 15

        normalizeAllDay(p);

        mapper.insert(p);
        log.debug("plan created uid={} id={}", userId, p.getId());
        return PlanResponse.from(p);
    }

    // ---------- getById ----------

    /**
     * 按 id 取计划详情；跨用户/不存在/已软删 → 抛 1003（与 Task 同款：MVP1 不拆分
     * 跨用户 vs 资源不存在，统一用 1003 防枚举）。
     */
    public PlanResponse getById(long id) {
        Long userId = requireUserId();
        return mapper.findByUserAndId(userId, id)
                .map(PlanResponse::from)
                .orElseThrow(() -> {
                    log.warn("plan get cross-user or missing uid={} id={}", userId, id);
                    return new BusinessException(ErrorCode.CROSS_USER, "无权操作该计划");
                });
    }

    // ---------- update ----------

    /**
     * 局部更新：所有字段为 {@code null} 时跳过；其他值覆盖原值。
     * 跨用户/不存在 → 抛 1003。{@code allDay=1} 时会归一化 start/end。
     */
    public void update(long id, PlanUpdateRequest req) {
        Long userId = requireUserId();
        Plan p = mapper.findByUserAndId(userId, id)
                .orElseThrow(() -> {
                    log.warn("plan update cross-user or missing uid={} id={}", userId, id);
                    return new BusinessException(ErrorCode.CROSS_USER, "无权操作该计划");
                });

        if (req.title() != null) p.setTitle(req.title());
        if (req.startTime() != null) p.setStartTime(req.startTime());
        if (req.endTime() != null) p.setEndTime(req.endTime());
        if (req.allDay() != null) p.setAllDay(req.allDay());
        if (req.location() != null) p.setLocation(req.location());
        if (req.note() != null) p.setNote(req.note());
        if (req.reminderMin() != null) p.setReminderMin(req.reminderMin());

        validateTimeRange(p.getStartTime(), p.getEndTime());
        normalizeAllDay(p);

        mapper.updateById(p);
        log.debug("plan updated uid={} id={}", userId, id);
    }

    // ---------- softDelete ----------

    /**
     * 软删（{@code @TableLogic} 触发 SQL 改 {@code deleted=1}）；先校验所有权。
     * 跨用户/不存在 → 1003。
     */
    public void softDelete(long id) {
        Long userId = requireUserId();
        Plan p = mapper.findByUserAndId(userId, id)
                .orElseThrow(() -> {
                    log.warn("plan softDelete cross-user or missing uid={} id={}", userId, id);
                    return new BusinessException(ErrorCode.CROSS_USER, "无权操作该计划");
                });
        mapper.deleteById(p.getId());
        log.debug("plan softDelete uid={} id={}", userId, id);
    }

    // ---------- pageByUser ----------

    /**
     * 日历范围分页查询；{@code from/to} 在 SQL 中以 {@code IS NULL} 跳过。
     *
     * @return {@link PageResponse} 含不可变 items 视图
     */
    public PageResponse<PlanListItem> pageByUser(PlanFilter f) {
        Long userId = requireUserId();
        int offset = (f.page() - 1) * f.size();
        List<Plan> rows = mapper.pageByUser(userId, f.from(), f.to(), offset, f.size());
        long total = mapper.countByUser(userId, f.from(), f.to());
        List<PlanListItem> items = rows.stream().map(PlanListItem::from).toList();
        return PageResponse.of(items, total, f.page(), f.size());
    }

    // ---------- private helpers ----------

    /**
     * 取当前 userId；filter 漏检时（防御性）抛 1002。
     * 正常路径下 filter 已确保设置，service 仅兜底。
     */
    private Long requireUserId() {
        Long userId = UserContext.current();
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
        return userId;
    }

    /**
     * spec §2.3：{@code end_time > start_time} 在 service 层校验。
     * 任一为 {@code null} 也视为非法（防御 controller 层 {@code @NotNull} 漏检）。
     */
    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.VALIDATION, "结束时间必须晚于开始时间");
        }
    }

    /**
     * MVP1 决策：{@code all_day=1} 时归一化为当日 00:00:00 / 23:59:59。
     * 跨日事件仍按一次事件存，CalendarMonth 跨日连续渲染。
     */
    private void normalizeAllDay(Plan p) {
        if (p.getAllDay() != null && p.getAllDay() == ALL_DAY) {
            p.setStartTime(p.getStartTime().toLocalDate().atStartOfDay());
            p.setEndTime(p.getEndTime().toLocalDate().atTime(23, 59, 59));
        }
    }
}