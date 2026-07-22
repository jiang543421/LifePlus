package com.lifepulse.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1.2-B · DTO Bean Validation 单元测试（plan §9）。
 *
 * <p>使用 Hibernate Validator（Spring Boot Validation starter 引入）触发 record 字段约束；
 * 任何 record 不存在都会让该 case 编译失败，作为 RED。
 *
 * <p>HIGH-3（issue 2026-07-18-settings-v1-1-followup）：密码规则改为
 * {@code @StrongPassword} 聚合校验；本类延伸覆盖弱密码字典命中场景。
 */
class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    // ---------- RegisterRequest ----------

    @Test
    void register_blankEmail_violates() {
        RegisterRequest req = new RegisterRequest("", "Valid1Pass", "alice");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void register_invalidEmailFormat_violates() {
        RegisterRequest req = new RegisterRequest("not-an-email", "Valid1Pass", "alice");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void register_shortPassword_violates() {
        // 7 chars < min(8)
        RegisterRequest req = new RegisterRequest("alice@example.com", "Short1A", null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void register_passwordNoDigit_violates() {
        // 8+ chars, only letters
        RegisterRequest req = new RegisterRequest("alice@example.com", "OnlyLetters", null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void register_oversizedNickname_violates() {
        String longNick = "a".repeat(33); // > max(32)
        RegisterRequest req = new RegisterRequest("alice@example.com", "Valid1Pass", longNick);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("nickname"));
    }

    @Test
    void register_weakPassword_violates() {
        // HIGH-3：弱密码字典命中。12345678 长度合法但复杂度失败 + 字典命中。
        RegisterRequest req = new RegisterRequest("alice@example.com", "12345678", null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void register_weakPassword_pwd1_violates() {
        // password1 长度合法 + 复杂度合法，但字典命中。
        RegisterRequest req = new RegisterRequest("alice@example.com", "password1", null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    // ---------- ChangePasswordRequest ----------

    @Test
    void changePassword_blankOld_violates() {
        ChangePasswordRequest req = new ChangePasswordRequest("", "Valid1Pass");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("oldPassword"));
    }

    @Test
    void changePassword_weakNew_violates() {
        ChangePasswordRequest req = new ChangePasswordRequest("OldPass1Old", "12345678");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("newPassword"));
    }

    @Test
    void changePassword_valid_passes() {
        ChangePasswordRequest req = new ChangePasswordRequest("OldPass1Old", "NewValid1Pass");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(req);

        assertThat(violations).isEmpty();
    }

    // ---------- LoginRequest ----------

    @Test
    void login_blankPassword_violates() {
        LoginRequest req = new LoginRequest("alice@example.com", "");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
