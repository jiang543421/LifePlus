import { describe, it, expect, beforeEach, vi, type Mock } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import AiAnalysisView from '@/views/AiAnalysisView.vue';
import { useAiInsightStore } from '@/stores/aiInsight';
import { aiApi } from '@/api/ai';
import { ApiError } from '@/api/http';
import type { AiInsightResponse, Mood } from '@/types';

vi.mock('@/api/ai', () => ({
  aiApi: {
    today: vi.fn(),
    analysis: vi.fn(),
    refresh: vi.fn(),
  },
}));

/**
 * 注入 store 的可控状态：test 不真正调 aiApi，而是直接给 store 喂数据，
 * 覆盖 4 段渲染 / source tag / degraded hint / empty state 这 4 条用例。
 */
function mountWithInsight(insight: AiInsightResponse | null): VueWrapper {
  setActivePinia(createPinia());
  const store = useAiInsightStore();
  // 直接写 state：避免 reactivity 警告 + 不污染 aiApi 的 mock 计数
  store.$patch({ insight, loading: false, error: null, errorCode: null });
  return mount(AiAnalysisView, {
    global: {
      plugins: [ElementPlus],
      // TrendPanel 内嵌在 AiAnalysisView 里（v2.2 C11），但本测试只覆盖
      // 4 段 + source tag + degraded hint + empty state 这几条路径；TrendPanel
      // 需要 router.query + aiTrendApi（依赖太多），单独 stub 掉。
      stubs: { TrendPanel: true },
    },
  });
}

const baseInsight: AiInsightResponse = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '', trend: 'DOWN', deltaText: '较上周 -¥40' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
};

describe('AiAnalysisView — 4 段 + source tag + degraded hint + empty state', () => {
  beforeEach(() => {
    vi.mocked(aiApi.analysis).mockReset();
    vi.mocked(aiApi.refresh).mockReset();
    vi.mocked(aiApi.today).mockReset();
  });

  it('insight 非空 + source=llm → 渲染 4 段（headline / advice / highlight / chips）+ AI 生成 tag', async () => {
    const insight: AiInsightResponse = {
      ...baseInsight,
      source: 'llm',
      advice: '优先完成 2 项高优先级任务',
      highlight: '任务完成率较昨日 +20%',
      mood: 'POSITIVE' as Mood,
    };
    const wrapper = mountWithInsight(insight);
    await flushPromises();

    // headline section
    const headline = wrapper.find('[data-testid="ai-analysis-headline"]');
    expect(headline.exists()).toBe(true);
    expect(headline.text()).toContain(baseInsight.headline);

    // source tag = success 类型 + 文本「AI 生成」（backend source=llm 的 UI 标识）
    const tag = wrapper.find('[data-testid="ai-analysis-source-tag"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toBe('AI 生成');

    // advice section
    const advice = wrapper.find('[data-testid="ai-analysis-advice"]');
    expect(advice.exists()).toBe(true);
    expect(advice.text()).toContain(insight.advice);

    // highlight section + mood 标签
    const highlight = wrapper.find('[data-testid="ai-analysis-highlight"]');
    expect(highlight.exists()).toBe(true);
    expect(highlight.text()).toContain(insight.highlight);
    const mood = wrapper.find('[data-testid="ai-analysis-mood"]');
    expect(mood.exists()).toBe(true);
    expect(mood.text()).toContain('积极');

    // chips section
    const chips = wrapper.findAll('[data-testid="ai-analysis-chip"]');
    expect(chips).toHaveLength(2);

    // source=llm 不显示 degraded hint
    expect(wrapper.find('[data-testid="ai-analysis-degraded-hint"]').exists()).toBe(false);
  });

  it('source=template → 显示「模板生成」tag + degraded 提示（advice/highlight/mood 等 LLM 字段可能为空）', async () => {
    const insight: AiInsightResponse = {
      ...baseInsight,
      source: 'template',
      // LLM 专属字段为空：模拟 L1 失败 → L2 模板降级 的场景。
      advice: undefined,
      highlight: undefined,
    };
    const wrapper = mountWithInsight(insight);
    await flushPromises();

    const tag = wrapper.find('[data-testid="ai-analysis-source-tag"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toBe('模板生成');

    const degraded = wrapper.find('[data-testid="ai-analysis-degraded-hint"]');
    expect(degraded.exists()).toBe(true);
    expect(degraded.text()).toContain('AI 服务暂不可用');

    // advice/highlight 空时回落到占位文案，仍渲染 section 框
    expect(wrapper.find('[data-testid="ai-analysis-advice"]').text()).toContain('暂无建议');
    expect(wrapper.find('[data-testid="ai-analysis-highlight"]').text()).toContain('暂无亮点');
    // mood 为空时整块 chip 不渲染
    expect(wrapper.find('[data-testid="ai-analysis-mood"]').exists()).toBe(false);
  });

  it('insight 为 null → 渲染 ElEmpty + 不渲染任何 section', async () => {
    const wrapper = mountWithInsight(null);
    await flushPromises();

    expect(wrapper.find('[data-testid="ai-analysis-empty"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="ai-analysis-headline"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="ai-analysis-advice"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="ai-analysis-highlight"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="ai-analysis-chips"]').exists()).toBe(false);
  });

  it('mount 时 onMounted 触发 store.loadAnalysis()（若缓存新鲜则跳过请求）', async () => {
    vi.mocked(aiApi.analysis).mockResolvedValue({ ...baseInsight, source: 'llm' });
    setActivePinia(createPinia());
    const store = useAiInsightStore();
    // 模拟从首页带过来的新鲜缓存
    store.$patch({ insight: { ...baseInsight, source: 'llm', freshnessSeconds: 60 }, loading: false });

    mount(AiAnalysisView, {
      global: { plugins: [ElementPlus], stubs: { TrendPanel: true } },
    });
    await flushPromises();

    // isFresh=true → store 不发请求
    expect(vi.mocked(aiApi.analysis)).not.toHaveBeenCalled();
  });
});
