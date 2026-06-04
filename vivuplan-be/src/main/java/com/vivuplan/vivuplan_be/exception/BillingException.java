package com.vivuplan.vivuplan_be.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BillingException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BillingException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static BillingException insufficientPlanCredits() {
        return new BillingException(
                "INSUFFICIENT_PLAN_CREDITS",
                "Bạn đã hết lượt tạo lịch trình. Mua thêm lượt để tiếp tục nhé.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    public static BillingException insufficientEditCredits() {
        return new BillingException(
                "INSUFFICIENT_EDIT_CREDITS",
                "Bạn đã hết lượt chỉnh ngày bằng AI. Mua thêm lượt để tiếp tục nhé.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    public static BillingException insufficientSuggestionCredits() {
        return new BillingException(
                "INSUFFICIENT_SUGGESTION_CREDITS",
                "Bạn đã hết lượt gợi ý điểm đến bằng AI. Vui lòng mua thêm lượt để tiếp tục.",
                HttpStatus.PAYMENT_REQUIRED);
    }
}
