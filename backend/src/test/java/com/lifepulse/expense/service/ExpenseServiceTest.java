package com.lifepulse.expense.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
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
import com.lifepulse.expense.entity.Expense;
import com.lifepulse.expense.repository.ExpenseMapper;
import com.lifepulse.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    ExpenseMapper mapper;
    @Mock
    RateLimiter rateLimiter;

    ExpenseService service;

    private static final long UID = 1L;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(mapper, rateLimiter);
        UserContext.set(UID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Expense mkExpense(long id, ExpenseCategory cat, String amount) {
        Expense e = new Expense();
        e.setId(id);
        e.setUserId(UID);
        e.setAmount(new BigDecimal(amount));
        e.setCategory(cat);
        e.setNote("n");
        var now = OffsetDateTime.now();
        e.setOccurredAt(now);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private OffsetDateTime fixedTime() {
        return OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private void allowWrite() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
    }

    // ---------- create ----------

    @Test
    void create_happy_persists_andReturnsResponse() {
        allowWrite();
        when(mapper.insert(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(100L);
            return 1;
        });
        var req = new CreateExpenseRequest(
                new BigDecimal("35.00"), ExpenseCategory.MEAL, "lunch", fixedTime());
        ExpenseResponse resp = service.create(req);
        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.amount()).isEqualByComparingTo("35.00");
        assertThat(resp.category()).isEqualTo(ExpenseCategory.MEAL);
        assertThat(resp.userId()).isEqualTo(UID);
    }

    @Test
    void create_rateLimited_throws1006() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        var req = new CreateExpenseRequest(
                BigDecimal.ONE, ExpenseCategory.MEAL, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.LOGIN_RATE_LIMIT);
    }

    @Test
    void create_unauthenticated_throws1002() {
        UserContext.clear();
        var req = new CreateExpenseRequest(
                BigDecimal.ONE, ExpenseCategory.MEAL, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.BAD_CREDENTIALS);
    }

    @Test
    void create_usesExpectedRateLimitKey() {
        allowWrite();
        when(mapper.insert(any(Expense.class))).thenAnswer(inv -> {
            ((Expense) inv.getArgument(0)).setId(1L);
            return 1;
        });
        var req = new CreateExpenseRequest(
                BigDecimal.ONE, ExpenseCategory.MEAL, null, fixedTime());
        service.create(req);
        verify(rateLimiter).hit(
                eq(ExpenseConstants.WRITE_RL_KEY_PREFIX + UID),
                eq(ExpenseConstants.WRITE_RL_MAX),
                eq(ExpenseConstants.WRITE_RL_WINDOW));
    }

    // ---------- getById ----------

    @Test
    void getById_found_returnsResponse() {
        when(mapper.findByUserAndId(UID, 100L)).thenReturn(mkExpense(100, ExpenseCategory.MEAL, "10.00"));
        ExpenseResponse resp = service.getById(100L);
        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void getById_crossUser_throws1003() {
        when(mapper.findByUserAndId(UID, 999L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
    }

    // ---------- list ----------

    @Test
    void list_happy_returnsPagedListItem() {
        when(mapper.listByUser(anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        mkExpense(2, ExpenseCategory.MEAL, "5.00"),
                        mkExpense(1, ExpenseCategory.TRANSPORT, "3.00")));
        when(mapper.countByUser(anyLong(), any(), any(), any())).thenReturn(2L);
        var f = new ExpenseFilter(null, null, null, 1, 20);
        PageResponse<ExpenseListItem> p = service.list(f);
        assertThat(p.total()).isEqualTo(2L);
        assertThat(p.items()).hasSize(2);
        assertThat(p.page()).isEqualTo(1);
        assertThat(p.size()).isEqualTo(20);
        assertThat(p.items().get(0).category()).isEqualTo(ExpenseCategory.MEAL);
    }

    @Test
    void list_passesCategoryNameAndOffset() {
        when(mapper.listByUser(anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(mapper.countByUser(anyLong(), any(), any(), any())).thenReturn(0L);
        var from = fixedTime();
        var to = from.plusDays(7);
        service.list(new ExpenseFilter(ExpenseCategory.SHOPPING, from, to, 3, 20));
        verify(mapper).listByUser(eq(UID), eq("SHOPPING"), eq(from), eq(to), eq(40), eq(20));
        verify(mapper).countByUser(eq(UID), eq("SHOPPING"), eq(from), eq(to));
    }

    // ---------- update ----------

    @Test
    void update_partial_onlyChangesProvidedFields() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkExpense(100, ExpenseCategory.MEAL, "10.00"));
        var req = new UpdateExpenseRequest(new BigDecimal("20.00"), null, null, null);
        service.update(100L, req);
        verify(mapper).updateById(argThat(e ->
                e.getAmount().compareTo(new BigDecimal("20.00")) == 0
                        && e.getCategory() == ExpenseCategory.MEAL
                        && "n".equals(e.getNote())));
    }

    @Test
    void update_crossUser_throws1003() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 999L)).thenReturn(null);
        var req = new UpdateExpenseRequest(null, null, null, null);
        assertThatThrownBy(() -> service.update(999L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
    }

    @Test
    void update_rateLimited_throws1006() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        var req = new UpdateExpenseRequest(null, null, null, null);
        assertThatThrownBy(() -> service.update(100L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.LOGIN_RATE_LIMIT);
    }

    @Test
    void update_changesAllProvidedFields() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkExpense(100, ExpenseCategory.MEAL, "10.00"));
        var when = fixedTime().plusDays(1);
        var req = new UpdateExpenseRequest(
                new BigDecimal("99.50"),
                ExpenseCategory.TRANSPORT,
                "taxi",
                when);
        service.update(100L, req);
        verify(mapper).updateById(argThat(e ->
                e.getAmount().compareTo(new BigDecimal("99.50")) == 0
                        && e.getCategory() == ExpenseCategory.TRANSPORT
                        && "taxi".equals(e.getNote())
                        && e.getOccurredAt().equals(when)));
    }

    // ---------- softDelete ----------

    @Test
    void softDelete_happy_callsMapperDelete() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkExpense(100, ExpenseCategory.MEAL, "10.00"));
        service.softDelete(100L);
        verify(mapper).deleteById(100L);
    }

    @Test
    void softDelete_crossUser_throws1003() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 999L)).thenReturn(null);
        assertThatThrownBy(() -> service.softDelete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
    }

    @Test
    void softDelete_rateLimited_throws1006() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        assertThatThrownBy(() -> service.softDelete(100L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.LOGIN_RATE_LIMIT);
    }

    // ---------- summary ----------

    @Test
    void summary_happy_returnsTotalAndZeroFilledBreakdown() {
        when(mapper.summaryTotal(anyLong(), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(mapper.summaryByCategory(anyLong(), any(), any())).thenReturn(Map.of(
                "MEAL", new BigDecimal("60.00"),
                "TRANSPORT", new BigDecimal("40.00")));
        ExpenseSummaryResponse s = service.summary(2026, 7);
        assertThat(s.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(s.amountByCategory())
                .containsEntry("MEAL", new BigDecimal("60.00"))
                .containsEntry("TRANSPORT", new BigDecimal("40.00"))
                .containsEntry("SHOPPING", BigDecimal.ZERO)
                .containsEntry("SUBSCRIPTION", BigDecimal.ZERO)
                .containsEntry("OTHER", BigDecimal.ZERO)
                .hasSize(5);
        assertThat(s.startMonth()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void summary_empty_returnsZerosForAllCategories() {
        when(mapper.summaryTotal(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(mapper.summaryByCategory(anyLong(), any(), any())).thenReturn(Map.of());
        ExpenseSummaryResponse s = service.summary(2026, 7);
        assertThat(s.totalAmount()).isEqualByComparingTo("0");
        assertThat(s.amountByCategory()).hasSize(5);
        assertThat(s.amountByCategory().values())
                .allMatch(v -> v.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void summary_nullRawMap_returnsZerosForAllCategories() {
        when(mapper.summaryTotal(anyLong(), any(), any())).thenReturn(null);
        when(mapper.summaryByCategory(anyLong(), any(), any())).thenReturn(null);
        ExpenseSummaryResponse s = service.summary(2026, 7);
        assertThat(s.totalAmount()).isEqualByComparingTo("0");
        assertThat(s.amountByCategory()).hasSize(5);
    }

    // ---------- categories ----------

    @Test
    void categories_returnsAllFiveInEnumOrderWithMatchingLabels() {
        java.util.List<CategoryItem> cats = service.categories();
        assertThat(cats).hasSize(5);
        for (int i = 0; i < ExpenseCategory.values().length; i++) {
            ExpenseCategory c = ExpenseCategory.values()[i];
            assertThat(cats.get(i).code()).isEqualTo(c.name());
            assertThat(cats.get(i).name()).isEqualTo(c.getLabel());
        }
    }

    // ---------- edge / unrelated ----------

    @Test
    void writeRateLimit_keyPrefix_isExpenseWrite() {
        allowWrite();
        when(mapper.insert(any(Expense.class))).thenAnswer(inv -> {
            ((Expense) inv.getArgument(0)).setId(1L);
            return 1;
        });
        service.create(new CreateExpenseRequest(
                BigDecimal.ONE, ExpenseCategory.MEAL, null, fixedTime()));
        verify(rateLimiter).hit(
                argThat(key -> key.startsWith("lp:rl:expense:write:")),
                anyInt(),
                any(Duration.class));
    }
}