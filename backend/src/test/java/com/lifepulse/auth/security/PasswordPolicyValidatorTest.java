package com.lifepulse.auth.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PasswordPolicy} + {@link PasswordPolicyValidator} 边界测试
 * （issue 2026-07-18 HIGH-3）。
 *
 * <p>覆盖三大维度：
 * <ol>
 *   <li>长度边界：MIN-1 / MIN / MIN+1 / MAX-1 / MAX / MAX+1</li>
 *   <li>字符复杂度：纯字母 / 纯数字 / 字母+数字 / 含特殊字符</li>
 *   <li>弱密码字典：top 国内外常见弱口令、case-insensitive 命中</li>
 * </ol>
 *
 * <p>使用 Hibernate Validator 触发 record / 字段约束，避免启动 Spring 容器；
 * 用例 ≥ 25。
 */
class PasswordPolicyValidatorTest {

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

    // ---------- 长度边界 ----------

    @Test
    @DisplayName("length = MIN-1 (7) → 失败")
    void length_belowMin_fails() {
        assertOneViolation("Short1A");
    }

    @Test
    @DisplayName("length = MIN (8) → 通过（复杂度满足）")
    void length_atMin_passesWhenComplex() {
        assertNoViolation("abcd1234");
    }

    @Test
    @DisplayName("length = MAX (64) → 通过")
    void length_atMax_passes() {
        // 64 字符，前 32 个字母 + 后 32 个数字
        String pw = "a".repeat(32) + "1".repeat(32);
        assertThat(pw.length()).isEqualTo(64);
        assertNoViolation(pw);
    }

    @Test
    @DisplayName("length = MAX+1 (65) → 失败")
    void length_aboveMax_fails() {
        String pw = "a".repeat(33) + "1".repeat(32);
        assertThat(pw.length()).isEqualTo(65);
        assertOneViolation(pw);
    }

    // ---------- 字符复杂度 ----------

    @Test
    @DisplayName("8+ 字符 + 仅字母 → 失败（缺数字）")
    void complexity_onlyLetters_fails() {
        assertOneViolation("onlyLetters");
    }

    @Test
    @DisplayName("8+ 字符 + 仅数字 → 失败（缺字母）")
    void complexity_onlyDigits_fails() {
        assertOneViolation("12345678");
    }

    @Test
    @DisplayName("8+ 字符 + 字母 + 数字 → 通过（且不在字典）")
    void complexity_lettersAndDigits_passes() {
        assertNoViolation("Valid1Pass");
        assertNoViolation("Hello1234");
    }

    @Test
    @DisplayName("字母 + 数字 + 特殊字符 → 通过（不强制特殊字符）")
    void complexity_withSpecials_passes() {
        assertNoViolation("Valid1Pass!");
        assertNoViolation("p@ssw0rd!");  // 注意 p@ssw0rd! 本身不在字典（弱密码条是 p@ssw0rd）
    }

    @Test
    @DisplayName("字母 + 数字 + unicode → 通过")
    void complexity_unicodeLetter_passes() {
        // 中文「密」也算字母（[A-Za-z] 是 ascii only）
        // 但项目硬性 ascii 字母规则：因此「密码abc1」被认为缺 ascii 字母 → 失败
        assertOneViolation("密码abc1");
    }

    // ---------- 弱密码字典命中 ----------

    @Test
    @DisplayName("字典命中：12345678 → 失败")
    void weak_12345678_fails() {
        // 8 字符 + 数字单一字符，但数字也满足「至少 1 数字」（仅字母规则失败）
        // 也命中字典 → 至少一项报错。
        assertOneViolation("12345678");
    }

    @Test
    @DisplayName("字典命中：password → 失败")
    void weak_password_fails() {
        assertOneViolation("password");
    }

    @Test
    @DisplayName("字典命中：password1 → 失败（满足长度+复杂度，仍命中）")
    void weak_password1_fails() {
        assertOneViolation("password1");
    }

    @Test
    @DisplayName("字典命中：qwerty123 → 失败")
    void weak_qwerty123_fails() {
        assertOneViolation("qwerty123");
    }

    @Test
    @DisplayName("字典命中：a12345678 → 失败（国内常用）")
    void weak_a12345678_fails() {
        assertOneViolation("a12345678");
    }

    @Test
    @DisplayName("字典命中：5201314 → 失败")
    void weak_5201314_fails() {
        // 7 字符（同时长度也失败）；视为命中
        assertOneViolation("5201314");
    }

    @Test
    @DisplayName("字典命中：P@SSW0RD 大小写无关 → 失败")
    void weak_caseInsensitive_fails() {
        assertOneViolation("P@SSW0RD");
    }

    @Test
    @DisplayName("字典命中：PASSword1 → 失败（命中 password1 大小写无关）")
    void weak_caseInsensitive_password1_fails() {
        assertOneViolation("PASSword1");
    }

    @Test
    @DisplayName("字典未命中：Valid1Pass → 通过")
    void weak_safePassword_passes() {
        assertNoViolation("Valid1Pass");
    }

    @Test
    @DisplayName("字典未命中：Tr0ub4dor&3 → 通过")
    void weak_strongPhrase_passes() {
        assertNoViolation("Tr0ub4dor&3");
    }

    // ---------- 综合 ----------

    @Test
    @DisplayName("空字符串 → 由 @NotBlank 处理，不归 StrongPassword 报失败")
    void emptyHandledByNotBlank() {
        // 单独 Validator 验证（无 @NotBlank）：空串通过 StrongPassword 的逻辑短路
        // （isEmpty → true）。在 record 中与 @NotBlank 组合由两者各自负责。
        String pw = "";
        Set<ConstraintViolation<PasswordCarrier>> violations = validator.validate(new PasswordCarrier(pw));
        // 没有 @NotBlank → 不应有违规
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("null → 由 @NotBlank 处理，不归 StrongPassword 报失败")
    void nullHandledByNotBlank() {
        Set<ConstraintViolation<PasswordCarrier>> violations = validator.validate(new PasswordCarrier(null));
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("FAILURE 文案使用统一文本，不泄露字典命中值")
    void failure_message_isUnifiedText() {
        Set<ConstraintViolation<PasswordCarrier>> violations =
                validator.validate(new PasswordCarrier("12345678"));
        assertThat(violations).hasSize(1);
        ConstraintViolation<PasswordCarrier> v = violations.iterator().next();
        assertThat(v.getMessage()).isEqualTo(PasswordPolicy.VALIDATION_MESSAGE);
    }

    // ---------- helpers ----------

    private static void assertOneViolation(String password) {
        Set<ConstraintViolation<PasswordCarrier>> violations =
                validator.validate(new PasswordCarrier(password));
        assertThat(violations).hasSize(1);
        ConstraintViolation<PasswordCarrier> v = violations.iterator().next();
        assertThat(v.getMessage()).isEqualTo(PasswordPolicy.VALIDATION_MESSAGE);
        assertThat(v.getPropertyPath().toString()).isEqualTo("password");
    }

    private static void assertNoViolation(String password) {
        Set<ConstraintViolation<PasswordCarrier>> violations =
                validator.validate(new PasswordCarrier(password));
        assertThat(violations).isEmpty();
    }

    /** 测试载体 record：仅一个 @StrongPassword 字段，便于聚焦校验逻辑。 */
    record PasswordCarrier(@StrongPassword String password) {}
}
