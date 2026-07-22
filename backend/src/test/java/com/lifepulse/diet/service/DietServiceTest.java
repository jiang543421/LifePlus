package com.lifepulse.diet.service;

import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import com.lifepulse.diet.DietConstants;
import com.lifepulse.diet.MealType;
import com.lifepulse.diet.dto.CreateDietRequest;
import com.lifepulse.diet.dto.DietFrequentItem;
import com.lifepulse.diet.dto.DietFilter;
import com.lifepulse.diet.dto.DietListItem;
import com.lifepulse.diet.dto.DietPageResponse;
import com.lifepulse.diet.dto.DietResponse;
import com.lifepulse.diet.dto.DietSummary;
import com.lifepulse.diet.dto.UpdateDietRequest;
import com.lifepulse.diet.entity.Diet;
import com.lifepulse.diet.repository.DietMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DietService 单元测试（diet v1.2.2 - plan T7）。
 *
 * <p>覆盖 spec 07-diet-design section 8.2 必测用例：
 * <ul>
 *   <li>happy path: list / get / create / update / delete / summary / frequent</li>
 *   <li>1001 校验：mealType 非法 / name 空 / 营养负数</li>
 *   <li>1003 跨用户</li>
 *   <li>1006 限流</li>
 *   <li>summary 聚合正确性（构造早午晚各一笔，断言 sum）</li>
 *   <li>frequent 聚合：top 1 是高频 name</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DietServiceTest {

    @Mock
    DietMapper mapper;
    @Mock
    RateLimiter rateLimiter;

    DietService service;

    private static final long UID = 1L;

    @BeforeEach
    void setUp() {
        service = new DietService(mapper, rateLimiter);
        UserContext.set(UID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Diet mkDiet(long id, MealType meal, String name, String kcal) {
        Diet d = new Diet();
        d.setId(id);
        d.setUserId(UID);
        d.setMealType(meal);
        d.setName(name);
        d.setKcal(new BigDecimal(kcal));
        d.setProteinG(BigDecimal.ZERO);
        d.setCarbG(BigDecimal.ZERO);
        d.setFatG(BigDecimal.ZERO);
        var t = fixedTime();
        d.setOccurredAt(t);
        d.setCreatedAt(t);
        d.setUpdatedAt(t);
        return d;
    }

    private OffsetDateTime fixedTime() {
        return OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneOffset.UTC);
    }

    private void allowWrite() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
    }

    // ---------- create ----------

    @Test
    void create_happy_persistsAndReturnsResponse() {
        allowWrite();
        when(mapper.insert(any(Diet.class))).thenAnswer(inv -> {
            Diet d = inv.getArgument(0);
            d.setId(100L);
            return 1;
        });
        var req = new CreateDietRequest("LUNCH", "米饭 1 碗", new BigDecimal("230"),
                new BigDecimal("5"), new BigDecimal("50"), new BigDecimal("1"), null, fixedTime());
        DietResponse resp = service.create(req);
        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.mealType()).isEqualTo("LUNCH");
        assertThat(resp.name()).isEqualTo("米饭 1 碗");
        assertThat(resp.kcal()).isEqualByComparingTo("230");
        assertThat(resp.userId()).isEqualTo(UID);
    }

    @Test
    void create_invalidMealType_throws1001() {
        var req = new CreateDietRequest("BRUNCH", "name", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(mapper, never()).insert(any(Diet.class));
    }

    @Test
    void create_blankName_throws1001() {
        var req = new CreateDietRequest("LUNCH", "  ", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void create_negativeKcal_throws1001() {
        var req = new CreateDietRequest("LUNCH", "name", new BigDecimal("-1"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void create_rateLimited_throws1006() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        var req = new CreateDietRequest("LUNCH", "name", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.LOGIN_RATE_LIMIT);
    }

    @Test
    void create_unauthenticated_throws1002() {
        UserContext.clear();
        var req = new CreateDietRequest("LUNCH", "name", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime());
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.BAD_CREDENTIALS);
    }

    @Test
    void create_usesExpectedRateLimitKey() {
        allowWrite();
        when(mapper.insert(any(Diet.class))).thenAnswer(inv -> {
            ((Diet) inv.getArgument(0)).setId(1L);
            return 1;
        });
        var req = new CreateDietRequest("LUNCH", "name", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime());
        service.create(req);
        verify(rateLimiter).hit(
                eq(DietConstants.WRITE_RL_KEY_PREFIX + UID),
                eq(DietConstants.WRITE_RL_MAX),
                eq(DietConstants.WRITE_RL_WINDOW));
    }

    // ---------- getById ----------

    @Test
    void getById_found_returnsResponse() {
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkDiet(100, MealType.LUNCH, "米饭", "230"));
        DietResponse resp = service.getById(100L);
        assertThat(resp.id()).isEqualTo(100L);
        assertThat(resp.mealType()).isEqualTo("LUNCH");
        assertThat(resp.kcal()).isEqualByComparingTo("230");
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
                        mkDiet(2, MealType.LUNCH, "a", "100"),
                        mkDiet(1, MealType.DINNER, "b", "200")));
        when(mapper.countByUser(anyLong(), any(), any(), any())).thenReturn(2L);
        var f = new DietFilter(null, null, null, 1, 20);
        DietPageResponse p = service.list(f);
        assertThat(p.total()).isEqualTo(2L);
        assertThat(p.items()).hasSize(2);
        assertThat(p.page()).isEqualTo(1);
        assertThat(p.size()).isEqualTo(20);
        assertThat(p.items().get(0).mealType()).isEqualTo("LUNCH");
    }

    @Test
    void list_passesMealTypeNameAndOffset() {
        when(mapper.listByUser(anyLong(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(mapper.countByUser(anyLong(), any(), any(), any())).thenReturn(0L);
        var from = fixedTime();
        var to = from.plusDays(7);
        service.list(new DietFilter("BREAKFAST", from, to, 3, 20));
        verify(mapper).listByUser(eq(UID), eq("BREAKFAST"), eq(from), eq(to), eq(40), eq(20));
        verify(mapper).countByUser(eq(UID), eq("BREAKFAST"), eq(from), eq(to));
    }

    // ---------- update ----------

    @Test
    void update_partial_onlyChangesProvidedFields() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkDiet(100, MealType.LUNCH, "old", "100"));
        var req = new UpdateDietRequest(null, null, new BigDecimal("200"),
                null, null, null, null, null);
        service.update(100L, req);
        verify(mapper).updateById(argThat(d ->
                d.getKcal().compareTo(new BigDecimal("200")) == 0
                        && d.getMealType() == MealType.LUNCH
                        && "old".equals(d.getName())));
    }

    @Test
    void update_blankName_throws1001() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkDiet(100, MealType.LUNCH, "old", "100"));
        var req = new UpdateDietRequest(null, "  ", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(100L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void update_invalidMealType_throws1001() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkDiet(100, MealType.LUNCH, "old", "100"));
        var req = new UpdateDietRequest("BRUNCH", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(100L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void update_crossUser_throws1003() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 999L)).thenReturn(null);
        var req = new UpdateDietRequest(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(999L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.CROSS_USER);
    }

    @Test
    void update_rateLimited_throws1006() {
        when(rateLimiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        var req = new UpdateDietRequest(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(100L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.LOGIN_RATE_LIMIT);
    }

    @Test
    void update_emptyNote_normalizesToNull() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkDiet(100, MealType.LUNCH, "old", "100"));
        var req = new UpdateDietRequest(null, null, null, null, null, null, "   ", null);
        service.update(100L, req);
        verify(mapper).updateById(argThat(d -> d.getNote() == null));
    }

    // ---------- softDelete ----------

    @Test
    void softDelete_happy_callsMapperDelete() {
        allowWrite();
        when(mapper.findByUserAndId(UID, 100L))
                .thenReturn(mkDiet(100, MealType.LUNCH, "name", "100"));
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
    void summary_happy_aggregatesAllFourNutritionSums() {
        var today = LocalDate.of(2026, 7, 20);
        var yesterday = today.minusDays(1);
        var lastWeek = today.minusDays(7);
        // Stub by exact from/to OffsetDateTime to distinguish windows deterministically.
        when(mapper.summaryOnDate(eq(UID),
                argThat(t -> t != null && t.toLocalDate().equals(today)),
                argThat(t -> t != null && t.toLocalDate().equals(today))))
                .thenReturn(Map.of(
                        "kcal", new BigDecimal("1650"),
                        "proteinG", new BigDecimal("55"),
                        "carbG", new BigDecimal("220"),
                        "fatG", new BigDecimal("50")));
        when(mapper.summaryOnDate(eq(UID),
                argThat(t -> t != null && t.toLocalDate().equals(yesterday)),
                argThat(t -> t != null && t.toLocalDate().equals(yesterday))))
                .thenReturn(Map.of(
                        "kcal", new BigDecimal("1530"),
                        "proteinG", new BigDecimal("60"),
                        "carbG", new BigDecimal("220"),
                        "fatG", new BigDecimal("50")));
        when(mapper.summaryOnDate(eq(UID),
                argThat(t -> t != null && t.toLocalDate().equals(lastWeek)),
                argThat(t -> t != null && t.toLocalDate().equals(lastWeek))))
                .thenReturn(Map.of(
                        "kcal", new BigDecimal("1730"),
                        "proteinG", new BigDecimal("50"),
                        "carbG", new BigDecimal("240"),
                        "fatG", new BigDecimal("55")));
        DietSummary s = service.summary(today);
        assertThat(s.kcal()).isEqualByComparingTo("1650");
        assertThat(s.proteinG()).isEqualByComparingTo("55");
        assertThat(s.carbG()).isEqualByComparingTo("220");
        assertThat(s.fatG()).isEqualByComparingTo("50");
        // kcal 1650 - 1530 = +120
        assertThat(s.kcalDeltaYesterday()).isEqualByComparingTo("120");
        // kcal 1650 - 1730 = -80
        assertThat(s.kcalDeltaLastWeek()).isEqualByComparingTo("-80");
    }

    @Test
    void summary_yesterdayEmpty_returnsNullDelta() {
        var today = LocalDate.of(2026, 7, 20);
        var yesterday = today.minusDays(1);
        var lastWeek = today.minusDays(7);
        when(mapper.summaryOnDate(eq(UID),
                argThat(t -> t != null && t.toLocalDate().equals(today)),
                argThat(t -> t != null && t.toLocalDate().equals(today))))
                .thenReturn(Map.of(
                        "kcal", new BigDecimal("1000"),
                        "proteinG", new BigDecimal("30"),
                        "carbG", new BigDecimal("100"),
                        "fatG", new BigDecimal("20")));
        // Yesterday: 全 0 (无数据) → delta null
        when(mapper.summaryOnDate(eq(UID),
                argThat(t -> t != null && t.toLocalDate().equals(yesterday)),
                argThat(t -> t != null && t.toLocalDate().equals(yesterday))))
                .thenReturn(Map.of(
                        "kcal", new BigDecimal("0"),
                        "proteinG", new BigDecimal("0"),
                        "carbG", new BigDecimal("0"),
                        "fatG", new BigDecimal("0")));
        // Last week: 有数据 → delta 可计算
        when(mapper.summaryOnDate(eq(UID),
                argThat(t -> t != null && t.toLocalDate().equals(lastWeek)),
                argThat(t -> t != null && t.toLocalDate().equals(lastWeek))))
                .thenReturn(Map.of(
                        "kcal", new BigDecimal("800"),
                        "proteinG", new BigDecimal("25"),
                        "carbG", new BigDecimal("80"),
                        "fatG", new BigDecimal("15")));
        DietSummary s = service.summary(today);
        assertThat(s.kcal()).isEqualByComparingTo("1000");
        assertThat(s.kcalDeltaYesterday()).isNull();   // 昨日 0 → 无对比数据
        assertThat(s.kcalDeltaLastWeek()).isEqualByComparingTo("200");  // 1000 - 800
    }

    // ---------- frequent ----------

    @Test
    void frequent_defaultWindow_30days_top10Limit() {
        when(mapper.frequentByUser(eq(UID), any(), any(), eq(10)))
                .thenReturn(List.of(
                        Map.of("name", "燕麦", "avgKcal", new BigDecimal("380"),
                                "avgProteinG", new BigDecimal("13"),
                                "avgCarbG", new BigDecimal("67"),
                                "avgFatG", new BigDecimal("7"),
                                "hitCount", 12L),
                        Map.of("name", "鸡胸肉", "avgKcal", new BigDecimal("165"),
                                "avgProteinG", new BigDecimal("31"),
                                "avgCarbG", new BigDecimal("0"),
                                "avgFatG", new BigDecimal("3.6"),
                                "hitCount", 8L)));
        List<DietFrequentItem> items = service.frequent(null, null, null);
        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("燕麦");
        assertThat(items.get(0).hitCount()).isEqualTo(12);
        assertThat(items.get(1).name()).isEqualTo("鸡胸肉");
    }

    @Test
    void frequent_limitClampedToMax() {
        when(mapper.frequentByUser(eq(UID), any(), any(), eq(DietConstants.MAX_FREQUENT_LIMIT)))
                .thenReturn(List.of());
        // 请求 limit = 999 → service 应 clamp 到 MAX_FREQUENT_LIMIT (50)
        service.frequent(null, null, 999);
        verify(mapper).frequentByUser(eq(UID), any(), any(),
                eq(DietConstants.MAX_FREQUENT_LIMIT));
    }

    @Test
    void frequent_emptyResult_returnsEmptyList() {
        when(mapper.frequentByUser(eq(UID), any(), any(), anyInt()))
                .thenReturn(List.of());
        assertThat(service.frequent(null, null, null)).isEmpty();
    }

    // ---------- write rate-limit prefix ----------

    @Test
    void writeRateLimit_keyPrefix_isDietWrite() {
        allowWrite();
        when(mapper.insert(any(Diet.class))).thenAnswer(inv -> {
            ((Diet) inv.getArgument(0)).setId(1L);
            return 1;
        });
        service.create(new CreateDietRequest("LUNCH", "name", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, fixedTime()));
        verify(rateLimiter).hit(
                argThat(key -> key.startsWith("lp:rl:diet:write:")),
                anyInt(),
                any(Duration.class));
    }
}
