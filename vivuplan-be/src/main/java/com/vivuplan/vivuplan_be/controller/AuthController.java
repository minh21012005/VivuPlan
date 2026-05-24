package com.vivuplan.vivuplan_be.controller;

import com.vivuplan.vivuplan_be.dto.AuthDto;
import com.vivuplan.vivuplan_be.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/request-otp")
    public ResponseEntity<AuthDto.RegisterOtpResponse> requestRegisterOtp(@Valid @RequestBody AuthDto.RegisterRequest req) {
        return ResponseEntity.ok(authService.requestRegistrationOtp(req));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<AuthDto.AuthResponse> verifyRegisterOtp(@Valid @RequestBody AuthDto.VerifyRegisterOtpRequest req) {
        return ResponseEntity.ok(authService.verifyRegistrationOtp(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@Valid @RequestBody AuthDto.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthDto.AuthResponse> googleLogin(@Valid @RequestBody AuthDto.GoogleLoginRequest req) {
        return ResponseEntity.ok(authService.loginWithGoogleToken(req.getIdToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDto.UserDto> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<AuthDto.UserDto> updateProfile(
            @Valid @RequestBody AuthDto.UpdateProfileRequest req,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(authService.updateProfile(userId, req));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<AuthDto.UserDto> changePassword(
            @Valid @RequestBody AuthDto.ChangePasswordRequest req,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(authService.changePassword(userId, req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // JWT is stateless – client simply discards the token
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }
}
