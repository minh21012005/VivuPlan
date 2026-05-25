package com.vivuplan.vivuplan_be.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.util.List;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Tên không được để trống")
        private String name;

        @NotBlank @Email(message = "Email không hợp lệ")
        private String email;

        @NotBlank @Size(min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
        private String password;
    }

    @Data
    public static class VerifyRegisterOtpRequest {
        @NotBlank @Email(message = "Email không hợp lệ")
        private String email;

        @NotBlank(message = "Mã xác nhận không được để trống")
        @Pattern(regexp = "\\d{6}", message = "Mã xác nhận gồm 6 chữ số")
        private String otp;
    }

    @Data
    public static class RegisterOtpResponse {
        private String email;
        private Long expiresInSeconds;

        public RegisterOtpResponse(String email, Long expiresInSeconds) {
            this.email = email;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    @Data
    public static class LoginRequest {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class GoogleLoginRequest {
        @NotBlank
        private String idToken;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private UserDto user;

        public AuthResponse(String token, UserDto user) {
            this.token = token;
            this.user = user;
        }
    }

    @Data
    public static class UserDto {
        private Long id;
        private String name;
        private String email;
        private String avatarUrl;
        private String role;
        private List<String> roles;
        private String provider;
        private Boolean accountLocked;

        public static UserDto from(com.vivuplan.vivuplan_be.entity.User u) {
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setName(u.getName());
            dto.setEmail(u.getEmail());
            dto.setAvatarUrl(u.getAvatarUrl());
            dto.setRole(u.getPrimaryRoleName());
            dto.setRoles(u.getRoleNames().stream().sorted().toList());
            dto.setProvider(u.getProvider() != null ? u.getProvider().name() : "LOCAL");
            dto.setAccountLocked(u.isAccountLocked());
            return dto;
        }
    }

    @Data
    public static class UpdateProfileRequest {
        @NotBlank(message = "Tên không được để trống")
        @Size(max = 100, message = "Tên không được vượt quá 100 ký tự")
        private String name;

        // Only applied for LOCAL users; ignored for GOOGLE users
        private String avatarUrl;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "Mật khẩu hiện tại không được để trống")
        private String currentPassword;

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = 8, message = "Mật khẩu mới tối thiểu 8 ký tự")
        private String newPassword;
    }
}
