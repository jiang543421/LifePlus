package com.lifepulse.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块配置项（spec §7.1）。
 *
 * <p>5 个 enabled 开关控制 provider 启用状态：
 * <ul>
 *   <li>task / plan / expense / diet 默认 true（对应模块已合入 main）</li>
 *   <li>daily 默认 false（v1.2.3 合并后改 true）</li>
 * </ul>
 *
 * <p>5 个开关独立，便于灰度与回滚。
 */
@ConfigurationProperties(prefix = "lp.ai")
public class AiInsightProperties {

    private boolean taskEnabled = true;
    private boolean planEnabled = true;
    private boolean expenseEnabled = true;
    private boolean dietEnabled = true;
    private boolean dailyEnabled = false;

    public boolean isTaskEnabled() {
        return taskEnabled;
    }

    public void setTaskEnabled(boolean taskEnabled) {
        this.taskEnabled = taskEnabled;
    }

    public boolean isPlanEnabled() {
        return planEnabled;
    }

    public void setPlanEnabled(boolean planEnabled) {
        this.planEnabled = planEnabled;
    }

    public boolean isExpenseEnabled() {
        return expenseEnabled;
    }

    public void setExpenseEnabled(boolean expenseEnabled) {
        this.expenseEnabled = expenseEnabled;
    }

    public boolean isDietEnabled() {
        return dietEnabled;
    }

    public void setDietEnabled(boolean dietEnabled) {
        this.dietEnabled = dietEnabled;
    }

    public boolean isDailyEnabled() {
        return dailyEnabled;
    }

    public void setDailyEnabled(boolean dailyEnabled) {
        this.dailyEnabled = dailyEnabled;
    }
}
