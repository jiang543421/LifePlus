package com.lifepulse.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifepulse.ai.AiInsightProperties;
import com.lifepulse.ai.model.MetricValue;
import com.lifepulse.ai.model.Trend;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DailyAiProviderTest {

    private final AiCollectContext ctx = new AiCollectContext(
        LocalDate.of(2026, 7, 21), ZoneId.of("Asia/Shanghai")
    );

    @Test
    void isEnabled_propertyFalse_returnsFalse() {
        AiInsightProperties props = new AiInsightProperties();
        props.setDailyEnabled(false);
        DailyAiProvider provider = new DailyAiProvider(props);

        assertThat(provider.isEnabled(1L)).isFalse();
    }

    @Test
    void isEnabled_propertyTrue_returnsTrue() {
        AiInsightProperties props = new AiInsightProperties();
        props.setDailyEnabled(true);
        DailyAiProvider provider = new DailyAiProvider(props);

        assertThat(provider.isEnabled(1L)).isTrue();
    }

    @Test
    void collect_alwaysReturnsNone() {
        AiInsightProperties props = new AiInsightProperties();
        props.setDailyEnabled(true);
        DailyAiProvider provider = new DailyAiProvider(props);

        MetricValue mv = provider.collect(1L, ctx);

        assertThat(mv.value()).isNull();
        assertThat(mv.trend()).isEqualTo(Trend.NONE);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void key_returnsDaily() {
        DailyAiProvider provider = new DailyAiProvider(new AiInsightProperties());

        assertThat(provider.key()).isEqualTo("daily");
    }
}