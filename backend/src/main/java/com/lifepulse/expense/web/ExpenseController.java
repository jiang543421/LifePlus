package com.lifepulse.expense.web;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.ExpenseConstants;
import com.lifepulse.expense.dto.CategoryItem;
import com.lifepulse.expense.dto.CreateExpenseRequest;
import com.lifepulse.expense.dto.ExpenseFilter;
import com.lifepulse.expense.dto.ExpenseListItem;
import com.lifepulse.expense.dto.ExpenseResponse;
import com.lifepulse.expense.dto.ExpenseSummaryResponse;
import com.lifepulse.expense.dto.UpdateExpenseRequest;
import com.lifepulse.expense.service.ExpenseService;
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

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 消费端点（spec §5）。
 *
 * <p>7 个公开方法：
 * <ul>
 *   <li>{@code GET    /api/v1/expenses} — 过滤 + 分页（category / from / to）</li>
 *   <li>{@code GET    /api/v1/expenses/{id}} — 详情</li>
 *   <li>{@code POST   /api/v1/expenses} — 创建（返回 201）</li>
 *   <li>{@code PATCH  /api/v1/expenses/{id}} — 局部更新（字段全 nullable）</li>
 *   <li>{@code DELETE /api/v1/expenses/{id}} — 软删</li>
 *   <li>{@code GET    /api/v1/expenses/summary?year=&month=} — 月度汇总</li>
 *   <li>{@code GET    /api/v1/expenses/categories} — 分类元数据</li>
 * </ul>
 *
 * <p>{@code from/to} 接受 ISO-8601 含时区（如 {@code 2026-07-15T10:00:00Z}），
 * 与 {@link OffsetDateTime} / DB DATETIME(3) UTC 一致；year/month 用方法体内显式校验，
 * 与 TaskController / PlanController 同款（{@code @Validated} 跨字段校验会引入
 * {@code ConstraintViolationException} 全局处理，刻意不引入）。
 */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService service;

    public ExpenseController(ExpenseService service) {
        this.service = service;
    }


    @GetMapping
    public MyResponse<PageResponse<ExpenseListItem>> list(
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        validatePage(page, size);
        ExpenseFilter filter = new ExpenseFilter(category, from, to, page, size);
        return MyResponse.ok(service.list(filter));
    }


    @GetMapping("/{id}")
    public MyResponse<ExpenseResponse> get(@PathVariable long id) {
        return MyResponse.ok(service.getById(id));
    }


    @PostMapping
    public ResponseEntity<MyResponse<ExpenseResponse>> create(@Valid @RequestBody CreateExpenseRequest req) {
        ExpenseResponse created = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(MyResponse.ok(created));
    }


    @PatchMapping("/{id}")
    public MyResponse<Void> update(@PathVariable long id,
                                   @Valid @RequestBody UpdateExpenseRequest req) {
        service.update(id, req);
        return MyResponse.ok(null);
    }


    @DeleteMapping("/{id}")
    public MyResponse<Void> delete(@PathVariable long id) {
        service.softDelete(id);
        return MyResponse.ok(null);
    }


    @GetMapping("/summary")
    public MyResponse<ExpenseSummaryResponse> summary(@RequestParam int year,
                                                     @RequestParam int month) {
        // YearMonth.of 会因 month>12 抛 DateTimeException 落到 1500；先在控制器层
        // 转 1001（与 TaskController.validatePage / summary 服务层契约同款）。
        if (year < 1900 || year > 9999 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION, "year/month 非法");
        }
        return MyResponse.ok(service.summary(year, month));
    }


    @GetMapping("/categories")
    public MyResponse<List<CategoryItem>> categories() {
        return MyResponse.ok(service.categories());
    }


    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw new BusinessException(ErrorCode.VALIDATION, "page must be >= 1");
        }
        if (size < 1 || size > ExpenseConstants.MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "size must be in [1, " + ExpenseConstants.MAX_PAGE_SIZE + "]");
        }
    }
}