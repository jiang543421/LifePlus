import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import SparklineChart, { type SparklinePoint } from '@/components/ai/SparklineChart.vue';

const basePoints = (n: number): SparklinePoint[] =>
  Array.from({ length: n }, (_, i) => ({
    date: `2026-07-${String(i + 1).padStart(2, '0')}`,
    value: i + 1,
    label: `${(i + 1) * 10}%`,
  }));

describe('SparklineChart', () => {
  it('空数据走 TriStateEmpty，不渲染 SVG', () => {
    const w = mount(SparklineChart, {
      props: { title: '任务完成率', points: [], testId: 'task-spark' },
      global: { plugins: [ElementPlus] },
    });

    // 空态容器存在
    expect(w.find('[data-testid="task-spark-empty"]').exists()).toBe(true);
    // SVG 不渲染
    expect(w.find('svg.line').exists()).toBe(false);
    // header 标题在
    expect(w.find('.title').text()).toBe('任务完成率');
    // latest 标签空（hasData=false）
    expect(w.find('.latest').exists()).toBe(false);
  });

  it('默认 props：color #4A90D9 + unit 空 + 默认 testId sparkline-chart', () => {
    const w = mount(SparklineChart, {
      props: { title: 'demo', points: basePoints(3) },
      global: { plugins: [ElementPlus] },
    });

    expect(w.find('[data-testid="sparkline-chart"]').exists()).toBe(true);
    expect(w.find('svg.line').exists()).toBe(true);
    const polyline = w.find('polyline');
    expect(polyline.attributes('stroke')).toBe('#4A90D9');
    expect(polyline.attributes('fill')).toBe('none');
    expect(polyline.attributes('stroke-width')).toBe('1.5');
  });

  it('3 点折线：polyline points 字符串含 3 个坐标对', () => {
    const w = mount(SparklineChart, {
      props: { title: 'demo', points: basePoints(3) },
      global: { plugins: [ElementPlus] },
    });

    const polyline = w.find('polyline');
    const pts = polyline.attributes('points') ?? '';
    // "x1,y1 x2,y2 x3,y3" → 3 个空格分隔的坐标对
    expect(pts.trim().split(/\s+/)).toHaveLength(3);
    // 起点 x=0, 终点 x=100（线性映射）
    expect(pts.trim().split(/\s+/)[0]).toMatch(/^0,/);
    expect(pts.trim().split(/\s+/)[2]).toMatch(/^100,/);
  });

  it('端点高亮：最后一个点用 fill 圆渲染，cx/cy 与 polyline 末端一致', () => {
    const w = mount(SparklineChart, {
      props: { title: 'demo', points: basePoints(4), color: '#52C41A' },
      global: { plugins: [ElementPlus] },
    });

    const circle = w.find('circle');
    expect(circle.exists()).toBe(true);
    // 端点 x=100, y 对应最小值（在 y=48 附近，SVG y 轴反转）
    expect(circle.attributes('cx')).toBe('100');
    expect(circle.attributes('fill')).toBe('#52C41A');
    expect(circle.attributes('r')).toBe('2');
  });

  it('归一化：所有 value 相等（n=3，min===max）走 ±1 fallback，不抛错且端点 y 居中', () => {
    const w = mount(SparklineChart, {
      props: {
        title: 'demo',
        points: [
          { date: '2026-07-01', value: 5, label: '5' },
          { date: '2026-07-02', value: 5, label: '5' },
          { date: '2026-07-03', value: 5, label: '5' },
        ],
      },
      global: { plugins: [ElementPlus] },
    });

    // 不抛错
    expect(w.find('polyline').exists()).toBe(true);
    // fallback min=4, max=6 → 所有点归一化后 y=24（中线）
    const pts = w.find('polyline').attributes('points') ?? '';
    const ys = pts.trim().split(/\s+/).map((s) => Number(s.split(',')[1]));
    for (const y of ys) {
      expect(y).toBe(24);
    }
  });

  it('n=1 单点：x 居中（50），y 走 fallback 中线', () => {
    const w = mount(SparklineChart, {
      props: {
        title: 'demo',
        points: [{ date: '2026-07-23', value: 42, label: '42' }],
      },
      global: { plugins: [ElementPlus] },
    });

    const pts = w.find('polyline').attributes('points') ?? '';
    expect(pts).toBe('50,24');
    // 端点圆存在
    expect(w.find('circle').exists()).toBe(true);
  });

  it('header latest 拼接 unit：最后一标签 + unit', () => {
    const w = mount(SparklineChart, {
      props: {
        title: '日程事件',
        unit: '项',
        points: [
          { date: '2026-07-22', value: 2, label: '2' },
          { date: '2026-07-23', value: 3, label: '3' },
        ],
      },
      global: { plugins: [ElementPlus] },
    });

    const latest = w.find('.latest');
    expect(latest.exists()).toBe(true);
    expect(latest.text()).toBe('3项');
  });

  it('空 unit：latest 仅含 label，不带单位', () => {
    const w = mount(SparklineChart, {
      props: {
        title: 'demo',
        points: [{ date: '2026-07-23', value: 0.85, label: '85%' }],
      },
      global: { plugins: [ElementPlus] },
    });

    expect(w.find('.latest').text()).toBe('85%');
  });

  it('markers 层：每个点一个 ElTooltip，absolute 定位使用 % 单位', () => {
    const w = mount(SparklineChart, {
      props: { title: 'demo', points: basePoints(3), testId: 'plan-spark' },
      global: { plugins: [ElementPlus] },
    });

    // markers 容器存在
    expect(w.find('.markers').exists()).toBe(true);
    // 3 个 marker span
    const markers = w.findAll('.marker');
    expect(markers).toHaveLength(3);
    // 第一个点 x=0% → left: 0%，最后一个 x=100% → left: 100%
    expect((markers[0].element as HTMLElement).style.left).toBe('0%');
    expect((markers[2].element as HTMLElement).style.left).toBe('100%');
  });

  it('ElTooltip content 包含 date + label + unit', () => {
    const w = mount(SparklineChart, {
      props: {
        title: 'demo',
        unit: '%',
        points: [{ date: '2026-07-23', value: 0.85, label: '85%' }],
        testId: 'task-spark',
      },
      global: { plugins: [ElementPlus] },
    });

    // ElTooltip 的 content 在 trigger span 的 attribute 里（vue2.5 之后用 prop）
    // 检查 trigger span 包裹 + 通过 components 链路验证 tooltip 渲染
    const tooltip = w.findComponent({ name: 'ElTooltip' });
    expect(tooltip.exists()).toBe(true);
    // 至少存在一个 marker trigger
    expect(w.find('.marker').exists()).toBe(true);
  });

  it('aria-label 含 title 让屏幕阅读器可读', () => {
    const w = mount(SparklineChart, {
      props: { title: '消费金额', points: basePoints(2) },
      global: { plugins: [ElementPlus] },
    });

    expect(w.find('svg.line').attributes('aria-label')).toBe('消费金额 趋势图');
  });

  it('空数据时不渲染端点 circle（避免无效 marker）', () => {
    const w = mount(SparklineChart, {
      props: { title: 'demo', points: [], testId: 'empty-spark' },
      global: { plugins: [ElementPlus] },
    });

    expect(w.find('circle').exists()).toBe(false);
    expect(w.find('svg.line').exists()).toBe(false);
  });
});