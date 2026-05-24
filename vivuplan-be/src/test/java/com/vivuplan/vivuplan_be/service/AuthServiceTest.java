package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AuthDto;
import com.vivuplan.vivuplan_be.entity.RegistrationOtp;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.RegistrationOtpRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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
    private RegistrationOtpRepository registrationOtpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private BillingService billingService;

    @Mock
    private EmailService emailService;

    private AuthService service() {
        AuthService service = new AuthService(
                userRepository,
                roleRepository,
                registrationOtpRepository,
                passwordEncoder,
                jwtUtil,
                billingService,
                emailService);
        ReflectionTestUtils.setField(service, "registrationOtpExpiryMinutes", 10L);
        return service;
    }

    @Test
    void requestRegistrationOtpStoresPendingRegistrationAndSendsEmail() {
        AuthService service = service();
        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest();
        req.setName("Minh");
        req.setEmail("Minh@Example.com ");
        req.setPassword("password123");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        AuthDto.RegisterOtpResponse response = service.requestRegistrationOtp(req);

        assertThat(response.getEmail()).isEqualTo("minh@example.com");
        assertThat(response.getExpiresInSeconds()).isEqualTo(600);
        ArgumentCaptor<RegistrationOtp> otpCaptor = ArgumentCaptor.forClass(RegistrationOtp.class);
        verify(registrationOtpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getEmail()).isEqualTo("minh@example.com");
        assertThat(otpCaptor.getValue().getPasswordHash()).isEqualTo("hashed");
        verify(emailService).sendRegistrationOtp(any(), any(), any(), any(Long.class));
    }

    @Test
    void verifyRegistrationOtpCreatesWalletAndGrantsSignupCredits() {
        AuthService service = service();
        Role userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        RegistrationOtp pending = RegistrationOtp.builder()
                .email("minh@example.com")
                .name("Minh")
                .passwordHash("encoded-password")
                .otpHash("encoded-otp")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .build();
        AuthDto.VerifyRegisterOtpRequest req = new AuthDto.VerifyRegisterOtpRequest();
        req.setEmail("minh@example.com");
        req.setOtp("123456");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("123456", "encoded-otp")).thenReturn(true);
        when(roleRepository.findByName(Role.RoleName.USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");

        AuthDto.AuthResponse response = service.verifyRegistrationOtp(req);

        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getUser().getEmail()).isEqualTo("minh@example.com");
        assertThat(pending.getConsumedAt()).isNotNull();
        verify(billingService).grantSignupCredits(any(User.class));
    }
}
