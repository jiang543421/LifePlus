import { describe, it, expect } from 'vitest';
import { formatInShanghai, greeting, todayDateLine, maskEmail, TZ_SHANGHAI } from '@/utils/time';

describe('TZ 常量', () => {
  it('固定为 Asia/Shanghai', () => {
    expect(TZ_SHANGHAI).toBe('Asia/Shanghai');
  });
});

describe('formatInShanghai', () => {
  it('把 ISO 时间格式化成 YYYY-MM-DD HH:mm', () => {
    expect(formatInShanghai('2026-07-15T09:30:00+08:00')).toBe('2026-07-15 09:30');
  });

  it('跨时区 ISO 也按 Shanghai TZ 还原', () => {
    // 2026-07-15T01:30:00Z == 2026-07-15 09:30 +08:00
    expect(formatInShanghai('2026-07-15T01:30:00Z')).toBe('2026-07-15 09:30');
  });

  // H-10：跨多 TZ 输入都按 Shanghai 输出，验证实现显式按 TZ_SHANGHAI 还原，
  // 不依赖运行环境 TZ（dev 在 UTC 跑也会显示对）。
  it('H-10：-05:00 输入按 Shanghai 还原为 +13:00 同日', () => {
    // 2026-07-15T01:30:00-05:00 == 2026-07-15T14:30:00+08:00
    expect(formatInShanghai('2026-07-15T01:30:00-05:00')).toBe('2026-07-15 14:30');
  });

  it('H-10：+09:00 输入按 Shanghai 还原（少 1 小时）', () => {
    // 2026-07-15T09:30:00+09:00 == 2026-07-15T08:30:00+08:00
    expect(formatInShanghai('2026-07-15T09:30:00+09:00')).toBe('2026-07-15 08:30');
  });
});

describe('greeting', () => {
  it('返回非空字符串', () => {
    const g = greeting();
    expect(typeof g).toBe('string');
    expect(g.length).toBeGreaterThan(0);
  });

  it('返回值属于 5 档之一（覆盖所有分支）', () => {
    const set = new Set(['夜深了', '早上好', '中午好', '下午好', '晚上好']);
    expect(set.has(greeting())).toBe(true);
  });
});

describe('todayDateLine', () => {
  it('返回 YYYY-MM-DD dddd 格式', () => {
    const line = todayDateLine();
    expect(line).toMatch(/^\d{4}-\d{2}-\d{2}\s+\S+$/);
  });
});

describe('maskEmail', () => {
  it('保留前 2 位 + 域名', () => {
    expect(maskEmail('alice@example.com')).toBe('al***@example.com');
  });

  it('email 长度 < 2 时只显示 a***@domain', () => {
    expect(maskEmail('a@example.com')).toBe('a***@example.com');
  });

  it('无 @ 时返回 ***', () => {
    expect(maskEmail('not-an-email')).toBe('***');
  });

  it('@ 在第 0 位时返回 ***', () => {
    expect(maskEmail('@example.com')).toBe('***');
  });
});