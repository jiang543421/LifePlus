package com.lifepulse.ai.provider;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Provider 采集上下文（spec §6）。
 *
 * <p>当前线程 userId 由 Provider 自行通过 {@link com.lifepulse.security.UserContext}
 * 读取；本上下文仅传日期相关参数，避免 Provider 内部重复计算"今天 / 本周"。
 */
public record AiCollectContext(
    LocalDate today,
    ZoneId zone
) {
    /** 默认上海时区今日。 */
    public static AiCollectContext nowInShanghai() {
        return new AiCollectContext(
            LocalDate.now(ZoneId.of("Asia/Shanghai")),
            ZoneId.of("Asia/Shanghai")
        );
    }
}