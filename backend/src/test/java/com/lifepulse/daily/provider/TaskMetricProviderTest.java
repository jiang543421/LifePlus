package com.lifepulse.daily.provider;

import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.task.TaskConstants;
import com.lifepulse.task.repository.TaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskMetricProvider 单元测试（plan §5 T3 / spec §6 service 行覆盖 ≥ 80%）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>空数据集 → 全零结构 + 三桶零 status / 四桶零 priority</li>
 *   <li>部分完成 → completionRate 按 completed / total 计算（total ≠ 0）</li>
 *   <li>全部完成 → completionRate = 1.0</li>
 *   <li>DB 中出现未知 status code → 防御性跳过、不污染 result map</li>
 *   <li>4 个 mapper 方法均以单日范围（from = to = targetDate）调用</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaskMetricProviderTest {

    private static final long USER_ID = 42L;
    private static final LocalDate DATE = LocalDate.of(2026, 7, 21);

    @Mock
    private TaskMapper taskMapper;

    private TaskMetricProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TaskMetricProvider(taskMapper);
    }

    @Test
    @DisplayName("空数据集：total=0, completed=0, rate=0.0, status/priority 全 0")
    void aggregateDaily_noTasks_returnsAllZeros() {
        when(taskMapper.countByUserDueBetween(USER_ID, DATE, DATE)).thenReturn(0L);
        when(taskMapper.countCompletedByUserDueBetween(
                USER_ID, DATE, DATE, TaskConstants.STATUS_DONE)).thenReturn(0L);
        when(taskMapper.selectStatusBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of());
        when(taskMapper.selectPriorityBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of());

        TaskMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.totalCount()).isZero();
        assertThat(m.completedCount()).isZero();
        assertThat(m.completionRate()).isEqualTo(0.0);
        assertThat(m.statusDistribution())
                .containsEntry("TODO", 0L)
                .containsEntry("DONE", 0L)
                .containsEntry("CANCELLED", 0L)
                .hasSize(3);
        assertThat(m.priorityDistribution())
                .containsEntry("NONE", 0L)
                .containsEntry("LOW", 0L)
                .containsEntry("MEDIUM", 0L)
                .containsEntry("HIGH", 0L)
                .hasSize(4);
    }

    @Test
    @DisplayName("部分完成：3 due / 1 done → completionRate ≈ 0.333")
    void aggregateDaily_partialCompletion_calculatesCompletionRate() {
        when(taskMapper.countByUserDueBetween(USER_ID, DATE, DATE)).thenReturn(3L);
        when(taskMapper.countCompletedByUserDueBetween(
                USER_ID, DATE, DATE, TaskConstants.STATUS_DONE)).thenReturn(1L);
        when(taskMapper.selectStatusBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of(bucket(TaskConstants.STATUS_TODO, 2L)));
        when(taskMapper.selectPriorityBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of(bucket(TaskConstants.PRIORITY_HIGH, 3L)));

        TaskMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.totalCount()).isEqualTo(3L);
        assertThat(m.completedCount()).isEqualTo(1L);
        assertThat(m.completionRate()).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(m.statusDistribution())
                .containsEntry("TODO", 2L)
                .containsEntry("DONE", 0L)
                .containsEntry("CANCELLED", 0L);
        assertThat(m.priorityDistribution())
                .containsEntry("HIGH", 3L)
                .containsEntry("NONE", 0L);
    }

    @Test
    @DisplayName("全部完成：5 due / 5 done → completionRate = 1.0")
    void aggregateDaily_allCompleted_returnsRate1() {
        when(taskMapper.countByUserDueBetween(USER_ID, DATE, DATE)).thenReturn(5L);
        when(taskMapper.countCompletedByUserDueBetween(
                USER_ID, DATE, DATE, TaskConstants.STATUS_DONE)).thenReturn(5L);
        when(taskMapper.selectStatusBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of(bucket(TaskConstants.STATUS_DONE, 5L)));
        when(taskMapper.selectPriorityBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of(
                        bucket(TaskConstants.PRIORITY_LOW, 2L),
                        bucket(TaskConstants.PRIORITY_HIGH, 3L)));

        TaskMetrics m = provider.aggregateDaily(USER_ID, DATE);

        assertThat(m.completionRate()).isEqualTo(1.0);
        assertThat(m.statusDistribution()).containsEntry("DONE", 5L);
    }

    @Test
    @DisplayName("DB 出现未知 status code（如 99）→ 防御性跳过、不污染 statusDistribution")
    void aggregateDaily_unknownStatusCodeInDb_ignoresUnknownBucket() {
        when(taskMapper.countByUserDueBetween(USER_ID, DATE, DATE)).thenReturn(2L);
        when(taskMapper.countCompletedByUserDueBetween(
                USER_ID, DATE, DATE, TaskConstants.STATUS_DONE)).thenReturn(0L);
        when(taskMapper.selectStatusBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of(
                        bucket(TaskConstants.STATUS_TODO, 1L),
                        bucket(99, 1L))); // 未知 code
        when(taskMapper.selectPriorityBucketsByUserDueBetween(USER_ID, DATE, DATE))
                .thenReturn(List.of());

        TaskMetrics m = provider.aggregateDaily(USER_ID, DATE);

        // 已知 code 正常填充；未知 code 不出现在 result
        assertThat(m.statusDistribution())
                .containsEntry("TODO", 1L)
                .containsEntry("DONE", 0L)
                .containsEntry("CANCELLED", 0L)
                .hasSize(3);
    }

    @Test
    @DisplayName("mapper 调用全部以单日范围（from = to = targetDate）")
    void aggregateDaily_callsMappersWithSingleDayRange() {
        when(taskMapper.countByUserDueBetween(anyLong(), eq(DATE), eq(DATE))).thenReturn(0L);
        when(taskMapper.countCompletedByUserDueBetween(
                anyLong(), eq(DATE), eq(DATE), anyInt())).thenReturn(0L);
        when(taskMapper.selectStatusBucketsByUserDueBetween(anyLong(), eq(DATE), eq(DATE)))
                .thenReturn(List.of());
        when(taskMapper.selectPriorityBucketsByUserDueBetween(anyLong(), eq(DATE), eq(DATE)))
                .thenReturn(List.of());

        provider.aggregateDaily(USER_ID, DATE);

        // capture 实参以断言 userId / date 精确一致
        ArgumentCaptor<Integer> doneCap = ArgumentCaptor.forClass(Integer.class);
        verify(taskMapper).countCompletedByUserDueBetween(eq(USER_ID), eq(DATE), eq(DATE), doneCap.capture());
        assertThat(doneCap.getValue()).isEqualTo(TaskConstants.STATUS_DONE);

        verify(taskMapper).countByUserDueBetween(USER_ID, DATE, DATE);
        verify(taskMapper).selectStatusBucketsByUserDueBetween(USER_ID, DATE, DATE);
        verify(taskMapper).selectPriorityBucketsByUserDueBetween(USER_ID, DATE, DATE);
    }

    @Test
    @DisplayName("name() 默认返回类名")
    void name_returnsSimpleClassName() {
        assertThat(provider.name()).isEqualTo("TaskMetricProvider");
    }

    // ---- helper ----

    private static Map<String, Object> bucket(int code, long cnt) {
        Map<String, Object> row = new HashMap<>();
        row.put("bucket", code);
        row.put("cnt", cnt);
        return row;
    }
}
