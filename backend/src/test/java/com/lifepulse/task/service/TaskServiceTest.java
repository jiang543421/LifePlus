package com.lifepulse.task.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.web.PageResponse;
import com.lifepulse.security.UserContext;
import com.lifepulse.task.TaskConstants;
import com.lifepulse.task.dto.TaskCreateRequest;
import com.lifepulse.task.dto.TaskFilter;
import com.lifepulse.task.dto.TaskListItem;
import com.lifepulse.task.dto.TaskResponse;
import com.lifepulse.task.dto.TaskUpdateRequest;
import com.lifepulse.task.entity.Task;
import com.lifepulse.task.repository.TaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
 * TaskService 单元测试（spec §6.1 service 行覆盖 ≥ 80%）。
 *
 * <p>{@link UserContext} 是 static 工具类，用 {@code set/clear} 控制；每个用例前
 * 显式 {@code UserContext.set(...)}（{@code @BeforeEach} 不设，避免共享态泄漏）。
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper mapper;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(mapper);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ---------- create ----------

    @Test
    void create_setsUserIdFromContext_persistsAndReturnsResponse() {
        UserContext.set(7L);
        TaskCreateRequest req = new TaskCreateRequest(
                "买菜", 2, LocalDate.of(2026, 8, 15), "home", 100L);

        service.create(req);

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(mapper).insert(cap.capture());
        Task inserted = cap.getValue();
        assertThat(inserted.getUserId()).isEqualTo(7L);
        assertThat(inserted.getTitle()).isEqualTo("买菜");
        assertThat(inserted.getPriority()).isEqualTo(2);
        assertThat(inserted.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(inserted.getTag()).isEqualTo("home");
        assertThat(inserted.getPlanId()).isEqualTo(100L);
        // status / deleted 不由 service 设置 → DB DEFAULT 0
        assertThat(inserted.getStatus()).isNull();
        assertThat(inserted.getDeleted()).isNull();
    }

    @Test
    void create_unauthenticated_throws1002() {
        TaskCreateRequest req = new TaskCreateRequest("买菜", null, null, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(mapper, never()).insert(any(Task.class));
    }

    @Test
    void create_optionalFieldsNull_keptAsNullOnEntity() {
        UserContext.set(7L);
        TaskCreateRequest req = new TaskCreateRequest("写日报", null, null, null, null);

        service.create(req);

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(mapper).insert(cap.capture());
        Task inserted = cap.getValue();
        assertThat(inserted.getPriority()).isNull();
        assertThat(inserted.getDueDate()).isNull();
        assertThat(inserted.getTag()).isNull();
        assertThat(inserted.getPlanId()).isNull();
    }

    // ---------- getById ----------

    @Test
    void getById_ownerMatch_returnsResponse() {
        UserContext.set(7L);
        Task t = ownedTask(42L, 7L, "切状态");
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(t));

        TaskResponse res = service.getById(42L);

        assertThat(res.id()).isEqualTo(42L);
        assertThat(res.title()).isEqualTo("切状态");
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

    // ---------- update ----------

    @Test
    void update_partialFields_onlyNonNullApplied() {
        UserContext.set(7L);
        Task existing = ownedTask(42L, 7L, "旧标题");
        existing.setStatus(TaskConstants.STATUS_TODO);
        existing.setPriority(TaskConstants.PRIORITY_LOW);
        existing.setDueDate(LocalDate.of(2026, 8, 1));
        existing.setTag("home");
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        TaskUpdateRequest req = new TaskUpdateRequest(
                "新标题", null, null, null, null, null);
        service.update(42L, req);

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(mapper).updateById(cap.capture());
        Task updated = cap.getValue();
        assertThat(updated.getTitle()).isEqualTo("新标题");
        // 其他字段保持原值
        assertThat(updated.getStatus()).isEqualTo(TaskConstants.STATUS_TODO);
        assertThat(updated.getPriority()).isEqualTo(TaskConstants.PRIORITY_LOW);
        assertThat(updated.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(updated.getTag()).isEqualTo("home");
    }

    @Test
    void update_allFieldsChanged_allApplied() {
        UserContext.set(7L);
        Task existing = ownedTask(42L, 7L, "旧");
        when(mapper.findByUserAndId(7L, 42L)).thenReturn(Optional.of(existing));

        LocalDate newDue = LocalDate.of(2026, 9, 1);
        TaskUpdateRequest req = new TaskUpdateRequest(
                "新", TaskConstants.STATUS_DONE, TaskConstants.PRIORITY_HIGH, newDue, "work", 9L);
        service.update(42L, req);

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(mapper).updateById(cap.capture());
        Task updated = cap.getValue();
        assertThat(updated.getTitle()).isEqualTo("新");
        assertThat(updated.getStatus()).isEqualTo(TaskConstants.STATUS_DONE);
        assertThat(updated.getPriority()).isEqualTo(TaskConstants.PRIORITY_HIGH);
        assertThat(updated.getDueDate()).isEqualTo(newDue);
        assertThat(updated.getTag()).isEqualTo("work");
    }

    @Test
    void update_crossUser_throws1003() {
        UserContext.set(2L);
        when(mapper.findByUserAndId(2L, 42L)).thenReturn(Optional.empty());

        TaskUpdateRequest req = new TaskUpdateRequest("新", null, null, null, null, null);

        assertThatThrownBy(() -> service.update(42L, req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_CROSS_USER);

        verify(mapper, never()).updateById(any(Task.class));
    }

    // ---------- patchStatus ----------

    @Test
    void patchStatus_ownerMatch_returnsAffectedOne() {
        UserContext.set(7L);
        when(mapper.updateStatusByUser(7L, 42L, TaskConstants.STATUS_DONE)).thenReturn(1);

        service.patchStatus(42L, TaskConstants.STATUS_DONE);

        verify(mapper).updateStatusByUser(7L, 42L, TaskConstants.STATUS_DONE);
    }

    @Test
    void patchStatus_noMatch_throws1003() {
        UserContext.set(7L);
        when(mapper.updateStatusByUser(eq(7L), eq(42L), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> service.patchStatus(42L, TaskConstants.STATUS_DONE))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_CROSS_USER);
    }

    // ---------- softDelete ----------

    @Test
    void softDelete_ownerMatch_callsMapperDeleteById() {
        UserContext.set(7L);
        Task existing = ownedTask(42L, 7L, "切删");
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

    // ---------- listByPlan ----------

    @Test
    void listByPlan_passesUserId_returnsMappedItems() {
        UserContext.set(7L);
        Task a = ownedTask(11L, 7L, "p1");
        Task b = ownedTask(12L, 7L, "p2");
        when(mapper.listByPlan(7L, 100L)).thenReturn(List.of(a, b));

        List<TaskListItem> items = service.listByPlan(100L);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(TaskListItem::title)
                .containsExactly("p1", "p2");
        verify(mapper).listByPlan(7L, 100L);
    }

    // ---------- pageByUser ----------

    @Test
    void pageByUser_page2_size10_appliesOffsetAndReturnsPage() {
        UserContext.set(7L);
        TaskFilter f = new TaskFilter(
                TaskConstants.STATUS_TODO, 2, "work",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                2, 10);

        Task row = ownedTask(101L, 7L, "page2-row");
        when(mapper.pageByUser(eq(7L), eq(TaskConstants.STATUS_TODO), eq(2), eq("work"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)),
                eq(10), eq(10))).thenReturn(List.of(row));
        when(mapper.countByUser(eq(7L), eq(TaskConstants.STATUS_TODO), eq(2), eq("work"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31))))
                .thenReturn(15L);

        PageResponse<TaskListItem> page = service.pageByUser(f);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(15L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        // offset = (2 - 1) * 10 = 10
        verify(mapper).pageByUser(eq(7L), eq(TaskConstants.STATUS_TODO), eq(2), eq("work"),
                eq(LocalDate.of(2026, 8, 1)), eq(LocalDate.of(2026, 8, 31)),
                eq(10), eq(10));
    }

    @Test
    void pageByUser_emptyResult_returnsEmptyItemsWithTotalZero() {
        UserContext.set(7L);
        TaskFilter f = new TaskFilter(null, null, null, null, null, 1, 20);
        when(mapper.pageByUser(eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(0), eq(20))).thenReturn(List.of());
        when(mapper.countByUser(eq(7L), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(0L);

        PageResponse<TaskListItem> page = service.pageByUser(f);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(0L);
    }

    @Test
    void pageByUser_unauthenticated_throws1002() {
        TaskFilter f = new TaskFilter(null, null, null, null, null, 1, 20);

        assertThatThrownBy(() -> service.pageByUser(f))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(mapper, never()).pageByUser(anyLong(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    // ---------- helpers ----------

    private Task ownedTask(long id, long userId, String title) {
        Task t = new Task();
        t.setId(id);
        t.setUserId(userId);
        t.setTitle(title);
        t.setStatus(TaskConstants.STATUS_TODO);
        t.setPriority(TaskConstants.PRIORITY_NONE);
        return t;
    }
}