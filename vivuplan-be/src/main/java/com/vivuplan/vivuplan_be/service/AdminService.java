package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.entity.PaymentOrder;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import com.vivuplan.vivuplan_be.repository.PaymentOrderRepository;
import com.vivuplan.vivuplan_be.repository.RoleRepository;
import com.vivuplan.vivuplan_be.repository.TripRepository;
import com.vivuplan.vivuplan_be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TripRepository tripRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    @Transactional(readOnly = true)
    public AdminDto.StatsResponse getStats() {
        AdminDto.StatsResponse stats = new AdminDto.StatsResponse();
        stats.setTotalUsers(userRepository.count());
        stats.setAdminUsers(userRepository.countByRoleName(Role.RoleName.ADMIN));
        stats.setTotalTrips(tripRepository.count());
        stats.setPublicTrips(tripRepository.countByIsPublicTrue());
        stats.setDraftTrips(tripRepository.countByStatus(Trip.TripStatus.DRAFT));
        stats.setPlannedTrips(tripRepository.countByStatus(Trip.TripStatus.PLANNED));
        stats.setCompletedTrips(tripRepository.countByStatus(Trip.TripStatus.COMPLETED));
        stats.setPaidOrders(paymentOrderRepository.countByStatus(PaymentOrder.Status.PAID));
        stats.setTotalRevenue(paymentOrderRepository.sumPaidAmountByStatus(PaymentOrder.Status.PAID));
        return stats;
    }

    @Transactional(readOnly = true)
    public Page<AdminDto.UserSummary> listUsers(int page, int size) {
        return userRepository.findAll(
                PageRequest.of(page, clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(AdminDto.UserSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminDto.TripSummary> listTrips(int page, int size) {
        return tripRepository.findAll(
                PageRequest.of(page, clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(AdminDto.TripSummary::from);
    }

    @Transactional
    public AdminDto.UserSummary updateUserRole(Long actorUserId, Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        Role newRole = getOrCreateRole(parseRole(role));
        Role userRole = getOrCreateRole(Role.RoleName.USER);
        if (actorUserId != null
                && actorUserId.equals(userId)
                && user.hasRole(Role.RoleName.ADMIN)
                && newRole.getName() == Role.RoleName.USER) {
            throw new IllegalArgumentException("Không thể tự hạ quyền tài khoản admin đang đăng nhập");
        }
        if (user.hasRole(Role.RoleName.ADMIN)
                && newRole.getName() == Role.RoleName.USER
                && userRepository.countByRoleName(Role.RoleName.ADMIN) <= 1) {
            throw new IllegalArgumentException("Không thể hạ quyền admin cuối cùng của hệ thống");
        }
        user.getRoles().clear();
        user.getRoles().add(userRole);
        if (newRole.getName() == Role.RoleName.ADMIN) {
            user.getRoles().add(newRole);
        }
        return AdminDto.UserSummary.from(userRepository.save(user));
    }

    private Role.RoleName parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role không được để trống");
        }
        try {
            return Role.RoleName.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Role không hợp lệ. Chỉ hỗ trợ USER hoặc ADMIN");
        }
    }

    private Role getOrCreateRole(Role.RoleName roleName) {
        return roleRepository.findByName(roleName).orElseGet(() ->
                roleRepository.save(Role.builder()
                        .name(roleName)
                        .description(roleName == Role.RoleName.ADMIN ? "System administrator" : "Standard user")
                        .build())
        );
    }

    private int clampPageSize(int size) {
        if (size < 1) return 20;
        return Math.min(size, 100);
    }
}
