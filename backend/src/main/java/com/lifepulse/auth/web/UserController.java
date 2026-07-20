package com.lifepulse.auth.web;

import com.lifepulse.auth.dto.ChangePasswordRequest;
import com.lifepulse.auth.dto.DeleteAccountRequest;
import com.lifepulse.auth.dto.UpdateNicknameRequest;
import com.lifepulse.auth.dto.UserResponse;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.auth.service.UserService;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自身信息端点（plan §3-B / spec §03 §5.2 + Settings v1.1）。
 *
 * <p>4 个端点：
 * <ul>
 *   <li>{@code GET /api/v1/users/me} — 当前用户资料</li>
 *   <li>{@code PATCH /api/v1/users/me} — 更新昵称（Settings v1.1）</li>
 *   <li>{@code POST /api/v1/users/me/password} — 修改密码（强制 revoke refresh）</li>
 *   <li>{@code DELETE /api/v1/users/me} — 注销账号（软删 + revoke）</li>
 * </ul>
 *
 * <p>{@code userId} 通过 {@link AuthenticationPrincipal} 从 Spring Security
 * {@code principal} 读取（由 {@code JwtAuthFilter} 注入）。3 个新写动作委托给
 * {@link UserService}，保持 controller 薄；既有 {@code me} 仍直接走
 * {@link UserMapper}（不重构以减小 blast radius）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserMapper userMapper;
    private final UserService userService;

    public UserController(UserMapper userMapper, UserService userService) {
        this.userMapper = userMapper;
        this.userService = userService;
    }

    // ---------- GET /me (existing) ----------

    @GetMapping("/me")
    public MyResponse<UserResponse> me(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        return MyResponse.ok(UserResponse.from(user));
    }

    // ---------- PATCH /me (Settings v1.1) ----------

    /**
     * 更新昵称。空字符串 trim 后落为 {@code null}（允许清空）。
     *
     * @return 更新后的 {@link UserResponse}
     */
    @PatchMapping("/me")
    public MyResponse<UserResponse> updateMe(
            @Valid @RequestBody UpdateNicknameRequest req,
            @AuthenticationPrincipal Long userId) {
        return MyResponse.ok(userService.updateNickname(userId, req.nickname()));
    }

    // ---------- POST /me/password (Settings v1.1) ----------

    /**
     * 修改密码。服务端会撤销该用户所有现存 refresh token。
     */
    @PostMapping("/me/password")
    public MyResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal Long userId) {
        userService.changePassword(userId, req.oldPassword(), req.newPassword());
        return MyResponse.ok(null);
    }

    // ---------- DELETE /me (Settings v1.1) ----------

    /**
     * 注销账号：要求客户端传当前密码二次验证；服务端软删（{@code @TableLogic}）
     * 并撤销所有 refresh token。已软删用户重复调用为幂等 no-op。
     */
    @DeleteMapping("/me")
    public MyResponse<Void> deleteMe(
            @Valid @RequestBody DeleteAccountRequest req,
            @AuthenticationPrincipal Long userId) {
        userService.deleteAccount(userId, req.password());
        return MyResponse.ok(null);
    }
}