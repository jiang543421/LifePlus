package com.lifepulse.daily.provider;

import java.time.LocalDate;

/**
 * 指标聚合器接口（plan §3 T2 / spec §2 Provider 模式）。
 *
 * <p>每个实现负责单一数据源（Task / Plan / Expense / Diet）的指定日期聚合。
 * 边界由调用方（{@link com.lifepulse.daily.service.DailyReportService}）按
 * {@link com.lifepulse.daily.DailyConstants#ZONE} 转 Asia/Shanghai 后传入。
 *
 * <p><b>设计决策</b>：本接口仅暴露 {@code aggregateDaily}，不强制
 * {@code aggregateWeekly}——周报由 Service 循环 7 次调用本接口，Provider 保持
 * 单日职责简单，避免多日聚合逻辑分散到各 Provider。
 *
 * @param <T> 指标 record 类型（{@code TaskMetrics} / {@code PlanMetrics} / 等）
 */
public interface MetricProvider<T> {

    /**
     * 聚合单日指标。
     *
     * @param userId 用户 ID（已通过 UserContext.current() 鉴权，越权拦截在 Service 层）
     * @param date   目标日期（Asia/Shanghai 时区边界已由 Service 处理）
     * @return 指标 record；空数据应返回零值结构而非 null
     */
    T aggregateDaily(long userId, LocalDate date);

    /**
     * Provider 元数据名称（用于日志与调试）。
     *
     * <p>默认实现取类名；子类可覆盖以提供更友好的标识。
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
