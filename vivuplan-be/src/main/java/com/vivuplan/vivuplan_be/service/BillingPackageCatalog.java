package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.BillingDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BillingPackageCatalog {

    private final List<CreditPackage> packages = List.of(
            new CreditPackage("PLAN_1", "Go co ban", "3 lich trinh AI + 5 luot chinh ngay", 19_000L, 3L, 5L, false),
            new CreditPackage("PLAN_3", "Go tieu chuan", "7 lich trinh AI + 15 luot chinh ngay", 39_000L, 7L, 15L, true),
            new CreditPackage("PLAN_10", "Go tiet kiem", "15 lich trinh AI + 35 luot chinh ngay", 79_000L, 15L, 35L, false)
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
