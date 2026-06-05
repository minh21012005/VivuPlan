package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.BillingDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingPackageCatalog {

    private final List<CreditPackage> packages = List.of(
            new CreditPackage("PLAN_BASIC", "Gói cơ bản",
                    "Vừa đủ cho một chuyến ngắn, có thêm lượt chỉnh lại ngày và gợi ý điểm đến.",
                    10_000L, 2L, 2L, 3L, false),
            new CreditPackage("PLAN_STANDARD", "Gói tiêu chuẩn",
                    "Hợp khi muốn so sánh vài phương án hoặc đi cùng nhóm.",
                    19_000L, 5L, 5L, 8L, true),
            new CreditPackage("PLAN_SAVING", "Gói tiết kiệm",
                    "Dành cho người hay lên kế hoạch và muốn nhiều lượt dự phòng.",
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
