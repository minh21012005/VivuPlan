package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.dto.BillingDto;
import com.vivuplan.vivuplan_be.entity.*;
import com.vivuplan.vivuplan_be.exception.BillingException;
import com.vivuplan.vivuplan_be.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private static final long FREE_SIGNUP_PLAN_CREDITS = 1L;
    private static final long FREE_SIGNUP_EDIT_CREDITS = 1L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ORDER_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final UserRepository userRepository;
    private final UserWalletRepository userWalletRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final SepayTransactionRepository sepayTransactionRepository;
    private final BillingPackageCatalog packageCatalog;
    private final ObjectMapper objectMapper;

    @Value("${app.billing.sepay.webhook-api-key:${SEPAY_WEBHOOK_API_KEY:}}")
    private String sepayWebhookApiKey;

    @Value("${app.billing.sepay.qr-url-template:${SEPAY_QR_URL_TEMPLATE:}}")
    private String sepayQrUrlTemplate;

    @Value("${app.billing.sepay.bank-code:${SEPAY_BANK_CODE:}}")
    private String sepayBankCode;

    @Value("${app.billing.sepay.account-number:${SEPAY_ACCOUNT_NUMBER:}}")
    private String sepayAccountNumber;

    @Value("${app.billing.sepay.account-name:${SEPAY_ACCOUNT_NAME:}}")
    private String sepayAccountName;

    @Value("${app.billing.sepay.order-prefix:${SEPAY_ORDER_PREFIX:VP}}")
    private String orderPrefix;

    @Value("${app.billing.order-expiry-minutes:${BILLING_ORDER_EXPIRY_MINUTES:30}}")
    private Long orderExpiryMinutes;

    public List<BillingDto.PackageResponse> packages() {
        return packageCatalog.list().stream()
                .map(BillingPackageCatalog.CreditPackage::toResponse)
                .toList();
    }

    @Transactional
    public void grantSignupCredits(User user) {
        if (userWalletRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }
        UserWallet wallet = UserWallet.builder()
                .user(user)
                .planCredits(FREE_SIGNUP_PLAN_CREDITS)
                .editCredits(FREE_SIGNUP_EDIT_CREDITS)
                .build();
        userWalletRepository.save(wallet);
        writeLedger(user, CreditLedger.CreditType.PLAN, FREE_SIGNUP_PLAN_CREDITS, "FREE_SIGNUP", null, null);
        writeLedger(user, CreditLedger.CreditType.EDIT, FREE_SIGNUP_EDIT_CREDITS, "FREE_SIGNUP", null, null);
    }

    @Transactional(readOnly = true)
    public BillingDto.BillingMeResponse me(Long userId) {
        User user = requireUser(userId);
        UserWallet wallet = userWalletRepository.findByUserId(userId)
                .orElse(UserWallet.builder().user(user).planCredits(0L).editCredits(0L).build());
        return BillingDto.BillingMeResponse.builder()
                .wallet(BillingDto.WalletResponse.from(wallet))
                .recentOrders(paymentOrderRepository.findTop8ByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(BillingDto.OrderResponse::from)
                        .toList())
                .build();
    }

    @Transactional
    public BillingDto.OrderResponse createOrder(Long userId, String packageCode) {
        User user = requireUser(userId);
        BillingPackageCatalog.CreditPackage creditPackage = packageCatalog.require(packageCode);
        String orderCode = generateUniqueOrderCode();
        PaymentOrder order = PaymentOrder.builder()
                .orderCode(orderCode)
                .user(user)
                .packageCode(creditPackage.code())
                .amount(creditPackage.amount())
                .planCredits(creditPackage.planCredits())
                .editCredits(creditPackage.editCredits())
                .status(PaymentOrder.Status.PENDING)
                .qrUrl(buildQrUrl(orderCode, creditPackage.amount()))
                .expiresAt(LocalDateTime.now().plusMinutes(orderExpiryMinutes))
                .build();
        return BillingDto.OrderResponse.from(paymentOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public BillingDto.OrderResponse getOrder(Long userId, String orderCode) {
        PaymentOrder order = paymentOrderRepository.findByOrderCode(orderCode)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Khong tim thay don thanh toan"));
        return BillingDto.OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public void requirePlanCredit(Long userId) {
        UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
        if (wallet == null || wallet.getPlanCredits() <= 0) {
            throw BillingException.insufficientPlanCredits();
        }
    }

    @Transactional(readOnly = true)
    public void requireEditCredit(Long userId) {
        UserWallet wallet = userWalletRepository.findByUserId(userId).orElse(null);
        if (wallet == null || wallet.getEditCredits() <= 0) {
            throw BillingException.insufficientEditCredits();
        }
    }

    @Transactional
    public void consumePlanCredit(Long userId, Trip trip) {
        UserWallet wallet = userWalletRepository.lockByUserId(userId)
                .orElseThrow(BillingException::insufficientPlanCredits);
        if (wallet.getPlanCredits() <= 0) {
            throw BillingException.insufficientPlanCredits();
        }
        wallet.setPlanCredits(wallet.getPlanCredits() - 1);
        writeLedger(wallet.getUser(), CreditLedger.CreditType.PLAN, -1L, "PLAN_GENERATION", null, trip);
    }

    @Transactional
    public void consumeEditCredit(Long userId, Trip trip) {
        UserWallet wallet = userWalletRepository.lockByUserId(userId)
                .orElseThrow(BillingException::insufficientEditCredits);
        if (wallet.getEditCredits() <= 0) {
            throw BillingException.insufficientEditCredits();
        }
        wallet.setEditCredits(wallet.getEditCredits() - 1);
        writeLedger(wallet.getUser(), CreditLedger.CreditType.EDIT, -1L, "DAY_REGENERATION", null, trip);
    }

    @Transactional
    public String handleSepayWebhook(String authorization, JsonNode payload) {
        validateWebhookAuthorization(authorization);

        String sepayId = readString(payload, "id");
        if (sepayId == null || sepayId.isBlank()) {
            throw new BillingException("INVALID_SEPAY_PAYLOAD", "Missing SePay transaction id", HttpStatus.BAD_REQUEST);
        }
        if (sepayTransactionRepository.existsBySepayId(sepayId)) {
            return "duplicate";
        }

        String code = readString(payload, "code");
        String content = readString(payload, "content");
        String transferType = readString(payload, "transferType");
        Long transferAmount = readLong(payload, "transferAmount");
        String referenceCode = readString(payload, "referenceCode");
        String rawPayload = toJson(payload);

        PaymentOrder order = findMatchingOrder(code, content).orElse(null);
        String transactionStatus = "UNMATCHED";

        if (!"in".equalsIgnoreCase(nullToBlank(transferType))) {
            transactionStatus = "IGNORED_TRANSFER_TYPE";
        } else if (order == null) {
            transactionStatus = "UNMATCHED";
        } else if (order.getStatus() == PaymentOrder.Status.PAID) {
            transactionStatus = "ORDER_ALREADY_PAID";
        } else if (order.getStatus() != PaymentOrder.Status.PENDING) {
            transactionStatus = "ORDER_NOT_PENDING";
        } else if (order.getExpiresAt().isBefore(LocalDateTime.now())) {
            order.setStatus(PaymentOrder.Status.EXPIRED);
            transactionStatus = "ORDER_EXPIRED";
        } else if (transferAmount == null || transferAmount < order.getAmount()) {
            order.setStatus(PaymentOrder.Status.UNDERPAID);
            order.setPaidAmount(transferAmount);
            transactionStatus = "UNDERPAID";
        } else {
            creditPaidOrder(order, transferAmount);
            transactionStatus = "CREDITED";
        }

        sepayTransactionRepository.save(SepayTransaction.builder()
                .sepayId(sepayId)
                .paymentOrder(order)
                .referenceCode(referenceCode)
                .code(code)
                .content(content)
                .transferType(transferType)
                .transferAmount(transferAmount)
                .status(transactionStatus)
                .rawPayload(rawPayload)
                .build());

        return transactionStatus;
    }

    private void creditPaidOrder(PaymentOrder order, Long paidAmount) {
        order.setStatus(PaymentOrder.Status.PAID);
        order.setPaidAmount(paidAmount);
        order.setPaidAt(LocalDateTime.now());

        UserWallet wallet = userWalletRepository.lockByUserId(order.getUser().getId()).orElseGet(() ->
                userWalletRepository.save(UserWallet.builder()
                        .user(order.getUser())
                        .planCredits(0L)
                        .editCredits(0L)
                        .build()));
        wallet.setPlanCredits(wallet.getPlanCredits() + order.getPlanCredits());
        wallet.setEditCredits(wallet.getEditCredits() + order.getEditCredits());

        if (order.getPlanCredits() > 0) {
            writeLedger(order.getUser(), CreditLedger.CreditType.PLAN, order.getPlanCredits(), "PAYMENT", order, null);
        }
        if (order.getEditCredits() > 0) {
            writeLedger(order.getUser(), CreditLedger.CreditType.EDIT, order.getEditCredits(), "PAYMENT", order, null);
        }
    }

    private Optional<PaymentOrder> findMatchingOrder(String code, String content) {
        if (code != null && !code.isBlank()) {
            Optional<PaymentOrder> byCode = paymentOrderRepository.lockByOrderCode(code.trim().toUpperCase(Locale.ROOT));
            if (byCode.isPresent()) {
                return byCode;
            }
        }
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String prefix = Pattern.quote(orderPrefix.toUpperCase(Locale.ROOT));
        java.util.regex.Matcher matcher = Pattern.compile(prefix + "[A-Z0-9]{6,24}")
                .matcher(content.toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            Optional<PaymentOrder> order = paymentOrderRepository.lockByOrderCode(matcher.group());
            if (order.isPresent()) {
                return order;
            }
        }
        return Optional.empty();
    }

    private void validateWebhookAuthorization(String authorization) {
        if (sepayWebhookApiKey == null || sepayWebhookApiKey.isBlank()) {
            throw new BillingException("SEPAY_WEBHOOK_NOT_CONFIGURED", "SePay webhook API key is not configured", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String expected = "Apikey " + sepayWebhookApiKey.trim();
        if (authorization == null || !authorization.trim().equals(expected)) {
            throw new BillingException("INVALID_SEPAY_API_KEY", "Invalid SePay API key", HttpStatus.UNAUTHORIZED);
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Nguoi dung khong ton tai"));
    }

    private void writeLedger(
            User user,
            CreditLedger.CreditType type,
            Long delta,
            String reason,
            PaymentOrder paymentOrder,
            Trip trip) {
        creditLedgerRepository.save(CreditLedger.builder()
                .user(user)
                .type(type)
                .delta(delta)
                .reason(reason)
                .paymentOrder(paymentOrder)
                .trip(trip)
                .build());
    }

    private String generateUniqueOrderCode() {
        String prefix = orderPrefix == null || orderPrefix.isBlank() ? "VP" : orderPrefix.trim().toUpperCase(Locale.ROOT);
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder builder = new StringBuilder(prefix);
            for (int i = 0; i < 10; i++) {
                builder.append(ORDER_CHARS[RANDOM.nextInt(ORDER_CHARS.length)]);
            }
            String candidate = builder.toString();
            if (!paymentOrderRepository.existsByOrderCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Khong the tao ma don thanh toan");
    }

    private String buildQrUrl(String orderCode, Long amount) {
        if (sepayQrUrlTemplate == null || sepayQrUrlTemplate.isBlank()) {
            return "";
        }
        return sepayQrUrlTemplate
                .replace("{bank}", encode(sepayBankCode))
                .replace("{account}", encode(sepayAccountNumber))
                .replace("{amount}", encode(String.valueOf(amount)))
                .replace("{description}", encode(orderCode))
                .replace("{accountName}", encode(sepayAccountName));
    }

    private String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String readString(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Long readLong(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String value = node.asText("").replaceAll("[^0-9]", "");
        return value.isBlank() ? null : Long.parseLong(value);
    }

    private String toJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
