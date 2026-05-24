package com.vivuplan.vivuplan_be.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.vivuplan.vivuplan_be.dto.AuthDto;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
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
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final BillingService billingService;

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapAdminEmail;

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .provider(User.AuthProvider.LOCAL)
                .emailVerified(true)
                .roles(resolveInitialRoles(req.getEmail()))
                .build();

        user = userRepository.save(user);
        billingService.grantSignupCredits(user);
        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng"));

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
            throw new IllegalArgumentException("Email Google chua duoc xac minh");
        }

        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = readStringClaim(payload, "name");
        String avatarUrl = readStringClaim(payload, "picture");

        if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Token Google khong hop le");
        }
        if (name == null || name.isBlank()) {
            int atIndex = email.indexOf("@");
            name = atIndex > 0 ? email.substring(0, atIndex) : email;
        }

        return loginWithGoogle(googleId, email, name, avatarUrl);
    }

    @Transactional
    public AuthDto.AuthResponse loginWithGoogle(String googleId, String email, String name, String avatarUrl) {
        User user = userRepository.findByGoogleId(googleId).orElse(null);
        if (user != null) {
            if (avatarUrl != null) {
                user.setAvatarUrl(avatarUrl);
                user = userRepository.save(user);
            }
        } else {
            user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                user.setGoogleId(googleId);
                user.setProvider(User.AuthProvider.GOOGLE);
                // Always sync avatar from Google on every login
                if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
                user = userRepository.save(user);
            } else {
                user = User.builder()
                        .name(name)
                        .email(email)
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
            throw new IllegalStateException("Chua cau hinh GOOGLE_CLIENT_ID");
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
                throw new IllegalArgumentException("Token Google khong hop le");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new IllegalArgumentException("Khong the xac thuc token Google");
        }
    }

    private String readStringClaim(GoogleIdToken.Payload payload, String claimName) {
        Object value = payload.get(claimName);
        return value instanceof String str ? str : null;
    }

    public AuthDto.UserDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        return AuthDto.UserDto.from(user);
    }

    @Transactional
    public AuthDto.UserDto updateProfile(Long userId, AuthDto.UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));

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

        if (user.getProvider() == User.AuthProvider.GOOGLE) {
            throw new IllegalArgumentException("Tài khoản đăng nhập bằng Google không sử dụng mật khẩu");
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("Tài khoản này chưa thiết lập mật khẩu");
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
