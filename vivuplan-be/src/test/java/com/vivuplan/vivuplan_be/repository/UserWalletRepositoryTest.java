package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.entity.UserWallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class UserWalletRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserWalletRepository userWalletRepository;

    @Test
    void decrementPlanCreditIfAvailableOnlyUpdatesWhenBalanceIsPositive() {
        User user = persistUser("plan-wallet@example.com");
        userWalletRepository.saveAndFlush(UserWallet.builder()
                .user(user)
                .planCredits(2L)
                .editCredits(0L)
                .suggestionCredits(0L)
                .build());

        assertThat(userWalletRepository.decrementPlanCreditIfAvailable(user.getId())).isEqualTo(1);
        assertThat(userWalletRepository.decrementPlanCreditIfAvailable(user.getId())).isEqualTo(1);
        assertThat(userWalletRepository.decrementPlanCreditIfAvailable(user.getId())).isZero();

        entityManager.clear();
        assertThat(userWalletRepository.findByUserId(user.getId()))
                .get()
                .extracting(UserWallet::getPlanCredits)
                .isEqualTo(0L);
    }

    @Test
    void decrementEditAndSuggestionCreditsUseTheirOwnBalances() {
        User user = persistUser("extra-wallet@example.com");
        userWalletRepository.saveAndFlush(UserWallet.builder()
                .user(user)
                .planCredits(0L)
                .editCredits(1L)
                .suggestionCredits(1L)
                .build());

        assertThat(userWalletRepository.decrementEditCreditIfAvailable(user.getId())).isEqualTo(1);
        assertThat(userWalletRepository.decrementEditCreditIfAvailable(user.getId())).isZero();
        assertThat(userWalletRepository.decrementSuggestionCreditIfAvailable(user.getId())).isEqualTo(1);
        assertThat(userWalletRepository.decrementSuggestionCreditIfAvailable(user.getId())).isZero();

        entityManager.clear();
        assertThat(userWalletRepository.findByUserId(user.getId()))
                .get()
                .satisfies(wallet -> {
                    assertThat(wallet.getPlanCredits()).isZero();
                    assertThat(wallet.getEditCredits()).isZero();
                    assertThat(wallet.getSuggestionCredits()).isZero();
                });
    }

    private User persistUser(String email) {
        return entityManager.persistAndFlush(User.builder()
                .name("Wallet Test")
                .email(email)
                .emailVerified(true)
                .build());
    }
}
