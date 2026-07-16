import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

dayjs.locale('zh-cn');

export const TZ_SHANGHAI = 'Asia/Shanghai';

/** 格式化 ISO 时间到 YYYY-MM-DD HH:mm（Shanghai TZ）。 */
export function formatInShanghai(iso: string): string {
  return dayjs(iso).format('YYYY-MM-DD HH:mm');
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