import http from './http';
import type { AiInsightResponse } from '@/types';

/**
 * AI 洞察 API（spec docs/architecture/ai-data-model.md + 后端 AiInsightController）。
 *
 * <p>两个端点：
 * <ul>
 *   <li>{@link #today}  — GET  /ai/insight/today（60/min/user，Redis 缓存优先）</li>
 *   <li>{@link #refresh} — POST /ai/insight/refresh（6/min/user，强制重算覆写缓存）</li>
 * </ul>
 *
 * <p>错误处理复用 {@link http} 的拦截器：
 * <ul>
 *   <li>{@code code=0} → 解包返回 {@link AiInsightResponse}</li>
 *   <li>{@code code=1501}（AI_DEGRADED，全部 provider 失败）→ 抛 {@code ApiError(1501, msg)}，由调用方捕获弹 toast</li>
 *   <li>{@code code=1006}（AI 限流）→ 抛 {@code ApiError(1006, msg)}，同上</li>
 *   <li>401 → 走 refresh 重放（与 task/plan/auth 一致）</li>
 * </ul>
 */
export const aiApi = {
  /** 获取今日 AI 洞察（GET /ai/insight/today）。 */
  today(): Promise<AiInsightResponse> {
    return http.get<unknown, AiInsightResponse>('/ai/insight/today');
  },

  /** 强制刷新（POST /ai/insight/refresh）。 */
  refresh(): Promise<AiInsightResponse> {
    return http.post<unknown, AiInsightResponse>('/ai/insight/refresh');
  },
};
