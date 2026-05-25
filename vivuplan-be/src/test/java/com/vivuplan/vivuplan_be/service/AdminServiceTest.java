package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.PaymentOrderRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import com.vivuplan.vivuplan_be.repository.UserWalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserWalletRepository userWalletRepository;

    private AdminService service() {
        return new AdminService(
                userRepository,
                roleRepository,
                tripRepository,
                paymentOrderRepository,
                userWalletRepository);
    }

    @Test
    void updateUserLockLocksUser() {
        AdminService service = service();
        User user = User.builder()
                .id(2L)
                .name("User")
                .email("user@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.USER).build()))
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminDto.UserSummary response = service.updateUserLock(1L, 2L, true);

        assertThat(user.isAccountLocked()).isTrue();
        assertThat(response.getAccountLocked()).isTrue();
    }

    @Test
    void updateUserLockUnlocksUser() {
        AdminService service = service();
        User user = User.builder()
                .id(2L)
                .name("User")
                .email("user@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.USER).build()))
                .accountLocked(true)
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminDto.UserSummary response = service.updateUserLock(1L, 2L, false);

        assertThat(user.isAccountLocked()).isFalse();
        assertThat(response.getAccountLocked()).isFalse();
    }

    @Test
    void updateUserLockRejectsSelfLock() {
        AdminService service = service();
        User admin = User.builder()
                .id(1L)
                .name("Admin")
                .email("admin@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.ADMIN).build()))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateUserLock(1L, 1L, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(admin.isAccountLocked()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserLockRejectsLastUnlockedAdmin() {
        AdminService service = service();
        User admin = User.builder()
                .id(2L)
                .name("Admin")
                .email("admin@example.com")
                .roles(Set.of(Role.builder().name(Role.RoleName.ADMIN).build()))
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.countUnlockedByRoleName(Role.RoleName.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateUserLock(1L, 2L, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(admin.isAccountLocked()).isFalse();
        verify(userRepository, never()).save(any(User.class));
    }
}
