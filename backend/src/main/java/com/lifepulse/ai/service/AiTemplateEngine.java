package com.lifepulse.ai.service;

import com.lifepulse.ai.AiConstants;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 模板引擎（spec §8）。
 *
 * <p>从 {@code classpath:ai-templates.properties} 加载模板键值，
 * 使用 JDK {@link MessageFormat} 渲染（支持 {0}/{1} 占位符）。
 *
 * <p>降级语义（spec §10.1）：
 * <ul>
 *   <li>键缺失 → 启动期 fail fast（构造时即抛异常）</li>
 *   <li>占位符数量不匹配 → log.error + 返回 {@code fallback.headline}</li>
 * </ul>
 */
@Component
public class AiTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(AiTemplateEngine.class);

    private static final String RESOURCE_PATH = "ai-templates.properties";
    private static final String FALLBACK_KEY = "fallback.headline";
    private static final String FALLBACK_DEFAULT = "数据异常，请稍后重试";
    private static final String EMPTY_DELTA = "—";

    /** 匹配模板中的 {0}/{1}/.../{n} 占位符。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

    private final Properties templates = new Properties();

    /** Spring 启动时调用（构造注入）。 */
    public AiTemplateEngine() {
        loadFromClasspath();
    }

    /** 显式加载（测试用）。 */
    public void loadFromClasspath() {
        InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH);
        if (in == null) {
            throw new IllegalStateException("Missing classpath resource: " + RESOURCE_PATH);
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            templates.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    /**
     * 渲染主文。
     *
     * @param key  模板键（如 {@code headline.full}）
     * @param args 占位符参数
     * @return 渲染结果；占位符错位时降级为 {@code fallback.headline}
     */
    public String formatHeadline(String key, Object... args) {
        String template = templates.getProperty(key);
        if (template == null) {
            log.error("Missing template key: {}", key);
            return templates.getProperty(AiConstants.TMPL_HEADLINE_EMPTY);
        }
        int expected = countPlaceholders(template);
        if (args.length < expected) {
            log.error("Template format error: key={}, expectedArgs={}, got={}",
                key, expected, args.length);
            return templates.getProperty(FALLBACK_KEY, FALLBACK_DEFAULT);
        }
        try {
            MessageFormat mf = new MessageFormat(template);
            mf.setLocale(java.util.Locale.ROOT);
            return mf.format(args);
        } catch (IllegalArgumentException e) {
            log.error("Template format error: key={}, args={}", key, args.length, e);
            return templates.getProperty(FALLBACK_KEY, FALLBACK_DEFAULT);
        }
    }

    /**
     * 渲染 chip 副标。
     *
     * <p>键由 {@code chip.<key>.<trend>} 拼接；缺失时返回 {@code "—"}。
     *
     * @param chipKey chip key（如 {@code taskCompletion}）
     * @param trend   trend（如 {@code up}/{@code down}/{@code flat}/{@code none}）
     * @param value   数值（用于格式化）
     */
    public String formatChipDelta(String chipKey, String trend, Object value) {
        String key = AiConstants.TMPL_CHIP_PREFIX + chipKey + "." + trend;
        String template = templates.getProperty(key);
        if (template == null) {
            return EMPTY_DELTA;
        }
        int expected = countPlaceholders(template);
        if (expected > 1) {
            log.error("Chip delta format error: key={}, trend={}, placeholders={}",
                chipKey, trend, expected);
            return EMPTY_DELTA;
        }
        try {
            MessageFormat mf = new MessageFormat(template);
            mf.setLocale(java.util.Locale.ROOT);
            return mf.format(new Object[]{value});
        } catch (IllegalArgumentException e) {
            log.error("Chip delta format error: key={}, trend={}", chipKey, trend, e);
            return EMPTY_DELTA;
        }
    }

    /** 数模板中 {n} 占位符总数（取最大 n + 1）。 */
    private static int countPlaceholders(String template) {
        int max = -1;
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > max) max = n;
        }
        return max + 1;
    }
}