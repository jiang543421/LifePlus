package com.lifepulse.diet.web;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.diet.DietConstants;
import com.lifepulse.diet.dto.CreateDietRequest;
import com.lifepulse.diet.dto.DietFrequentItem;
import com.lifepulse.diet.dto.DietFilter;
import com.lifepulse.diet.dto.DietPageResponse;
import com.lifepulse.diet.dto.DietResponse;
import com.lifepulse.diet.dto.DietSummary;
import com.lifepulse.diet.dto.UpdateDietRequest;
import com.lifepulse.diet.service.DietService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 饮食端点（spec 07-diet-design §5）。
 *
 * <p>7 个公开方法：
 * <ul>
 *   <li>{@code GET    /api/v1/diets} — 过滤 + 分页（mealType / from / to）</li>
 *   <li>{@code GET    /api/v1/diets/{id}} — 详情</li>
 *   <li>{@code POST   /api/v1/diets} — 创建（返回 201）</li>
 *   <li>{@code PATCH  /api/v1/diets/{id}} — 局部更新（字段全 nullable）</li>
 *   <li>{@code DELETE /api/v1/diets/{id}} — 软删</li>
 *   <li>{@code GET    /api/v1/diets/summary?date=YYYY-MM-DD} — 当日营养汇总</li>
 *   <li>{@code GET    /api/v1/diets/frequent?from=&to=&limit=} — 高频名称聚合</li>
 * </ul>
 *
 * <p>{@code from/to} 接受 ISO-8601 含时区；{@code date} 用 {@link LocalDate}。
 * 与 ExpenseController 同款：page / size / date 校验在控制器层做，
 * 转 1001 而非 {@code ConstraintViolationException} 全局 1500。
 */
@RestController
@RequestMapping("/api/v1/diets")
public class DietController {

    private final DietService service;

    public DietController(DietService service) {
        this.service = service;
    }

    // ---------- list ----------

    @GetMapping
    public MyResponse<DietPageResponse> list(
            @RequestParam(required = false) String mealType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePage(page, size);
        DietFilter filter = new DietFilter(mealType, from, to, page, size);
        return MyResponse.ok(service.list(filter));
    }

    // ---------- get ----------

    @GetMapping("/{id}")
    public MyResponse<DietResponse> get(@PathVariable long id) {
        return MyResponse.ok(service.getById(id));
    }

    // ---------- create ----------

    @PostMapping
    public ResponseEntity<MyResponse<DietResponse>> create(@Valid @RequestBody CreateDietRequest req) {
        DietResponse created = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(MyResponse.ok(created));
    }

    // ---------- update (PATCH: all fields nullable) ----------

    @PatchMapping("/{id}")
    public MyResponse<Void> update(@PathVariable long id,
                                   @Valid @RequestBody UpdateDietRequest req) {
        service.update(id, req);
        return MyResponse.ok(null);
    }

    // ---------- delete ----------

    @DeleteMapping("/{id}")
    public MyResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return MyResponse.ok(null);
    }

    // ---------- summary ----------

    @GetMapping("/summary")
    public MyResponse<DietSummary> summary(@RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                          LocalDate date) {
        if (date == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "date 不能为空");
        }
        return MyResponse.ok(service.summary(date));
    }

    // ---------- frequent ----------

    @GetMapping("/frequent")
    public MyResponse<List<DietFrequentItem>> frequent(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Integer limit) {
        return MyResponse.ok(service.frequent(from, to, limit));
    }

    // ---------- private helpers ----------

    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION, "page must be >= 1");
        }
        if (size < 1 || size > DietConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "size must be in [1, " + DietConstants.MAX_PAGE_SIZE + "]");
        }
    }
}