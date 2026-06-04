package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.BillingDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingPackageCatalog {

    private final List<CreditPackage> packages = List.of(
            new CreditPackage("PLAN_BASIC", "Gói cơ bản",
                    "2 lượt lập lịch trình, 2 lượt chỉnh sửa ngày và 3 lượt gợi ý điểm đến phù hợp",
                    10_000L, 2L, 2L, 3L, false),
            new CreditPackage("PLAN_STANDARD", "Gói tiêu chuẩn",
                    "5 lượt lập lịch trình, 5 lượt chỉnh sửa ngày và 8 lượt gợi ý điểm đến phù hợp",
                    19_000L, 5L, 5L, 8L, true),
            new CreditPackage("PLAN_SAVING", "Gói tiết kiệm",
                    "12 lượt lập lịch trình, 12 lượt chỉnh sửa ngày và 20 lượt gợi ý điểm đến phù hợp",
                    39_000L, 12L, 12L, 20L, false)
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
            Long suggestionCredits,
            Boolean highlighted) {

        public BillingDto.PackageResponse toResponse() {
            return BillingDto.PackageResponse.builder()
                    .code(code)
                    .name(name)
                    .description(description)
                    .amount(amount)
                    .planCredits(planCredits)
                    .editCredits(editCredits)
                    .suggestionCredits(suggestionCredits)
                    .highlighted(highlighted)
                    .build();
        }
    }
}
