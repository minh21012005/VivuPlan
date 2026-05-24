package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.BillingDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingPackageCatalog {

    private final List<CreditPackage> packages = List.of(
            new CreditPackage("PLAN_1", "Go 1 plan", "1 lich trinh AI + 2 luot chinh ngay", 10_000L, 1L, 2L, false),
            new CreditPackage("PLAN_3", "Go 3 plans", "3 lich trinh AI + 9 luot chinh ngay", 29_000L, 3L, 9L, true),
            new CreditPackage("PLAN_10", "Go 10 plans", "10 lich trinh AI + 35 luot chinh ngay", 89_000L, 10L, 35L, false),
            new CreditPackage("EDIT_5", "Go chinh ngay", "5 luot regenerate ngay bang AI", 5_000L, 0L, 5L, false)
    );

    public List<CreditPackage> list() {
        return packages;
    }

    public CreditPackage require(String code) {
        return packages.stream()
                .filter(item -> item.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Goi credit khong hop le"));
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
