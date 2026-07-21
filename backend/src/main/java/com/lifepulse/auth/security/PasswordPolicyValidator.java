package com.lifepulse.auth.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link StrongPassword} 的 Hibernate Validator 实现（HIGH-3）。
 *
 * <p>策略细节见 {@link PasswordPolicy}。本类只负责「检查 + 报错」，不
 * 暴露弱密码字典细节到 {@code ConstraintViolation}（避免经日志/响应漏出）。
 * 多维问题通过同一条固定 message 触发，由前端统一兜底文案。
 *
 * <p>CLAUDE.md §7.3：弱密码命中时仅返回固定文案，不记录命中值。
 */
public class PasswordPolicyValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null / blank 由 @NotBlank 单独负责；这里只校验通过 @NotBlank 的值。
        if (value == null || value.isEmpty()) {
            return true;
        }
        boolean lengthOk = PasswordPolicy.isValidLength(value);
        boolean complexityOk = PasswordPolicy.meetsComplexity(value);
        boolean weak = lengthOk && PasswordPolicy.isWeak(value);
        boolean overallOk = lengthOk && complexityOk && !weak;
        if (overallOk) {
            return true;
        }
        // 失败：替换默认 message 为统一文案，确保前端 authErrorMessage(1001)
        // 落「密码不符合安全策略」对应到《PasswordRules.vue》。
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(PasswordPolicy.VALIDATION_MESSAGE)
                .addConstraintViolation();
        return false;
    }
}