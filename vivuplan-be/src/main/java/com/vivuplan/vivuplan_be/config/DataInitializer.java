package com.vivuplan.vivuplan_be.config;

import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.data-initializer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        ensureRole(Role.RoleName.USER, "Standard user");
        ensureRole(Role.RoleName.ADMIN, "System administrator");
    }

    private void ensureRole(Role.RoleName roleName, String description) {
        if (!roleRepository.existsByName(roleName)) {
            roleRepository.save(Role.builder()
                    .name(roleName)
                    .description(description)
                    .build());
        }
    }
}
