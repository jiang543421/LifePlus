package com.lifepulse.auth.security;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 强密码约束注解（issue 2026-07-18-settings-v1-1-followup §2 HIGH-3）。
 *
 * <p>单一约束入口：聚合 {@link PasswordPolicy} 三维（长度 / 字符复杂度 /
 * 弱密码字典），注册与改密 DTO 复用。校验失败 → Bean Validation
 * {@code ConstraintViolationException} → {@code GlobalExceptionHandler} 转
 * code=1001 / message=「密码不符合安全策略」。
 *
 * <p>为什么要注解而非 Service 层手写校验：
 * <ul>
 *   <li>DTO record 字段上加约束后，控制层 {@code @Valid} 自动触发，
 *       业务代码零侵入</li>
 *   <li>错误码统一走 1001，前端 {@code authErrorMessage(1001)} 落文案</li>
 *   <li>复用 Hibernate Validator 引擎，单测可走 {@code Validator.validate()}</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "密码不符合安全策略";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}