package com.vivuplan.vivuplan_be.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

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

        public static UserDto from(com.vivuplan.vivuplan_be.entity.User u) {
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setName(u.getName());
            dto.setEmail(u.getEmail());
            dto.setAvatarUrl(u.getAvatarUrl());
            dto.setRole(u.getRole().name());
            return dto;
        }
    }
}
