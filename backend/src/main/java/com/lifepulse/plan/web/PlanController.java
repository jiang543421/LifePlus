package com.lifepulse.plan.web;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.plan.PlanConstants;
import com.lifepulse.plan.dto.PlanCreateRequest;
import com.lifepulse.plan.dto.PlanFilter;
import com.lifepulse.plan.dto.PlanListItem;
import com.lifepulse.plan.dto.PlanResponse;
import com.lifepulse.plan.dto.PlanUpdateRequest;
import com.lifepulse.plan.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 计划端点（spec §5.4）。
 *
 * <p>5 个公开方法：
 * <ul>
 *   <li>{@code GET    /api/v1/plans} — 日历范围查 + 分页</li>
 *   <li>{@code GET    /api/v1/plans/{id}} — 详情</li>
 *   <li>{@code POST   /api/v1/plans} — 创建（返回 201）</li>
 *   <li>{@code PUT    /api/v1/plans/{id}} — 局部更新</li>
 *   <li>{@code DELETE /api/v1/plans/{id}} — 软删</li>
 * </ul>
 *
 * <p>{@code from/to} 接受 ISO-8601 datetime 字符串（如 {@code 2026-08-15T10:00:00}），
 * 由 {@link DateTimeFormat} 解析为 {@link LocalDateTime}。
 *
 * <p>分页参数 {@code page/size} 不在方法签名加 {@code @Min/@Max}（与 {@code TaskController} 同款），
 * 改为方法体内显式校验抛 {@link BusinessException} 1001。
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    // ---------- list ----------

    @GetMapping
    public MyResponse<PageResponse<PlanListItem>> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePage(page, size);

        PlanFilter filter = new PlanFilter(from, to, page, size);
        return MyResponse.ok(service.pageByUser(filter));
    }

    // ---------- get ----------

    @GetMapping("/{id}")
    public MyResponse<PlanResponse> get(@PathVariable long id) {
        return MyResponse.ok(service.getById(id));
    }

    // ---------- create ----------

    @PostMapping
    public ResponseEntity<MyResponse<PlanResponse>> create(@Valid @RequestBody PlanCreateRequest req) {
        PlanResponse created = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(MyResponse.ok(created));
    }

    // ---------- update ----------

    @PutMapping("/{id}")
    public MyResponse<Void> update(@PathVariable long id,
                                   @Valid @RequestBody PlanUpdateRequest req) {
        service.update(id, req);
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
            throw new BusinessException(ErrorCode.VALIDATION, "page must be >= 1");
        }
        if (size < 1 || size > PlanConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "size must be in [1, " + PlanConstants.MAX_PAGE_SIZE + "]");
        }
    }
}