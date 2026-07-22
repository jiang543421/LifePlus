package com.lifepulse.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MetricValueTest {

    @Test
    void isNonEmpty_valueGreaterThanZero_returnsTrue() {
        var mv = new MetricValue(new BigDecimal("80"), "%", Trend.UP);
        assertThat(mv.isNonEmpty()).isTrue();
    }

    @Test
    void isNonEmpty_valueZero_returnsFalse() {
        var mv = new MetricValue(BigDecimal.ZERO, "项", Trend.NONE);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void isNonEmpty_valueNull_returnsFalse() {
        var mv = new MetricValue(null, "项", Trend.NONE);
        assertThat(mv.isNonEmpty()).isFalse();
    }

    @Test
    void trend_upOrDown_isSignificant() {
        assertThat(Trend.UP.isSignificant()).isTrue();
        assertThat(Trend.DOWN.isSignificant()).isTrue();
        assertThat(Trend.FLAT.isSignificant()).isFalse();
        assertThat(Trend.NONE.isSignificant()).isFalse();
    }
}