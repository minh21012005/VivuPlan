package com.vivuplan.vivuplan_be.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDomainPolicyServiceTest {

    @Test
    void loadsVendoredDisposableDomainListAndBlocksExactAndSubdomainMatches() {
        EmailDomainPolicyService service = service();

        assertThat(service.blockedDomainCount()).isGreaterThan(6_000);
        assertThat(service.isRegistrationEmailBlocked("minh@Yopmail.com")).isTrue();
        assertThat(service.isRegistrationEmailBlocked("minh@sub.yopmail.com")).isTrue();
        assertThat(service.isRegistrationEmailBlocked("minh@gmail.com")).isFalse();
        assertThat(service.isRegistrationEmailBlocked("minh@outlook.com")).isFalse();
    }

    private EmailDomainPolicyService service() {
        EmailDomainPolicyService service = new EmailDomainPolicyService(new DefaultResourceLoader());
        service.loadPolicy();
        return service;
    }
}
