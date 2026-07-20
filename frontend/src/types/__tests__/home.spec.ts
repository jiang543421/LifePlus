import { describe, it, expect } from 'vitest';
import { HOME_CARDS } from '../home';

describe('HOME_CARDS', () => {
  it('has exactly 6 cards', () => {
    expect(HOME_CARDS).toHaveLength(6);
  });

  it('keys are unique', () => {
    const keys = HOME_CARDS.map((c) => c.key);
    expect(new Set(keys).size).toBe(keys.length);
  });

  it('module cards come before placeholders', () => {
    const firstPlaceholderIdx = HOME_CARDS.findIndex((c) => c.kind === 'placeholder');
    const lastModuleIdx = HOME_CARDS.map((c) => c.kind).lastIndexOf('module');
    expect(firstPlaceholderIdx).toBeGreaterThan(lastModuleIdx);
  });

  it('every module card has a non-empty to route', () => {
    const modules = HOME_CARDS.filter((c) => c.kind === 'module');
    expect(modules).toHaveLength(4);
    for (const m of modules) {
      expect(m.to).toBeTruthy();
      expect(m.to?.startsWith('/')).toBe(true);
    }
  });

  it('module card routes align with the expense v1.2.1 / diet v1.2.2 wiring', () => {
    const byKey = Object.fromEntries(HOME_CARDS.map((c) => [c.key, c]));
    expect(byKey.task?.to).toBe('/tasks');
    expect(byKey.plan?.to).toBe('/plans');
    expect(byKey.expense?.to).toBe('/expenses');
    expect(byKey.diet?.to).toBe('/diets');
  });

  it('no placeholder card carries a to route', () => {
    const placeholders = HOME_CARDS.filter((c) => c.kind === 'placeholder');
    expect(placeholders).toHaveLength(2);
    for (const p of placeholders) {
      expect(p.to).toBeUndefined();
    }
  });

  it('each card has non-empty title and icon', () => {
    for (const c of HOME_CARDS) {
      expect(c.title).toBeTruthy();
      expect(c.icon).toBeTruthy();
    }
  });
});
