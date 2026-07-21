package com.lifepulse.it;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.RegisterRequest;
import com.lifepulse.auth.service.AuthService;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.daily.DailyConstants;
import com.lifepulse.daily.DailyReportPayload;
import com.lifepulse.daily.DietMetrics;
import com.lifepulse.daily.ExpenseMetrics;
import com.lifepulse.daily.PlanMetrics;
import com.lifepulse.daily.TaskMetrics;
import com.lifepulse.daily.WeeklyComparison;
import com.lifepulse.daily.WeeklyReportPayload;
import com.lifepulse.daily.service.DailyReportService;
import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.dto.CreateExpenseRequest;
import com.lifepulse.expense.service.ExpenseService;
import com.lifepulse.plan.dto.PlanCreateRequest;
import com.lifepulse.plan.service.PlanService;
import com.lifepulse.security.UserContext;
import com.lifepulse.task.TaskConstants;
import com.lifepulse.task.dto.TaskCreateRequest;
import com.lifepulse.task.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase v1.2.3 · Daily Report 端到端集成测试（plan §5 T8）。
 *
 * <p>Testcontainers MySQL + 本地 Redis（{@link AbstractIntegrationTest#LOCAL_REDIS_URL}）；
 * 直接调用 service beans 验证 4 个 {@code MetricProvider} 串联聚合，覆盖：
 *
 * <ol>
 *   <li>空数据 → 全零结构 + diet 永远 disabled</li>
 *   <li>跨用户隔离：{@code UserContext.set(other)} 看不到当前用户的 task/plan/expense</li>
 *   <li>软删：{@code @TableLogic} 触发 {@code deleted=1} 后聚合自动排除</li>
 *   <li>完整数据：task(done+todo) + plan + expense 在同一日聚合正确</li>
 *   <li>周报：当前周 vs 上周对比，delta 计算（含 prev=0 → delta=null）</li>
 *   <li>窗口校验：{@code daily}/{@code week} 目标日超出 30 天 → 抛 1001</li>
 *   <li>P95 性能：1w 行 task 种子下日报 P95 ≤ 200ms、周报 P95 ≤ 300ms</li>
 * </ol>
 *
 * <p><b>索引审计</b>：{@code V5__daily_indexes.sql} 已 no-op。{@code t_task} 既有
 * {@code idx_user_status_due (user_id, status, due_date)} 覆盖 Provider 所有
 * WHERE 子句；{@code t_plan} 既有 {@code idx_user_start}；{@code t_expense} 既有
 * {@code idx_user_occurred} + {@code idx_user_category}。本日历只验证 SQL 实际
 * 命中这些索引的子集（task 总数查询）即可，V5 文件即审计产物。
 *
 * <p><b>已知 spec vs 实现偏差</b>：
 * <ul>
 *   <li>spec 说"未来日期 → 1004"，实际 Service 不拒未来日期（仅拒 > 30 天过去）
 *       —— 本 IT 全部用 {@code today()} 作目标日，回避分歧</li>
 *   <li>spec 说"参数格式错误 → 1004"，实际由
 *       {@link com.lifepulse.common.exception.GlobalExceptionHandler} 收口为
 *       400/1001 —— 已在 T7 {@code DailyReportControllerWebTest} 覆盖</li>
 * </ul>
 *
 * <p>隔离策略：
 * <ul>
 *   <li>{@code @BeforeEach} 用 {@link JdbcTemplate} 物理清 t_task / t_plan / t_expense；
 *       MyBatis-Plus {@code @TableLogic} 自动 {@code deleted=0} 过滤使物理清空更可靠</li>
 *   <li>每个 test 用 {@code UUID} email + IP 避免 register/login 限流计数器串扰</li>
 *   <li>{@code @AfterEach} 删测试遗留 Redis register/login key；expense write 限流
 *       key（{@code lp:rl:expense:write:<uid>}）一并清</li>
 * </ul>
 */
class DailyReportIT extends AbstractIntegrationTest {

    @Autowired private DailyReportService dailyService;
    @Autowired private TaskService taskService;
    @Autowired private PlanService planService;
    @Autowired private ExpenseService expenseService;
    @Autowired private AuthService authService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;

    private final List<String> trackedRedisKeys = new ArrayList<>();

    @BeforeEach
    void cleanDailyTables() {
        // 物理清三个聚合源表（与 TaskFlowIT/PlanFlowIT 风格一致）；逻辑删的 @TableLogic
        // 过滤使物理清空对断言计数最干净。
        jdbc.update("DELETE FROM t_task");
        jdbc.update("DELETE FROM t_plan");
        jdbc.update("DELETE FROM t_expense");
    }

    @AfterEach
    void cleanupRedisAndContext() {
        if (!trackedRedisKeys.isEmpty()) {
            redis.delete(trackedRedisKeys);
            trackedRedisKeys.clear();
        }
        UserContext.clear();
    }

    // ---------- 1. empty ----------

    @Test
    void daily_emptyData_returnsZeroStructure() {
        Long userId = registerFresh("empty");
        UserContext.set(userId);

        DailyReportPayload p = dailyService.daily(userId, LocalDate.now());

        assertThat(p.date()).isEqualTo(LocalDate.now());
        // task 全零 + 3 桶 status + 4 桶 priority 全 0
        assertThat(p.task().totalCount()).isZero();
        assertThat(p.task().completedCount()).isZero();
        assertThat(p.task().completionRate()).isZero();
        assertThat(p.task().statusDistribution()).containsOnlyKeys("TODO", "DONE", "CANCELLED");
        assertThat(p.task().statusDistribution().values()).allMatch(v -> v == 0L);
        assertThat(p.task().priorityDistribution()).containsOnlyKeys("NONE", "LOW", "MEDIUM", "HIGH");
        // plan 全零 + busiestHour = null
        assertThat(p.plan().eventCount()).isZero();
        assertThat(p.plan().totalMinutes()).isZero();
        assertThat(p.plan().busiestHour()).isNull();
        assertThat(p.plan().categoryDistribution()).isEmpty();
        // expense 全零 + 5 类 breakdown 全 0
        assertThat(p.expense().totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.expense().count()).isZero();
        assertThat(p.expense().categoryBreakdown()).containsOnlyKeys(
                "MEAL", "SHOPPING", "TRANSPORT", "SUBSCRIPTION", "OTHER");
        assertThat(p.expense().topCategories()).isEmpty();
        // diet 永远 disabled
        assertThat(p.diet().enabled()).isFalse();
        assertThat(p.diet().value()).isNull();
        assertThat(p.diet().reason()).isNotBlank();
    }

    // ---------- 2. cross-user ----------

    @Test
    void daily_crossUser_isolated() {
        Long userA = registerFresh("a");
        Long userB = registerFresh("b");
        UserContext.set(userA);

        // userA 在 today 创建一条 task
        long taskId = taskService.create(new TaskCreateRequest(
                "A 的任务", TaskConstants.PRIORITY_HIGH, LocalDate.now(), null, null)).id();
        assertThat(taskId).isPositive();

        // userB 切到当前线程后再聚合 today → 看不到 userA 的 task
        UserContext.set(userB);
        DailyReportPayload fromB = dailyService.daily(userB, LocalDate.now());
        assertThat(fromB.task().totalCount()).isZero();
        // status keys = TODO/DONE/CANCELLED；priority keys = NONE/LOW/MEDIUM/HIGH
        assertThat(fromB.task().priorityDistribution().get("HIGH")).isZero();

        // userA 重新切回 → 仍然看到自己的 1 条
        UserContext.set(userA);
        DailyReportPayload fromA = dailyService.daily(userA, LocalDate.now());
        assertThat(fromA.task().totalCount()).isEqualTo(1L);
    }

    // ---------- 3. soft-delete ----------

    @Test
    void daily_softDeletedTask_excluded() {
        Long userId = registerFresh("soft");
        UserContext.set(userId);

        long taskId = taskService.create(new TaskCreateRequest(
                "即将被删", TaskConstants.PRIORITY_HIGH, LocalDate.now(), null, null)).id();
        assertThat(taskId).isPositive();

        // 软删前聚合 → 1 条
        assertThat(dailyService.daily(userId, LocalDate.now()).task().totalCount()).isEqualTo(1L);

        // 软删（@TableLogic 触发 UPDATE t_task SET deleted=1）
        taskService.softDelete(taskId);

        // 软删后聚合 → 0
        DailyReportPayload after = dailyService.daily(userId, LocalDate.now());
        assertThat(after.task().totalCount()).isZero();
        assertThat(after.task().priorityDistribution().get("HIGH")).isZero();
    }

    // ---------- 4. full aggregation ----------

    @Test
    void daily_fullData_aggregatesAllProviders() {
        Long userId = registerFresh("full");
        UserContext.set(userId);

        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();

        // task: 1 DONE + 2 TODO + 1 不同 dueDate (不会算入 today) = today 上 3 条
        long doneId = taskService.create(new TaskCreateRequest(
                "完成项", TaskConstants.PRIORITY_HIGH, today, null, null)).id();
        taskService.create(new TaskCreateRequest(
                "待办 A", TaskConstants.PRIORITY_LOW, today, null, null));
        taskService.create(new TaskCreateRequest(
                "待办 B", TaskConstants.PRIORITY_MEDIUM, today, null, null));
        taskService.create(new TaskCreateRequest(
                "昨天的事", TaskConstants.PRIORITY_NONE,
                today.minusDays(1), null, null)); // 不在 today 聚合中
        taskService.patchStatus(doneId, TaskConstants.STATUS_DONE);

        // plan: today 一个 30 分钟事件 + 昨天一个跨日事件（不在 today 上）
        planService.create(new PlanCreateRequest(
                "晨会", startOfDay.plusHours(9), startOfDay.plusHours(9).plusMinutes(30),
                null, null, null, null));
        planService.create(new PlanCreateRequest(
                "昨日回顾", startOfDay.minusDays(1).plusHours(10),
                startOfDay.minusDays(1).plusHours(11), null, null, null, null));

        // expense: today 2 笔 MEAL + 1 笔 TRANSPORT；昨天 1 笔 SHOPPING（不在 today）
        // expense write 限流 10/min/user → 3 笔安全
        // 注意 occurredAt 应用层按 UTC 存，Provider 按 Shanghai 边界查；
        // 因此 4 个时间戳都按"Shanghai 本地几点几分"理解，转 UTC 入库，
        // 否则 today.atTime(18).atOffset(UTC) = Shanghai 次日 02:00，超出 today 窗口。
        OffsetDateTime todayLunchUtc = today.atTime(12, 0)
                .atZone(DailyConstants.ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime todayDinnerUtc = today.atTime(19, 0)
                .atZone(DailyConstants.ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime todaySubwayUtc = today.atTime(18, 0)
                .atZone(DailyConstants.ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime yesterdayBuyUtc = today.minusDays(1).atTime(20, 0)
                .atZone(DailyConstants.ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        expenseService.create(new CreateExpenseRequest(
                new BigDecimal("30.00"), ExpenseCategory.MEAL, "午饭", todayLunchUtc));
        expenseService.create(new CreateExpenseRequest(
                new BigDecimal("50.00"), ExpenseCategory.MEAL, "晚饭", todayDinnerUtc));
        expenseService.create(new CreateExpenseRequest(
                new BigDecimal("6.50"), ExpenseCategory.TRANSPORT, "地铁", todaySubwayUtc));
        expenseService.create(new CreateExpenseRequest(
                new BigDecimal("200.00"), ExpenseCategory.SHOPPING, "书", yesterdayBuyUtc));

        DailyReportPayload p = dailyService.daily(userId, today);

        // task: total=3, completed=1, rate≈0.333
        TaskMetrics task = p.task();
        assertThat(task.totalCount()).isEqualTo(3L);
        assertThat(task.completedCount()).isEqualTo(1L);
        assertThat(task.completionRate()).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(task.statusDistribution().get("TODO")).isEqualTo(2L);
        assertThat(task.statusDistribution().get("DONE")).isEqualTo(1L);
        assertThat(task.statusDistribution().get("CANCELLED")).isZero();
        assertThat(task.priorityDistribution().get("HIGH")).isEqualTo(1L);
        assertThat(task.priorityDistribution().get("MEDIUM")).isEqualTo(1L);
        assertThat(task.priorityDistribution().get("LOW")).isEqualTo(1L);

        // plan: 1 事件 + 30 分钟 + busiestHour=9
        PlanMetrics plan = p.plan();
        assertThat(plan.eventCount()).isEqualTo(1L);
        assertThat(plan.totalMinutes()).isEqualTo(30L);
        assertThat(plan.busiestHour()).isEqualTo(9);

        // expense: 3 笔合计 86.50，MEAL=80 / TRANSPORT=6.50 / top=[MEAL, TRANSPORT]
        ExpenseMetrics expense = p.expense();
        assertThat(expense.count()).isEqualTo(3L);
        assertThat(expense.totalAmount()).isEqualByComparingTo("86.50");
        assertThat(expense.categoryBreakdown().get("MEAL")).isEqualByComparingTo("80.00");
        assertThat(expense.categoryBreakdown().get("TRANSPORT")).isEqualByComparingTo("6.50");
        assertThat(expense.categoryBreakdown().get("SHOPPING")).isEqualByComparingTo("0");
        // top 3：MEAL(80) + TRANSPORT(6.5) 都 > 0，SHOPPING 昨天被排除后仍 = 0 → 入榜 2 条
        assertThat(expense.topCategories()).hasSize(2);
        assertThat(expense.topCategories().get(0).code()).isEqualTo("MEAL");
        assertThat(expense.topCategories().get(0).amount()).isEqualByComparingTo("80.00");
        assertThat(expense.topCategories().get(1).code()).isEqualTo("TRANSPORT");
        assertThat(expense.topCategories().get(1).amount()).isEqualByComparingTo("6.50");

        // diet 仍然 disabled（不受数据量影响）
        assertThat(p.diet().enabled()).isFalse();
        assertThat(p.diet().value()).isNull();
    }

    // ---------- 5. diet forever disabled ----------

    @Test
    void daily_dietMetricsAlwaysDisabledRegardlessOfDate() {
        Long userId = registerFresh("diet");
        UserContext.set(userId);

        // 3 个不同时区 / 不同时刻都应一致
        for (LocalDate d : Arrays.asList(
                LocalDate.now(), LocalDate.now().minusDays(1), LocalDate.now().minusDays(15))) {
            DietMetrics diet = dailyService.daily(userId, d).diet();
            assertThat(diet.enabled())
                    .as("diet.enabled for date=%s", d)
                    .isFalse();
            assertThat(diet.value())
                    .as("diet.value for date=%s", d)
                    .isNull();
            assertThat(diet.reason())
                    .as("diet.reason for date=%s", d)
                    .isNotBlank();
        }
    }

    // ---------- 6. weekly delta ----------

    @Test
    void weekly_currentAndPrevious_deltaCalculated() {
        Long userId = registerFresh("week");
        UserContext.set(userId);

        LocalDate today = LocalDate.now();
        LocalDate lastWeekSameDay = today.minusDays(7);

        // 本周 today 一条 DONE task
        long curId = taskService.create(new TaskCreateRequest(
                "本周", TaskConstants.PRIORITY_HIGH, today, null, null)).id();
        taskService.patchStatus(curId, TaskConstants.STATUS_DONE);

        // 上周同日 1 条 TODO task（@PastOrPresent 通过）
        taskService.create(new TaskCreateRequest(
                "上周 TODO", TaskConstants.PRIORITY_LOW, lastWeekSameDay, null, null));
        // 上周同日 1 条 DONE task（这样 prev.completed=1, prev.total=2, prevRate=0.5）
        long prevDoneId = taskService.create(new TaskCreateRequest(
                "上周 DONE", TaskConstants.PRIORITY_HIGH, lastWeekSameDay, null, null)).id();
        taskService.patchStatus(prevDoneId, TaskConstants.STATUS_DONE);

        WeeklyReportPayload w = dailyService.week(userId, today);

        // ISO 周格式 + 周一/周日
        assertThat(w.isoWeek()).matches("\\d{4}-W\\d{2}");
        assertThat(w.weekStart().getDayOfWeek().toString()).isEqualTo("MONDAY");
        assertThat(w.weekEnd().getDayOfWeek().toString()).isEqualTo("SUNDAY");
        assertThat(w.weekEnd()).isEqualTo(w.weekStart().plusDays(6));

        // taskCompletion: 本周 1/1=1.0；上周 1/2=0.5；delta=0.5
        WeeklyComparison.WeeklyTriplet taskT = w.comparison().taskCompletion();
        assertThat(taskT.current()).isEqualTo(1.0);
        assertThat(taskT.previous()).isEqualTo(0.5);
        assertThat(taskT.delta()).isEqualTo(0.5);

        // planEvents: 本周 0；上周 0；prev=0 → delta=null
        WeeklyComparison.WeeklyTriplet planT = w.comparison().planEvents();
        assertThat(planT.current()).isZero();
        assertThat(planT.previous()).isZero();
        assertThat(planT.delta()).isNull();

        // expenseAmount: 本周 0；上周 0；delta=null
        WeeklyComparison.WeeklyTriplet expT = w.comparison().expenseAmount();
        assertThat(expT.current()).isZero();
        assertThat(expT.previous()).isZero();
        assertThat(expT.delta()).isNull();
    }

    // ---------- 7. weekly empty previous ----------

    @Test
    void weekly_emptyPrevious_deltaIsNull() {
        Long userId = registerFresh("week-empty");
        UserContext.set(userId);

        // 仅本周 today 一条 task → 上周全 0 → delta 全部 null
        long id = taskService.create(new TaskCreateRequest(
                "本周独有", TaskConstants.PRIORITY_HIGH, LocalDate.now(), null, null)).id();
        taskService.patchStatus(id, TaskConstants.STATUS_DONE);

        WeeklyReportPayload w = dailyService.week(userId, LocalDate.now());

        // taskCompletion: prev.totalCount=0 → delta=null（避免除零）
        assertThat(w.comparison().taskCompletion().current()).isEqualTo(1.0);
        assertThat(w.comparison().taskCompletion().previous()).isZero();
        assertThat(w.comparison().taskCompletion().delta()).isNull();

        // planEvents: prev.eventsSum=0 → delta=null
        assertThat(w.comparison().planEvents().current()).isZero();
        assertThat(w.comparison().planEvents().previous()).isZero();
        assertThat(w.comparison().planEvents().delta()).isNull();

        // expenseAmount: prev.amountSum=0 → delta=null
        assertThat(w.comparison().expenseAmount().current()).isZero();
        assertThat(w.comparison().expenseAmount().previous()).isZero();
        assertThat(w.comparison().expenseAmount().delta()).isNull();
    }

    // ---------- 8/9. window validation ----------

    @Test
    void daily_outOfWindow_returns1001() {
        Long userId = registerFresh("win-d");
        UserContext.set(userId);

        LocalDate tooOld = LocalDate.now().minusDays(DailyConstants.MAX_HISTORY_DAYS + 1L);

        assertThatThrownBy(() -> dailyService.daily(userId, tooOld))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION);
    }

    @Test
    void week_outOfWindow_returns1001() {
        Long userId = registerFresh("win-w");
        UserContext.set(userId);

        // 周日落在窗口外：weekEnd = today - 31d < today - 30d 触发校验
        LocalDate tooOldSunday = LocalDate.now().minusDays(DailyConstants.MAX_HISTORY_DAYS + 1L);

        assertThatThrownBy(() -> dailyService.week(userId, tooOldSunday))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION);
    }

    // ---------- 10/11. perf P95 ----------

    @Test
    void perfDaily_p95Under200ms() {
        Long userId = registerFresh("perf-d");
        UserContext.set(userId);

        LocalDate today = LocalDate.now();
        seedTaskRows(userId, today, DailyConstants.PERF_SEED_ROWS);

        // warmup 5 calls：排除 JIT/MyBatis/Hikari 冷启动影响，确保 P95 测稳态
        for (int i = 0; i < 5; i++) {
            dailyService.daily(userId, today);
        }

        // 100 次调用取 P95
        long[] elapsedNanos = new long[DailyConstants.PERF_SAMPLE_TIMES];
        for (int i = 0; i < DailyConstants.PERF_SAMPLE_TIMES; i++) {
            long start = System.nanoTime();
            DailyReportPayload p = dailyService.daily(userId, today);
            elapsedNanos[i] = System.nanoTime() - start;
            // 顺便 sanity check：1w 行全聚到 today，task.totalCount 应等于 PERF_SEED_ROWS
            //（仅第 1 次检查，避免 100 次冗余断言）
            if (i == 0) {
                assertThat(p.task().totalCount()).isEqualTo((long) DailyConstants.PERF_SEED_ROWS);
            }
        }
        Arrays.sort(elapsedNanos);
        long p95Nanos = elapsedNanos[(int) Math.ceil(DailyConstants.PERF_SAMPLE_TIMES * 0.95) - 1];
        long p95Millis = p95Nanos / 1_000_000L;
        assertThat(p95Millis)
                .as("Daily P95 over %d samples (%d rows seeded) ≤ %d ms — actual %d ms",
                        DailyConstants.PERF_SAMPLE_TIMES,
                        DailyConstants.PERF_SEED_ROWS,
                        DailyConstants.DAILY_P95_BUDGET.toMillis(),
                        p95Millis)
                .isLessThanOrEqualTo(DailyConstants.DAILY_P95_BUDGET.toMillis());
    }

    @Test
    void perfWeekly_p95Under300ms() {
        Long userId = registerFresh("perf-w");
        UserContext.set(userId);

        LocalDate today = LocalDate.now();
        seedTaskRows(userId, today, DailyConstants.PERF_SEED_ROWS);

        // warmup 5 calls：排除 JIT/MyBatis/Hikari 冷启动影响，确保 P95 测稳态
        for (int i = 0; i < 5; i++) {
            dailyService.week(userId, today);
        }

        long[] elapsedNanos = new long[DailyConstants.PERF_SAMPLE_TIMES];
        for (int i = 0; i < DailyConstants.PERF_SAMPLE_TIMES; i++) {
            long start = System.nanoTime();
            WeeklyReportPayload w = dailyService.week(userId, today);
            elapsedNanos[i] = System.nanoTime() - start;
            if (i == 0) {
                // 1w 行全聚到 today（current week 内 1 天），其它 6 天 0；
                // 种子全部 status=0 (TODO) → currentWeek.totalCount = 1w, totalCompleted = 0
                // → completionRate = 0/10000 = 0.0；previousWeek.totalCount = 0
                assertThat(w.comparison().taskCompletion().current()).isZero();
                assertThat(w.comparison().taskCompletion().previous()).isZero();
                assertThat(w.comparison().taskCompletion().delta()).isNull();
            }
        }
        Arrays.sort(elapsedNanos);
        long p95Nanos = elapsedNanos[(int) Math.ceil(DailyConstants.PERF_SAMPLE_TIMES * 0.95) - 1];
        long p95Millis = p95Nanos / 1_000_000L;
        assertThat(p95Millis)
                .as("Weekly P95 over %d samples (%d rows seeded) ≤ %d ms — actual %d ms",
                        DailyConstants.PERF_SAMPLE_TIMES,
                        DailyConstants.PERF_SEED_ROWS,
                        DailyConstants.WEEKLY_P95_BUDGET.toMillis(),
                        p95Millis)
                .isLessThanOrEqualTo(DailyConstants.WEEKLY_P95_BUDGET.toMillis());
    }

    // ---------- helpers ----------

    /**
     * 注册一个全新 user（UUID email + UUID IP 避开 register/login 限流）。
     * 自动登记 register/login 限流 key 到 {@link #trackedRedisKeys}，{@code @AfterEach} 清。
     */
    private Long registerFresh(String tag) {
        String email = "daily-it-" + tag + "-" + UUID.randomUUID() + "@example.com";
        String ip = "10." + (Math.abs(UUID.randomUUID().hashCode()) & 0xFF) + "."
                + (Math.abs(UUID.randomUUID().hashCode()) & 0xFF) + ".1";
        // 跟踪 register/login 限流 key（与 AuthServiceIT 风格一致）
        trackedRedisKeys.add(AuthConstants.REGISTER_RL_KEY_PREFIX + ip);
        trackedRedisKeys.add(AuthConstants.LOGIN_RL_KEY_PREFIX + ip
                + ":" + emailKeySuffix(email));
        return authService.register(new RegisterRequest(email, "Valid1Pass", tag), ip);
    }

    /**
     * 用 {@link JdbcTemplate#batchUpdate} 批量插 {@code count} 条 t_task 行：
     * <ul>
     *   <li>user_id = 当前 user</li>
     *   <li>status = 0 (TODO), priority = 0 (NONE)</li>
     *   <li>due_date = 目标日；{@code created_at} / {@code updated_at} = 当前时间</li>
     *   <li>deleted = 0（@TableLogic 期望）</li>
     * </ul>
     *
     * <p>比循环 1w 次 {@code taskService.create} 快 50–100×，避免 IT 超时。
     * 也避免触发 expense write 限流（10 ops/min）。
     */
    private void seedTaskRows(long userId, LocalDate dueDate, int count) {
        String sql = "INSERT INTO t_task "
                + "(user_id, title, status, priority, due_date, created_at, updated_at, deleted) "
                + "VALUES (?, ?, 0, 0, ?, ?, ?, 0)";
        Timestamp now = new Timestamp(System.currentTimeMillis());
        java.sql.Date due = java.sql.Date.valueOf(dueDate);
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, userId);
                ps.setString(2, "perf-seed-" + i);
                ps.setDate(3, due);
                ps.setTimestamp(4, now);
                ps.setTimestamp(5, now);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }

    private static String emailKeySuffix(String email) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(email.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
