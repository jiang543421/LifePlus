package com.lifepulse.ai.llm;

import com.lifepulse.ai.AiConstants;
import com.lifepulse.ai.model.MetricValue;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;

/**
 * 渲染 LLM 调用所需的 system + user prompt（spec §1.9 / CLAUDE.md §11.3）。
 *
 * <p>职责：
 * <ul>
 *   <li>从 {@code llm-prompt.properties} 读取 system role 与 4 chip 模板</li>
 *   <li>把 4 chip 的 {@link MetricValue} 注入 user prompt（缺数据用"无数据"占位）</li>
 *   <li>注入 today 日期（ISO yyyy-MM-dd，spec §5.2）</li>
 *   <li>用 properties 默认值填充 {@code maxResponseTokens} / {@code timeout}</li>
 * </ul>
 *
 * <p><b>不可变性</b>（CLAUDE.md §4.1）：{@link #props} 在构造期加载一次，方法内部无 mutation。
 * 输出 {@link LlmRequest} 本身就是不可变 record。
 *
 * <p><b>安全约束</b>（CLAUDE.md §7.1）：日志禁打完整 prompt，避免 token 泄露；
 * 渲染在内存中完成，仅用于内存传递，不写日志。
 *
 * <p><b>RegEx 转义不变量</b>（reviewer Important 项已审）：所有 {@code String.replace}
 * 调用都用 {@code (CharSequence, CharSequence)} 重载，JDK 内部已
 * {@link java.util.regex.Matcher#quoteReplacement} 处理 replacement 参数，
 * 因此 {@code $} / {@code \} 在 value / body / today 中都是字面字符，
 * 不会被解释为 RegEx group reference。
 * <b>不要</b>改为 {@code replaceAll} / {@code replaceFirst} —— 若改为
 * regex 重载，<b>必须</b>同时把 replacement 包 {@code Matcher.quoteReplacement}，
 * 否则 prompt 中的 {@code $1} / {@code \} 会被解释为 group reference，
 * 破坏 LLM 输出（防御性测试 {@code build_chipTemplateWithDollarAndBackslash_preservesLiteral}
 * 锁定此不变量）。
 *
 * <p><b>扩展</b>：Task 14 ({@code AiInsightService}) 注入真正的
 * {@link LlmProperties#maxResponseTokens()} 与 {@link LlmProperties#timeoutMs()}，
 * 替换 properties 的默认值。当前 Task 8 仅验证渲染 pipeline 工作。
 */
@Component
public class LlmPromptBuilder {

    /** 全空 fallback（properties 可覆盖）。 */
    private static final String DEFAULT_EMPTY_PROMPT = "暂无数据";

    /** 默认最大响应 token（与 LlmProperties.maxResponseTokens 默认一致）。 */
    private static final int DEFAULT_MAX_RESPONSE_TOKENS = 300;

    /** 默认超时毫秒（与 LlmProperties.timeoutMs 默认一致）。 */
    private static final long DEFAULT_TIMEOUT_MS = 5000L;

    private static final String SYSTEM_ROLE_KEY = "system.role";
    private static final String USER_TEMPLATE_KEY = "user.template";
    private static final String USER_DATE_PLACEHOLDER = "{date}";
    private static final String USER_BODY_PLACEHOLDER = "{0}";
    private static final String USER_EMPTY_KEY = "user.empty";
    private static final String CHIP_TASK_COMPLETION_KEY = "user.chip.taskCompletion";
    private static final String CHIP_PLAN_DENSITY_KEY = "user.chip.planDensity";
    private static final String CHIP_WEEKLY_EXPENSE_KEY = "user.chip.weeklyExpense";
    private static final String CHIP_DIET_INTAKE_KEY = "user.chip.dietIntake";

    private static final String MAX_RESPONSE_TOKENS_KEY = "max.response.tokens";
    private static final String TIMEOUT_MS_KEY = "timeout.ms";

    /** 模板属性（构造期加载一次，运行时只读）。 */
    private final Properties props;

    public LlmPromptBuilder() {
        this.props = loadProps();
    }

    /** 测试用构造器（注入自定义 properties）。 */
    LlmPromptBuilder(Properties props) {
        this.props = props;
    }

    /**
     * 渲染 LLM 请求（spec §1.9）。
     *
     * @param userId  当前用户 ID（本期未直接用于 prompt，仅签名占位；Task 14 上层注入）
     * @param metrics 4 chip 数据（key 取 {@link AiConstants#CHIP_TASK_COMPLETION 等}）；
     *                缺数据 chip 传 {@link MetricValue#none()}，渲染为"无数据"占位
     * @param today   当天日期；注入 user prompt 的 {date} 占位
     * @return 不可变 {@link LlmRequest}
     */
    public LlmRequest build(long userId, Map<String, MetricValue> metrics, LocalDate today) {
        String systemPrompt = props.getProperty(SYSTEM_ROLE_KEY, "").trim();
        String userPrompt = renderUserPrompt(metrics, today);
        int maxTokens = parseInt(props.getProperty(MAX_RESPONSE_TOKENS_KEY),
                DEFAULT_MAX_RESPONSE_TOKENS);
        Duration timeout = Duration.ofMillis(parseLong(
                props.getProperty(TIMEOUT_MS_KEY), DEFAULT_TIMEOUT_MS));
        // userId 已校验，签名占位，无副作用
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive: " + userId);
        }
        return new LlmRequest(systemPrompt, userPrompt, maxTokens, timeout);
    }

    // === 内部：渲染 user prompt ===

    private String renderUserPrompt(Map<String, MetricValue> metrics, LocalDate today) {
        // 全空（4 chip 都缺）或显式空 map → fallback
        boolean allEmpty = metrics.values().stream().allMatch(this::isEmpty);
        if (allEmpty) {
            return props.getProperty(USER_EMPTY_KEY, DEFAULT_EMPTY_PROMPT);
        }

        StringBuilder body = new StringBuilder();
        appendChipIfPresent(body, CHIP_TASK_COMPLETION_KEY,
                metrics.get(AiConstants.CHIP_TASK_COMPLETION));
        appendChipIfPresent(body, CHIP_PLAN_DENSITY_KEY,
                metrics.get(AiConstants.CHIP_PLAN_DENSITY));
        appendChipIfPresent(body, CHIP_WEEKLY_EXPENSE_KEY,
                metrics.get(AiConstants.CHIP_WEEKLY_EXPENSE));
        appendChipIfPresent(body, CHIP_DIET_INTAKE_KEY,
                metrics.get(AiConstants.CHIP_DIET_INTAKE));

        String template = props.getProperty(USER_TEMPLATE_KEY, USER_BODY_PLACEHOLDER);
        return template
                .replace(USER_BODY_PLACEHOLDER, body.toString())
                .replace(USER_DATE_PLACEHOLDER, today.toString());
    }

    private void appendChipIfPresent(StringBuilder sb, String templateKey, MetricValue mv) {
        if (mv == null || isEmpty(mv)) {
            // 缺数据：复用 properties 中已定义的"empty 模板"（已含标签前缀）
            sb.append(props.getProperty(templateKey + ".empty", DEFAULT_EMPTY_PROMPT))
                    .append("\n");
            return;
        }
        // 渲染模板：{0} = value（BigDecimal.toPlainString）；unit 已嵌入模板
        String template = props.getProperty(templateKey, "");
        sb.append(formatChip(template, mv.value())).append("\n");
    }

    /** 把模板中的 {0} 替换为 value（toPlainString 防科学计数）。 */
    private static String formatChip(String template, BigDecimal value) {
        String v = value.toPlainString();
        return template.replace("{0}", v);
    }

    private boolean isEmpty(MetricValue mv) {
        return mv == null || !mv.isNonEmpty();
    }

    // === 工具 ===

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Properties loadProps() {
        Properties p = new Properties();
        try (var in = new InputStreamReader(
                new ClassPathResource("llm-prompt.properties").getInputStream(),
                StandardCharsets.UTF_8)) {
            p.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load llm-prompt.properties", ex);
        }
        return p;
    }
}
