package com.vivuplan.vivuplan_be.service;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapAdminEmail;

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
    public AuthDto.AuthResponse loginWithGoogle(String googleId, String email, String name, String avatarUrl) {
        User user = userRepository.findByGoogleId(googleId).orElseGet(() ->
                userRepository.findByEmail(email).map(u -> {
                    u.setGoogleId(googleId);
                    u.setProvider(User.AuthProvider.GOOGLE);
                    if (u.getAvatarUrl() == null) u.setAvatarUrl(avatarUrl);
                    return userRepository.save(u);
                }).orElseGet(() -> {
                    User newUser = User.builder()
                            .name(name)
                            .email(email)
                            .googleId(googleId)
                            .avatarUrl(avatarUrl)
                            .provider(User.AuthProvider.GOOGLE)
                            .emailVerified(true)
                            .roles(resolveInitialRoles(email))
                            .build();
                    return userRepository.save(newUser);
                })
        );

        String token = generateToken(user);
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    public AuthDto.UserDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
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
