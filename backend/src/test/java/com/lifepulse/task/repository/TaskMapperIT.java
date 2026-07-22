package com.lifepulse.task.repository;

import com.lifepulse.it.AbstractIntegrationTest;
import com.lifepulse.task.TaskConstants;
import com.lifepulse.task.entity.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2-A · t_task Mapper 集成测试（spec §6.4 Testcontainers）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>insert + AUTO_INCREMENT 回填 + 时间字段自动填充 + deleted 默认 0</li>
 *   <li>{@link TaskMapper#findByUserAndId} 跨用户返回 empty（1003 防御的基础）</li>
 *   <li>{@link TaskMapper#updateStatusByUser} 跨用户返回 0 行</li>
 *   <li>{@link TaskMapper#pageByUser} 多过滤组合 + 分页</li>
 *   <li>{@link TaskMapper#countByUser} 与 pageByUser 总数对齐</li>
 *   <li>{@link TaskMapper#listByPlan} plan_id 过滤</li>
 *   <li>BaseMapper deleteById 软删（{@code @TableLogic} 自动 {@code deleted = 1}）</li>
 * </ul>
 */
class TaskMapperIT extends AbstractIntegrationTest {

    @Autowired
    private TaskMapper taskMapper;

    /**
     * MySQL 容器在 JVM 内被 {@link AbstractIntegrationTest#MYSQL} 复用，
     * 各 IT class 共享同一 schema 的同一组 t_task 行；
     * 每个用例前先清空，避免跨用例数据污染。
     */
    @BeforeEach
    void wipeTable() {
        // @TableLogic 默认 filter 只作用于 select；deleteById 走逻辑删除 SQL，
        // 因此先用 BaseMapper 自带的物理删除绕过 @TableLogic 不存在的 API，
        // 改用 @Sql 或直连 SQL：这里用最简方式——selectById/deleteById 配合 force。
        // BaseMapper 没有物理删 API；采用最干净路径：循环单条软删 + 不留尾。
        // 实际是直接用 selectList + deleteById 把所有行删掉（@TableLogic 生效 → deleted=1）。
        List<Task> all = taskMapper.selectList(null);
        for (Task t : all) {
            taskMapper.deleteById(t.getId());
        }
    }

    // ---------- insert + auto-fill ----------

    @Test
    void insert_setsAutoIncrementIdAndAutoFillsTimestamps() {
        // Arrange
        Task t = newTask(1L, "写计划", null, LocalDate.of(2026, 8, 1));

        // Act
        taskMapper.insert(t);

        // Assert
        assertThat(t.getId()).as("AUTO_INCREMENT 回填").isNotNull();
        assertThat(t.getCreatedAt()).as("INSERT 自动填充 createdAt").isNotNull();
        assertThat(t.getUpdatedAt()).as("INSERT 自动填充 updatedAt").isNotNull();
        // @TableLogic 字段在 insert 时不自动填充（依赖 DB DEFAULT 0）；
        // 重新 select 验证 DB 列确为 0。
        Task reloaded = taskMapper.selectById(t.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getDeleted()).as("DB DEFAULT 0 + @TableLogic 加载").isEqualTo(0);
        assertThat(reloaded.getStatus()).as("status 默认 0（待办）").isEqualTo(TaskConstants.STATUS_TODO);
        assertThat(reloaded.getPriority()).as("priority 默认 0（无）").isEqualTo(TaskConstants.PRIORITY_NONE);
    }

    // ---------- findByUserAndId（1003 防御基础） ----------

    @Test
    void findByUserAndId_ownerMatch_returnsTask() {
        Task t = newTask(1L, "买菜", 100L, null);
        taskMapper.insert(t);

        Optional<Task> found = taskMapper.findByUserAndId(1L, t.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("买菜");
    }

    @Test
    void findByUserAndId_otherUser_returnsEmpty() {
        Task t = newTask(1L, "user1 的任务", null, null);
        taskMapper.insert(t);

        // user=2 试图读取 user=1 的 task → 应为空
        Optional<Task> found = taskMapper.findByUserAndId(2L, t.getId());

        assertThat(found).as("跨用户必须返回 empty，service 才会抛 1003").isEmpty();
    }

    @Test
    void findByUserAndId_softDeleted_returnsEmpty() {
        Task t = newTask(1L, "将被软删", null, null);
        taskMapper.insert(t);
        taskMapper.deleteById(t.getId());

        Optional<Task> found = taskMapper.findByUserAndId(1L, t.getId());

        assertThat(found).as("软删后不可见").isEmpty();
    }

    // ---------- updateStatusByUser ----------

    @Test
    void updateStatusByUser_owner_returnsOneRowAffected() {
        Task t = newTask(1L, "切状态", null, null);
        taskMapper.insert(t);

        int rows = taskMapper.updateStatusByUser(1L, t.getId(), TaskConstants.STATUS_DONE);

        assertThat(rows).isEqualTo(1);
        Task reloaded = taskMapper.selectById(t.getId());
        assertThat(reloaded.getStatus()).isEqualTo(TaskConstants.STATUS_DONE);
    }

    @Test
    void updateStatusByUser_otherUser_returnsZero() {
        Task t = newTask(1L, "user1 的任务", null, null);
        taskMapper.insert(t);

        // user=2 试图改 user=1 的 task → 0 行 → service 抛 1003
        int rows = taskMapper.updateStatusByUser(2L, t.getId(), TaskConstants.STATUS_DONE);

        assertThat(rows).isEqualTo(0);
        Task reloaded = taskMapper.selectById(t.getId());
        assertThat(reloaded.getStatus()).as("状态不变").isEqualTo(TaskConstants.STATUS_TODO);
    }

    // ---------- listByPlan ----------

    @Test
    void listByPlan_returnsLinkedTasksOnly() {
        Task a = newTask(1L, "planA-1", 100L, null);
        Task b = newTask(1L, "planA-2", 100L, null);
        Task c = newTask(1L, "planB-1", 200L, null);
        Task d = newTask(1L, "no-plan", null, null);
        taskMapper.insert(a);
        taskMapper.insert(b);
        taskMapper.insert(c);
        taskMapper.insert(d);

        List<Task> planA = taskMapper.listByPlan(1L, 100L);

        assertThat(planA).hasSize(2);
        assertThat(planA).extracting(Task::getTitle).containsExactlyInAnyOrder("planA-1", "planA-2");
    }

    @Test
    void listByPlan_otherUser_returnsEmpty() {
        Task t = newTask(1L, "user1 的 plan task", 100L, null);
        taskMapper.insert(t);

        List<Task> result = taskMapper.listByPlan(2L, 100L);

        assertThat(result).as("跨用户不可见").isEmpty();
    }

    // ---------- pageByUser + countByUser ----------

    @Test
    void pageByUser_appliesFiltersAndPaginates() {
        // Arrange：5 条待办、3 条已完成、不同优先级 / 截止 / 标签
        for (int i = 0; i < 5; i++) {
            Task t = newTask(1L, "todo-" + i, null,
                    LocalDate.of(2026, 8, 1).plusDays(i));
            t.setPriority(i % 2);
            t.setTag(i % 2 == 0 ? "work" : "home");
            taskMapper.insert(t);
        }
        for (int i = 0; i < 3; i++) {
            Task t = newTask(1L, "done-" + i, null, null);
            t.setStatus(TaskConstants.STATUS_DONE);
            taskMapper.insert(t);
        }

        // Act：过滤 status=TODO + tag=work + size=2 offset=0
        List<Task> page1 = taskMapper.pageByUser(1L,
                TaskConstants.STATUS_TODO, null, "work",
                null, null, 0, 2);
        long total = taskMapper.countByUser(1L,
                TaskConstants.STATUS_TODO, null, "work",
                null, null);

        // Assert：3 条 (todo-0/2/4) 匹配，分页取前 2 条
        assertThat(total).isEqualTo(3L);
        assertThat(page1).hasSize(2);
        assertThat(page1).extracting(Task::getStatus)
                .containsOnly(TaskConstants.STATUS_TODO);
        assertThat(page1).extracting(Task::getTag).containsOnly("work");
    }

    @Test
    void pageByUser_dueRange_appliesBounds() {
        Task in = newTask(1L, "范围内", null, LocalDate.of(2026, 8, 15));
        Task before = newTask(1L, "下界之前", null, LocalDate.of(2026, 8, 1));
        Task after = newTask(1L, "上界之后", null, LocalDate.of(2026, 8, 31));
        taskMapper.insert(in);
        taskMapper.insert(before);
        taskMapper.insert(after);

        List<Task> range = taskMapper.pageByUser(1L,
                null, null, null,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20),
                0, 10);

        assertThat(range).hasSize(1);
        assertThat(range.get(0).getTitle()).isEqualTo("范围内");
    }

    @Test
    void pageByUser_otherUser_returnsEmpty() {
        Task t = newTask(1L, "user1", null, null);
        taskMapper.insert(t);

        List<Task> result = taskMapper.pageByUser(2L, null, null, null, null, null, 0, 20);

        assertThat(result).isEmpty();
    }

    // ---------- 软删 ----------

    @Test
    void deleteById_setsDeletedFlagAndHidesFromSelect() {
        Task t = newTask(1L, "即将消失", null, null);
        taskMapper.insert(t);
        Long id = t.getId();

        taskMapper.deleteById(id);

        // BaseMapper 的逻辑删除：selectById 返回 null（已软删）
        assertThat(taskMapper.selectById(id)).isNull();
        // 自定义 findByUserAndId 也应返回 empty（@TableLogic 已注入 deleted=0 过滤不适用此方法）
        assertThat(taskMapper.findByUserAndId(1L, id)).isEmpty();
    }

    // ---------- helpers ----------

    private Task newTask(long userId, String title, Long planId, LocalDate dueDate) {
        Task t = new Task();
        t.setUserId(userId);
        t.setTitle(title);
        t.setPlanId(planId);
        t.setDueDate(dueDate);
        return t;
    }
}
