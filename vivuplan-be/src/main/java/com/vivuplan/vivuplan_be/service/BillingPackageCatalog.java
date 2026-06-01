package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.BillingDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingPackageCatalog {

    private final List<CreditPackage> packages = List.of(
            new CreditPackage("PLAN_1", "Gói cơ bản", "2 lịch trình AI + 3 lượt chỉnh ngày", 10_000L, 2L, 3L, false),
            new CreditPackage("PLAN_3", "Gói tiêu chuẩn", "5 lịch trình AI + 10 lượt chỉnh ngày", 19_000L, 5L, 10L, true),
            new CreditPackage("PLAN_10", "Gói tiết kiệm", "12 lịch trình AI + 20 lượt chỉnh ngày", 39_000L, 12L, 20L, false)
    );

    public List<CreditPackage> list() {
        return packages;
    }

    public CreditPackage require(String code) {
        return packages.stream()
                .filter(item -> item.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Gói lượt dùng không hợp lệ"));
    }

    public record CreditPackage(
            String code,
            String name,
            String description,
            Long amount,
            Long planCredits,
            Long editCredits,
            Boolean highlighted) {

        public BillingDto.PackageResponse toResponse() {
            return BillingDto.PackageResponse.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .amount(amount)
                    .planCredits(planCredits)
                    .editCredits(editCredits)
                    .highlighted(highlighted)
                    .build();
        }
    }
}
