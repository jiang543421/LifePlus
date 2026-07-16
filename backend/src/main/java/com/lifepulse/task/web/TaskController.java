package com.lifepulse.task.web;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.task.TaskConstants;
import com.lifepulse.task.dto.TaskCreateRequest;
import com.lifepulse.task.dto.TaskFilter;
import com.lifepulse.task.dto.TaskListItem;
import com.lifepulse.task.dto.TaskResponse;
import com.lifepulse.task.dto.TaskStatusRequest;
import com.lifepulse.task.dto.TaskUpdateRequest;
import com.lifepulse.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务端点（spec §5.3）。
 *
 * <p>7 个公开方法：
 * <ul>
 *   <li>{@code GET    /api/v1/tasks} — 过滤 + 分页</li>
 *   <li>{@code GET    /api/v1/tasks/by-plan/{planId}} — 按 plan 聚合</li>
 *   <li>{@code GET    /api/v1/tasks/{id}} — 详情</li>
 *   <li>{@code POST   /api/v1/tasks} — 创建（返回 201）</li>
 *   <li>{@code PUT    /api/v1/tasks/{id}} — 局部更新</li>
 *   <li>{@code PATCH  /api/v1/tasks/{id}/status} — 状态切换</li>
 *   <li>{@code DELETE /api/v1/tasks/{id}} — 软删</li>
 * </ul>
 *
 * <p>分页参数 {@code page/size} 不在方法签名加 {@code @Min/@Max}（避免引入
 * {@code @Validated} + {@code ConstraintViolationException} 全局处理）；
 * 改为方法体内显式校验抛 {@link BusinessException} 1001。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    // ---------- list ----------

    @GetMapping
    public MyResponse<PageResponse<TaskListItem>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePage(page, size);

        TaskFilter filter = new TaskFilter(status, priority, tag, dueFrom, dueTo, page, size);
        return MyResponse.ok(service.pageByUser(filter));
    }

    // ---------- byPlan ----------

    @GetMapping("/by-plan/{planId}")
    public MyResponse<List<TaskListItem>> byPlan(@PathVariable long planId) {
        return MyResponse.ok(service.listByPlan(planId));
    }

    // ---------- get ----------

    @GetMapping("/{id}")
    public MyResponse<TaskResponse> get(@PathVariable long id) {
        return MyResponse.ok(service.getById(id));
    }

    // ---------- create ----------

    @PostMapping
    public ResponseEntity<MyResponse<TaskResponse>> create(@Valid @RequestBody TaskCreateRequest req) {
        TaskResponse created = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(MyResponse.ok(created));
    }

    // ---------- update ----------

    @PutMapping("/{id}")
    public MyResponse<Void> update(@PathVariable long id,
                                   @Valid @RequestBody TaskUpdateRequest req) {
        service.update(id, req);
        return MyResponse.ok(null);
    }

    // ---------- patchStatus ----------

    @PatchMapping("/{id}/status")
    public MyResponse<Void> patchStatus(@PathVariable long id,
                                        @Valid @RequestBody TaskStatusRequest req) {
        service.patchStatus(id, req.status());
        return MyResponse.ok(null);
    }

    // ---------- delete ----------

    @DeleteMapping("/{id}")
    public MyResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return MyResponse.ok(null);
    }

    // ---------- private helpers ----------

    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw new BusinessException(AuthConstants.ERR_VALIDATION, "page must be >= 1");
        }
        if (size < 1 || size > TaskConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(AuthConstants.ERR_VALIDATION,
                    "size must be in [1, " + TaskConstants.MAX_PAGE_SIZE + "]");
        }
    }
}