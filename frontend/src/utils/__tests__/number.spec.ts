import { describe, it, expect } from 'vitest';
import { formatAmount, formatAmountWithSymbol, compareDelta } from '../number';

describe('number utils / formatAmount', () => {
  it('null/undefined → "0.00"', () => {
    expect(formatAmount(null)).toBe('0.00');
    expect(formatAmount(undefined)).toBe('0.00');
  });

  it('number：整数补 .00', () => {
    expect(formatAmount(10)).toBe('10.00');
    expect(formatAmount(0)).toBe('0.00');
  });

  it('number：小数四舍五入到两位', () => {
    expect(formatAmount(12.5)).toBe('12.50');
    expect(formatAmount(12.345)).toBe('12.35');
    expect(formatAmount(12.344)).toBe('12.34');
  });

  it('string：parseFloat 后格式化', () => {
    expect(formatAmount('12.5')).toBe('12.50');
    expect(formatAmount('10')).toBe('10.00');
  });

  it('string：非有限数（NaN / 解析失败）→ "0.00"', () => {
    expect(formatAmount('abc')).toBe('0.00');
    expect(formatAmount('')).toBe('0.00');
  });

  it('number：NaN / Infinity → "0.00"', () => {
    expect(formatAmount(Number.NaN)).toBe('0.00');
    expect(formatAmount(Number.POSITIVE_INFINITY)).toBe('0.00');
    expect(formatAmount(Number.NEGATIVE_INFINITY)).toBe('0.00');
  });

  it('负数保留符号（避免转绝对值搞错退款场景）', () => {
    expect(formatAmount(-10)).toBe('-10.00');
  });
});

describe('number utils / formatAmountWithSymbol', () => {
  it('前置 "¥ " + formatAmount', () => {
    expect(formatAmountWithSymbol(10)).toBe('¥ 10.00');
    expect(formatAmountWithSymbol(12.5)).toBe('¥ 12.50');
    expect(formatAmountWithSymbol(null)).toBe('¥ 0.00');
    expect(formatAmountWithSymbol('abc')).toBe('¥ 0.00');
  });
});

describe('number utils / compareDelta', () => {
  it('正增长 → "+N.N%"', () => {
    expect(compareDelta('120', '100')).toBe('+20.0%');
    expect(compareDelta(150, 100)).toBe('+50.0%');
  });

  it('负增长 → "-N.N%"（不前置 +）', () => {
    expect(compareDelta('80', '100')).toBe('-20.0%');
    expect(compareDelta(50, 100)).toBe('-50.0%');
  });

  it('零增长 → "+0.0%"（diff=0 仍走 + 分支）', () => {
    expect(compareDelta('100', '100')).toBe('+0.0%');
  });

  it('previous=null → 返回 null（无历史可比）', () => {
    expect(compareDelta('100', null)).toBeNull();
    expect(compareDelta(100, null)).toBeNull();
  });

  it('previous=0 → 返回 null（避免除零）', () => {
    expect(compareDelta('100', '0')).toBeNull();
    expect(compareDelta(100, 0)).toBeNull();
  });

  it('previous 非有限数（NaN）→ null', () => {
    expect(compareDelta('100', Number.NaN)).toBeNull();
    expect(compareDelta(100, 'abc')).toBeNull();
  });
});