package com.lifepulse.plan.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.plan.PlanConstants;
import com.lifepulse.plan.dto.PlanCreateRequest;
import com.lifepulse.plan.dto.PlanFilter;
import com.lifepulse.plan.dto.PlanListItem;
import com.lifepulse.plan.dto.PlanResponse;
import com.lifepulse.plan.dto.PlanUpdateRequest;
import com.lifepulse.plan.entity.Plan;
import com.lifepulse.plan.repository.PlanMapper;
import com.lifepulse.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PlanService 单元测试（spec §6.1 service 行覆盖 ≥ 80%）。
 *
 * <p>{@link UserContext} 是 static 工具类，用 {@code set/clear} 控制；每个用例前
 * 显式 {@code UserContext.set(...)}（{@code @BeforeEach} 不设，避免共享态泄漏）。
 *
 * <p>重点覆盖：
 * <ul>
 *   <li>create 正常路径 + all_day 归一化（核心业务规则）</li>
 *   <li>跨字段校验 {@code endTime > startTime} 抛 1001</li>
 *   <li>所有 {id} 操作的跨用户 1003 防御</li>
 *   <li>update 局部更新 + all_day 重归一化</li>
 *   <li>pageByUser 分页 + total 计数</li>
 *   <li>{@code requireUserId} 1002 防御</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanMapper mapper;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(mapper);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ---------- create ----------

    @Test
    void create_setsUserIdFromContext_persistsAndReturnsResponse() {
        UserContext.set(7L);
        LocalDateTime start = LocalDateTime.of(2026, 8, 15, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 15, 11, 0);
        PlanCreateRequest req = new PlanCreateRequest(
                "周会", start, end, 0, "会议室 A", "议程", 30);

        PlanResponse res = service.create(req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).insert(cap.capture());
        Plan inserted = cap.getValue();
        assertThat(inserted.getUserId()).isEqualTo(7L);
        assertThat(inserted.getTitle()).isEqualTo("周会");
        assertThat(inserted.getStartTime()).isEqualTo(start);
        assertThat(inserted.getEndTime()).isEqualTo(end);
        assertThat(inserted.getAllDay()).isEqualTo(0);
        assertThat(inserted.getLocation()).isEqualTo("会议室 A");
        assertThat(inserted.getNote()).isEqualTo("议程");
        assertThat(inserted.getReminderMin()).isEqualTo(30);
        assertThat(res.title()).isEqualTo("周会");
    }

    @Test
    void create_allDayNull_defaultsToZero() {
        UserContext.set(7L);
        PlanCreateRequest req = new PlanCreateRequest(
                "周会",
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 11, 0),
                null, null, null, null);

        service.create(req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getAllDay()).isEqualTo(0);
        assertThat(cap.getValue().getLocation()).isNull();
        assertThat(cap.getValue().getNote()).isNull();
        assertThat(cap.getValue().getReminderMin()).isNull();
    }

    @Test
    void create_allDayTrue_normalizesStartEndToDayBounds() {
        UserContext.set(7L);
        // 跨日事件：8/15 22:00 → 8/16 02:00，allDay=true 应归一为 8/15 00:00 → 8/16 23:59:59
        PlanCreateRequest req = new PlanCreateRequest(
                "全天出差",
                LocalDateTime.of(2026, 8, 15, 22, 0),
                LocalDateTime.of(2026, 8, 16, 2, 0),
                1, null, null, null);

        service.create(req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).insert(cap.capture());
        Plan inserted = cap.getValue();
        assertThat(inserted.getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0, 0));
        assertThat(inserted.getEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 16, 23, 59, 59));
        assertThat(inserted.getAllDay()).isEqualTo(1);
    }

    @Test
    void create_allDayFalse_keepsTimesAsGiven() {
        UserContext.set(7L);
        LocalDateTime start = LocalDateTime.of(2026, 8, 15, 10, 30);
        LocalDateTime end = LocalDateTime.of(2026, 8, 15, 11, 45);
        PlanCreateRequest req = new PlanCreateRequest(
                "约会议", start, end, 0, null, null, null);

        service.create(req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).insert(cap.capture());
        assertThat(cap.getValue().getStartTime()).isEqualTo(start);
        assertThat(cap.getValue().getEndTime()).isEqualTo(end);
    }

    @Test
    void create_endTimeEqualsStart_throws1001() {
        UserContext.set(7L);
        LocalDateTime t = LocalDateTime.of(2026, 8, 15, 10, 0);
        PlanCreateRequest req = new PlanCreateRequest(
                "边界", t, t, 0, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_VALIDATION);

        verify(mapper, never()).insert(any(Plan.class));
    }

    @Test
    void create_endTimeBeforeStart_throws1001() {
        UserContext.set(7L);
        PlanCreateRequest req = new PlanCreateRequest(
                "倒序",
                LocalDateTime.of(2026, 8, 15, 11, 0),
                LocalDateTime.of(2026, 8, 15, 10, 0),
                0, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_VALIDATION);

        verify(mapper, never()).insert(any(Plan.class));
    }

    @Test
    void create_unauthenticated_throws1002() {
        PlanCreateRequest req = new PlanCreateRequest(
                "x",
                LocalDateTime.of(2026, 8, 15, 10, 0),
                LocalDateTime.of(2026, 8, 15, 11, 0),
                0, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(mapper, never()).insert(any(Plan.class));
    }

    // ---------- getById ----------

    @Test
    void getById_ownerMatch_returnsResponse() {
        UserContext.set(7L);
        Plan p = ownedPlan(42L, 7L, "周会");
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(p));

        PlanResponse res = service.getById(42L);

        assertThat(res.id()).isEqualTo(42L);
        assertThat(res.title()).isEqualTo("周会");
    }

    @Test
    void getById_crossUser_throws1003() {
        UserContext.set(2L);
        when(mapper.findByUserAndId(2L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_CROSS_USER);

        verify(mapper).findByUserAndId(2L, 42L);
    }

    @Test
    void getById_unauthenticated_throws1002() {
        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(mapper, never()).findByUserAndId(anyLong(), anyLong());
    }

    // ---------- update ----------

    @Test
    void update_partialFields_onlyNonNullApplied() {
        UserContext.set(7L);
        Plan existing = ownedPlan(42L, 7L, "旧标题");
        existing.setStartTime(LocalDateTime.of(2026, 8, 1, 10, 0));
        existing.setEndTime(LocalDateTime.of(2026, 8, 1, 11, 0));
        existing.setAllDay(0);
        existing.setLocation("旧地点");
        existing.setNote("旧备注");
        existing.setReminderMin(15);
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        PlanUpdateRequest req = new PlanUpdateRequest(
                "新标题", null, null, null, null, null, null);
        service.update(42L, req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).updateById(cap.capture());
        Plan updated = cap.getValue();
        assertThat(updated.getTitle()).isEqualTo("新标题");
        // 其他字段保持原值
        assertThat(updated.getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
        assertThat(updated.getEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 11, 0));
        assertThat(updated.getAllDay()).isEqualTo(0);
        assertThat(updated.getLocation()).isEqualTo("旧地点");
        assertThat(updated.getNote()).isEqualTo("旧备注");
        assertThat(updated.getReminderMin()).isEqualTo(15);
    }

    @Test
    void update_allDaySetToOne_normalizesStartEnd() {
        UserContext.set(7L);
        // 已存在 plan 是普通时段；更新把 allDay 切到 1，应归一化
        Plan existing = ownedPlan(42L, 7L, "改全天");
        existing.setStartTime(LocalDateTime.of(2026, 8, 15, 14, 30));
        existing.setEndTime(LocalDateTime.of(2026, 8, 15, 16, 0));
        existing.setAllDay(0);
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        PlanUpdateRequest req = new PlanUpdateRequest(
                null, null, null, 1, null, null, null);
        service.update(42L, req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).updateById(cap.capture());
        Plan updated = cap.getValue();
        assertThat(updated.getAllDay()).isEqualTo(1);
        assertThat(updated.getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 15, 0, 0, 0));
        assertThat(updated.getEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 15, 23, 59, 59));
    }

    @Test
    void update_allFieldsChanged_allApplied() {
        UserContext.set(7L);
        Plan existing = ownedPlan(42L, 7L, "旧");
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        LocalDateTime newStart = LocalDateTime.of(2026, 9, 1, 9, 0);
        LocalDateTime newEnd = LocalDateTime.of(2026, 9, 1, 18, 0);
        PlanUpdateRequest req = new PlanUpdateRequest(
                "新", newStart, newEnd, 0, "新地点", "新备注", 60);
        service.update(42L, req);

        ArgumentCaptor<Plan> cap = ArgumentCaptor.forClass(Plan.class);
        verify(mapper).updateById(cap.capture());
        Plan updated = cap.getValue();
        assertThat(updated.getTitle()).isEqualTo("新");
        assertThat(updated.getStartTime()).isEqualTo(newStart);
        assertThat(updated.getEndTime()).isEqualTo(newEnd);
        assertThat(updated.getAllDay()).isEqualTo(0);
        assertThat(updated.getLocation()).isEqualTo("新地点");
        assertThat(updated.getNote()).isEqualTo("新备注");
        assertThat(updated.getReminderMin()).isEqualTo(60);
    }

    @Test
    void update_crossUser_throws1003() {
        UserContext.set(2L);
        when(mapper.findByUserAndId(2L, 42L)).thenReturn(Optional.empty());

        PlanUpdateRequest req = new PlanUpdateRequest("新", null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(42L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_CROSS_USER);

        verify(mapper, never()).updateById(any(Plan.class));
    }

    @Test
    void update_endTimeNotAfterStart_throws1001() {
        UserContext.set(7L);
        Plan existing = ownedPlan(42L, 7L, "改坏");
        existing.setStartTime(LocalDateTime.of(2026, 8, 15, 10, 0));
        existing.setEndTime(LocalDateTime.of(2026, 8, 15, 11, 0));
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        // 把 end 改成早于 start
        PlanUpdateRequest req = new PlanUpdateRequest(
                null, null, LocalDateTime.of(2026, 8, 15, 9, 0), null, null, null, null);

        assertThatThrownBy(() -> service.update(42L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_VALIDATION);

        verify(mapper, never()).updateById(any(Plan.class));
    }

    // ---------- softDelete ----------

    @Test
    void softDelete_ownerMatch_callsMapperDeleteById() {
        UserContext.set(7L);
        Plan existing = ownedPlan(42L, 7L, "切删");
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        service.softDelete(42L);

        verify(mapper).deleteById(42L);
    }

    @Test
    void softDelete_crossUser_throws1003_noDelete() {
        UserContext.set(2L);
        when(mapper.findByUserAndId(2L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.softDelete(42L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_CROSS_USER);

        verify(mapper, never()).deleteById(anyLong());
    }

    // ---------- pageByUser ----------

    @Test
    void pageByUser_page2_size10_appliesOffsetAndReturnsPage() {
        UserContext.set(7L);
        PlanFilter f = new PlanFilter(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                2, 10);

        Plan row = ownedPlan(101L, 7L, "page2-row");
        when(mapper.pageByUser(eq(7L),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 31, 23, 59)),
                eq(10), eq(10))).thenReturn(List.of(row));
        when(mapper.countByUser(eq(7L),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 31, 23, 59))))
                .thenReturn(15L);

        PageResponse<PlanListItem> page = service.pageByUser(f);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(15L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.items().get(0).title()).isEqualTo("page2-row");
        // offset = (2 - 1) * 10 = 10
        verify(mapper).pageByUser(eq(7L),
                eq(LocalDateTime.of(2026, 8, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 31, 23, 59)),
                eq(10), eq(10));
    }

    @Test
    void pageByUser_nullBounds_returnsEmptyPageWithTotalZero() {
        UserContext.set(7L);
        PlanFilter f = new PlanFilter(null, null, 1, 20);
        when(mapper.pageByUser(eq(7L), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of());
        when(mapper.countByUser(eq(7L), isNull(), isNull())).thenReturn(0L);

        PageResponse<PlanListItem> page = service.pageByUser(f);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(0L);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void pageByUser_unauthenticated_throws1002() {
        PlanFilter f = new PlanFilter(null, null, 1, 20);

        assertThatThrownBy(() -> service.pageByUser(f))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(mapper, never()).pageByUser(anyLong(), any(), any(), anyInt(), anyInt());
    }

    // ---------- helpers ----------

    private Plan ownedPlan(long id, long userId, String title) {
        Plan p = new Plan();
        p.setId(id);
        p.setUserId(userId);
        p.setTitle(title);
        p.setStartTime(LocalDate.of(2026, 8, 15).atStartOfDay());
        p.setEndTime(LocalDate.of(2026, 8, 15).atTime(23, 59, 59));
        p.setAllDay(0);
        p.setReminderMin(PlanConstants.DEFAULT_REMINDER_MIN);
        return p;
    }
}