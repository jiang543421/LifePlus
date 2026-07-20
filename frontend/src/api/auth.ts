import http from './http';
import type {
  AuthResponse,
  ChangePasswordRequest,
  DeleteAccountRequest,
  LoginRequest,
  LogoutRequest,
  RegisterRequest,
  UpdateProfileRequest,
  UserResponse,
} from '@/types';

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
  /** Settings v1.1 — 更新昵称（trim 后空 → null）。 */
  updateProfile(req: UpdateProfileRequest) {
    return http.patch<unknown, UserResponse>('/users/me', req);
  },
  /** Settings v1.1 — 修改密码（强制 revoke refresh）。 */
  changePassword(req: ChangePasswordRequest) {
    return http.post<unknown, void>('/users/me/password', req);
  },
  /** Settings v1.1 — 注销账号（需当前密码二次验证 + 软删）。 */
  deleteAccount(req: DeleteAccountRequest) {
    return http.delete<unknown, void>('/users/me', { data: req });
  },
};