import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import HomeView from '@/views/HomeView.vue';
import { useAuthStore } from '@/stores/auth';

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('HomeView', () => {
  it('展示当前用户的 greetingName', () => {
    const auth = useAuthStore();
    auth.setUser({ id: 1, email: 'alice@example.com', nickname: '小爱' });
    const wrapper = mount(HomeView, { global: { plugins: [ElementPlus] } });
    expect(wrapper.text()).toContain('小爱');
  });

  it('无 nickname 时展示 email 前缀', () => {
    const auth = useAuthStore();
    auth.setUser({ id: 1, email: 'bob@example.com', nickname: null });
    const wrapper = mount(HomeView, { global: { plugins: [ElementPlus] } });
    expect(wrapper.text()).toContain('bob');
  });
});
