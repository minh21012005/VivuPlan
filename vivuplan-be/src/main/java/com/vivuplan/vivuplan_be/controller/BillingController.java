package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.dto.BillingDto;
import com.vivuplan.vivuplan_be.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/packages")
    public ResponseEntity<List<BillingDto.PackageResponse>> packages() {
        return ResponseEntity.ok(billingService.packages());
    }

    @GetMapping("/me")
    public ResponseEntity<BillingDto.BillingMeResponse> me(Authentication auth) {
        return ResponseEntity.ok(billingService.me((Long) auth.getPrincipal()));
    }

    @PostMapping("/orders")
    public ResponseEntity<BillingDto.OrderResponse> createOrder(
            @Valid @RequestBody BillingDto.CreateOrderRequest req,
            Authentication auth) {
        return ResponseEntity.ok(billingService.createOrder((Long) auth.getPrincipal(), req.getPackageCode()));
    }

    @GetMapping("/orders/{orderCode}")
    public ResponseEntity<BillingDto.OrderResponse> getOrder(
            @PathVariable String orderCode,
            Authentication auth) {
        return ResponseEntity.ok(billingService.getOrder((Long) auth.getPrincipal(), orderCode));
    }

    @PostMapping("/sepay/webhook")
    public ResponseEntity<Map<String, String>> sepayWebhook(
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestBody String rawBody) {
        String status = billingService.handleSepayWebhook(signature, timestamp, rawBody);
        return ResponseEntity.ok(Map.of("status", status));
    }
}
