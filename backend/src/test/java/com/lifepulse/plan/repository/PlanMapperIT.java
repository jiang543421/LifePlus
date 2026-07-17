package com.lifepulse.plan.repository;

import com.lifepulse.it.AbstractIntegrationTest;
import com.lifepulse.plan.PlanConstants;
import com.lifepulse.plan.entity.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3-A · t_plan Mapper 集成测试（spec §6.4 Testcontainers）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>insert + AUTO_INCREMENT 回填 + 时间字段自动填充 + deleted 默认 0 + reminder_min 默认 15</li>
 *   <li>{@link PlanMapper#findByUserAndId} 跨用户返回 empty（1003 防御的基础）</li>
 *   <li>{@link PlanMapper#pageByUser} 日期范围 + 分页 + 跨用户隔离</li>
 *   <li>{@link PlanMapper#countByUser} 与 pageByUser 总数对齐</li>
 *   <li>BaseMapper deleteById 软删（{@code @TableLogic} 自动 {@code deleted = 1}）</li>
 * </ul>
 */
class PlanMapperIT extends AbstractIntegrationTest {

    @Autowired
    private PlanMapper planMapper;

    /**
     * MySQL 容器在 JVM 内被 {@link AbstractIntegrationTest#MYSQL} 复用，
     * 各 IT class 共享同一 schema 的同一组 t_plan 行；
     * 每个用例前先清空非软删行，避免跨用例数据污染。
     */
    @BeforeEach
    void wipeTable() {
        List<Plan> all = planMapper.selectList(null);
        for (Plan p : all) {
            planMapper.deleteById(p.getId());
        }
    }

    // ---------- insert + auto-fill ----------

    @Test
    void insert_setsAutoIncrementIdAndAutoFillsTimestamps() {
        // Arrange
        Plan p = newPlan(1L, "周会", LocalDateTime.of(2026, 8, 1, 10, 0));

        // Act
        planMapper.insert(p);

        // Assert
        assertThat(p.getId()).as("AUTO_INCREMENT 回填").isNotNull();
        assertThat(p.getCreatedAt()).as("INSERT 自动填充 createdAt").isNotNull();
        assertThat(p.getUpdatedAt()).as("INSERT 自动填充 updatedAt").isNotNull();

        Plan reloaded = planMapper.selectById(p.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getDeleted()).as("DB DEFAULT 0 + @TableLogic 加载").isEqualTo(0);
        assertThat(reloaded.getAllDay()).as("allDay 默认 0（非全天）").isEqualTo(0);
        assertThat(reloaded.getReminderMin())
                .as("reminder_min 默认 15（DB DEFAULT）")
                .isEqualTo(PlanConstants.DEFAULT_REMINDER_MIN);
    }

    @Test
    void insert_allDay_setsFlagAndStoresTimesAsGiven() {
        // Arrange
        Plan p = newPlan(1L, "全天会议", LocalDateTime.of(2026, 8, 1, 0, 0));
        p.setEndTime(LocalDateTime.of(2026, 8, 1, 23, 59));
        p.setAllDay(1);

        // Act
        planMapper.insert(p);

        // Assert
        Plan reloaded = planMapper.selectById(p.getId());
        assertThat(reloaded.getAllDay()).isEqualTo(1);
        assertThat(reloaded.getStartTime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(reloaded.getEndTime()).isEqualTo(LocalDateTime.of(2026, 8, 1, 23, 59));
    }

    // ---------- findByUserAndId（1003 防御基础） ----------

    @Test
    void findByUserAndId_ownerMatch_returnsPlan() {
        Plan p = newPlan(1L, "约会", LocalDateTime.of(2026, 8, 5, 19, 0));
        planMapper.insert(p);

        Optional<Plan> found = planMapper.findByUserAndId(1L, p.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("约会");
    }

    @Test
    void findByUserAndId_otherUser_returnsEmpty() {
        Plan p = newPlan(1L, "user1 的日程", LocalDateTime.of(2026, 8, 5, 19, 0));
        planMapper.insert(p);

        // user=2 试图读取 user=1 的 plan → 应为空
        Optional<Plan> found = planMapper.findByUserAndId(2L, p.getId());

        assertThat(found).as("跨用户必须返回 empty，service 才会抛 1003").isEmpty();
    }

    @Test
    void findByUserAndId_softDeleted_returnsEmpty() {
        Plan p = newPlan(1L, "将被软删", LocalDateTime.of(2026, 8, 5, 19, 0));
        planMapper.insert(p);
        planMapper.deleteById(p.getId());

        Optional<Plan> found = planMapper.findByUserAndId(1L, p.getId());

        assertThat(found).as("软删后不可见").isEmpty();
    }

    // ---------- pageByUser 范围查询 ----------

    @Test
    void pageByUser_appliesDateRangeAndSorts() {
        // Arrange：3 条在范围内 + 1 条下界之前 + 1 条上界之后
        planMapper.insert(newPlan(1L, "in-1", LocalDateTime.of(2026, 8, 10, 9, 0)));
        planMapper.insert(newPlan(1L, "in-2", LocalDateTime.of(2026, 8, 15, 14, 0)));
        planMapper.insert(newPlan(1L, "in-3", LocalDateTime.of(2026, 8, 20, 18, 0)));
        planMapper.insert(newPlan(1L, "before", LocalDateTime.of(2026, 7, 31, 23, 59)));
        planMapper.insert(newPlan(1L, "after", LocalDateTime.of(2026, 9, 1, 0, 0)));

        // Act
        List<Plan> range = planMapper.pageByUser(
                1L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                0, 100);

        // Assert：3 条命中，按 start_time ASC 排序
        assertThat(range).hasSize(3);
        assertThat(range).extracting(Plan::getTitle)
                .containsExactly("in-1", "in-2", "in-3");
    }

    @Test
    void pageByUser_inclusiveBounds_includeEdges() {
        // 下界/上界正好命中 start_time → 包含
        planMapper.insert(newPlan(1L, "edge-low", LocalDateTime.of(2026, 8, 1, 0, 0)));
        planMapper.insert(newPlan(1L, "edge-high", LocalDateTime.of(2026, 8, 31, 23, 59)));
        planMapper.insert(newPlan(1L, "just-before", LocalDateTime.of(2026, 7, 31, 23, 59, 59)));

        List<Plan> range = planMapper.pageByUser(
                1L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                0, 100);

        assertThat(range).extracting(Plan::getTitle)
                .containsExactlyInAnyOrder("edge-low", "edge-high");
    }

    @Test
    void pageByUser_paginates() {
        // Arrange：5 条都在范围内
        for (int i = 0; i < 5; i++) {
            planMapper.insert(newPlan(1L, "p-" + i, LocalDateTime.of(2026, 8, 10 + i, 10, 0)));
        }

        // Act：size=2, offset=0 / 2 / 4
        List<Plan> page0 = planMapper.pageByUser(
                1L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                0, 2);
        List<Plan> page1 = planMapper.pageByUser(
                1L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                2, 2);
        List<Plan> page2 = planMapper.pageByUser(
                1L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                4, 2);

        // Assert
        assertThat(page0).hasSize(2);
        assertThat(page1).hasSize(2);
        assertThat(page2).hasSize(1);
        // 排序稳定：start_time ASC
        assertThat(page0.get(0).getTitle()).isEqualTo("p-0");
        assertThat(page0.get(1).getTitle()).isEqualTo("p-1");
    }

    @Test
    void pageByUser_otherUser_returnsEmpty() {
        planMapper.insert(newPlan(1L, "user1", LocalDateTime.of(2026, 8, 15, 10, 0)));

        List<Plan> result = planMapper.pageByUser(
                2L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59),
                0, 100);

        assertThat(result).as("跨用户不可见").isEmpty();
    }

    @Test
    void pageByUser_nullBounds_returnsAllUserPlans() {
        planMapper.insert(newPlan(1L, "any-1", LocalDateTime.of(2026, 7, 1, 10, 0)));
        planMapper.insert(newPlan(1L, "any-2", LocalDateTime.of(2026, 12, 31, 10, 0)));
        // user=2 的干扰数据，应被 user_id 过滤掉
        planMapper.insert(newPlan(2L, "user2-plan", LocalDateTime.of(2026, 8, 15, 10, 0)));

        List<Plan> all = planMapper.pageByUser(1L, null, null, 0, 100);

        assertThat(all).extracting(Plan::getTitle)
                .containsExactlyInAnyOrder("any-1", "any-2");
    }

    // ---------- countByUser ----------

    @Test
    void countByUser_matchesPageTotal() {
        planMapper.insert(newPlan(1L, "in-1", LocalDateTime.of(2026, 8, 10, 9, 0)));
        planMapper.insert(newPlan(1L, "in-2", LocalDateTime.of(2026, 8, 15, 14, 0)));
        planMapper.insert(newPlan(1L, "out", LocalDateTime.of(2026, 7, 1, 9, 0)));

        long total = planMapper.countByUser(
                1L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59));

        assertThat(total).isEqualTo(2L);
    }

    // ---------- 软删 ----------

    @Test
    void deleteById_setsDeletedFlagAndHidesFromSelect() {
        Plan p = newPlan(1L, "即将消失", LocalDateTime.of(2026, 8, 15, 10, 0));
        planMapper.insert(p);
        Long id = p.getId();

        planMapper.deleteById(id);

        // BaseMapper 的逻辑删除：selectById 返回 null（已软删）
        assertThat(planMapper.selectById(id)).isNull();
        // 自定义 findByUserAndId 也应返回 empty
        assertThat(planMapper.findByUserAndId(1L, id)).isEmpty();
    }

    // ---------- helpers ----------

    private Plan newPlan(long userId, String title, LocalDateTime startTime) {
        Plan p = new Plan();
        p.setUserId(userId);
        p.setTitle(title);
        p.setStartTime(startTime);
        p.setEndTime(startTime.plusHours(1));
        // allDay / location / note / reminderMin 走 DB DEFAULT 或 null
        return p;
    }
}