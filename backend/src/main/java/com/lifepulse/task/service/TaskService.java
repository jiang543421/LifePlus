package com.lifepulse.task.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.security.UserContext;
import com.lifepulse.task.dto.TaskCreateRequest;
import com.lifepulse.task.dto.TaskFilter;
import com.lifepulse.task.dto.TaskListItem;
import com.lifepulse.task.dto.TaskResponse;
import com.lifepulse.task.dto.TaskUpdateRequest;
import com.lifepulse.task.entity.Task;
import com.lifepulse.task.repository.TaskMapper;
import com.lifepulse.plan.repository.PlanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务服务（spec §5.3）。
 *
 * <p>所有公开方法以 {@link UserContext#current()} 取当前用户 id；
 * 跨用户越权（不存在/不属于当前 user/已软删）一律抛
 * {@link BusinessException}{@code (ErrorCode.CROSS_USER)}，禁止用
 * {@code Optional.empty()} 隐式掩盖（CLAUDE.md §4.5）。
 *
 * <p>构造器显式注入（与 {@code AuthService} 同款），便于单测手写 mock；
 * 不使用 {@code @RequiredArgsConstructor} 以保持与项目既有 service 一致。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskMapper mapper;
    private final PlanMapper planMapper;

    public TaskService(TaskMapper mapper, PlanMapper planMapper) {
        this.mapper = mapper;
        this.planMapper = planMapper;
    }


    /**
     * 创建任务：当前 user 即归属者；可选字段 {@code null} 跳过 → DB DEFAULT。
     * 未登录（{@code UserContext.current() == null}）→ 抛 1002（防御性，filter 漏检时）。
     */
    public TaskResponse create(TaskCreateRequest req) {
        Long userId = requireUserId();
        requireOwnedPlan(userId, req.planId());

        Task t = new Task();
        t.setUserId(userId);
        t.setTitle(req.title());
        // status: 不设 → DB DEFAULT 0 (待办)
        if (req.priority() != null) t.setPriority(req.priority());
        t.setDueDate(req.dueDate());
        t.setTag(req.tag());
        t.setPlanId(req.planId());
        // priority 默认 0 → 未设时由 DB DEFAULT 处理

        mapper.insert(t);
        log.debug("task created uid={} id={}", userId, t.getId());
        return TaskResponse.from(t);
    }


    /**
     * 按 id 取任务详情；跨用户/不存在/已软删 → 抛 1003（与"不存在"统一码，
     * 防枚举 — 但 spec 要求跨用户用 1003，缺失用 1004；MVP1 全部按 1003 处理，
     * 简化错误面；Phase 3 视情况拆分）。
     */
    public TaskResponse getById(long id) {
        Long userId = requireUserId();
        return mapper.findByUserAndId(userId, id)
                .map(TaskResponse::from)
                .orElseThrow(() -> {
                    log.warn("task get cross-user or missing uid={} id={}", userId, id);
                    return new BusinessException(ErrorCode.CROSS_USER, "无权操作该任务");
                });
    }


    /**
     * 局部更新：所有字段为 {@code null} 时跳过；其他值覆盖原值。
     * 跨用户/不存在 → 抛 1003。
     */
    public void update(long id, TaskUpdateRequest req) {
        Long userId = requireUserId();
        Task t = mapper.findByUserAndId(userId, id)
                .orElseThrow(() -> {
                    log.warn("task update cross-user or missing uid={} id={}", userId, id);
                    return new BusinessException(ErrorCode.CROSS_USER, "无权操作该任务");
                });

        if (req.title() != null) t.setTitle(req.title());
        if (req.status() != null) t.setStatus(req.status());
        if (req.priority() != null) t.setPriority(req.priority());
        if (req.dueDate() != null) t.setDueDate(req.dueDate());
        if (req.tag() != null) t.setTag(req.tag());
        // planId：若请求提供新 planId，先校验归属；MVP1 不支持显式"清空 plan 关联"
        // 操作（spec §5.3 PUT 仅描述字段变更，未涉及清空语义）；null → 保留旧值。
        if (req.planId() != null) {
            requireOwnedPlan(userId, req.planId());
            t.setPlanId(req.planId());
        }

        mapper.updateById(t);
        log.debug("task updated uid={} id={}", userId, id);
    }


    /**
     * 状态切换（专用端点）；用 {@link TaskMapper#updateStatusByUser} 一次 SQL 命中，
     * 0 行 → 1003（不存在 / 跨用户 / 已软删的合并视图）。
     */
    public void patchStatus(long id, int status) {
        Long userId = requireUserId();
        int rows = mapper.updateStatusByUser(userId, id, status);
        if (rows == 0) {
            log.warn("task patchStatus no-match uid={} id={} status={}", userId, id, status);
            throw new BusinessException(ErrorCode.CROSS_USER, "无权操作该任务");
        }
        log.debug("task patchStatus uid={} id={} status={}", userId, id, status);
    }


    /**
     * 软删（{@code @TableLogic} 触发 SQL 改 {@code deleted=1}）；先校验所有权。
     * 跨用户/不存在 → 1003。
     */
    public void softDelete(long id) {
        Long userId = requireUserId();
        Task t = mapper.findByUserAndId(userId, id)
                .orElseThrow(() -> {
                    log.warn("task softDelete cross-user or missing uid={} id={}", userId, id);
                    return new BusinessException(ErrorCode.CROSS_USER, "无权操作该任务");
                });
        mapper.deleteById(t.getId());
        log.debug("task softDelete uid={} id={}", userId, id);
    }


    /**
     * 列出某 plan 下的任务；按 {@code user_id + plan_id} 隔离；空 plan 返回空列表。
     */
    public List<TaskListItem> listByPlan(long planId) {
        Long userId = requireUserId();
        return mapper.listByPlan(userId, planId).stream()
                .map(TaskListItem::from)
                .toList();
    }


    /**
     * 分页查询；过滤参数 {@code null} 在 SQL 中以 {@code IS NULL} 跳过（详见 mapper）。
     *
     * @return {@link PageResponse} 含不可变 items 视图
     */
    public PageResponse<TaskListItem> pageByUser(TaskFilter f) {
        Long userId = requireUserId();
        int offset = (f.page() - 1) * f.size();
        List<Task> rows = mapper.pageByUser(
                userId, f.status(), f.priority(), f.tag(),
                f.dueFrom(), f.dueTo(), offset, f.size());
        long total = mapper.countByUser(
                userId, f.status(), f.priority(), f.tag(),
                f.dueFrom(), f.dueTo());
        List<TaskListItem> items = rows.stream().map(TaskListItem::from).toList();
        return PageResponse.of(items, total, f.page(), f.size());
    }


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
     * 校验 plan 归属（CLAUDE.md §7.2 跨用户越权硬门）。
     * {@code planId == null} 表示不关联 plan，跳过校验。
     * 跨用户 / 不存在 / 已软删 → 抛 1003，与 service 其它方法语义一致。
     */
    private void requireOwnedPlan(Long userId, Long planId) {
        if (planId == null) return;
        planMapper.findByUserAndId(userId, planId).orElseThrow(() -> {
            log.warn("task plan cross-user uid={} planId={}", userId, planId);
            return new BusinessException(ErrorCode.CROSS_USER, "无权关联该日程");
        });
    }
}
