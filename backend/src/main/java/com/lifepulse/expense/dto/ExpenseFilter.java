package com.lifepulse.expense.dto;

import com.lifepulse.expense.ExpenseCategory;

import java.time.OffsetDateTime;

// Expense list filter (spec section 5 GET /expenses).
//
// All fields nullable: null = no filter. page is 1-based (controller validates),
// size is bounded by ExpenseConstants.MAX_PAGE_SIZE.
public record ExpenseFilter(
        ExpenseCategory category,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
) {
}
