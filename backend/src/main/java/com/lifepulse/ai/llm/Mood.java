package com.lifepulse.ai.llm;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 洞察情绪标签（spec §1.5 / CLAUDE.md §11.3）。
 *
 * <p>LLM 输出 JSON 的 mood 字段在序列化 / 反序列化时大小写不固定（实测可能
 * 输出 {@code "positive"} 小写或 {@code "POSITIVE"} 全大写）。{@code @JsonCreator}
 * 配合 {@code switch(s.toLowerCase())} 容忍所有大小写变体；未知值 / {@code null}
 * 一律 clamp 到 {@link #NEUTRAL}，避免 1512（响应解析失败）扩散成噪声。
 *
 * <p>序列化默认值仍为 PascalCase（{@code POSITIVE} / {@code NEUTRAL} / {@code CAUTIOUS}）。
 */
public enum Mood {
    POSITIVE, NEUTRAL, CAUTIOUS;

    private static final Logger log = LoggerFactory.getLogger(Mood.class);

    @JsonCreator
    public static Mood fromString(String s) {
        if (s == null) return NEUTRAL;
        return switch (s.toLowerCase()) {
            case "positive" -> POSITIVE;
            case "neutral"  -> NEUTRAL;
            case "cautious" -> CAUTIOUS;
            default -> {
                log.warn("Mood: unknown value '{}', clamped to NEUTRAL", s);
                yield NEUTRAL;
            }
        };
    }
}
