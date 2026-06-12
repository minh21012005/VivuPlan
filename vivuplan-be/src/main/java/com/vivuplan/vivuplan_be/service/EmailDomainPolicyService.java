package com.vivuplan.vivuplan_be.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailDomainPolicyService {

    private static final String DISPOSABLE_DOMAIN_RESOURCE = "data/disposable_email_blocklist.txt";
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?!.*\\.\\.)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])$"
    );
    private static final String BLOCKED_EMAIL_MESSAGE =
            "Vui lòng sử dụng email cá nhân hoặc email công việc hợp lệ.";

    private final ResourceLoader resourceLoader;

    private Set<String> blockedDomains = Set.of();

    @PostConstruct
    void loadPolicy() {
        Set<String> nextBlockedDomains = new HashSet<>();
        loadDomainResource(nextBlockedDomains);

        blockedDomains = Set.copyOf(nextBlockedDomains);
        log.info("Loaded {} disposable email domains", blockedDomains.size());
    }

    public void assertRegistrationEmailAllowed(String email) {
        if (isRegistrationEmailBlocked(email)) {
            throw new IllegalArgumentException(BLOCKED_EMAIL_MESSAGE);
        }
    }

    boolean isRegistrationEmailBlocked(String email) {
        String domain = extractEmailDomain(email);
        if (domain.isBlank()) {
            return false;
        }
        return matchesDomain(blockedDomains, domain);
    }

    int blockedDomainCount() {
        return blockedDomains.size();
    }

    private void loadDomainResource(Set<String> domains) {
        Resource resource = resourceLoader.getResource("classpath:" + DISPOSABLE_DOMAIN_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing disposable email domain list: " + DISPOSABLE_DOMAIN_RESOURCE);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String domain = normalizeDomainLine(line);
                if (domain.isBlank()) {
                    continue;
                }
                validateDomain(domain, DISPOSABLE_DOMAIN_RESOURCE + ":" + lineNumber);
                domains.add(domain);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load disposable email domain list", e);
        }
    }

    private String normalizeDomainLine(String raw) {
        String domain = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int commentIndex = domain.indexOf('#');
        if (commentIndex >= 0) {
            domain = domain.substring(0, commentIndex).trim();
        }
        return domain;
    }

    private void validateDomain(String domain, String source) {
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalStateException("Invalid email domain in " + source + ": " + domain);
        }
    }

    private String extractEmailDomain(String email) {
        int atIndex = email == null ? -1 : email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return "";
        }
        return email.substring(atIndex + 1).trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesDomain(Set<String> configuredDomains, String domain) {
        String candidate = domain;
        while (!candidate.isBlank()) {
            if (configuredDomains.contains(candidate)) {
                return true;
            }
            int dotIndex = candidate.indexOf('.');
            if (dotIndex < 0 || dotIndex == candidate.length() - 1) {
                return false;
            }
            candidate = candidate.substring(dotIndex + 1);
        }
        return false;
    }
}
