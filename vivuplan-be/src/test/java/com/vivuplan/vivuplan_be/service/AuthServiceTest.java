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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
        verify(emailService).sendRegistrationOtpAsync(any(), any(), any(), any(Long.class));
    }

    @Test
    void requestRegistrationOtpThrowsWhenResendingTooSoon() {
        AuthService service = service();
        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest();
        req.setName("Minh");
        req.setEmail("minh@example.com");
        req.setPassword("password123");
        RegistrationOtp pending = RegistrationOtp.builder()
                .id(1L)
                .email("minh@example.com")
                .name("Minh")
                .passwordHash("old-password")
                .otpHash("old-otp")
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .lastSentAt(LocalDateTime.now().minusSeconds(20))
                .resendCount(1)
                .attempts(0)
                .build();

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.requestRegistrationOtp(req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(registrationOtpRepository, never()).save(any());
        verify(emailService, never()).sendRegistrationOtpAsync(any(), any(), any(), any(Long.class));
    }

    @Test
    void requestRegistrationOtpThrowsWhenSendLimitReached() {
        AuthService service = service();
        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest();
        req.setName("Minh");
        req.setEmail("minh@example.com");
        req.setPassword("password123");
        RegistrationOtp pending = RegistrationOtp.builder()
                .id(1L)
                .email("minh@example.com")
                .name("Minh")
                .passwordHash("old-password")
                .otpHash("old-otp")
                .expiresAt(LocalDateTime.now().plusMinutes(9))
                .lastSentAt(LocalDateTime.now().minusMinutes(2))
                .resendCount(5)
                .attempts(0)
                .build();

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.requestRegistrationOtp(req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(registrationOtpRepository, never()).save(any());
        verify(emailService, never()).sendRegistrationOtpAsync(any(), any(), any(), any(Long.class));
    }

    @Test
    void requestRegistrationOtpThrowsWhenEmailAlreadyBelongsToGoogleAccount() {
        AuthService service = service();
        AuthDto.RegisterRequest req = new AuthDto.RegisterRequest();
        req.setName("Minh");
        req.setEmail("Minh@Example.com ");
        req.setPassword("password123");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.requestRegistrationOtp(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email đã được sử dụng");

        verify(registrationOtpRepository, never()).save(any());
        verify(emailService, never()).sendRegistrationOtpAsync(any(), any(), any(), any(Long.class));
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
        verify(registrationOtpRepository).delete(pending);
        verify(billingService).grantSignupCredits(any(User.class));
    }

    @Test
    void verifyRegistrationOtpThrowsWhenOtpIsWrongAndIncrementsAttempts() {
        AuthService service = service();
        RegistrationOtp pending = RegistrationOtp.builder()
                .email("minh@example.com")
                .name("Minh")
                .passwordHash("encoded-password")
                .otpHash("encoded-otp")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(1)
                .build();
        AuthDto.VerifyRegisterOtpRequest req = new AuthDto.VerifyRegisterOtpRequest();
        req.setEmail("minh@example.com");
        req.setOtp("000000");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("000000", "encoded-otp")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyRegistrationOtp(req))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(pending.getAttempts()).isEqualTo(2);
        verify(registrationOtpRepository).save(pending);
        verify(userRepository, never()).save(any(User.class));
        verify(billingService, never()).grantSignupCredits(any(User.class));
    }

    @Test
    void verifyRegistrationOtpThrowsWhenOtpExpired() {
        AuthService service = service();
        RegistrationOtp pending = RegistrationOtp.builder()
                .email("minh@example.com")
                .name("Minh")
                .passwordHash("encoded-password")
                .otpHash("encoded-otp")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .attempts(0)
                .build();
        AuthDto.VerifyRegisterOtpRequest req = new AuthDto.VerifyRegisterOtpRequest();
        req.setEmail("minh@example.com");
        req.setOtp("123456");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.verifyRegistrationOtp(req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).save(any(User.class));
        verify(billingService, never()).grantSignupCredits(any(User.class));
    }

    @Test
    void verifyRegistrationOtpThrowsWhenNoPendingOtpExists() {
        AuthService service = service();
        AuthDto.VerifyRegisterOtpRequest req = new AuthDto.VerifyRegisterOtpRequest();
        req.setEmail("minh@example.com");
        req.setOtp("123456");

        when(userRepository.existsByEmail("minh@example.com")).thenReturn(false);
        when(registrationOtpRepository.findByEmail("minh@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyRegistrationOtp(req))
                .isInstanceOf(IllegalArgumentException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).save(any(User.class));
        verify(billingService, never()).grantSignupCredits(any(User.class));
    }

    @Test
    void loginNormalizesEmailBeforeLookup() {
        AuthService service = service();
        Role userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        User user = User.builder()
                .id(9L)
                .name("Minh")
                .email("minh@example.com")
                .password("encoded-password")
                .provider(User.AuthProvider.LOCAL)
                .roles(Set.of(userRole))
                .build();
        AuthDto.LoginRequest req = new AuthDto.LoginRequest();
        req.setEmail(" Minh@Example.com ");
        req.setPassword("password123");

        when(userRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");

        AuthDto.AuthResponse response = service.login(req);

        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getUser().getEmail()).isEqualTo("minh@example.com");
    }

    @Test
    void loginRejectsGoogleOnlyAccountWithoutPassword() {
        AuthService service = service();
        User user = User.builder()
                .id(9L)
                .name("Minh")
                .email("minh@example.com")
                .googleId("google-1")
                .provider(User.AuthProvider.GOOGLE)
                .build();
        AuthDto.LoginRequest req = new AuthDto.LoginRequest();
        req.setEmail("minh@example.com");
        req.setPassword("password123");

        when(userRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tài khoản này đang đăng nhập bằng Google. Vui lòng dùng Google để tiếp tục.");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginWithGoogleLinksExistingPasswordAccountWithoutChangingProvider() {
        AuthService service = service();
        Role userRole = Role.builder().id(1L).name(Role.RoleName.USER).build();
        User existing = User.builder()
                .id(9L)
                .name("Minh")
                .email("minh@example.com")
                .password("encoded-password")
                .provider(User.AuthProvider.LOCAL)
                .emailVerified(false)
                .roles(Set.of(userRole))
                .build();

        when(userRepository.findByGoogleId("google-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("minh@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");

        AuthDto.AuthResponse response = service.loginWithGoogle(
                "google-1",
                "Minh@Example.com ",
                "Google Minh",
                "https://example.com/avatar.png");

        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getUser().getProvider()).isEqualTo("LOCAL");
        assertThat(existing.getGoogleId()).isEqualTo("google-1");
        assertThat(existing.getProvider()).isEqualTo(User.AuthProvider.LOCAL);
        assertThat(existing.getEmailVerified()).isTrue();
        assertThat(existing.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(billingService, never()).grantSignupCredits(any(User.class));
    }

    @Test
    void changePasswordAllowsPasswordAccountLinkedToGoogle() {
        AuthService service = service();
        User user = User.builder()
                .id(9L)
                .name("Minh")
                .email("minh@example.com")
                .password("encoded-password")
                .googleId("google-1")
                .provider(User.AuthProvider.LOCAL)
                .build();
        AuthDto.ChangePasswordRequest req = new AuthDto.ChangePasswordRequest();
        req.setCurrentPassword("old-password");
        req.setNewPassword("new-password");

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "encoded-password")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-password")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-encoded-password");
        when(userRepository.save(user)).thenReturn(user);

        AuthDto.UserDto response = service.changePassword(9L, req);

        assertThat(response.getEmail()).isEqualTo("minh@example.com");
        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
    }

    @Test
    void changePasswordRejectsGoogleOnlyAccountWithoutPassword() {
        AuthService service = service();
        User user = User.builder()
                .id(9L)
                .name("Minh")
                .email("minh@example.com")
                .googleId("google-1")
                .provider(User.AuthProvider.GOOGLE)
                .build();
        AuthDto.ChangePasswordRequest req = new AuthDto.ChangePasswordRequest();
        req.setCurrentPassword("old-password");
        req.setNewPassword("new-password");

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(9L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tài khoản đăng nhập bằng Google không sử dụng mật khẩu");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).save(any(User.class));
    }
}
