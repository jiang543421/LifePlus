import http from './http';
import type { DailyReportPayload, WeeklyReportPayload } from '@/types';

/**
 * Daily 模块 API（spec §08-daily-report-design §6 + 后端 DailyReportController）。
 *
 * <p>axios 实例已配置 {@code baseURL='/api/v1'} + 鉴权拦截 + 信封拦截，
 * 路径只写 {@code /daily...}；HTTP 4xx/5xx 与业务 code!=0 由拦截器抛
 * {@code ApiError(code, message)}，调用方只需关心成功路径。
 *
 * <p>{@link #daily} / {@link #week} 的 {@code date} 参数可选：
 * <ul>
 *   <li>未传 → 后端取 {@code today()}（Asia/Shanghai）作为默认</li>
 *   <li>已传 → 写入 query（格式 {@code YYYY-MM-DD}）</li>
 * </ul>
 * null/undefined 字段不写入 query（避免空字符串污染）。
 */
export const dailyApi = {
  /**
   * 单日日报（GET /daily）。
   *
   * @param date 可选 YYYY-MM-DD；缺省时由后端按 Asia/Shanghai 当日兜底
   * @returns 解包 envelope 后的 DailyReportPayload
   * @throws ApiError 1001（日期超出 30 天窗口）/ 1002（未登录）/ 500（聚合失败）
   */
  daily(date?: string): Promise<DailyReportPayload> {
    const params: Record<string, string> = {};
    if (date) params.date = date;
    return http.get<unknown, DailyReportPayload>('/daily', { params });
  },

  /**
   * 周报（GET /daily/week）。
   *
   * @param date 可选 YYYY-MM-DD（目标周内任意一天）；缺省时由后端按 ISO 周对齐到周一首日
   * @returns 解包 envelope 后的 WeeklyReportPayload（comparison.delta 可为 null）
   */
  week(date?: string): Promise<WeeklyReportPayload> {
    const params: Record<string, string> = {};
    if (date) params.date = date;
    return http.get<unknown, WeeklyReportPayload>('/daily/week', { params });
  },
};
