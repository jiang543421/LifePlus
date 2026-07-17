package com.lifepulse.auth.web;

import com.lifepulse.auth.dto.UserResponse;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.common.exception.BusinessException;
import com.lifepulse.common.exception.ErrorCode;
import com.lifepulse.common.web.MyResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自身信息端点（plan §3-B / spec §03 §5.2）。
 *
 * <p>{@code GET /api/v1/users/me} 鉴权后返回当前登录用户的
 * {@link UserResponse}。{@code userId} 通过 {@link AuthenticationPrincipal}
 * 从 Spring Security {@code principal} 读取（由 {@code JwtAuthFilter} 注入，
 * plan §6.1 双轨制）。
 *
 * <p>越权与找不到用户的区分：未认证 → 1002/401（{@code JwtAuthEntryPoint} 处理）；
 * 已认证但 userId 在 DB 找不到 → 1004/404（理论上不应发生，留防御）。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public MyResponse<UserResponse> me(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            // 防御：理论上 SecurityFilterChain 已挡住未认证请求，到达此处说明配置异常
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        return MyResponse.ok(UserResponse.from(user));
    }
}