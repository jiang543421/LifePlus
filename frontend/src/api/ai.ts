import http from './http';
import type { AiInsightResponse } from '@/types';

/**
 * AI 洞察 API（spec docs/architecture/ai-data-model.md + 后端 AiInsightController）。
 *
 * <p>三个端点：
 * <ul>
 *   <li>{@link #today}      — GET  /ai/insight/today（30/min/user，Redis 缓存优先）</li>
 *   <li>{@link #analysis}    — GET  /ai/insight/analysis（v2.1 PR3 独立分析页，共享缓存 + 限流）</li>
 *   <li>{@link #refresh}     — POST /ai/insight/refresh（3/min/user，强制重算覆写缓存）</li>
 * </ul>
 *
 * <p>错误处理复用 {@link http} 的拦截器：
 * <ul>
 *   <li>{@code code=0} → 解包返回 {@link AiInsightResponse}</li>
 *   <li>{@code code=1501}（AI_DEGRADED，全部 provider 失败）→ 抛 {@code ApiError(1501, msg)}，由调用方捕获弹 toast</li>
 *   <li>{@code code=1006}（AI 限流）→ 抛 {@code ApiError(1006, msg)}，同上</li>
 *   <li>401 → 走 refresh 重放（与 task/plan/auth 一致）</li>
 * </ul>
 *
 * <p>v2.1 三个端点共享同缓存（key 由后端按 userId 派生），所以 {@link #analysis}
 * 与 {@link #today} 的服务端语义无差异，差异仅在前端调用点（首页卡片 vs 独立页）。
 */
export const aiApi = {
  /** 获取今日 AI 洞察（GET /ai/insight/today）。 */
  today(): Promise<AiInsightResponse> {
    return http.get<unknown, AiInsightResponse>('/ai/insight/today');
  },

  /**
   * v2.1 PR3：独立分析页入口（GET /ai/insight/analysis）。
   * 与 {@link #today} 共享缓存 + 限流；返回结构完全相同，仅响应里 v2.1 字段
   * （source / advice / highlight / mood / llmMeta）通常有值（独立页是 LLM
   * 增强场景的主入口）。
   */
  analysis(): Promise<AiInsightResponse> {
    return http.get<unknown, AiInsightResponse>('/ai/insight/analysis');
  },

  /** 强制刷新（POST /ai/insight/refresh）。 */
  refresh(): Promise<AiInsightResponse> {
    return http.post<unknown, AiInsightResponse>('/ai/insight/refresh');
  },
};
