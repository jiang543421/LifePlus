import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import HelloView from '@/views/HelloView.vue';

describe('HelloView', () => {
    it('shows hello text', () => {
        const wrapper = mount(HelloView);
        expect(wrapper.text()).toContain('Hello');
    });
});
