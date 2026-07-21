package com.lifepulse.ai.provider;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import com.lifepulse.task.repository.TaskMapper;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 任务完成率 provider（spec §7.1）。
 *
 * <p>完成率 = 今日已完成任务数 / 今日总任务数 * 100。
 * 无任务时返回 0 + NONE（不算"有意义信号"）。
 */
@Component
public class TaskAiProvider implements AiInsightProvider {

    private final TaskMapper taskMapper;

    public TaskAiProvider(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public String key() {
        return AiConstants.PROVIDER_TASK;
    }

    @Override
    public boolean isEnabled(Long userId) {
        // Task 模块在 MVP1 已上线；无需配置开关
        return true;
    }

    @Override
    public MetricValue collect(Long userId, AiCollectContext ctx) {
        int total = taskMapper.countTodayTasks(userId, ctx.today());
        int done = taskMapper.countTodayCompletedTasks(userId, ctx.today());
        int rate = total == 0 ? 0 : Math.round((float) done * 100 / total);
        return new MetricValue(
            new BigDecimal(rate),
            "%",
            total == 0 ? Trend.NONE : Trend.FLAT
        );
    }
}