package com.lifepulse.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.auth.AuthConstants;
import com.lifepulse.auth.dto.UserResponse;
import com.lifepulse.auth.entity.User;
import com.lifepulse.auth.repository.UserMapper;
import com.lifepulse.auth.service.JwtService;
import com.lifepulse.auth.service.UserService;
import com.lifepulse.common.exception.GlobalExceptionHandler;
import com.lifepulse.security.JwtAuthEntryPoint;
import com.lifepulse.security.JwtAuthFilter;
import com.lifepulse.security.SecurityConfig;
import com.lifepulse.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController Settings v1.1 切片测试（issue 2026-07-18-settings-v1-1）。
 *
 * <p>覆盖 PATCH /me、POST /me/password、DELETE /me 三个端点的鉴权 / 校验 / 越权 / 错误码契约；
 * UserService mock，UserMapper 仍 mock 以兼容既有 GET /me 路径。
 *
 * <p>鉴权旁路策略与 {@link UserControllerWebTest} 一致：mock {@link JwtAuthFilter}
 * 显式转发到 chain；测试用例通过 {@code authentication(...)} 注入 principal。
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class, JwtAuthEntryPoint.class, GlobalExceptionHandler.class})
class UserSettingsWebTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void bypassFilter() throws Exception {
        Mockito.doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthFilter).doFilter(
                Mockito.any(ServletRequest.class),
                Mockito.any(ServletResponse.class),
                Mockito.any(FilterChain.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    // ---------- PATCH /me (updateNickname) ----------

    @Test
    void patchMe_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"NewName\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));
    }

    @Test
    void patchMe_validToken_returnsUpdatedUserResponse() throws Exception {
        UserResponse updated = new UserResponse(42L, "alice@example.com", "NewName", null);
        when(userService.updateNickname(eq(42L), anyString())).thenReturn(updated);

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"NewName\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("NewName"));
    }

    @Test
    void patchMe_blankNickname_allowedAndNormalized() throws Exception {
        UserResponse updated = new UserResponse(42L, "alice@example.com", null, null);
        when(userService.updateNickname(eq(42L), anyString())).thenReturn(updated);

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").doesNotExist());
    }

    @Test
    void patchMe_nicknameTooLong_returns400WithCode1001() throws Exception {
        String tooLong = "a".repeat(33);

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));
    }

    @Test
    void patchMe_userNotFound_returns404WithCode1004() throws Exception {
        when(userService.updateNickname(anyLong(), anyString()))
                .thenThrow(new com.lifepulse.common.exception.BusinessException(
                        com.lifepulse.common.exception.ErrorCode.NOT_FOUND, "user not found"));

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_NOT_FOUND));
    }

    // ---------- POST /me/password (changePassword) ----------

    @Test
    void postPassword_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(post("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old12345\",\"newPassword\":\"New12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));
    }

    @Test
    void postPassword_validToken_returns200() throws Exception {
        doNothing().when(userService).changePassword(eq(42L), anyString(), anyString());

        mvc.perform(post("/api/v1/users/me/password")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old12345\",\"newPassword\":\"New12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void postPassword_wrongOld_returns401WithCode1002() throws Exception {
        doThrow(new com.lifepulse.common.exception.BusinessException(
                com.lifepulse.common.exception.ErrorCode.BAD_CREDENTIALS, "当前密码不正确"))
                .when(userService).changePassword(anyLong(), anyString(), anyString());

        mvc.perform(post("/api/v1/users/me/password")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong0000\",\"newPassword\":\"New12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));
    }

    @Test
    void postPassword_weakNewPassword_returns400WithCode1001() throws Exception {
        // 无字母无数字的弱密码触发 @Pattern 校验
        mvc.perform(post("/api/v1/users/me/password")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old1234\",\"newPassword\":\"@@@@@@\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));
    }

    @Test
    void postPassword_tooShort_returns400WithCode1001() throws Exception {
        mvc.perform(post("/api/v1/users/me/password")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Old1234\",\"newPassword\":\"Ab1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));
    }

    // ---------- DELETE /me (deleteAccount) ----------

    @Test
    void deleteMe_noToken_returns401WithCode1002() throws Exception {
        mvc.perform(delete("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Pass1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));
    }

    @Test
    void deleteMe_validToken_returns200() throws Exception {
        doNothing().when(userService).deleteAccount(anyLong(), anyString());

        mvc.perform(delete("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Pass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deleteMe_wrongPassword_returns401WithCode1002() throws Exception {
        doThrow(new com.lifepulse.common.exception.BusinessException(
                com.lifepulse.common.exception.ErrorCode.BAD_CREDENTIALS, "当前密码不正确"))
                .when(userService).deleteAccount(anyLong(), anyString());

        mvc.perform(delete("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_BAD_CREDENTIALS));
    }

    @Test
    void deleteMe_blankPassword_returns400WithCode1001() throws Exception {
        mvc.perform(delete("/api/v1/users/me")
                        .with(authentication(authOf(42L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(AuthConstants.ERR_VALIDATION));
    }

    // ---------- helpers ----------

    private static UsernamePasswordAuthenticationToken authOf(long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}