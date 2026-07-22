package com.lifepulse.auth.service;

import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.UserResponse;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.RefreshTokenMapper;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 用户设置服务（Settings v1.1，issue 2026-07-18-settings-v1-1）。
 *
 * <p>负责 {@code /users/me} 的三个写动作：
 * <ul>
 *   <li>{@link #updateNickname} — 改昵称，trim 后空 → {@code null}</li>
 *   <li>{@link #changePassword} — 改密码，强制 revoke 该用户所有 refresh token</li>
 *   <li>{@link #deleteAccount} — 注销账号，软删（{@code @TableLogic}）+ revoke</li>
 * </ul>
 *
 * <p>{@code deleteAccount} 对已软删用户幂等（第二次调用 no-op 返回 200），符合前端
 * 「删除成功 → 跳 /login → 旧 token 调用无歧义」的体验。
 *
 * <p>3 个写动作均按 {@code userId} 维度限流（{@link RateLimiter#hit}），
 * key 前缀见 {@link AuthConstants} 的 {@code *_RL_KEY_PREFIX}。阈值参考 §7.2 +
 * Review HIGH-1：合法 token 持有者对写动作的频次保护。
 *
 * <p>错误码统一复用 {@link ErrorCode}：1001 / 1002 / 1003 / 1004 / 1006。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    public UserService(UserMapper userMapper,
                       RefreshTokenMapper refreshTokenMapper,
                       PasswordEncoder passwordEncoder,
                       RateLimiter rateLimiter) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
    }


    /**
     * 更新昵称；trim 后空字符串落为 {@code null}（允许清空）。
     *
     * <p>走专用 {@link UserMapper#updateNicknameById} 而非
     * {@code BaseMapper.updateById}，原因是后者默认 {@code FieldStrategy.NOT_NULL}
     * 会跳过 null 字段，导致清空昵称静默失效。
     *
     * @param userId   当前用户 id（来自 {@code @AuthenticationPrincipal}）
     * @param nickname 新昵称（可为 {@code null} 或空）
     * @return 更新后的 {@link UserResponse}
     * @throws BusinessException 1002 未登录 / 1004 用户不存在
     */
    public UserResponse updateNickname(Long userId, String nickname) {
        requireUserId(userId);
        checkRateLimit(AuthConstants.NICKNAME_RL_KEY_PREFIX + userId,
                AuthConstants.NICKNAME_RL_MAX, AuthConstants.NICKNAME_RL_WINDOW);
        // 先校验存在（防御性，否则 mapper 返回 0 难定位原因）
        User u = loadActiveUser(userId);
        String normalized = (nickname == null || nickname.trim().isEmpty()) ? null : nickname.trim();
        userMapper.updateNicknameById(userId, normalized);
        u.setNickname(normalized);  // 内存里也同步，避免 re-fetch
        log.debug("user nickname updated uid={}", userId);
        return UserResponse.from(u);
    }


    /**
     * 修改密码：BCrypt 校验旧密码 → 编码新密码 → 撤销所有 refresh token。
     *
     * <p>access token 仍可存活至自然过期（≤15min，{@code lp.jwt.access-ttl: PT15M}，
     * issue 2026-07-18 HIGH-2 决策）。MVP1 暂无 JWT deny-list；前端改密码成功后
     * 立即 {@code auth.clear()} 退出，与该限制配套（issue 决策记录）。
     *
     * @throws BusinessException 1002 未登录 / 旧密码错；1004 用户不存在
     */
    /**
     * 修改密码 + 撤销该用户所有 refresh token（两者必须在同一事务，否则断电后会
     * 出现「新 hash 已写但 refresh 未撤销」的中间态——Review HIGH-1 的姊妹问题）。
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        requireUserId(userId);
        checkRateLimit(AuthConstants.PASSWORD_RL_KEY_PREFIX + userId,
                AuthConstants.PASSWORD_RL_MAX, AuthConstants.PASSWORD_RL_WINDOW);
        User u = loadActiveUser(userId);
        if (!passwordEncoder.matches(oldPassword, u.getPasswordHash())) {
            log.warn("change password: old mismatch uid={}", userId);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "当前密码不正确");
        }
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(u);
        int revoked = refreshTokenMapper.revokeAllByUserId(userId, OffsetDateTime.now());
        log.info("password changed uid={} revokedRefreshTokens={}", userId, revoked);
    }


    /**
     * 注销账号：BCrypt 校验当前密码 → 软删（{@code @TableLogic} 翻 {@code deleted=1}）
     * → 撤销所有 refresh token。已软删用户重复调用为幂等 no-op。
     *
     * @throws BusinessException 1002 未登录 / 当前密码错
     */
    /**
     * 注销账号 + 撤销所有 refresh token；同事务保证原子性。
     * 已软删用户重复调用为幂等 no-op（不抛 1004）。
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        requireUserId(userId);
        checkRateLimit(AuthConstants.DELETE_ACCOUNT_RL_KEY_PREFIX + userId,
                AuthConstants.DELETE_ACCOUNT_RL_MAX, AuthConstants.DELETE_ACCOUNT_RL_WINDOW);
        User u = userMapper.selectById(userId);
        if (u == null) {
            // 已被软删或不存在 → 幂等 no-op，不抛 1004（与 plan 决策一致）
            log.info("deleteAccount: already deleted or missing uid={}", userId);
            return;
        }
        if (!passwordEncoder.matches(password, u.getPasswordHash())) {
            log.warn("delete account: password mismatch uid={}", userId);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "当前密码不正确");
        }
        userMapper.deleteById(u.getId());
        int revoked = refreshTokenMapper.revokeAllByUserId(userId, OffsetDateTime.now());
        log.info("account deleted uid={} revokedRefreshTokens={}", userId, revoked);
    }


    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
    }

    private User loadActiveUser(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            log.warn("user not found uid={}", userId);
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        return u;
    }

    /**
     * 限流计数；超限抛 1006（与 login/register 一致，便于前端 showAuthError 复用）。
     * 调用方已通过 {@link #requireUserId} 保证 userId 非空。
     */
    private void checkRateLimit(String rlKey, int max, java.time.Duration window) {
        if (rateLimiter.hit(rlKey, max, window)) {
            log.warn("rate limit exceeded: key={}", rlKey);
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMIT, "操作过于频繁，请稍后再试");
        }
    }
}
