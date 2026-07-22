package com.lifepulse.ai.model;

/**
 * 指标变化方向（用于 chip 颜色与副标路由）。
 *
 * <p>{@code UP} 绿、{@code DOWN} 红、{@code FLAT} 灰、{@code NONE} 浅灰。
 */
public enum Trend {
    UP, DOWN, FLAT, NONE;

    /** 是否为"有意义"的变化方向（用于决定副标路由）。 */
    public boolean isSignificant() {
        return this == UP || this == DOWN;
    }
}