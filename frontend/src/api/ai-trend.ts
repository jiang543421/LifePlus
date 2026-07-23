import http from './http';
import type { AiTrendResponse } from '@/types';

/**
 * AI 趋势图 API（spec §v2.2 trend / 后端 AiTrendController）。
 *
 * <p>端点：
 * <ul>
 *   <li>{@link #trend} — GET /ai/insight/trend?window=7|14|30（30/min/user，
 *       Redis 6h 缓存优先；window 默认 14）</li>
 * </ul>
 *
 * <p>错误处理复用 {@link http} 的拦截器：
 * <ul>
 *   <li>{@code code=0} → 解包返回 {@link AiTrendResponse}</li>
 *   <li>{@code code=1001}（VALIDATION，window ∉ {7,14,30}）→ 抛 {@code ApiError(1001)}</li>
 *   <li>{@code code=1006}（AI 限流）→ 抛 {@code ApiError(1006)}</li>
 *   <li>401 → 走 refresh 重放（与 task/plan/auth 一致）</li>
 * </ul>
 */

/** Trend 端点支持的窗口档位（与后端 {@code AiConstants.TREND_WINDOWS} 对齐）。 */
export type TrendWindowDays = 7 | 14 | 30;

export const aiTrendApi = {
  /**
   * 获取指定窗口的趋势数据（GET /ai/insight/trend?window=N）。
   *
   * @param windowDays 窗口档位，必须 ∈ {7, 14, 30}；非法值会被 URL 参数拦截（后端 1001）
   */
  trend(windowDays: TrendWindowDays): Promise<AiTrendResponse> {
    return http.get<unknown, AiTrendResponse>('/ai/insight/trend', {
      params: { window: windowDays },
    });
  },
};