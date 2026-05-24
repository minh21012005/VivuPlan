package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AuthDto;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private BillingService billingService;

    @Test
    void registerCreatesWalletAndGrantsSignupCredits() {
        AuthService service = new AuthService(userRepository, roleRepository, passwordEncoder, jwtUtil, billingService);
        Role userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest();
        req.setName("Minh");
        req.setEmail("minh@example.com");
        req.setPassword("password123");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");

        AuthDto.AuthResponse response = service.register(req);

        assertThat(response.getToken()).isEqualTo("token");
        verify(billingService).grantSignupCredits(any(User.class));
    }
}
