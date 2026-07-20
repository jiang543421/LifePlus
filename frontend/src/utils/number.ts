// 通用数字格式化工具（spec §06-expense section 5）。
//
// 当前主用途是消费金额展示（amount 后端 BigDecimal 序列化为 number）；
// 所有函数保持纯函数、无副作用，可被 store / component / utils 复用。

/**
 * 把任意数值格式化为两位小数字符串；null/undefined/非有限数 → "0.00"。
 *
 * <p>支持输入：
 * <ul>
 *   <li>{@code number}：直接走 toFixed(2)</li>
 *   <li>{@code string}：先 {@code parseFloat}，无法解析（非有限数）→ "0.00"</li>
 *   <li>{@code null} / {@code undefined}：直接 fallback 到 "0.00"</li>
 * </ul>
 *
 * <p>不抛错：调用方（如 ExpenseListItem）可以无条件传入，不需 try/catch。
 */
export function formatAmount(s: number | string | null | undefined): string {
  if (s == null) return '0.00';
  const n = typeof s === 'string' ? parseFloat(s) : s;
  if (!Number.isFinite(n)) return '0.00';
  return n.toFixed(2);
}

/** 同 {@link formatAmount}，前置 "¥ " 符号；用于金额显著展示位（卡片、表头）。 */
export function formatAmountWithSymbol(s: number | string | null | undefined): string {
  return '¥ ' + formatAmount(s);
}

/**
 * 计算相对增量百分比（用于「同比/环比」展示）。
 *
 * @param current 当期值
 * @param previous 上期值；{@code null} → 返回 {@code null}（无历史可比）
 * @returns 形如 "+20.0%" / "-15.3%"；上期为 0 → {@code null}（避免除零）
 */
export function compareDelta(
  current: number | string,
  previous: number | string | null,
): string | null {
  if (previous == null) return null;
  const c = typeof current === 'string' ? parseFloat(current) : current;
  const p = typeof previous === 'string' ? parseFloat(previous) : previous;
  if (!Number.isFinite(c) || !Number.isFinite(p)) return null;
  if (p === 0) return null;
  const diff = c - p;
  const pct = (diff / p) * 100;
  const sign = diff >= 0 ? '+' : '';
  return `${sign}${pct.toFixed(1)}%`;
}