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
    void login_blankPassword_violates() {
        LoginRequest req = new LoginRequest("alice@example.com", "");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(req);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}