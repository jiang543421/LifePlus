import { describe, it, expect } from 'vitest';
import { safeRedirect } from './safeRedirect';

/**
 * safeRedirect 单元测试（CLAUDE.md §7 + Review C-4）。
 *
 * <p>覆盖：合法相对路径放行；外部 URL / protocol-relative / 非字符串回退。
 */
describe('safeRedirect', () => {
  describe('合法相对路径', () => {
    it('根路径 / 通过', () => {
      expect(safeRedirect('/')).toBe('/');
    });

    it('内部路径 /tasks 通过', () => {
      expect(safeRedirect('/tasks')).toBe('/tasks');
    });

    it('带 query 的内部路径通过', () => {
      expect(safeRedirect('/tasks?status=done&page=2')).toBe('/tasks?status=done&page=2');
    });

    it('带 hash 的内部路径通过', () => {
      expect(safeRedirect('/tasks/123#section')).toBe('/tasks/123#section');
    });

    it('数字 id 路径通过', () => {
      expect(safeRedirect('/tasks/42')).toBe('/tasks/42');
    });
  });

  describe('拒绝外部 URL', () => {
    it('绝对 URL https://evil.com → 回退 /', () => {
      expect(safeRedirect('https://evil.com')).toBe('/');
    });

    it('绝对 URL http://evil.com → 回退 /', () => {
      expect(safeRedirect('http://evil.com')).toBe('/');
    });

    it('javascript: 伪协议 → 回退 /', () => {
      expect(safeRedirect('javascript:alert(1)')).toBe('/');
    });

    it('protocol-relative //evil.com → 回退 /', () => {
      expect(safeRedirect('//evil.com')).toBe('/');
    });

    it('protocol-relative //evil.com/path → 回退 /', () => {
      expect(safeRedirect('//evil.com/path')).toBe('/');
    });

    it('Windows 路径 /\\evil → 回退 /', () => {
      expect(safeRedirect('/\\evil')).toBe('/');
    });

    it('data: URI → 回退 /', () => {
      expect(safeRedirect('data:text/html,<script>alert(1)</script>')).toBe('/');
    });

    it('非 / 开头的相对路径 evil.com/path → 回退 /', () => {
      expect(safeRedirect('evil.com/path')).toBe('/');
    });
  });

  describe('拒绝非字符串 / 异常输入', () => {
    it('undefined → 回退 /', () => {
      expect(safeRedirect(undefined)).toBe('/');
    });

    it('null → 回退 /', () => {
      expect(safeRedirect(null)).toBe('/');
    });

    it('空字符串 → 回退 /', () => {
      expect(safeRedirect('')).toBe('/');
    });

    it('数字 → 回退 /', () => {
      expect(safeRedirect(42)).toBe('/');
    });

    it('数组（Vue Router query 重复键场景）→ 回退 /', () => {
      expect(safeRedirect(['/tasks', '/plans'])).toBe('/');
    });

    it('对象 → 回退 /', () => {
      expect(safeRedirect({ path: '/tasks' })).toBe('/');
    });
  });
});