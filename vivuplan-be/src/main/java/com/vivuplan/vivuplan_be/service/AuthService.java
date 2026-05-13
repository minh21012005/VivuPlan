package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AuthDto;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
                .build();

        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Email hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email hoặc mật khẩu không đúng");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
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
                            .build();
                    return userRepository.save(newUser);
                })
        );

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        return new AuthDto.AuthResponse(token, AuthDto.UserDto.from(user));
    }

    public AuthDto.UserDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        return AuthDto.UserDto.from(user);
    }
}
