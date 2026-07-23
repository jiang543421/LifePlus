package com.lifepulse.ai.llm;

import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LlmPromptBuilder}.
 *
 * <p>覆盖 5 个核心场景：
 * <ol>
 *   <li>所有 4 chip 都有值 → system + user prompt 都渲染</li>
 *   <li>部分 chip 空 → 空 chip 用"无数据"占位，但其它 chip 正常</li>
 *   <li>全部 chip 空 → user prompt 渲染 skeleton（"暂无数据"）</li>
 *   <li>占位符注入：system prompt 含角色定义；user prompt 含 today 日期</li>
 *   <li>数值类型转换：BigDecimal → String、去尾零、保持精度</li>
 * </ol>
 */
class LlmPromptBuilderTest {

    private final LlmPromptBuilder builder = new LlmPromptBuilder();

    /** 构造 4 chip 测试数据，按 spec §1.8 chip key 命名。 */
    private Map<String, MetricValue> sampleMetrics() {
        Map<String, MetricValue> m = new LinkedHashMap<>();
        m.put("taskCompletion", new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        m.put("planDensity", new MetricValue(new BigDecimal("4"), "项", Trend.FLAT));
        m.put("weeklyExpense", new MetricValue(new BigDecimal("420"), "¥", Trend.DOWN));
        m.put("dietIntake", new MetricValue(new BigDecimal("1500"), "kcal", Trend.NONE));
        return m;
    }

    @Test
    void build_allChipsPresent_rendersSystemAndUserPrompt() {
        LlmRequest req = builder.build(1L, sampleMetrics(), LocalDate.of(2026, 7, 22));

        // system prompt 非空且含角色定义
        assertThat(req.systemPrompt()).isNotBlank();
        assertThat(req.systemPrompt()).contains("个人数字生活助手");

        // user prompt 含 4 chip（按 spec chip key 命名）
        assertThat(req.userPrompt()).contains("【任务】完成率 80%");
        assertThat(req.userPrompt()).contains("【日程】今日 4 项");
        assertThat(req.userPrompt()).contains("【消费】本周 ¥420");
        assertThat(req.userPrompt()).contains("【饮食】摄入 1500 kcal");

        // 来自 properties 默认值（builder 不依赖 Spring 容器）
        assertThat(req.maxResponseTokens()).isPositive();
        assertThat(req.maxResponseTokens()).isLessThanOrEqualTo(1000);
        assertThat(req.timeout()).isEqualTo(Duration.ofMillis(5000));
    }

    @Test
    void build_partialChips_rendersPlaceholderForEmpty() {
        Map<String, MetricValue> m = new LinkedHashMap<>();
        m.put("taskCompletion", new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        m.put("planDensity", MetricValue.none());
        m.put("weeklyExpense", MetricValue.none());
        m.put("dietIntake", MetricValue.none());

        LlmRequest req = builder.build(1L, m, LocalDate.of(2026, 7, 22));

        // 非空 chip 正常渲染
        assertThat(req.userPrompt()).contains("【任务】完成率 80%");
        // 空 chip 用占位文本"无数据"（不字面输出 "none" / "null"）
        assertThat(req.userPrompt()).contains("无数据");
        assertThat(req.userPrompt()).doesNotContain("任务】完成率 none");
    }

    @Test
    void build_emptyMetrics_rendersSkeletonFallback() {
        LlmRequest req = builder.build(1L, Map.of(), LocalDate.of(2026, 7, 22));

        // 全空 → 渲染 skeleton："暂无数据"
        assertThat(req.userPrompt()).contains("暂无数据");
        // system prompt 仍必须有值（角色定义独立于数据）
        assertThat(req.systemPrompt()).isNotBlank();
    }

    @Test
    void build_injectsDatePlaceholder() {
        LlmRequest req = builder.build(1L, sampleMetrics(), LocalDate.of(2026, 7, 22));

        // today 占位符被注入为 ISO 日期（spec §5.2 LocalDate yyyy-MM-dd）
        assertThat(req.userPrompt()).contains("2026-07-22");
    }

    @Test
    void build_bigDecimalValue_preservesStringRepresentation() {
        Map<String, MetricValue> m = new LinkedHashMap<>();
        // 大数 + 小数（验证 toPlainString 避免科学计数；0 被视为空数据，按 isNonEmpty 语义）
        m.put("taskCompletion", new MetricValue(new BigDecimal("100"), "%", Trend.FLAT));
        m.put("planDensity", new MetricValue(new BigDecimal("3"), "项", Trend.FLAT));
        m.put("weeklyExpense", new MetricValue(new BigDecimal("12.50"), "¥", Trend.DOWN));
        m.put("dietIntake", new MetricValue(new BigDecimal("1500"), "kcal", Trend.NONE));

        LlmRequest req = builder.build(1L, m, LocalDate.of(2026, 7, 22));

        // 100（整数）/ 3 / 12.50（保留尾零）/ 1500 都用 toPlainString 输出
        assertThat(req.userPrompt()).contains("【任务】完成率 100%");
        assertThat(req.userPrompt()).contains("【日程】今日 3 项");
        assertThat(req.userPrompt()).contains("【消费】本周 ¥12.50");
        assertThat(req.userPrompt()).contains("【饮食】摄入 1500 kcal");
    }

    /**
     * 防御性测试：chip 模板含 RegEx 反向引用字符（{@code $1} / {@code \}）时，
     * 渲染输出必须保留字面量，不能被 Matcher 解释为 group reference。
     * <p>
     * 当前实现用 {@code String.replace(CharSequence, CharSequence)}（JDK 内部已
     * {@code Matcher.quoteReplacement}），本就安全；本测试锁定该不变量，防止后续
     * 误改为 {@code replaceAll} / {@code replaceFirst} 而引入回归。
     * <p>
     * <b>测试设计</b>：在 properties 中放入 chip 模板 {@code pre$1\{0}post}，调用
     * {@code build(...)} 注入 BigDecimal("80")。正确渲染后输出应包含字面
     * {@code pre$1\80post}；若被误改为 {@code replaceAll(replacement)} 且 replacement
     * 未 quote，则 {@code $1} 会被解释为 group 1 reference（抛 {@link IndexOutOfBoundsException}
     * 或被替换为空）。
     */
    @Test
    void build_chipTemplateWithDollarAndBackslash_preservesLiteral() {
        Properties props = new Properties();
        props.setProperty("system.role", "test-role");
        props.setProperty("user.template", "data={0};date={date}");
        // 模板字面：pre$1\{0}post（Java literal 中 \\ = 单 \）
        props.setProperty("user.chip.taskCompletion", "pre$1\\{0}post");
        props.setProperty("user.chip.taskCompletion.empty", "tc=empty");
        props.setProperty("user.chip.planDensity", "pd={0}");
        props.setProperty("user.chip.planDensity.empty", "pd=empty");
        props.setProperty("user.chip.weeklyExpense", "we={0}");
        props.setProperty("user.chip.weeklyExpense.empty", "we=empty");
        props.setProperty("user.chip.dietIntake", "di={0}");
        props.setProperty("user.chip.dietIntake.empty", "di=empty");
        props.setProperty("user.empty", "暂无数据");

        LlmPromptBuilder localBuilder = new LlmPromptBuilder(props);
        Map<String, MetricValue> m = new LinkedHashMap<>();
        m.put("taskCompletion", new MetricValue(new BigDecimal("80"), "%", Trend.UP));
        m.put("planDensity", MetricValue.none());
        m.put("weeklyExpense", MetricValue.none());
        m.put("dietIntake", MetricValue.none());

        LlmRequest req = localBuilder.build(1L, m, LocalDate.of(2026, 7, 22));

        // 字面 "pre$1\80post"：Java literal 中 $1 = $1（$ 无需 escape），\\ = \
        assertThat(req.userPrompt()).contains("pre$1\\80post");
    }
}
