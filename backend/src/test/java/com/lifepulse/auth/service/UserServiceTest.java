package com.lifepulse.auth.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.UserResponse;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.RefreshTokenMapper;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.security.RateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试（Settings v1.1，issue 2026-07-18-settings-v1-1）。
 *
 * <p>覆盖 updateNickname / changePassword / deleteAccount 三个动作的核心契约：
 * <ul>
 *   <li>trim 后空 → null（昵称允许清空）</li>
 *   <li>旧密码 / 当前密码错误 → 1002（与登录语义统一，避免账号枚举）</li>
 *   <li>改密码 / 注销后必须 revoke 所有 refresh token</li>
 *   <li>注销对已软删用户幂等（第二次调用 no-op）</li>
 * </ul>
 *
 * <p>{@code PasswordEncoder} 用 Mockito mock；{@code matches} 返回 true / false，
 * {@code encode} 返回可预测字符串以便断言。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimiter rateLimiter;

    @InjectMocks
    private UserService service;

    // ---------- updateNickname ----------

    @Test
    void updateNickname_trimsAndPersists_returnsUpdatedUserResponse() {
        User u = userWithId(7L, "alice@lifepulse.test", "OldName", "hash-old");
        when(userMapper.selectById(7L)).thenReturn(u);

        UserResponse res = service.updateNickname(7L, "  NewName  ");

        assertThat(res.id()).isEqualTo(7L);
        assertThat(res.nickname()).isEqualTo("NewName");

        ArgumentCaptor<String> nickCap = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updateNicknameById(eq(7L), nickCap.capture());
        assertThat(nickCap.getValue()).isEqualTo("NewName");
    }

    @Test
    void updateNickname_emptyString_normalizesToNull() {
        User u = userWithId(7L, "alice@lifepulse.test", "OldName", "hash");
        when(userMapper.selectById(7L)).thenReturn(u);

        UserResponse res = service.updateNickname(7L, "   ");

        assertThat(res.nickname()).isNull();
        verify(userMapper).updateNicknameById(eq(7L), isNull());
    }

    @Test
    void updateNickname_nullValue_persistsAsNull() {
        User u = userWithId(7L, "alice@lifepulse.test", "OldName", "hash");
        when(userMapper.selectById(7L)).thenReturn(u);

        UserResponse res = service.updateNickname(7L, null);

        assertThat(res.nickname()).isNull();
        verify(userMapper).updateNicknameById(eq(7L), isNull());
    }

    @Test
    void updateNickname_userNotFound_throws1004() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateNickname(99L, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_NOT_FOUND);

        verify(userMapper, never()).updateNicknameById(anyLong(), anyString());
    }

    @Test
    void updateNickname_nullUserId_throws1002() {
        assertThatThrownBy(() -> service.updateNickname(null, "x"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    void updateNickname_rateLimitExceeded_throws1006() {
        when(rateLimiter.hit(eq(AuthConstants.NICKNAME_RL_KEY_PREFIX + 7L),
                anyInt(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.updateNickname(7L, "newname"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_LOGIN_RATE_LIMIT);

        verify(userMapper, never()).selectById(anyLong());
        verify(userMapper, never()).updateNicknameById(anyLong(), anyString());
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_happy_encodesNew_andRevokesAllRefreshTokens() {
        User u = userWithId(7L, "alice@lifepulse.test", "Alice", "hash-old");
        when(userMapper.selectById(7L)).thenReturn(u);
        when(passwordEncoder.matches("oldPass1", "hash-old")).thenReturn(true);
        when(passwordEncoder.encode("newPass2")).thenReturn("hash-new");
        when(refreshTokenMapper.revokeAllByUserId(anyLong(), any(OffsetDateTime.class))).thenReturn(2);

        service.changePassword(7L, "oldPass1", "newPass2");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("hash-new");
        verify(refreshTokenMapper).revokeAllByUserId(anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void changePassword_wrongOld_throws1002_andDoesNotEncodeOrRevoke() {
        User u = userWithId(7L, "alice@lifepulse.test", "Alice", "hash-old");
        when(userMapper.selectById(7L)).thenReturn(u);
        when(passwordEncoder.matches("wrong", "hash-old")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(7L, "wrong", "newPass2"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userMapper, never()).updateById(any(User.class));
        verify(refreshTokenMapper, never()).revokeAllByUserId(anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void changePassword_userNotFound_throws1004() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.changePassword(99L, "old", "newPass2"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_NOT_FOUND);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void changePassword_rateLimitExceeded_throws1006() {
        when(rateLimiter.hit(eq(AuthConstants.PASSWORD_RL_KEY_PREFIX + 7L),
                anyInt(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(7L, "oldPass1", "newPass2"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_LOGIN_RATE_LIMIT);

        verify(userMapper, never()).selectById(anyLong());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // ---------- deleteAccount ----------

    @Test
    void deleteAccount_happy_softDeletesUser_andRevokesAllRefreshTokens() {
        User u = userWithId(7L, "alice@lifepulse.test", "Alice", "hash");
        when(userMapper.selectById(7L)).thenReturn(u);
        when(passwordEncoder.matches("Pass1234", "hash")).thenReturn(true);
        when(refreshTokenMapper.revokeAllByUserId(anyLong(), any(OffsetDateTime.class))).thenReturn(3);

        service.deleteAccount(7L, "Pass1234");

        verify(userMapper).deleteById(7L);  // @TableLogic 软删
        verify(refreshTokenMapper).revokeAllByUserId(anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void deleteAccount_wrongPassword_throws1002_noDeleteNoRevoke() {
        User u = userWithId(7L, "alice@lifepulse.test", "Alice", "hash");
        when(userMapper.selectById(7L)).thenReturn(u);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteAccount(7L, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(userMapper, never()).deleteById(anyLong());
        verify(refreshTokenMapper, never()).revokeAllByUserId(anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void deleteAccount_alreadySoftDeleted_idempotentNoOp() {
        // 第二次调用：selectById 因 @TableLogic 过滤返回 null
        when(userMapper.selectById(7L)).thenReturn(null);

        service.deleteAccount(7L, "Pass1234");  // 不抛错

        verify(userMapper, never()).deleteById(anyLong());
        verify(refreshTokenMapper, never()).revokeAllByUserId(anyLong(), any(OffsetDateTime.class));
    }

    @Test
    void deleteAccount_nullUserId_throws1002() {
        assertThatThrownBy(() -> service.deleteAccount(null, "Pass1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_BAD_CREDENTIALS);

        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    void deleteAccount_rateLimitExceeded_throws1006() {
        when(rateLimiter.hit(eq(AuthConstants.DELETE_ACCOUNT_RL_KEY_PREFIX + 7L),
                anyInt(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> service.deleteAccount(7L, "Pass1234"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(AuthConstants.ERR_LOGIN_RATE_LIMIT);

        verify(userMapper, never()).selectById(anyLong());
        verify(userMapper, never()).deleteById(anyLong());
        verify(refreshTokenMapper, never()).revokeAllByUserId(anyLong(), any(OffsetDateTime.class));
    }

    // ---------- helpers ----------

    private User userWithId(long id, String email, String nickname, String hash) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setNickname(nickname);
        u.setPasswordHash(hash);
        return u;
    }
}