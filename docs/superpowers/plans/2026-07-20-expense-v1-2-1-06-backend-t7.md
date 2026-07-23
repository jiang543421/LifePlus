### T7. ExpenseController + @WebMvcTest

**Files:**
- Create: `backend/src/main/java/com/lifepulse/expense/web/ExpenseController.java`
- Test: `backend/src/test/java/com/lifepulse/expense/web/ExpenseControllerWebTest.java`

**Interfaces:**
- Consumes: T6 (ExpenseService)
- Produces: 7 HTTP endpoints

- [ ] **Step 1**: 创建 controller

```java
package com.lifepulse.expense.web;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.expense.ExpenseConstants;
import com.lifepulse.expense.dto.*;
import com.lifepulse.expense.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

  private final ExpenseService service;

  public ExpenseController(ExpenseService service) {
    this.service = service;
  }

  @GetMapping
  public MyResponse<PageResponse<ExpenseResponse>> list(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    validatePage(page, size);
    return MyResponse.ok(service.list(new ExpenseFilter(category, from, to, page, size)));
  }

  @GetMapping("/{id}")
  public MyResponse<ExpenseResponse> get(@PathVariable long id) {
    return MyResponse.ok(service.getById(id));
  }

  @PostMapping
  public ResponseEntity<MyResponse<ExpenseResponse>> create(@Valid @RequestBody CreateExpenseRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(MyResponse.ok(service.create(req)));
  }

  @PatchMapping("/{id}")
  public MyResponse<Void> update(@PathVariable long id, @Valid @RequestBody UpdateExpenseRequest req) {
    service.update(id, req);
    return MyResponse.ok(null);
  }

  @DeleteMapping("/{id}")
  public MyResponse<Void> delete(@PathVariable long id) {
    service.softDelete(id);
    return MyResponse.ok(null);
  }

  @GetMapping("/summary")
  public MyResponse<ExpenseSummary> summary(@RequestParam int year, @RequestParam int month) {
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
    if (page < 1) throw new BusinessException(ErrorCode.VALIDATION, "page must be >= 1");
    if (size < 1 || size > ExpenseConstants.MAX_PAGE_SIZE) {
      throw new BusinessException(ErrorCode.VALIDATION,
          "size must be in [1, " + ExpenseConstants.MAX_PAGE_SIZE + "]");
    }
  }
}
```

- [ ] **Step 2**: 写 WebMvc test（核心覆盖）

```java
package com.lifepulse.expense.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.expense.ExpenseConstants;
import com.lifepulse.expense.dto.CreateExpenseRequest;
import com.lifepulse.expense.dto.UpdateExpenseRequest;
import com.lifepulse.expense.service.ExpenseService;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.security.JwtAuthFilter;
import com.lifepulse.security.UserContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExpenseController.class)
@Import({JwtAuthFilter.class, com.lifepulse.common.web.GlobalExceptionHandler.class})
class ExpenseControllerWebTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockBean ExpenseService service;

  @org.springframework.boot.test.context.TestConfiguration
  static class StubFilterConfig {
    @org.springframework.context.annotation.Bean
    JwtAuthFilter jwtAuthFilter() {
      return new JwtAuthFilter() {
        @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest req,
                                                    jakarta.servlet.http.HttpServletResponse resp,
                                                    jakarta.servlet.FilterChain chain)
            throws java.io.IOException, jakarta.servlet.ServletException {
          if (req.getHeader("X-Test-User") != null) {
            UserContext.set(Long.parseLong(req.getHeader("X-Test-User")));
          }
          chain.doFilter(req, resp);
          UserContext.clear();
        }
      };
    }
  }

  private static final String AUTH = "X-Test-User: 1";

  @Test void list_unauthorized_returns1002() throws Exception {
    mvc.perform(get("/api/v1/expenses")).andExpect(status().isUnauthorized());
  }
  @Test void list_happy_returns200() throws Exception {
    when(service.list(any())).thenReturn(com.lifepulse.common.web.PageResponse.of(java.util.List.of(), 0, 1, 20));
    mvc.perform(get("/api/v1/expenses").header("X-Test-User", "1"))
        .andExpect(status().isOk());
  }
  @Test void create_happy_returns201() throws Exception {
    when(service.create(any())).thenReturn(new com.lifepulse.expense.dto.ExpenseResponse(
        1L, new BigDecimal("10.00"), "MEAL", null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()));
    var req = new CreateExpenseRequest(new BigDecimal("10.00"), "MEAL", null, LocalDateTime.now());
    mvc.perform(post("/api/v1/expenses").header("X-Test-User", "1")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
        .andExpect(status().isCreated());
  }
  @Test void create_invalidAmount_returns400() throws Exception {
    var req = new CreateExpenseRequest(new BigDecimal("0"), "MEAL", null, LocalDateTime.now());
    mvc.perform(post("/api/v1/expenses").header("X-Test-User", "1")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }
  @Test void get_crossUser_returns1003() throws Exception {
    when(service.getById(100L)).thenThrow(new BusinessException(ErrorCode.CROSS_USER, "无权操作"));
    mvc.perform(get("/api/v1/expenses/100").header("X-Test-User", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1003));
  }
  @Test void patch_happy_returns200() throws Exception {
    mvc.perform(patch("/api/v1/expenses/1").header("X-Test-User", "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new UpdateExpenseRequest(null, "TRANSPORT", null, null))))
        .andExpect(status().isOk());
  }
  @Test void delete_happy_returns200() throws Exception {
    mvc.perform(delete("/api/v1/expenses/1").header("X-Test-User", "1"))
        .andExpect(status().isOk());
  }
  @Test void write_rateLimited_returns1006() throws Exception {
    doThrow(new BusinessException(ErrorCode.RATE_LIMIT, "操作过于频繁"))
        .when(service).create(any());
    var req = new CreateExpenseRequest(new BigDecimal("10.00"), "MEAL", null, LocalDateTime.now());
    mvc.perform(post("/api/v1/expenses").header("X-Test-User", "1")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
        .andExpect(jsonPath("$.code").value(1006));
  }
  @Test void summary_badMonth_returns1001() throws Exception {
    mvc.perform(get("/api/v1/expenses/summary?year=2026&month=13").header("X-Test-User", "1"))
        .andExpect(jsonPath("$.code").value(1001));
  }
  @Test void summary_happy_returns200() throws Exception {
    when(service.summary(2026, 7)).thenReturn(new com.lifepulse.expense.dto.ExpenseSummary(
        new BigDecimal("100"), java.util.Map.of(), null, null));
    mvc.perform(get("/api/v1/expenses/summary?year=2026&month=7").header("X-Test-User", "1"))
        .andExpect(status().isOk());
  }
  @Test void categories_returns5() throws Exception {
    when(service.categories()).thenReturn(java.util.List.of(
        new com.lifepulse.expense.dto.CategoryItem("MEAL", "餐饮"),
        new com.lifepulse.expense.dto.CategoryItem("SHOPPING", "购物"),
        new com.lifepulse.expense.dto.CategoryItem("TRANSPORT", "交通"),
        new com.lifepulse.expense.dto.CategoryItem("SUBSCRIPTION", "订阅"),
        new com.lifepulse.expense.dto.CategoryItem("OTHER", "其他")));
    mvc.perform(get("/api/v1/expenses/categories").header("X-Test-User", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(5));
  }
}
```

- [ ] **Step 3**: Run → PASS

Run: `cd backend && mvn test -Dtest=ExpenseControllerWebTest -q`
Expected: BUILD SUCCESS, 11 tests passed

> 若 `JwtAuthFilter` 抽象类无法直接继承或 UserContext 未就绪，需在测试中用 `@WithMockUser` 自定义 annotation 或在 stub 配置中替换为 `OncePerRequestFilter`。

- [ ] **Step 4**: Commit

```bash
git add backend/src/main/java/com/lifepulse/expense/web/ExpenseController.java \
        backend/src/test/java/com/lifepulse/expense/web/ExpenseControllerWebTest.java
git commit -m "feat(expense): add ExpenseController with 7 endpoints and WebMvc tests"
```

---
