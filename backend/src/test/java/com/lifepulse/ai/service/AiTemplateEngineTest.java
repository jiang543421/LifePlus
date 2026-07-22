package com.lifepulse.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiTemplateEngineTest {

    private AiTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AiTemplateEngine();
        engine.loadFromClasspath();
    }

    @Test
    void formatHeadline_emptyKey_rendersWelcomeText() {
        String result = engine.formatHeadline("headline.empty");
        assertThat(result).isEqualTo("还没有数据，继续记录几天后将出现洞察。");
    }

    @Test
    void formatHeadline_fullKey_rendersAllPlaceholders() {
        String result = engine.formatHeadline(
            "headline.full",
            "80", "较昨日 +5pp", "420", "较上周 -12%"
        );
        assertThat(result).isEqualTo(
            "今日任务完成率 80%，较昨日 +5pp；本周消费 ¥420，较上周 -12%。"
        );
    }

    @Test
    void formatHeadline_taskOnlyKey_omitsExpense() {
        String result = engine.formatHeadline(
            "headline.taskOnly",
            "80", "较昨日 +5pp"
        );
        assertThat(result).isEqualTo(
            "今日任务完成率 80%，较昨日 +5pp。继续记录几天后将出现更全面的洞察。"
        );
    }

    @Test
    void formatChipDelta_up_returnsPlusDeltaText() {
        String result = engine.formatChipDelta("taskCompletion", "up", "5");
        assertThat(result).isEqualTo("较昨日 +5pp");
    }

    @Test
    void formatChipDelta_down_returnsMinusDeltaText() {
        String result = engine.formatChipDelta("weeklyExpense", "down", "12");
        assertThat(result).isEqualTo("较上周 -12%");
    }

    @Test
    void formatChipDelta_flat_returnsNeutralText() {
        String result = engine.formatChipDelta("taskCompletion", "flat", "0");
        assertThat(result).isEqualTo("与昨日持平");
    }

    @Test
    void formatChipDelta_none_returnsDash() {
        String result = engine.formatChipDelta("taskCompletion", "none", "");
        assertThat(result).isEqualTo("—");
    }

    @Test
    void formatHeadline_placeholderCountMismatch_fallsBackToErrorText() {
        String result = engine.formatHeadline("headline.full", "80", "up");
        assertThat(result).isEqualTo("数据异常，请稍后重试");
    }
}
