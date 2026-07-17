import { defineStore } from 'pinia';
import { authApi } from '@/api/auth';
import type { UserResponse } from '@/types';

const LS_ACCESS = 'lp_access_token';
const LS_REFRESH = 'lp_refresh_token';
const LS_USER = 'lp_user';

function readJson<T>(key: string): T | null {
  try {
    const v = localStorage.getItem(key);
    return v ? (JSON.parse(v) as T) : null;
  } catch {
    return null;
  }
}

function writeJson(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value));
}

function removeKey(key: string): void {
  localStorage.removeItem(key);
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: UserResponse | null;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: readJson<string>(LS_ACCESS),
    refreshToken: readJson<string>(LS_REFRESH),
    user: readJson<UserResponse>(LS_USER),
  }),
  getters: {
    isLoggedIn: (s): boolean => !!s.accessToken && !!s.user,
    greetingName: (s): string => s.user?.nickname ?? s.user?.email?.split('@')[0] ?? '用户',
  },
  actions: {
    setTokens(access: string, refresh: string) {
      this.accessToken = access;
      this.refreshToken = refresh;
      writeJson(LS_ACCESS, access);
      writeJson(LS_REFRESH, refresh);
    },
    setUser(user: UserResponse) {
      this.user = user;
      writeJson(LS_USER, user);
    },
    async login(email: string, password: string) {
      const resp = await authApi.login({ email, password });
      this.setTokens(resp.accessToken, resp.refreshToken);
      this.setUser(await authApi.me());
    },
    async register(email: string, password: string, nickname?: string) {
      await authApi.register({ email, password, nickname });
      await this.login(email, password);
    },
    async logout() {
      if (this.refreshToken) {
        try {
          await authApi.logout({ refreshToken: this.refreshToken });
        } catch {
          /* best-effort: 服务端失败也清本地态 */
        }
      }
      this.clear();
    },
    clear() {
      this.accessToken = null;
      this.refreshToken = null;
      this.user = null;
      removeKey(LS_ACCESS);
      removeKey(LS_REFRESH);
      removeKey(LS_USER);
    },
  },
});