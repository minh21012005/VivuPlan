package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.AdminDto;
import com.vivuplan.vivuplan_be.entity.Role;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
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
    public AdminDto.UserSummary updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại"));
        Role newRole = getOrCreateRole(parseRole(role));
        Role userRole = getOrCreateRole(Role.RoleName.USER);
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
