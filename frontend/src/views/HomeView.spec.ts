import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { nextTick } from 'vue';
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus, { ElMessage, ElTooltip } from 'element-plus';
import HomeView from '@/views/HomeView.vue';
import { useAuthStore } from '@/stores/auth';
import { aiApi } from '@/api/ai';
import { ApiError } from '@/api/http';
import type { AiInsightResponse } from '@/types';
import { AuthErrorCode, ExtraErrorCode } from '@/types';

// ---- mocks ----
vi.mock('@/api/ai', () => ({
  aiApi: {
    today: vi.fn(),
    refresh: vi.fn(),
  },
}));

const routerPush = vi.fn().mockResolvedValue(undefined);
vi.mock('@/router', () => ({
  default: {
    push: (...args: unknown[]) => routerPush(...args),
    currentRoute: { value: { fullPath: '/' } },
  },
}));
// HomeView 调用 useRouter() 直接来自 'vue-router'，需要同步 mock 同一函数。
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router');
  return {
    ...actual,
    useRouter: () => ({
      push: (...args: unknown[]) => routerPush(...args),
      currentRoute: { value: { fullPath: '/' } },
    }),
  };
});

const sampleInsight: AiInsightResponse = {
  headline: '今日任务完成率 80%；本周消费 ¥420。',
  chips: [
    { key: 'taskCompletion', label: '任务完成', value: '80', unit: '%', trend: 'FLAT', deltaText: '与昨日持平' },
    { key: 'weeklyExpense', label: '本周消费', value: '¥420', unit: '', trend: 'DOWN', deltaText: '较上周 -¥40' },
    { key: 'planDensity', label: '日程', value: '3', unit: '项', trend: 'NONE', deltaText: '今日 3 项' },
  ],
  generatedAt: '2026-07-22T08:00:00Z',
  freshnessSeconds: 12,
};

function mountHome(): VueWrapper {
  return mount(HomeView, {
    global: {
      plugins: [ElementPlus, createPinia()],
      stubs: {
        // TopBar 含汉堡菜单 / 用户菜单链路，单元测试不依赖其内部，挂 stub。
        TopBar: true,
        // router-link 由 ModuleCard 渲染；用 RouterLinkStub 保留 <a> 标签与 to 属性，
        // 让模块卡的 href 断言可执行（不依赖真实 router 实例）。
        RouterLink: RouterLinkStub,
      },
    },
  });
}

async function clickCard(wrapper: VueWrapper, cardKey: string): Promise<void> {
  const card = wrapper.find(`[data-testid="home-card-${cardKey}"]`);
  const btn = card.find('[data-testid="module-card-placeholder"]');
  expect(btn.exists(), `placeholder button for ${cardKey} not found`).toBe(true);
  await btn.trigger('click');
  await nextTick();
}

describe('HomeView — AI 卡集成', () => {
  let elMessageWarn: ReturnType<typeof vi.spyOn> = vi.fn();

  beforeEach(() => {
    setActivePinia(createPinia());
    const auth = useAuthStore();
    auth.setUser({ id: 1, email: 'lp@ex.com', nickname: 'lp' });
    elMessageWarn = vi.spyOn(ElMessage, 'warning') as unknown as ReturnType<typeof vi.spyOn>;
    vi.mocked(aiApi.today).mockReset();
    vi.mocked(aiApi.refresh).mockReset();
  });

  afterEach(() => {
    elMessageWarn.mockRestore();
  });

  it('点击 AI 占位卡调用 aiApi.today() 并通过 HomeView 内部状态持有 insight', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce(sampleInsight);
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    expect(vi.mocked(aiApi.today)).toHaveBeenCalledTimes(1);
    // HomeView 自己持有的 aiInsight 用于驱动 AiDrawer 的 v-bind; EP 的 ElDrawer
    // 在 jsdom 下不会真的渲染 slot 内容，但 v-model:show 的绑定仍生效，
    // 所以改测 HomeView 内部 v-model 状态 (aiDrawerOpen === true)。
    const vm = wrapper.vm as unknown as { aiDrawerOpen: boolean; aiInsight: AiInsightResponse | null };
    expect(vm.aiDrawerOpen).toBe(true);
    expect(vm.aiInsight).toEqual(sampleInsight);
  });

  it('点击日报模块卡 → 渲染 router-link（不再是 button），不调 aiApi', async () => {
    const wrapper = mountHome();
    await flushPromises();

    // v1.2.4 起 daily 卡升级为 module（spec §08-daily-report-design），
    // ModuleCard 渲染 <a class="module-card">（router-link stub），
    // 不再渲染 placeholder button，也不再触发「即将上线」toast。
    const dailyWrap = wrapper.find('[data-testid="home-card-daily"]');
    expect(dailyWrap.find('a.module-card').exists()).toBe(true);
    // ModuleCard 在 module 形态下不渲染 placeholder button
    expect(dailyWrap.find('[data-testid="module-card-placeholder"]').exists()).toBe(false);
    // 验证 aiApi 未被调用、toast 未弹出
    expect(vi.mocked(aiApi.today)).not.toHaveBeenCalled();
    expect(elMessageWarn).not.toHaveBeenCalled();
  });

  it('aiApi.today() 抛 1501 → 弹降级 Toast，drawer open 但 insight 仍为 null', async () => {
    vi.mocked(aiApi.today).mockRejectedValueOnce(
      new ApiError(ExtraErrorCode.AiDegraded, 'AI 洞察数据暂时不可用，请稍后重试'),
    );
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    expect(elMessageWarn).toHaveBeenCalledWith('AI 洞察数据暂时不可用，请稍后重试');
    const vm = wrapper.vm as unknown as { aiDrawerOpen: boolean; aiInsight: AiInsightResponse | null };
    expect(vm.aiDrawerOpen).toBe(true);
    expect(vm.aiInsight).toBeNull();
  });

  it('aiApi.today() 抛 1006 → 弹限流 Toast', async () => {
    vi.mocked(aiApi.today).mockRejectedValueOnce(
      new ApiError(AuthErrorCode.RateLimit, 'AI 洞察请求过于频繁，请稍后重试'),
    );
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    expect(elMessageWarn).toHaveBeenCalledWith('AI 洞察请求过于频繁，请稍后重试');
  });

  it('抽屉「刷新」按钮 emit → aiApi.refresh() 被调用，insight 替换为新值', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce(sampleInsight);
    const refreshed: AiInsightResponse = {
      ...sampleInsight,
      freshnessSeconds: 0,
      headline: '（重算后）今日已完成 90%。',
    };
    vi.mocked(aiApi.refresh).mockResolvedValueOnce(refreshed);
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    // 抽屉 body 内 ai-drawer-refresh 节点；通过 wrapper 内部组件实例直接 emit refresh 事件。
    const vm = wrapper.vm as unknown as {
      aiInsight: AiInsightResponse | null;
      aiDrawerOpen: boolean;
    };
    expect(vm.aiInsight).toEqual(sampleInsight);

    // 在 AiDrawer 上模拟 emit refresh：HomeView 自己定义 emit('refresh') 绑定；
    // 直接通过 vm 找 AiDrawer 子组件（用 ids）找到对应 instance。
    const aiDrawer = wrapper.findComponent({ name: undefined }) ? null : null;
    // 改为：通过 AiDrawer 实例触发 emit。
    const drawerInstance = wrapper.findComponent({ ref: undefined });
    // 兜底：直接取组件树中 AiDrawer
    const AiDrawerCmp = (await import('@/components/AiDrawer.vue')).default;
    const drawer = wrapper.findComponent(AiDrawerCmp);
    expect(drawer.exists()).toBe(true);
    await drawer.vm.$emit('refresh');
    await flushPromises();

    expect(vi.mocked(aiApi.refresh)).toHaveBeenCalledTimes(1);
    expect(vm.aiInsight).toEqual(refreshed);
  });

  it('aiApi.refresh() 抛任意 ApiError → Toast 不阻断抽屉，insight 仍保留旧值', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce(sampleInsight);
    vi.mocked(aiApi.refresh).mockRejectedValueOnce(
      new ApiError(AuthErrorCode.RateLimit, 'too fast'),
    );
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    const AiDrawerCmp = (await import('@/components/AiDrawer.vue')).default;
    const drawer = wrapper.findComponent(AiDrawerCmp);
    await drawer.vm.$emit('refresh');
    await flushPromises();

    // RateLimit 会被 handleAiError 翻译成固定中文 msg；不应原样泄漏 axios/raw 文本
    expect(elMessageWarn).toHaveBeenCalledWith('AI 洞察请求过于频繁，请稍后重试');
    const vm = wrapper.vm as unknown as { aiInsight: AiInsightResponse | null };
    // refresh 失败不应清空 insight，仍保留旧值
    expect(vm.aiInsight).toEqual(sampleInsight);
  });

  it('aiApi.today() 抛网络错（code=-1）→ 兜底 Toast（不暴露技术细节）', async () => {
    vi.mocked(aiApi.today).mockRejectedValueOnce(new ApiError(-1, 'Network Error'));
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    expect(elMessageWarn).toHaveBeenCalledTimes(1);
    const msg = elMessageWarn.mock.calls[0][0] as string;
    expect(msg).toMatch(/网络异常/);
  });

  // ---- v2.1 PR3：AI 卡 source 角标 ----

  it('AI 卡加载成功（source=llm）→ 右上角显示「AI 智能」角标', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce({ ...sampleInsight, source: 'llm' });
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    const badge = wrapper.find('[data-testid="home-card-source-badge"]');
    expect(badge.exists()).toBe(true);
    expect(badge.text()).toBe('AI 智能');
    expect(badge.attributes('aria-label')).toBe('AI 智能生成');
    expect(badge.classes()).toContain('home-view__source-badge--llm');
  });

  it('AI 卡 source=template → 显示「模板」角标（灰）', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce({ ...sampleInsight, source: 'template' });
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    const badge = wrapper.find('[data-testid="home-card-source-badge"]');
    expect(badge.exists()).toBe(true);
    expect(badge.text()).toBe('模板');
    expect(badge.classes()).toContain('home-view__source-badge--template');
  });

  it('AI 卡未加载（aiInsight 为 null）→ 不显示角标', () => {
    const wrapper = mountHome();
    expect(wrapper.find('[data-testid="home-card-source-badge"]').exists()).toBe(false);
  });

  it('非 AI 卡（task / daily 等）→ 永远不显示角标', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce({ ...sampleInsight, source: 'llm' });
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    // AI 卡有角标，其他卡没有
    expect(wrapper.find('[data-testid="home-card-source-badge"]').exists()).toBe(true);
    const aiCard = wrapper.find('[data-testid="home-card-ai"]');
    expect(aiCard.find('[data-testid="home-card-source-badge"]').exists()).toBe(true);
    const dailyCard = wrapper.find('[data-testid="home-card-daily"]');
    expect(dailyCard.find('[data-testid="home-card-source-badge"]').exists()).toBe(false);
  });

  // ---- v1.2.5 #2：AI 角标 hover 提示 ----

  it('AI 角标（llm）由 ElTooltip 包裹，content 说明 LLM 智能生成', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce({ ...sampleInsight, source: 'llm' });
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    // ElTooltip 不透传 data-testid 到 DOM，改用组件实例 props 断言 content。
    const tooltip = wrapper.findComponent(ElTooltip);
    expect(tooltip.exists()).toBe(true);
    const content = tooltip.props('content') as string;
    expect(content).toContain('AI 模型实时生成');
  });

  it('AI 角标（template）tooltip 文案说明模板降级语义', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce({ ...sampleInsight, source: 'template' });
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    const tooltip = wrapper.findComponent(ElTooltip);
    expect(tooltip.exists()).toBe(true);
    const content = tooltip.props('content') as string;
    expect(content).toContain('模板');
    expect(content).toContain('AI 模型暂不可用');
  });

  // ---- v2.1 PR3：抽屉「查看完整分析 →」跳转 ----

  it('抽屉 emit open-analysis → drawer 关闭 + router.push(\'ai-analysis\')', async () => {
    vi.mocked(aiApi.today).mockResolvedValueOnce({ ...sampleInsight, source: 'llm' });
    const wrapper = mountHome();
    await flushPromises();

    await clickCard(wrapper, 'ai');
    await flushPromises();

    const AiDrawerCmp = (await import('@/components/AiDrawer.vue')).default;
    const drawer = wrapper.findComponent(AiDrawerCmp);
    expect(drawer.exists()).toBe(true);

    const vm = wrapper.vm as unknown as { aiDrawerOpen: boolean };
    expect(vm.aiDrawerOpen).toBe(true);

    await drawer.vm.$emit('open-analysis');
    await flushPromises();

    expect(vm.aiDrawerOpen).toBe(false);
    expect(routerPush).toHaveBeenCalledWith({ name: 'ai-analysis' });
  });
});
