package com.lifepulse.expense.dto;

import com.lifepulse.expense.ExpenseCategory;
import com.lifepulse.expense.ExpenseConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CreateExpenseRequest} Bean Validation 测试（plan T4）。
 *
 * <p>覆盖：{@code @NotNull amount/category/occurredAt}、
 * {@code @DecimalMin 0.01}、{@code @Digits fraction=2}、{@code @Size MAX_NOTE_LEN}；
 * 反向 case：合法请求零 violation。
 */
class CreateExpenseRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void validRequest_hasNoViolation() {
        var req = new CreateExpenseRequest(
                new BigDecimal("12.50"),
                ExpenseCategory.MEAL,
                "lunch",
                OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneOffset.UTC)
        );
        Set<ConstraintViolation<CreateExpenseRequest>> v = validator.validate(req);
        assertThat(v).isEmpty();
    }

    @Test
    void amount_isRequired() {
        var req = new CreateExpenseRequest(
                null,
                ExpenseCategory.MEAL,
                null,
                OffsetDateTime.now()
        );
        Set<ConstraintViolation<CreateExpenseRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("amount"));
    }

    @Test
    void amount_mustBePositive() {
        var req = new CreateExpenseRequest(
                BigDecimal.ZERO,
                ExpenseCategory.MEAL,
                null,
                OffsetDateTime.now()
        );
        Set<ConstraintViolation<CreateExpenseRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("amount"));

        var req2 = new CreateExpenseRequest(
                new BigDecimal("-1.00"),
                ExpenseCategory.MEAL,
                null,
                OffsetDateTime.now()
        );
        assertThat(validator.validate(req2))
                .anyMatch(c -> c.getPropertyPath().toString().equals("amount"));
    }

    @Test
    void amount_fractionAtMost2Digits() {
        var req = new CreateExpenseRequest(
                new BigDecimal("12.345"),
                ExpenseCategory.MEAL,
                null,
                OffsetDateTime.now()
        );
        Set<ConstraintViolation<CreateExpenseRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("amount"));
    }

    @Test
    void category_isRequired() {
        var req = new CreateExpenseRequest(
                new BigDecimal("12.50"),
                null,
                null,
                OffsetDateTime.now()
        );
        Set<ConstraintViolation<CreateExpenseRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("category"));
    }

    @Test
    void note_canBeNullOrWithinMax() {
        // null = OK
        var req1 = new CreateExpenseRequest(
                new BigDecimal("12.50"), ExpenseCategory.MEAL, null, OffsetDateTime.now());
        assertThat(validator.validate(req1)).isEmpty();

        // exactly MAX = OK
        var maxNote = "x".repeat(ExpenseConstants.MAX_NOTE_LEN);
        var req2 = new CreateExpenseRequest(
                new BigDecimal("12.50"), ExpenseCategory.MEAL, maxNote, OffsetDateTime.now());
        assertThat(validator.validate(req2)).isEmpty();

        // MAX+1 = violation
        var tooLong = "x".repeat(ExpenseConstants.MAX_NOTE_LEN + 1);
        var req3 = new CreateExpenseRequest(
                new BigDecimal("12.50"), ExpenseCategory.MEAL, tooLong, OffsetDateTime.now());
        assertThat(validator.validate(req3))
                .anyMatch(c -> c.getPropertyPath().toString().equals("note"));
    }

    @Test
    void occurredAt_isRequired() {
        var req = new CreateExpenseRequest(
                new BigDecimal("12.50"),
                ExpenseCategory.MEAL,
                null,
                null
        );
        Set<ConstraintViolation<CreateExpenseRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(c -> c.getPropertyPath().toString().equals("occurredAt"));
    }

    @Test
    void allFiveEnumCategories_areAccepted() {
        for (var cat : ExpenseCategory.values()) {
            var req = new CreateExpenseRequest(
                    new BigDecimal("1.00"), cat, "n", OffsetDateTime.now());
            assertThat(validator.validate(req))
                    .as("category=%s should pass", cat)
                    .isEmpty();
        }
    }
}
