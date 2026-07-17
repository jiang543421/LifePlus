import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import 'dayjs/locale/zh-cn';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.locale('zh-cn');

export const TZ_SHANGHAI = 'Asia/Shanghai';

// H-10：把全局 default TZ 钉死成 Asia/Shanghai（CLAUDE.md §2.2）。
// 否则 dev 在 UTC 环境跑 `dayjs()` 类无参调用会拿到 UTC 时刻，date 漂移一天。
dayjs.tz.setDefault(TZ_SHANGHAI);

/** 格式化 ISO 时间到 YYYY-MM-DD HH:mm（Shanghai TZ）。 */
export function formatInShanghai(iso: string): string {
  // 显式按 TZ_SHANGHAI 还原 — 不依赖运行环境 TZ 与 setDefault，
  // 保证 CI 在 UTC 跑、dev 切 TZ 都拿到 Shanghai 时刻。
  return dayjs.utc(iso).tz(TZ_SHANGHAI).format('YYYY-MM-DD HH:mm');
}

/** 时段问候（5-11 早上 / 12-13 中午 / 14-18 下午 / 19-4 晚上）。 */
export function greeting(): string {
  const h = dayjs().hour();
  if (h < 5) return '夜深了';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
}

/** 当前日期行：YYYY-MM-DD 星期X。 */
export function todayDateLine(): string {
  return dayjs().format('YYYY-MM-DD dddd');
}

/** email 掩码（CLAUDE.md §7.3 不打完整 email）：前 2 位 + '***@' + 域名。 */
export function maskEmail(email: string): string {
  const at = email.indexOf('@');
  if (at <= 0) return '***';
  const head = email.slice(0, Math.min(2, at));
  const domain = email.slice(at);
  return `${head}***${domain}`;
}