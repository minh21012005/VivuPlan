package com.vivuplan.vivuplan_be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivuplan.vivuplan_be.entity.PaymentOrder;
import com.vivuplan.vivuplan_be.entity.SepayTransaction;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.entity.UserWallet;
import com.vivuplan.vivuplan_be.exception.BillingException;
import com.vivuplan.vivuplan_be.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserWalletRepository userWalletRepository;

    @Mock
    private CreditLedgerRepository creditLedgerRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private SepayTransactionRepository sepayTransactionRepository;

    private BillingService service() {
        BillingService service = new BillingService(
                userRepository,
                userWalletRepository,
                creditLedgerRepository,
                paymentOrderRepository,
                sepayTransactionRepository,
                new BillingPackageCatalog(),
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "sepayWebhookApiKey", "secret");
        ReflectionTestUtils.setField(service, "sepayQrUrlTemplate", "https://qr.example/{bank}/{account}?amount={amount}&des={description}&name={accountName}");
        ReflectionTestUtils.setField(service, "sepayBankCode", "MBBANK");
        ReflectionTestUtils.setField(service, "sepayAccountNumber", "123456");
        ReflectionTestUtils.setField(service, "sepayAccountName", "VivuPlan");
        ReflectionTestUtils.setField(service, "orderPrefix", "VP");
        ReflectionTestUtils.setField(service, "orderExpiryMinutes", 30L);
        return service;
    }

    @Test
    void grantSignupCreditsCreatesWalletAndLedgerOnce() {
        BillingService service = service();
        User user = sampleUser();
        when(userWalletRepository.findByUserId(7L)).thenReturn(Optional.empty());

        service.grantSignupCredits(user);

        ArgumentCaptor<UserWallet> walletCaptor = ArgumentCaptor.forClass(UserWallet.class);
        verify(userWalletRepository).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().getPlanCredits()).isEqualTo(1);
        assertThat(walletCaptor.getValue().getEditCredits()).isEqualTo(1);
        verify(creditLedgerRepository, times(2)).save(any());
    }

    @Test
    void consumePlanCreditDecrementsExactlyOneCredit() {
        BillingService service = service();
        UserWallet wallet = UserWallet.builder()
                .user(sampleUser())
                .planCredits(2L)
                .editCredits(1L)
                .build();
        when(userWalletRepository.lockByUserId(7L)).thenReturn(Optional.of(wallet));

        service.consumePlanCredit(7L, null);

        assertThat(wallet.getPlanCredits()).isEqualTo(1);
        verify(creditLedgerRepository).save(argThat(ledger ->
                ledger.getDelta().equals(-1L) && ledger.getType().name().equals("PLAN")));
    }

    @Test
    void consumeEditCreditThrowsWhenBalanceIsEmpty() {
        BillingService service = service();
        UserWallet wallet = UserWallet.builder()
                .user(sampleUser())
                .planCredits(1L)
                .editCredits(0L)
                .build();
        when(userWalletRepository.lockByUserId(7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.consumeEditCredit(7L, null))
                .isInstanceOf(BillingException.class)
                .hasMessageContaining("chinh ngay");
        verify(creditLedgerRepository, never()).save(any());
    }

    @Test
    void sepayWebhookWithEnoughAmountMarksOrderPaidAndCreditsWallet() throws Exception {
        BillingService service = service();
        User user = sampleUser();
        PaymentOrder order = pendingOrder(user);
        UserWallet wallet = UserWallet.builder()
                .user(user)
                .planCredits(0L)
                .editCredits(0L)
                .build();
        when(sepayTransactionRepository.existsBySepayId("999")).thenReturn(false);
        when(paymentOrderRepository.lockByOrderCode("VPTEST1234")).thenReturn(Optional.of(order));
        when(userWalletRepository.lockByUserId(7L)).thenReturn(Optional.of(wallet));

        String status = service.handleSepayWebhook(
                "Apikey secret",
                new ObjectMapper().readTree("""
                        {"id":999,"code":"VPTEST1234","content":"VPTEST1234","transferType":"in","transferAmount":10000,"referenceCode":"ABC"}
                        """));

        assertThat(status).isEqualTo("CREDITED");
        assertThat(order.getStatus()).isEqualTo(PaymentOrder.Status.PAID);
        assertThat(wallet.getPlanCredits()).isEqualTo(1);
        assertThat(wallet.getEditCredits()).isEqualTo(2);
        verify(creditLedgerRepository, times(2)).save(any());
        verify(sepayTransactionRepository).save(any(SepayTransaction.class));
    }

    @Test
    void duplicateSepayWebhookReturnsSuccessWithoutReprocessing() throws Exception {
        BillingService service = service();
        when(sepayTransactionRepository.existsBySepayId("999")).thenReturn(true);

        String status = service.handleSepayWebhook(
                "Apikey secret",
                new ObjectMapper().readTree("""
                        {"id":999,"code":"VPTEST1234","transferType":"in","transferAmount":10000}
                        """));

        assertThat(status).isEqualTo("duplicate");
        verify(paymentOrderRepository, never()).lockByOrderCode(anyString());
        verify(creditLedgerRepository, never()).save(any());
    }

    @Test
    void underpaidSepayWebhookDoesNotCreditWallet() throws Exception {
        BillingService service = service();
        PaymentOrder order = pendingOrder(sampleUser());
        when(sepayTransactionRepository.existsBySepayId("999")).thenReturn(false);
        when(paymentOrderRepository.lockByOrderCode("VPTEST1234")).thenReturn(Optional.of(order));

        String status = service.handleSepayWebhook(
                "Apikey secret",
                new ObjectMapper().readTree("""
                        {"id":999,"code":"VPTEST1234","transferType":"in","transferAmount":5000}
                        """));

        assertThat(status).isEqualTo("UNDERPAID");
        assertThat(order.getStatus()).isEqualTo(PaymentOrder.Status.UNDERPAID);
        verify(userWalletRepository, never()).lockByUserId(anyLong());
        verify(creditLedgerRepository, never()).save(any());
    }

    @Test
    void invalidSepayWebhookApiKeyReturnsUnauthorizedBillingException() throws Exception {
        BillingService service = service();

        assertThatThrownBy(() -> service.handleSepayWebhook(
                "Apikey wrong",
                new ObjectMapper().readTree("""
                        {"id":999}
                        """)))
                .isInstanceOf(BillingException.class)
                .satisfies(error -> assertThat(((BillingException) error).getStatus().value()).isEqualTo(401));
    }

    private User sampleUser() {
        User user = new User();
        user.setId(7L);
        user.setName("Minh");
        user.setEmail("minh@example.com");
        return user;
    }

    private PaymentOrder pendingOrder(User user) {
        return PaymentOrder.builder()
                .orderCode("VPTEST1234")
                .user(user)
                .packageCode("PLAN_1")
                .amount(10_000L)
                .planCredits(1L)
                .editCredits(2L)
                .status(PaymentOrder.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();
    }
}
