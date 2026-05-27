package com.vivuplan.vivuplan_be.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.vivuplan.vivuplan_be.dto.AuthDto;
import com.vivuplan.vivuplan_be.entity.PasswordResetOtp;
import com.vivuplan.vivuplan_be.entity.RegistrationOtp;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.PasswordResetOtpRepository;
import com.vivuplan.vivuplan_be.repository.RegistrationOtpRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int REGISTRATION_OTP_MAX_ATTEMPTS = 5;
    private static final int REGISTRATION_OTP_MAX_SENDS = 5;
    private static final int REGISTRATION_OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RegistrationOtpRepository registrationOtpRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final BillingService billingService;
    private final EmailService emailService;

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapAdminEmail;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${app.auth.registration-otp-expiry-minutes:${REGISTRATION_OTP_EXPIRY_MINUTES:10}}")
    private Long registrationOtpExpiryMinutes;

    @Transactional
    public AuthDto.RegisterOtpResponse requestRegistrationOtp(AuthDto.RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());
        String name = req.getName() == null ? "" : req.getName().trim();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        LocalDateTime now = LocalDateTime.now();
        String otp = generateOtp();
        LocalDateTime expiresAt = now.plusMinutes(registrationOtpExpiryMinutes);
        RegistrationOtp pending = registrationOtpRepository.findByEmail(email)
                .orElseGet(RegistrationOtp::new);
        validateRegistrationOtpSendLimit(pending, now);
        pending.setEmail(email);
        pending.setName(name);
        pending.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiresAt(expiresAt);
        pending.setAttempts(0);
        pending.setResendCount(nextResendCount(pending, now));
        pending.setLastSentAt(now);
        pending.setConsumedAt(null);
        registrationOtpRepository.save(pending);

        emailService.sendRegistrationOtpAsync(email, name, otp, registrationOtpExpiryMinutes);
        return new AuthDto.RegisterOtpResponse(email, registrationOtpExpiryMinutes * 60);
    }

    @Transactional
    public AuthDto.AuthResponse verifyRegistrationOtp(AuthDto.VerifyRegisterOtpRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        RegistrationOtp pending = registrationOtpRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Mã xác nhận không hợp lệ hoặc đã hết hạn"));
        if (pending.getConsumedAt() != null || pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Mã xác nhận không hợp lệ hoặc đã hết hạn");
        }
        if (pending.getAttempts() != null && pending.getAttempts() >= REGISTRATION_OTP_MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Bạn đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã mới.");
        }
        if (!passwordEncoder.matches(req.getOtp(), pending.getOtpHash())) {
            pending.setAttempts((pending.getAttempts() == null ? 0 : pending.getAttempts()) + 1);
            registrationOtpRepository.save(pending);
            throw new IllegalArgumentException("Mã xác nhận không đúng");
        }

        User user = User.builder()
                .name(pending.getName())
                .email(email)
                .password(pending.getPasswordHash())
                .provider(User.AuthProvider.LOCAL)
                .emailVerified(true)
                .roles(resolveInitialRoles(email))
                .build();

        user = userRepository.save(user);
        registrationOtpRepository.delete(pending);
        billingService.grantSignupCredits(user);
        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    @Transactional
    public AuthDto.ForgotPasswordOtpResponse requestPasswordResetOtp(AuthDto.ForgotPasswordRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email chưa được đăng ký"));
        ensureAccountNotLocked(user);
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Tài khoản này đang đăng nhập bằng Google. Vui lòng dùng Google để tiếp tục.");
        }

        LocalDateTime now = LocalDateTime.now();
        String otp = generateOtp();
        LocalDateTime expiresAt = now.plusMinutes(registrationOtpExpiryMinutes);
        PasswordResetOtp pending = passwordResetOtpRepository.findByEmail(email)
                .orElseGet(PasswordResetOtp::new);
        validatePasswordResetOtpSendLimit(pending, now);
        pending.setEmail(email);
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiresAt(expiresAt);
        pending.setAttempts(0);
        pending.setResendCount(nextPasswordResetResendCount(pending, now));
        pending.setLastSentAt(now);
        pending.setConsumedAt(null);
        passwordResetOtpRepository.save(pending);

        emailService.sendPasswordResetOtpAsync(email, user.getName(), otp, registrationOtpExpiryMinutes);
        return new AuthDto.ForgotPasswordOtpResponse(email, registrationOtpExpiryMinutes * 60);
    }

    @Transactional
    public void resetPasswordWithOtp(AuthDto.ResetPasswordRequest req) {
        String email = normalizeEmail(req.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));
        ensureAccountNotLocked(user);
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Tài khoản này đang đăng nhập bằng Google. Vui lòng dùng Google để tiếp tục.");
        }

        PasswordResetOtp pending = passwordResetOtpRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Yêu cầu đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));
        if (pending.getConsumedAt() != null || pending.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Yêu cầu đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        if (pending.getAttempts() != null && pending.getAttempts() >= REGISTRATION_OTP_MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Bạn đã nhập sai quá số lần cho phép. Vui lòng gửi lại mã mới.");
        }
        if (!passwordEncoder.matches(req.getOtp(), pending.getOtpHash())) {
            pending.setAttempts((pending.getAttempts() == null ? 0 : pending.getAttempts()) + 1);
            passwordResetOtpRepository.save(pending);
            throw new IllegalArgumentException("Mã xác nhận không đúng");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        passwordResetOtpRepository.delete(pending);
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepository.findByEmail(normalizeEmail(req.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng"));
        ensureAccountNotLocked(user);

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Tài khoản này đang đăng nhập bằng Google. Vui lòng dùng Google để tiếp tục.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng");
        }

        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    @Transactional
    public AuthDto.AuthResponse loginWithGoogleToken(String idTokenValue) {
        GoogleIdToken.Payload payload = verifyGoogleToken(idTokenValue);
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new IllegalArgumentException("Email Google chưa được xác minh");
        }

        String googleId = payload.getSubject();
        String email = normalizeEmail(payload.getEmail());
        String name = readStringClaim(payload, "name");
        String avatarUrl = readStringClaim(payload, "picture");

        if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Token Google không hợp lệ");
        }
        if (name == null || name.isBlank()) {
            int atIndex = email.indexOf("@");
            name = atIndex > 0 ? email.substring(0, atIndex) : email;
        }

        return loginWithGoogle(googleId, email, name, avatarUrl);
    }

    @Transactional
    public AuthDto.AuthResponse loginWithGoogle(String googleId, String email, String name, String avatarUrl) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByGoogleId(googleId).orElse(null);
        if (user != null) {
            ensureAccountNotLocked(user);
            if (avatarUrl != null) {
                user.setAvatarUrl(avatarUrl);
                user = userRepository.save(user);
            }
        } else {
            user = userRepository.findByEmail(normalizedEmail).orElse(null);
            if (user != null) {
                ensureAccountNotLocked(user);
                user.setGoogleId(googleId);
                user.setEmailVerified(true);
                if (user.getPassword() == null || user.getPassword().isBlank()) {
                    user.setProvider(User.AuthProvider.GOOGLE);
                }
                if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
                user = userRepository.save(user);
            } else {
                user = User.builder()
                        .name(name)
                        .email(normalizedEmail)
                        .googleId(googleId)
                        .avatarUrl(avatarUrl)
                        .provider(User.AuthProvider.GOOGLE)
                        .emailVerified(true)
                        .roles(resolveInitialRoles(email))
                        .build();
                user = userRepository.save(user);
                billingService.grantSignupCredits(user);
            }
        }

        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    private GoogleIdToken.Payload verifyGoogleToken(String idTokenValue) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình GOOGLE_CLIENT_ID");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenValue);
            if (idToken == null) {
                throw new IllegalArgumentException("Token Google không hợp lệ");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new IllegalArgumentException("Không thể xác thực token Google");
        }
    }

    private String readStringClaim(GoogleIdToken.Payload payload, String claimName) {
        Object value = payload.get(claimName);
        return value instanceof String str ? str : null;
    }

    public AuthDto.UserDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        ensureAccountNotLocked(user);
        return AuthDto.UserDto.from(user);
    }

    @Transactional
    public AuthDto.UserDto updateProfile(Long userId, AuthDto.UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        ensureAccountNotLocked(user);

        if (user.getProvider() == User.AuthProvider.GOOGLE) {
            throw new IllegalArgumentException("Tài khoản Google không được phép cập nhật thông tin thủ công");
        }

        user.setName(req.getName().trim());

        // Avatar URL is only updatable for LOCAL accounts
        user.setAvatarUrl(req.getAvatarUrl() != null && !req.getAvatarUrl().isBlank()
                ? req.getAvatarUrl().trim() : null);

        user = userRepository.save(user);
        return AuthDto.UserDto.from(user);
    }

    @Transactional
    public AuthDto.UserDto changePassword(Long userId, AuthDto.ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        ensureAccountNotLocked(user);

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Tài khoản đăng nhập bằng Google không sử dụng mật khẩu");
        }

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user = userRepository.save(user);
        return AuthDto.UserDto.from(user);
    }

    private String generateToken(User user) {
        return jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRoleNames());
    }

    private void ensureAccountNotLocked(User user) {
        if (user.isAccountLocked()) {
            throw new IllegalArgumentException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ hỗ trợ.");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOtp() {
        return String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
    }

    private void validateRegistrationOtpSendLimit(RegistrationOtp pending, LocalDateTime now) {
        if (pending.getId() == null || pending.getLastSentAt() == null) {
            return;
        }
        if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(now)) {
            return;
        }
        if (pending.getLastSentAt().plusSeconds(REGISTRATION_OTP_RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException("Vui lòng chờ một chút trước khi gửi lại mã xác nhận.");
        }
        if (pending.getResendCount() != null && pending.getResendCount() >= REGISTRATION_OTP_MAX_SENDS) {
            throw new IllegalArgumentException("Bạn đã gửi mã quá nhiều lần. Vui lòng thử lại sau.");
        }
    }

    private int nextResendCount(RegistrationOtp pending, LocalDateTime now) {
        if (pending.getId() == null || pending.getExpiresAt() == null || pending.getExpiresAt().isBefore(now)) {
            return 1;
        }
        return (pending.getResendCount() == null ? 0 : pending.getResendCount()) + 1;
    }

    private void validatePasswordResetOtpSendLimit(PasswordResetOtp pending, LocalDateTime now) {
        if (pending.getId() == null || pending.getLastSentAt() == null) {
            return;
        }
        if (pending.getExpiresAt() != null && pending.getExpiresAt().isBefore(now)) {
            return;
        }
        if (pending.getLastSentAt().plusSeconds(REGISTRATION_OTP_RESEND_COOLDOWN_SECONDS).isAfter(now)) {
            throw new IllegalArgumentException("Vui lòng chờ một chút trước khi gửi lại mã xác nhận.");
        }
        if (pending.getResendCount() != null && pending.getResendCount() >= REGISTRATION_OTP_MAX_SENDS) {
            throw new IllegalArgumentException("Bạn đã gửi mã quá nhiều lần. Vui lòng thử lại sau.");
        }
    }

    private int nextPasswordResetResendCount(PasswordResetOtp pending, LocalDateTime now) {
        if (pending.getId() == null || pending.getExpiresAt() == null || pending.getExpiresAt().isBefore(now)) {
            return 1;
        }
        return (pending.getResendCount() == null ? 0 : pending.getResendCount()) + 1;
    }

    private java.util.Set<Role> resolveInitialRoles(String email) {
        java.util.Set<Role> roles = new java.util.HashSet<>();
        roles.add(getOrCreateRole(Role.RoleName.USER));
        if (bootstrapAdminEmail != null
                && !bootstrapAdminEmail.isBlank()
                && bootstrapAdminEmail.trim().equalsIgnoreCase(email.trim())) {
            roles.add(getOrCreateRole(Role.RoleName.ADMIN));
        }
        return roles;
    }

    private Role getOrCreateRole(Role.RoleName roleName) {
        return roleRepository.findByName(roleName).orElseGet(() ->
                roleRepository.save(Role.builder()
                        .name(roleName)
                        .description(roleName == Role.RoleName.ADMIN ? "System administrator" : "Standard user")
                        .build())
        );
    }
}
