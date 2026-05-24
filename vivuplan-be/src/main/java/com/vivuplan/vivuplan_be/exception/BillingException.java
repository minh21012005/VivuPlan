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
                "Ban can them luot tao lich trinh AI.",
                HttpStatus.PAYMENT_REQUIRED);
    }

    public static BillingException insufficientEditCredits() {
        return new BillingException(
                "INSUFFICIENT_EDIT_CREDITS",
                "Ban can them luot chinh ngay bang AI.",
                HttpStatus.PAYMENT_REQUIRED);
    }
}
