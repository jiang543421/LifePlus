import http from './http';
import type { AuthResponse, LoginRequest, LogoutRequest, RegisterRequest, UserResponse } from '@/types';

interface RegisterResponse {
  userId: number;
}

export const authApi = {
  register(req: RegisterRequest) {
    return http.post<unknown, RegisterResponse>('/auth/register', req);
  },
  login(req: LoginRequest) {
    return http.post<unknown, AuthResponse>('/auth/login', req);
  },
  refresh(refreshToken: string) {
    return http.post<unknown, AuthResponse>('/auth/refresh', { refreshToken });
  },
  logout(req: LogoutRequest) {
    return http.post<unknown, void>('/auth/logout', req);
  },
  me() {
    return http.get<unknown, UserResponse>('/users/me');
  },
};